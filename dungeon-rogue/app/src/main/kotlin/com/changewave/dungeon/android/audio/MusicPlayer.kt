package com.changewave.dungeon.android.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.changewave.dungeon.audio.AmbientMusicEngine
import kotlin.math.pow

/**
 * Riproduce la musica generata da [AmbientMusicEngine] con un [AudioTrack] in
 * modalita' streaming: un thread dedicato chiede blocchi al sintetizzatore e li
 * scrive nella coda audio. Nessun file, nessuna decodifica.
 *
 * Il thread di scrittura si blocca dentro `AudioTrack.write` quando il buffer e'
 * pieno, quindi il ritmo di generazione lo impone la scheda audio: non serve
 * alcun temporizzatore.
 */
class MusicPlayer(
    private val sampleRate: Int = AmbientMusicEngine.DEFAULT_SAMPLE_RATE,
) {

    private val engine = AmbientMusicEngine(sampleRate = sampleRate)

    private var track: AudioTrack? = null
    private var thread: Thread? = null

    @Volatile
    private var running = false

    @Volatile
    private var enabled = true

    @Volatile
    private var foreground = false

    private val lock = Any()

    /** Volume dell'interfaccia, 0..1 lineare. */
    fun setVolume(volume: Float) {
        // Curva percettiva: l'orecchio non e' lineare, con una rampa diretta la
        // meta' dello slider suonerebbe quasi come il massimo.
        engine.masterVolume = volume.coerceIn(0f, 1f).toDouble().pow(1.8) * 0.95
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        if (value) resume() else stopPlayback(resetEngine = true)
    }

    fun setDepth(depth: Int) {
        engine.setDepth(depth)
    }

    /** Da chiamare quando l'app torna in primo piano. */
    fun resume() {
        foreground = true
        if (!enabled) return
        startPlayback()
    }

    /** Da chiamare quando l'app va in background: libera il thread e il device audio. */
    fun pause() {
        foreground = false
        stopPlayback(resetEngine = false)
    }

    fun release() {
        stopPlayback(resetEngine = true)
    }

    private fun startPlayback() = synchronized(lock) {
        if (running) return@synchronized
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(BLOCK_FRAMES * 2)
        // Buffer generoso: la musica e' ambient, la latenza non conta, mentre un
        // sottorun produrrebbe un vuoto udibile.
        val bufferBytes = minBuffer * 4

        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(bufferBytes)
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

    private fun stopPlayback(resetEngine: Boolean) {
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

    companion object {
        /** 2048 campioni a 22,05 kHz = 93 ms per blocco: poche chiamate, nessun singhiozzo. */
        const val BLOCK_FRAMES = 2048
    }
}
