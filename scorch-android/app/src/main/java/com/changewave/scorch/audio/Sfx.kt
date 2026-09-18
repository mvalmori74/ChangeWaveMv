package com.changewave.scorch.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import com.changewave.scorch.game.SoundBank
import java.io.File
import java.io.IOException
import kotlin.math.abs

/**
 * Riproduce gli effetti sintetizzati da [SfxSynth]. I file vengono generati una volta
 * nella cache e caricati in un [SoundPool]; il fischio del proiettile e' un anello la cui
 * velocita' di riproduzione segue la caduta.
 */
object Sfx : SoundBank {

    private const val TAG = "ScorchSfx"
    private const val PREFS = "scorch_prefs"
    private const val KEY_ENABLED = "audio_enabled"

    private var pool: SoundPool? = null
    private val ids = HashMap<String, Int>()
    private val loaded = HashSet<Int>()

    @Volatile
    var enabled = true
        private set

    private var prefs: android.content.SharedPreferences? = null
    private var whistleStream = 0
    private var whistleRate = 1f

    /** Prepara l'audio: la sintesi (poche centinaia di ms) avviene fuori dal thread UI. */
    fun init(context: Context) {
        val app = context.applicationContext
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        enabled = prefs?.getBoolean(KEY_ENABLED, true) ?: true
        if (pool != null) return

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val p = SoundPool.Builder()
            .setMaxStreams(12)
            .setAudioAttributes(attributes)
            .build()
        p.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) synchronized(loaded) { loaded.add(sampleId) }
        }
        pool = p

        Thread({
            try {
                val dir = File(app.cacheDir, "sfx")
                val files = SfxSynth.ensureFiles(dir)
                for ((name, file) in files) {
                    val id = p.load(file.absolutePath, 1)
                    if (id != 0) synchronized(ids) { ids[name] = id }
                }
            } catch (e: IOException) {
                Log.w(TAG, "generazione suoni non riuscita", e)
            } catch (e: SecurityException) {
                Log.w(TAG, "cache non accessibile", e)
            }
        }, "scorch-sfx-init").start()
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        prefs?.edit()?.putBoolean(KEY_ENABLED, value)?.apply()
        if (!value) whistleStop()
    }

    fun toggle(): Boolean {
        setEnabled(!enabled)
        return enabled
    }

    fun release() {
        whistleStop()
        pool?.release()
        pool = null
        synchronized(ids) { ids.clear() }
        synchronized(loaded) { loaded.clear() }
    }

    // ---------------------------------------------------------------- riproduzione

    private fun play(name: String, volume: Float, rate: Float = 1f, loop: Int = 0): Int {
        if (!enabled) return 0
        val p = pool ?: return 0
        val id = synchronized(ids) { ids[name] } ?: return 0
        if (synchronized(loaded) { !loaded.contains(id) }) return 0
        val v = volume.coerceIn(0f, 1f)
        return try {
            p.play(id, v, v, 1, loop, rate.coerceIn(0.5f, 2f))
        } catch (e: IllegalStateException) {
            0
        }
    }

    override fun fire(power: Float) {
        val k = (power / 1000f).coerceIn(0f, 1f)
        play(SfxSynth.FIRE, volume = 0.55f + 0.4f * k, rate = 1.15f - 0.3f * k)
    }

    override fun whistle(verticalSpeed: Float) {
        if (!enabled) return
        // il tono sale mentre il proiettile scende
        val target = (0.85f + verticalSpeed / 1400f).coerceIn(0.7f, 1.7f)
        if (whistleStream == 0) {
            whistleRate = target
            whistleStream = play(SfxSynth.WHISTLE, volume = 0.3f, rate = target, loop = -1)
        } else if (abs(target - whistleRate) > 0.02f) {
            whistleRate = target
            try {
                pool?.setRate(whistleStream, target)
            } catch (e: IllegalStateException) {
                whistleStream = 0
            }
        }
    }

    override fun whistleStop() {
        val stream = whistleStream
        whistleStream = 0
        if (stream != 0) {
            try {
                pool?.stop(stream)
            } catch (e: IllegalStateException) {
                Log.w(TAG, "stop del fischio", e)
            }
        }
    }

    override fun explosion(radius: Float) {
        // sotto i 90 di raggio il campione corto, sopra quello lungo; la velocita'
        // di riproduzione rifinisce la taglia
        if (radius < 90f) {
            val k = (radius / 90f).coerceIn(0f, 1f)
            play(SfxSynth.EXPLOSION_SMALL, volume = 0.6f + 0.35f * k, rate = 1.25f - 0.3f * k)
        } else {
            val k = ((radius - 90f) / 110f).coerceIn(0f, 1f)
            play(SfxSynth.EXPLOSION_BIG, volume = 0.75f + 0.25f * k, rate = 1.1f - 0.25f * k)
        }
    }

    override fun dirt() {
        play(SfxSynth.DIRT, volume = 0.6f, rate = 0.95f)
    }

    override fun destroyed() {
        play(SfxSynth.DESTROYED, volume = 1f)
    }

    override fun thud(intensity: Float) {
        val k = intensity.coerceIn(0f, 1f)
        play(SfxSynth.THUD, volume = 0.4f + 0.5f * k, rate = 1.1f - 0.25f * k)
    }

    override fun turnStart() {
        play(SfxSynth.TURN, volume = 0.45f)
    }

    override fun click() {
        play(SfxSynth.CLICK, volume = 0.35f)
    }

    override fun purchase() {
        play(SfxSynth.PURCHASE, volume = 0.5f)
    }
}
