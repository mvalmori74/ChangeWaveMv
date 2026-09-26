package com.changewave.dungeon

import com.changewave.dungeon.audio.AmbientMusicEngine
import com.changewave.dungeon.audio.MusicDirector
import com.changewave.dungeon.audio.MusicScale
import com.changewave.dungeon.audio.MusicStyle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt

class MusicTest {

    private fun render(depth: Int, seconds: Double, volume: Double = 0.85, seed: Long = 1234): ShortArray {
        val engine = AmbientMusicEngine(seed = seed, depth = depth)
        engine.masterVolume = volume
        val frames = (seconds * engine.sampleRate).toInt()
        val out = ShortArray(frames)
        // A blocchi, come fa il thread audio su Android.
        var written = 0
        val block = ShortArray(1024)
        while (written < frames) {
            val n = minOf(block.size, frames - written)
            engine.renderBlock(block, n)
            System.arraycopy(block, 0, out, written, n)
            written += n
        }
        return out
    }

    private fun peak(samples: ShortArray): Double =
        samples.maxOf { abs(it.toInt()) } / 32768.0

    private fun rms(samples: ShortArray): Double =
        sqrt(samples.sumOf { val v = it / 32768.0; v * v } / samples.size)

    /**
     * Energia nella banda 200-2500 Hz rapportata al totale, misurata con una
     * coppia di passa-basso a un polo (differenza = passa-banda approssimato).
     * Non serve precisione da analizzatore: serve accorgersi se la musica
     * finisce tutta sotto i 200 Hz.
     */
    private fun midBandRatio(samples: ShortArray, sampleRate: Int): Double {
        fun lowpass(cutoff: Double): DoubleArray {
            val x = 2.0 * PI * cutoff / sampleRate
            val a = x / (x + 1.0)
            var state = 0.0
            val out = DoubleArray(samples.size)
            for (i in samples.indices) {
                state += a * (samples[i] / 32768.0 - state)
                out[i] = state
            }
            return out
        }
        val low = lowpass(200.0)
        val high = lowpass(2500.0)
        var band = 0.0
        var total = 0.0
        for (i in samples.indices) {
            val b = high[i] - low[i]
            band += b * b
            val v = samples[i] / 32768.0
            total += v * v
        }
        return if (total == 0.0) 0.0 else band / total
    }

    @Test
    fun `la macabrita' cresce in modo monotono con la profondita'`() {
        var previous = -1.0
        var previousRoot = Double.MAX_VALUE
        var previousDissonance = -1.0
        for (depth in 1..MusicDirector.MAX_DEPTH) {
            val p = MusicDirector.paletteFor(depth)
            assertTrue(p.macabreIndex > previous, "profondita' $depth: indice non crescente")
            assertTrue(p.rootHz < previousRoot, "profondita' $depth: la fondamentale deve scendere")
            assertTrue(p.scale.dissonance >= previousDissonance, "la dissonanza non deve calare")
            previous = p.macabreIndex
            previousRoot = p.rootHz
            previousDissonance = p.scale.dissonance
        }
        assertEquals(0.0, MusicDirector.paletteFor(1).macabreIndex, 0.001)
        assertEquals(1.0, MusicDirector.paletteFor(10).macabreIndex, 0.001)
    }

    @Test
    fun `la profondita' fuori scala viene limitata invece di rompere`() {
        assertEquals(1, MusicDirector.paletteFor(-5).depth)
        assertEquals(MusicDirector.MAX_DEPTH, MusicDirector.paletteFor(99).depth)
    }

    @Test
    fun `nessuna saturazione a nessuna profondita'`() {
        for (depth in 1..MusicDirector.MAX_DEPTH) {
            val samples = render(depth, 6.0, volume = 1.0)
            val saturated = samples.count { abs(it.toInt()) >= 32767 }
            assertEquals(0, saturated, "profondita' $depth: $saturated campioni saturati")
            assertTrue(peak(samples) < 0.95, "profondita' $depth: picco ${peak(samples)} troppo vicino al fondo scala")
        }
    }

    @Test
    fun `il livello sonoro e' udibile ma non eccessivo`() {
        for (depth in 1..MusicDirector.MAX_DEPTH) {
            val level = rms(render(depth, 8.0))
            assertTrue(level in 0.02..0.35, "profondita' $depth: RMS $level fuori intervallo utile")
        }
    }

    @Test
    fun `la musica scende di livello sonoro al calare del volume`() {
        val piano = rms(render(5, 6.0, volume = 0.2))
        val forte = rms(render(5, 6.0, volume = 0.9))
        assertTrue(forte > piano * 2.5, "il volume deve incidere: $piano vs $forte")
    }

    @Test
    fun `a volume zero il segnale e' silenzio assoluto`() {
        val samples = render(7, 3.0, volume = 0.0)
        assertTrue(samples.all { it == 0.toShort() }, "con volume 0 non deve uscire alcun campione")
    }

    /**
     * Guardia di regressione: su un altoparlante di telefono tutto cio' che sta
     * sotto i 200 Hz e' inudibile. Una prima versione di questo motore metteva
     * l'83% dell'energia sotto gli 80 Hz e in pratica non si sentiva nulla.
     */
    @Test
    fun `una quota rilevante di energia sta nella banda riprodotta dai telefoni`() {
        for (depth in 1..MusicDirector.MAX_DEPTH) {
            val engine = AmbientMusicEngine(depth = depth)
            val samples = render(depth, 10.0)
            val ratio = midBandRatio(samples, engine.sampleRate)
            assertTrue(ratio > 0.12, "profondita' $depth: solo ${(ratio * 100).toInt()}% dell'energia fra 200 e 2500 Hz")
        }
    }

    @Test
    fun `a parita' di seme la musica e' identica`() {
        assertArrayEquals(render(4, 4.0, seed = 99), render(4, 4.0, seed = 99))
    }

    @Test
    fun `semi diversi producono musica diversa`() {
        val a = render(4, 4.0, seed = 1)
        val b = render(4, 4.0, seed = 2)
        assertTrue(!a.contentEquals(b), "seme diverso deve dare una realizzazione diversa")
    }

    @Test
    fun `la dimensione del blocco non altera il risultato`() {
        // Se lo stato interno non fosse continuo fra i blocchi, il thread audio
        // produrrebbe clic ad ogni buffer: qui si verifica che non accada.
        fun renderWithBlock(blockSize: Int): ShortArray {
            val engine = AmbientMusicEngine(seed = 7, depth = 6)
            engine.masterVolume = 0.8
            val total = engine.sampleRate * 3
            val out = ShortArray(total)
            val block = ShortArray(blockSize)
            var written = 0
            while (written < total) {
                val n = minOf(blockSize, total - written)
                engine.renderBlock(block, n)
                System.arraycopy(block, 0, out, written, n)
                written += n
            }
            return out
        }
        assertArrayEquals(renderWithBlock(256), renderWithBlock(4096))
    }

    @Test
    fun `il segnale non presenta discontinuita' brusche`() {
        for (depth in intArrayOf(1, 5, 10)) {
            val samples = render(depth, 6.0)
            var worst = 0
            for (i in 1 until samples.size) {
                val delta = abs(samples[i] - samples[i - 1])
                if (delta > worst) worst = delta
            }
            // Un clic da buffer produce salti prossimi al fondo scala (32768).
            assertTrue(worst < 6000, "profondita' $depth: salto massimo $worst fra campioni consecutivi")
        }
    }

    @Test
    fun `il cambio di profondita' avviene con transizione graduale`() {
        val engine = AmbientMusicEngine(seed = 5, depth = 1)
        engine.masterVolume = 0.8
        val block = ShortArray(1024)
        repeat(40) { engine.renderBlock(block) }

        engine.setDepth(10)
        assertEquals(10, engine.currentDepth())
        // Subito dopo il comando la tavolozza in suono e' ancora quella di partenza.
        assertTrue(engine.currentPalette().macabreIndex < 0.2, "la transizione non deve essere istantanea")

        // Dopo la durata della dissolvenza deve essere arrivata a destinazione.
        val frames = (AmbientMusicEngine.CROSSFADE_SECONDS * engine.sampleRate).toInt() + engine.sampleRate
        var written = 0
        while (written < frames) {
            engine.renderBlock(block)
            written += block.size
        }
        assertTrue(engine.currentPalette().macabreIndex > 0.98, "la transizione deve completarsi")
    }

    @Test
    fun `il cambio di profondita' non produce uno scatto nel segnale`() {
        val engine = AmbientMusicEngine(seed = 11, depth = 2)
        engine.masterVolume = 0.85
        val block = ShortArray(2048)
        repeat(30) { engine.renderBlock(block) }
        val before = block.last()
        engine.setDepth(9)
        engine.renderBlock(block)
        assertTrue(abs(block.first() - before) < 6000, "salto di ${abs(block.first() - before)} al cambio di livello")
    }

    @Test
    fun `reset azzera lo stato e riparte in dissolvenza`() {
        val engine = AmbientMusicEngine(seed = 3, depth = 8)
        engine.masterVolume = 0.9
        val block = ShortArray(2048)
        repeat(20) { engine.renderBlock(block) }
        engine.reset()
        engine.renderBlock(block)
        // Il primo campione dopo il reset parte dal silenzio (dissolvenza in apertura).
        assertTrue(abs(block.first().toInt()) < 400, "dopo il reset la musica deve entrare in dissolvenza")
    }

    /**
     * Inviluppo di energia del segnale su finestre da 20 ms, a media nulla.
     * E' la base delle misure ritmiche: l'andamento dell'energia nel tempo.
     */
    private fun centeredEnvelope(samples: ShortArray, sampleRate: Int): DoubleArray {
        val window = (sampleRate * 0.02).toInt()
        val values = ArrayList<Double>()
        var index = 0
        while (index + window < samples.size) {
            var energy = 0.0
            for (i in index until index + window) {
                val v = samples[i] / 32768.0
                energy += v * v
            }
            values.add(sqrt(energy / window))
            index += window
        }
        val mean = values.average()
        return DoubleArray(values.size) { values[it] - mean }
    }

    private fun autocorrelation(envelope: DoubleArray, lagSeconds: Double): Double {
        val lag = (lagSeconds / 0.02).toInt().coerceAtLeast(1)
        if (lag >= envelope.size) return 0.0
        val denominator = envelope.sumOf { it * it }.takeIf { it > 0.0 } ?: return 0.0
        var numerator = 0.0
        for (i in 0 until envelope.size - lag) numerator += envelope[i] * envelope[i + lag]
        return numerator / denominator
    }

    /**
     * Contrasto ritmico: autocorrelazione dell'inviluppo al periodo della
     * semiminima rapportata a quella di un ritardo non musicale.
     *
     * Un brano ritmico ha un *picco* al battito e fondo piatto (misurato: 5-8).
     * Un bordone continuo e' correlato a qualunque ritardo, quindi non ha picco
     * e il rapporto resta vicino a 1 o sotto (misurato: ~0,8). E' il valore
     * assoluto dell'autocorrelazione a non distinguere i due casi, non il
     * rapporto: per questo la misura e' costruita cosi'.
     */
    private fun beatContrast(samples: ShortArray, sampleRate: Int, bpm: Double): Double {
        val envelope = centeredEnvelope(samples, sampleRate)
        val atBeat = autocorrelation(envelope, 60.0 / bpm)
        val atReference = autocorrelation(envelope, 0.47)
        return atBeat / maxOf(1e-6, atReference)
    }

    @Test
    fun `il primo livello usa il tema d'avventura e non quello cavernoso`() {
        val first = MusicDirector.paletteFor(1)
        assertEquals(MusicStyle.ISLAND, first.style)
        assertEquals(MusicScale.MIXOLYDIAN, first.scale)
        assertEquals(0.0, first.macabreIndex, 0.001)
        assertTrue(first.marimbaLevel > 0.0 && first.bassLevel > 0.0, "gli strati ritmici devono essere attivi")
        assertTrue(first.heartLevel == 0.0 && first.noiseLevel == 0.0, "nel primo livello non ci sono battito e rumore")

        for (depth in 2..MusicDirector.MAX_DEPTH) {
            assertEquals(MusicStyle.CAVERN, MusicDirector.paletteFor(depth).style, "profondita' $depth")
        }
    }

    @Test
    fun `il primo livello e' ritmico, i livelli profondi sono continui`() {
        val engine = AmbientMusicEngine()
        val tempo = MusicDirector.paletteFor(1).tempoBpm
        val island = beatContrast(render(1, 14.0), engine.sampleRate, tempo)
        val cavern = beatContrast(render(9, 14.0), engine.sampleRate, tempo)
        // Misurati su due semi: 5,5 e 7,7 per il tema ritmico, 0,79 per i bordoni.
        assertTrue(island > 2.5, "il primo livello deve avere un battito riconoscibile: contrasto $island")
        assertTrue(cavern < 1.5, "i livelli profondi devono restare continui: contrasto $cavern")
    }

    @Test
    fun `il primo livello non e' piu' silenzioso degli altri`() {
        val first = rms(render(1, 12.0))
        val second = rms(render(2, 12.0))
        assertTrue(first > second * 0.7, "livello 1 a $first contro livello 2 a $second: troppo sbilanciato")
    }

    @Test
    fun `il passaggio dal primo al secondo livello non produce uno scatto`() {
        val engine = AmbientMusicEngine(seed = 21, depth = 1)
        engine.masterVolume = 0.85
        val block = ShortArray(2048)
        repeat(40) { engine.renderBlock(block) }
        val before = block.last()
        engine.setDepth(2)
        engine.renderBlock(block)
        assertTrue(abs(block.first() - before) < 6000, "salto di ${abs(block.first() - before)} fra i due stili")
        // A transizione completata comanda lo stile cavernoso.
        val frames = (AmbientMusicEngine.CROSSFADE_SECONDS * engine.sampleRate).toInt() + engine.sampleRate
        var written = 0
        while (written < frames) { engine.renderBlock(block); written += block.size }
        assertEquals(MusicStyle.CAVERN, engine.currentPalette().style)
    }

    @Test
    fun `la descrizione della tavolozza e' leggibile`() {
        val deep = MusicDirector.describe(10)
        assertTrue(deep.contains("diabolus"), deep)
        assertTrue(deep.contains("100%"), deep)
        val first = MusicDirector.describe(1)
        assertTrue(first.contains("avventura"), first)
        assertTrue(first.contains("96 bpm"), first)
    }
}
