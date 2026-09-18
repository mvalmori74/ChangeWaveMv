package com.changewave.scorch.audio

import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Sintetizza a runtime i suoni del gioco e li salva come WAV nella cache: nessun asset
 * audio nell'APK. Tutto qui dentro e' Kotlin puro (nessuna API Android), quindi si puo'
 * generare e verificare anche fuori dal telefono.
 */
object SfxSynth {

    const val SAMPLE_RATE = 22_050

    /** Cambiare versione rigenera i file in cache. */
    const val VERSION = 1

    const val FIRE = "fire"
    const val WHISTLE = "whistle"
    const val EXPLOSION_SMALL = "boom_small"
    const val EXPLOSION_BIG = "boom_big"
    const val DIRT = "dirt"
    const val DESTROYED = "destroyed"
    const val THUD = "thud"
    const val TURN = "turn"
    const val CLICK = "click"
    const val PURCHASE = "purchase"

    val ALL = listOf(
        FIRE, WHISTLE, EXPLOSION_SMALL, EXPLOSION_BIG, DIRT,
        DESTROYED, THUD, TURN, CLICK, PURCHASE
    )

    // ------------------------------------------------------------------ file

    fun fileFor(dir: File, name: String): File = File(dir, "${name}_v$VERSION.wav")

    /** Genera i file mancanti e restituisce la mappa nome -> file. */
    fun ensureFiles(dir: File): Map<String, File> {
        if (!dir.exists()) dir.mkdirs()
        val result = LinkedHashMap<String, File>()
        for (name in ALL) {
            val file = fileFor(dir, name)
            if (!file.exists() || file.length() < 64L) {
                writeWav(file, render(name))
            }
            result[name] = file
        }
        return result
    }

    fun writeWav(file: File, samples: ShortArray) {
        val dataSize = samples.size * 2
        val header = ByteArray(44)
        fun ascii(offset: Int, text: String) {
            for (i in text.indices) header[offset + i] = text[i].code.toByte()
        }
        fun int32(offset: Int, value: Int) {
            header[offset] = (value and 0xFF).toByte()
            header[offset + 1] = ((value shr 8) and 0xFF).toByte()
            header[offset + 2] = ((value shr 16) and 0xFF).toByte()
            header[offset + 3] = ((value shr 24) and 0xFF).toByte()
        }
        fun int16(offset: Int, value: Int) {
            header[offset] = (value and 0xFF).toByte()
            header[offset + 1] = ((value shr 8) and 0xFF).toByte()
        }

        ascii(0, "RIFF")
        int32(4, 36 + dataSize)
        ascii(8, "WAVE")
        ascii(12, "fmt ")
        int32(16, 16)           // dimensione del blocco fmt
        int16(20, 1)            // PCM
        int16(22, 1)            // mono
        int32(24, SAMPLE_RATE)
        int32(28, SAMPLE_RATE * 2) // byte al secondo
        int16(32, 2)            // byte per frame
        int16(34, 16)           // bit per campione
        ascii(36, "data")
        int32(40, dataSize)

        val body = ByteArray(dataSize)
        for (i in samples.indices) {
            val v = samples[i].toInt()
            body[i * 2] = (v and 0xFF).toByte()
            body[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
        }

        FileOutputStream(file).use {
            it.write(header)
            it.write(body)
        }
    }

    // ------------------------------------------------------------------ suoni

    fun render(name: String): ShortArray = when (name) {
        FIRE -> fireShot()
        WHISTLE -> whistleLoop()
        EXPLOSION_SMALL -> explosion(durationMs = 420, bodyHz = 78f, decay = 8.5f, cutoffFrom = 1800f, cutoffTo = 260f, seed = 11)
        EXPLOSION_BIG -> explosion(durationMs = 1100, bodyHz = 44f, decay = 3.0f, cutoffFrom = 900f, cutoffTo = 80f, seed = 23)
        DIRT -> dirtFall()
        DESTROYED -> destroyed()
        THUD -> thud()
        TURN -> turnChime()
        CLICK -> click()
        PURCHASE -> purchase()
        else -> ShortArray(0)
    }

    /** Cannone: colpo secco piu' una discesa di tono. */
    private fun fireShot(): ShortArray {
        val n = samples(260)
        val out = FloatArray(n)
        val rnd = Random(7)
        var phase = 0.0
        val lp = OnePole()
        for (i in 0 until n) {
            val t = i.toFloat() / SAMPLE_RATE
            // corpo: sweep 230 -> 58 Hz
            val freq = 230.0 * exp(-t * 9.0) + 58.0
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val body = sin(phase).toFloat() * exp(-t * 11f)
            // schiocco iniziale
            val click = lp.process(rnd.nextFloat() * 2f - 1f, 2600f) * exp(-t * 90f)
            out[i] = body * 0.85f + click * 0.5f
        }
        return finish(out, peak = 0.92f)
    }

    /**
     * Fischio del proiettile: un anello richiudibile senza scatto, perche' contiene un
     * numero intero di cicli. Il tono vero viene poi variato cambiando la velocita'.
     */
    private fun whistleLoop(): ShortArray {
        val n = samples(500)
        // 485 cicli in mezzo secondo = 970 Hz; il numero intero di cicli chiude l'anello
        val cycles = 485
        val vibratoCycles = 6
        val out = FloatArray(n)
        for (i in 0 until n) {
            val u = i.toDouble() / n          // 0..1 sul giro completo
            val vibrato = 1.0 + 0.035 * sin(2.0 * PI * vibratoCycles * u)
            val phase = 2.0 * PI * cycles * u * vibrato
            val tone = sin(phase) * 0.75 + sin(phase * 2.0) * 0.12
            out[i] = tone.toFloat()
        }
        // dissolvenza corta solo per sicurezza sui bordi
        val fade = samples(4)
        for (i in 0 until fade) {
            val k = i.toFloat() / fade
            out[i] *= k
            out[n - 1 - i] *= k
        }
        return finish(out, peak = 0.45f)
    }

    /** Esplosione: rumore filtrato che si scurisce, piu' un corpo grave. */
    private fun explosion(
        durationMs: Int,
        bodyHz: Float,
        decay: Float,
        cutoffFrom: Float,
        cutoffTo: Float,
        seed: Int
    ): ShortArray {
        val n = samples(durationMs)
        val out = FloatArray(n)
        val rnd = Random(seed)
        val lp = OnePole()
        val rumble = OnePole()
        var phase = 0.0
        for (i in 0 until n) {
            val t = i.toFloat() / SAMPLE_RATE
            val u = i.toFloat() / n
            val env = exp(-t * decay)
            val cutoff = cutoffFrom + (cutoffTo - cutoffFrom) * u
            val noise = lp.process(rnd.nextFloat() * 2f - 1f, cutoff)
            // il corpo scende leggermente di tono
            val freq = bodyHz * (1.0 - 0.25 * u)
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val body = sin(phase).toFloat() * exp(-t * decay * 0.65f)
            val tail = rumble.process(rnd.nextFloat() * 2f - 1f, 90f) * env * 0.8f
            out[i] = (noise * 1.1f + body * 0.9f + tail) * env
        }
        return finish(out, peak = 0.97f)
    }

    /** Frana di terra / palla di terra. */
    private fun dirtFall(): ShortArray {
        val n = samples(340)
        val out = FloatArray(n)
        val rnd = Random(31)
        // due poli in cascata: una frana e' un rumore sordo, non un sibilo
        val lp1 = OnePole()
        val lp2 = OnePole()
        var grain = 0f
        var hold = 0
        for (i in 0 until n) {
            val t = i.toFloat() / SAMPLE_RATE
            val u = i.toFloat() / n
            val cutoff = 430f + (130f - 430f) * u
            // granuli tenuti per qualche campione: zolle, non polvere
            if (hold <= 0) {
                grain = if (rnd.nextFloat() < 0.6f) rnd.nextFloat() * 2f - 1f else 0f
                hold = 4 + rnd.nextInt(6)
            }
            hold--
            out[i] = lp2.process(lp1.process(grain, cutoff), cutoff) * exp(-t * 7.5f)
        }
        return finish(out, peak = 0.7f)
    }

    /** Carro distrutto: boato piu' risonanza metallica. */
    private fun destroyed(): ShortArray {
        val n = samples(900)
        val out = FloatArray(n)
        val rnd = Random(97)
        val lp = OnePole()
        var p1 = 0.0
        var p2 = 0.0
        var p3 = 0.0
        var body = 0.0
        for (i in 0 until n) {
            val t = i.toFloat() / SAMPLE_RATE
            val u = i.toFloat() / n
            val env = exp(-t * 3.6f)
            val noise = lp.process(rnd.nextFloat() * 2f - 1f, 1400f + (150f - 1400f) * u)
            body += 2.0 * PI * (60.0 - 18.0 * u) / SAMPLE_RATE
            p1 += 2.0 * PI * 523.0 / SAMPLE_RATE
            p2 += 2.0 * PI * 741.0 / SAMPLE_RATE
            p3 += 2.0 * PI * 1109.0 / SAMPLE_RATE
            val metal = (sin(p1) * 0.5 + sin(p2) * 0.32 + sin(p3) * 0.2).toFloat() * exp(-t * 6.5f)
            out[i] = (noise * 1.0f + sin(body).toFloat() * 0.9f + metal * 0.35f) * env
        }
        return finish(out, peak = 0.97f)
    }

    /** Carro che tocca terra. */
    private fun thud(): ShortArray {
        val n = samples(180)
        val out = FloatArray(n)
        val rnd = Random(5)
        val lp = OnePole()
        var phase = 0.0
        for (i in 0 until n) {
            val t = i.toFloat() / SAMPLE_RATE
            val freq = 120.0 * exp(-t * 14.0) + 52.0
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val body = sin(phase).toFloat() * exp(-t * 16f)
            val grit = lp.process(rnd.nextFloat() * 2f - 1f, 700f) * exp(-t * 26f)
            out[i] = body * 0.9f + grit * 0.45f
        }
        return finish(out, peak = 0.8f)
    }

    /** Inizio turno: due note. */
    private fun turnChime(): ShortArray = tones(listOf(659f to 110, 880f to 150), peak = 0.5f)

    /** Acquisto: nota che sale. */
    private fun purchase(): ShortArray {
        val n = samples(160)
        val out = FloatArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            val t = i.toFloat() / SAMPLE_RATE
            val u = i.toFloat() / n
            val freq = 440.0 + 500.0 * u
            phase += 2.0 * PI * freq / SAMPLE_RATE
            out[i] = (sin(phase) * 0.8 + sin(phase * 2) * 0.15).toFloat() * envShort(u) * exp(-t * 4f)
        }
        return finish(out, peak = 0.55f)
    }

    /** Tocco su un comando. */
    private fun click(): ShortArray {
        val n = samples(40)
        val out = FloatArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            val t = i.toFloat() / SAMPLE_RATE
            phase += 2.0 * PI * 1500.0 / SAMPLE_RATE
            out[i] = sin(phase).toFloat() * exp(-t * 110f)
        }
        return finish(out, peak = 0.3f)
    }

    private fun tones(list: List<Pair<Float, Int>>, peak: Float): ShortArray {
        val total = list.sumOf { samples(it.second) }
        val out = FloatArray(total)
        var offset = 0
        for ((freq, ms) in list) {
            val n = samples(ms)
            var phase = 0.0
            for (i in 0 until n) {
                val u = i.toFloat() / n
                phase += 2.0 * PI * freq / SAMPLE_RATE
                out[offset + i] = (sin(phase) * 0.85 + sin(phase * 3) * 0.1).toFloat() * envShort(u)
            }
            offset += n
        }
        return finish(out, peak)
    }

    // ------------------------------------------------------------------ utili

    private fun samples(ms: Int): Int = max(1, SAMPLE_RATE * ms / 1000)

    /** Attacco rapido e rilascio morbido, per non sentire scatti. */
    private fun envShort(u: Float): Float {
        val attack = 0.02f
        val release = 0.35f
        return when {
            u < attack -> u / attack
            u > 1f - release -> ((1f - u) / release)
            else -> 1f
        }
    }

    /** Normalizza al picco richiesto, addolcisce i bordi e converte a 16 bit. */
    private fun finish(buffer: FloatArray, peak: Float): ShortArray {
        var maxAbs = 0f
        for (v in buffer) {
            val a = if (v < 0f) -v else v
            if (a > maxAbs) maxAbs = a
        }
        val gain = if (maxAbs > 1e-6f) peak / maxAbs else 0f

        val fade = min(samples(6), buffer.size / 8)
        val out = ShortArray(buffer.size)
        for (i in buffer.indices) {
            var v = buffer[i] * gain
            if (fade > 0) {
                if (i < fade) v *= i.toFloat() / fade
                val fromEnd = buffer.size - 1 - i
                if (fromEnd < fade) v *= fromEnd.toFloat() / fade
            }
            // saturazione morbida: niente clip duro
            v = tanhApprox(v)
            out[i] = (v * 32_600f).toInt().coerceIn(-32_768, 32_767).toShort()
        }
        return out
    }

    private fun tanhApprox(x: Float): Float {
        val x2 = x * x
        return x * (27f + x2) / (27f + 9f * x2)
    }

    /** Filtro passa-basso a un polo, con taglio variabile campione per campione. */
    private class OnePole {
        private var state = 0f

        fun process(input: Float, cutoffHz: Float): Float {
            val c = cutoffHz.coerceIn(30f, SAMPLE_RATE / 2.2f)
            val alpha = (2.0 * PI * c / SAMPLE_RATE).toFloat().coerceIn(0.001f, 0.99f)
            state += alpha * (input - state)
            return state
        }
    }
}
