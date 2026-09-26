package com.changewave.dungeon.android.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import com.changewave.dungeon.audio.AmbientMusicEngine
import kotlin.math.pow

/**
 * Riproduzione della colonna sonora. Due sorgenti possibili:
 *
 *  - la musica generata da [AmbientMusicEngine], scritta in streaming su un
 *    [AudioTrack] da un thread dedicato: nessun file, nessuna decodifica;
 *  - un brano scelto dall'utente fra i file del telefono, riprodotto in loop con
 *    [MediaPlayer], limitatamente al livello configurato.
 *
 * La scelta fra le due avviene in [refresh], che e' l'unico punto in cui si
 * accendono e spengono le sorgenti: ogni cambiamento di stato (volume, livello,
 * primo piano, brano scelto) passa da lì, così non possono restare attive
 * entrambe.
 */
class MusicPlayer(
    private val context: Context,
    private val sampleRate: Int = AmbientMusicEngine.DEFAULT_SAMPLE_RATE,
) {

    private val engine = AmbientMusicEngine(sampleRate = sampleRate)

    private var track: AudioTrack? = null
    private var thread: Thread? = null
    private var mediaPlayer: MediaPlayer? = null

    @Volatile
    private var running = false

    private var enabled = true
    private var foreground = false
    private var volume = 0.6f
    private var depth = 1

    private var customUri: Uri? = null
    private var customDepth = 1

    /** Invocato quando il brano scelto dall'utente non e' riproducibile. */
    var onCustomTrackFailed: ((String) -> Unit)? = null

    private val lock = Any()

    // --- API ------------------------------------------------------------------

    fun setVolume(value: Float) {
        volume = value.coerceIn(0f, 1f)
        // Curva percettiva: l'orecchio non e' lineare, con una rampa diretta la
        // meta' dello slider suonerebbe quasi come il massimo.
        val curved = volume.toDouble().pow(1.8)
        engine.masterVolume = curved * 0.95
        runCatching { mediaPlayer?.setVolume(curved.toFloat(), curved.toFloat()) }
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        refresh()
    }

    fun setDepth(value: Int) {
        engine.setDepth(value)
        if (value == depth) return
        depth = value
        // Il cambio di livello puo' far passare dal brano personalizzato alla
        // musica generata, e viceversa.
        refresh()
    }

    /** Imposta (o rimuove, con [uri] nullo) il brano personalizzato e il livello in cui suona. */
    fun setCustomTrack(uri: Uri?, trackDepth: Int) {
        customUri = uri
        customDepth = trackDepth
        refresh()
    }

    fun resume() {
        foreground = true
        refresh()
    }

    fun pause() {
        foreground = false
        refresh()
    }

    fun release() {
        foreground = false
        enabled = false
        refresh()
        engine.reset()
    }

    // --- scelta della sorgente ------------------------------------------------

    private fun useCustomTrack(): Boolean = customUri != null && depth == customDepth

    private fun refresh() {
        val shouldPlay = enabled && foreground
        when {
            !shouldPlay -> {
                stopSynth(resetEngine = !enabled)
                stopCustom()
            }
            useCustomTrack() -> {
                stopSynth(resetEngine = false)
                startCustom()
            }
            else -> {
                stopCustom()
                startSynth()
            }
        }
    }

    // --- musica generata ------------------------------------------------------

    private fun startSynth() = synchronized(lock) {
        if (running) return@synchronized
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(BLOCK_FRAMES * 2)
        // Buffer generoso: la musica e' ambient, la latenza non conta, mentre un
        // sottorun produrrebbe un vuoto udibile.
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(gameAudioAttributes())
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(minBuffer * 4)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        track = audioTrack
        running = true
        audioTrack.play()

        thread = Thread({
            val block = ShortArray(BLOCK_FRAMES)
            try {
                while (running) {
                    engine.renderBlock(block)
                    var offset = 0
                    while (offset < block.size && running) {
                        val written = audioTrack.write(block, offset, block.size - offset)
                        if (written <= 0) break // dispositivo audio non disponibile
                        offset += written
                    }
                }
            } catch (_: IllegalStateException) {
                // AudioTrack rilasciato mentre scrivevamo: uscita silenziosa.
            }
        }, "musica-dungeon").apply {
            priority = Thread.NORM_PRIORITY + 1
            isDaemon = true
            start()
        }
    }

    private fun stopSynth(resetEngine: Boolean) {
        val (oldThread, oldTrack) = synchronized(lock) {
            if (!running) return
            running = false
            val t = thread
            val a = track
            thread = null
            track = null
            t to a
        }
        oldThread?.join(500)
        runCatching {
            oldTrack?.pause()
            oldTrack?.flush()
            oldTrack?.release()
        }
        if (resetEngine) engine.reset()
    }

    // --- brano scelto dall'utente ---------------------------------------------

    private fun startCustom() {
        val uri = customUri ?: return
        if (mediaPlayer != null) {
            runCatching { if (mediaPlayer?.isPlaying == false) mediaPlayer?.start() }
            return
        }
        val player = MediaPlayer()
        val curved = volume.toDouble().pow(1.8).toFloat()
        try {
            player.setAudioAttributes(gameAudioAttributes())
            player.setDataSource(context, uri)
            player.isLooping = true
            player.setVolume(curved, curved)
            player.setOnErrorListener { _, what, extra ->
                reportCustomFailure("errore di riproduzione ($what/$extra)")
                true
            }
            player.setOnPreparedListener { it.start() }
            player.prepareAsync()
            mediaPlayer = player
        } catch (e: Exception) {
            // File spostato, cancellato, formato non supportato o permesso
            // persistente revocato: si torna alla musica generata.
            runCatching { player.release() }
            mediaPlayer = null
            reportCustomFailure(e.message ?: "file non leggibile")
        }
    }

    private fun stopCustom() {
        val player = synchronized(lock) {
            val p = mediaPlayer
            mediaPlayer = null
            p
        } ?: return
        runCatching {
            if (player.isPlaying) player.stop()
            player.release()
        }
    }

    private fun reportCustomFailure(reason: String) {
        customUri = null
        stopCustom()
        onCustomTrackFailed?.invoke(reason)
        refresh()
    }

    private fun gameAudioAttributes(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    companion object {
        /** 2048 campioni a 22,05 kHz = 93 ms per blocco: poche chiamate, nessun singhiozzo. */
        const val BLOCK_FRAMES = 2048
    }
}
