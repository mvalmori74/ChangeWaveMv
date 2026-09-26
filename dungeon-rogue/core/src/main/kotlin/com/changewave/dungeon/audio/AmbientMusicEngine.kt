package com.changewave.dungeon.audio

import com.changewave.dungeon.rules.GameRandom
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Sintetizzatore della colonna sonora: genera campioni PCM a 16 bit senza usare
 * alcun file audio.
 *
 * Perche' procedurale invece di tracce registrate:
 *  - nessun asset da distribuire (APK invariato, nessuna licenza da gestire);
 *  - la musica cambia in modo *continuo* con la profondita', non a scatti fra
 *    tracce diverse, ed e' virtualmente infinita, senza loop riconoscibili;
 *  - e' codice Kotlin puro, quindi collaudabile in JUnit come il resto del motore.
 *
 * Catena di segnale, tutta in [renderBlock]:
 *
 *   bordone (3 osc. scordati) ┐
 *   pad melodico (6 voci)     ├─> somma ─> passa-basso ─> +eco ─> saturazione ─> volume
 *   campana inarmonica        │                              (linea di ritardo con
 *   battito cardiaco          │                               riaccoppiamento)
 *   letto di rumore filtrato  ┘
 *
 * Costo: qualche decina di operazioni in virgola mobile per campione, cioe'
 * ~1 MFLOP/s a 22,05 kHz. Trascurabile anche su hardware modesto.
 *
 * La classe non e' thread-safe: va usata da un solo thread audio.
 */
class AmbientMusicEngine(
    val sampleRate: Int = DEFAULT_SAMPLE_RATE,
    seed: Long = 0x0D06F00DL,
    depth: Int = 1,
) {

    /** Volume finale, 0..1. La UI ci arriva applicando una curva percettiva. */
    var masterVolume: Double = 0.7
        set(value) {
            field = value.coerceIn(0.0, 1.0)
        }

    private val rng = GameRandom.fromSeed(seed)

    private var palette: MusicPalette = MusicDirector.paletteFor(depth)
    private var targetPalette: MusicPalette = palette

    /** Avanzamento della transizione fra due tavolozze, 0..1. */
    private var blend: Double = 1.0
    private val blendStepPerSample: Double = 1.0 / (CROSSFADE_SECONDS * sampleRate)

    /** Dissolvenza iniziale, evita il clic all'avvio. */
    private var fadeIn: Double = 0.0

    // --- stato delle voci -----------------------------------------------------

    private val dronePhase = DoubleArray(4)
    private val padVoices = Array(PAD_VOICES) { PadVoice() }
    private val bell = BellVoice()
    private val heart = HeartVoice()

    private var noiseLowpass = 0.0
    private var noiseLfoPhase = 0.0
    private var droneLfoPhase = 0.0

    private var masterLowpass = 0.0

    /** Strato ritmico del primo livello (stile ISLAND). */
    private val island = IslandLayer(sampleRate, rng)

    private val delayLine = DoubleArray((DELAY_SECONDS * sampleRate).toInt().coerceAtLeast(1))
    private var delayIndex = 0

    private var samplesToNextNote = (sampleRate * 0.8).toInt()
    private var samplesToNextBell = (sampleRate * 6.0).toInt()
    private var heartSamples = 0

    // --- API ------------------------------------------------------------------

    /** Cambia profondita': i parametri migrano gradualmente, senza stacchi. */
    fun setDepth(depth: Int) {
        val next = MusicDirector.paletteFor(depth)
        if (next.depth == targetPalette.depth) return
        // Si riparte dalla tavolozza effettivamente in suono, non da quella nominale.
        palette = currentPalette()
        targetPalette = next
        blend = 0.0
    }

    fun currentDepth(): Int = targetPalette.depth

    fun currentPalette(): MusicPalette = palette.blend(targetPalette, blend)

    /**
     * Riempie [out] con [frames] campioni mono a 16 bit. Chiamata ripetutamente
     * dal thread audio: lo stato interno garantisce continuita' fra i blocchi.
     */
    fun renderBlock(out: ShortArray, frames: Int = out.size) {
        require(frames <= out.size) { "frames oltre la dimensione del buffer" }
        for (i in 0 until frames) {
            out[i] = (nextSample() * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
    }

    /** Un campione in virgola mobile, nominalmente in [-1, 1]. */
    fun nextSample(): Double {
        if (blend < 1.0) blend = (blend + blendStepPerSample).coerceAtMost(1.0)
        if (fadeIn < 1.0) fadeIn = (fadeIn + 1.0 / (FADE_IN_SECONDS * sampleRate)).coerceAtMost(1.0)
        val p = currentPalette()

        // Peso dei due stili durante la transizione: entrambi vengono sempre
        // generati e poi miscelati, cosi' le voci in decadimento non vengono
        // troncate quando si passa da un livello all'altro.
        val islandWeight = styleWeight()
        var mix = 0.0
        val cavern = drone(p) + pads(p) + bellLayer(p) + heartLayer(p) + noiseLayer(p)
        mix += cavern * (1.0 - islandWeight)
        mix += island.render(p) * islandWeight

        // Passa-basso a un polo: chiudendolo la musica diventa piu' cupa e lontana.
        val cutoff = p.lowpassHz.coerceIn(120.0, sampleRate / 2.2)
        val alpha = onePoleCoefficient(cutoff)
        masterLowpass += alpha * (mix - masterLowpass)
        var signal = masterLowpass

        // Eco: la caverna risponde. Il riaccoppiamento cresce con la profondita'.
        val echoed = delayLine[delayIndex]
        signal += echoed * 0.55
        delayLine[delayIndex] = (signal * p.delayFeedback).coerceIn(-4.0, 4.0)
        delayIndex = (delayIndex + 1) % delayLine.size

        // Saturazione morbida: limita i picchi senza il taglio netto della distorsione.
        val limited = tanh(signal * 0.9)
        return limited * masterVolume * fadeIn
    }

    private fun styleWeight(): Double {
        val from = if (palette.style == MusicStyle.ISLAND) 1.0 - blend else 0.0
        val to = if (targetPalette.style == MusicStyle.ISLAND) blend else 0.0
        return (from + to).coerceIn(0.0, 1.0)
    }

    /** Silenzia e azzera gli stati: da usare quando la musica viene spenta. */
    fun reset() {
        dronePhase.fill(0.0)
        island.reset()
        padVoices.forEach { it.silence() }
        bell.silence()
        heart.active = false
        noiseLowpass = 0.0
        masterLowpass = 0.0
        delayLine.fill(0.0)
        delayIndex = 0
        fadeIn = 0.0
        samplesToNextNote = (sampleRate * 0.8).toInt()
        samplesToNextBell = (sampleRate * 6.0).toInt()
        heartSamples = 0
    }

    // --- strati ---------------------------------------------------------------

    /** Bordone: tre oscillatori scordati su fondamentale, quinta (o tritono) e ottava. */
    private fun drone(p: MusicPalette): Double {
        // Al crescere della dissonanza la quinta giusta diventa tritono: e' il
        // singolo intervallo che rende "sbagliato" un accordo altrimenti stabile.
        val secondInterval = if (p.scale.dissonance >= 0.7) 6.0 else 7.0
        val detune = p.detuneCents / 1200.0
        // La fondamentale sta sotto i 60 Hz: su un altoparlante di telefono non
        // esce. Il peso del bordone e' quindi sull'ottava e sulla quinta
        // superiori, dove il trasduttore lavora; la fondamentale resta come
        // sostegno per chi ascolta in cuffia.
        val frequencies = doubleArrayOf(
            p.rootHz,
            p.rootHz * 2.0 * Math.pow(2.0, secondInterval / 12.0) * (1.0 + detune),
            p.rootHz * 4.0 * (1.0 - detune),
            p.rootHz * 8.0 * Math.pow(2.0, secondInterval / 12.0) * (1.0 + detune * 0.5),
        )
        val weights = doubleArrayOf(0.35, 0.85, 0.80, 0.50)
        droneLfoPhase = advance(droneLfoPhase, 0.037)
        val breath = 0.82 + 0.18 * sin(droneLfoPhase)

        var sum = 0.0
        for (i in frequencies.indices) {
            dronePhase[i] = advance(dronePhase[i], frequencies[i])
            // Onda triangolare addolcita: ricca di armoniche basse, senza asprezza.
            sum += triangle(dronePhase[i]) * weights[i]
        }
        return sum / 2.6 * p.droneLevel * breath
    }

    /** Pad melodico: note sparse e lunghe, prese dalla scala del livello. */
    private fun pads(p: MusicPalette): Double {
        if (--samplesToNextNote <= 0) {
            scheduleNote(p)
            val meanInterval = 1.0 / p.noteRateHz.coerceAtLeast(0.02)
            // Intervalli irregolari: una griglia regolare suonerebbe meccanica.
            val jitter = 0.45 + rng.nextDouble() * 1.3
            samplesToNextNote = (meanInterval * jitter * sampleRate).toInt().coerceAtLeast(sampleRate / 4)
        }
        var sum = 0.0
        for (voice in padVoices) sum += voice.render()
        return sum * p.padLevel * 0.55
    }

    private fun scheduleNote(p: MusicPalette) {
        val degrees = p.scale.degrees
        val semitone = degrees[rng.nextInt(degrees.size)]
        // Ottave 3-4 sopra la fondamentale: colloca la melodia fra 300 e 1500 Hz,
        // la banda in cui un altoparlante di telefono e' efficiente.
        val octave = 3 + rng.nextInt(2)
        val frequency = p.rootHz * Math.pow(2.0, (semitone + 12.0 * octave) / 12.0)
        val attack = 1.2 + rng.nextDouble() * 1.8
        val release = 3.5 + rng.nextDouble() * 4.0
        allocate()?.start(frequency, attack, release, sampleRate)
        // Cluster di seconde minori: due note a distanza di un semitono battono
        // fra loro e producono la tensione tipica del genere horror.
        if (rng.chance(p.clusterChance)) {
            allocate()?.start(frequency * Math.pow(2.0, 1.0 / 12.0), attack, release, sampleRate)
        }
    }

    private fun allocate(): PadVoice? =
        padVoices.firstOrNull { !it.active } ?: padVoices.minByOrNull { it.envelope }

    /** Campana inarmonica: rintocco lontano, solo dai livelli profondi. */
    private fun bellLayer(p: MusicPalette): Double {
        if (p.bellLevel <= 0.0) return 0.0
        if (--samplesToNextBell <= 0) {
            val frequency = p.rootHz * Math.pow(2.0, (12 * 3 + p.scale.degrees[rng.nextInt(3)]) / 12.0)
            bell.strike(frequency, sampleRate)
            samplesToNextBell = ((5.0 + rng.nextDouble() * 9.0) * sampleRate).toInt()
        }
        return bell.render() * p.bellLevel
    }

    /**
     * Battito cardiaco: due impulsi ravvicinati ("lub-dub") per ciclo, con
     * frequenza che sale con la profondita'. E' il segnale che il giocatore
     * percepisce come pericolo imminente anche senza accorgersene.
     */
    private fun heartLayer(p: MusicPalette): Double {
        if (p.heartLevel <= 0.0) return 0.0
        val cycleSamples = (60.0 / p.heartBpm * sampleRate).toInt().coerceAtLeast(1)
        val secondBeat = (0.19 * sampleRate).toInt()
        if (heartSamples == 0) heart.strike(p.rootHz * 0.85, sampleRate, 1.0)
        if (heartSamples == secondBeat) heart.strike(p.rootHz * 0.78, sampleRate, 0.7)
        heartSamples = (heartSamples + 1) % cycleSamples
        return heart.render() * p.heartLevel
    }

    /** Letto di rumore filtrato: il respiro del dungeon. */
    private fun noiseLayer(p: MusicPalette): Double {
        if (p.noiseLevel <= 0.0) return 0.0
        noiseLfoPhase = advance(noiseLfoPhase, 0.071)
        val breathing = 0.55 + 0.45 * sin(noiseLfoPhase)
        val white = rng.nextDouble() * 2.0 - 1.0
        val cutoff = 900.0 + 1700.0 * breathing
        noiseLowpass += onePoleCoefficient(cutoff) * (white - noiseLowpass)
        return noiseLowpass * p.noiseLevel * breathing * 0.9
    }

    // --- primitive di sintesi -------------------------------------------------

    private fun advance(phase: Double, frequency: Double): Double {
        var next = phase + 2.0 * PI * frequency / sampleRate
        if (next > 2.0 * PI) next -= 2.0 * PI
        return next
    }

    private fun onePoleCoefficient(cutoffHz: Double): Double {
        val x = 2.0 * PI * cutoffHz / sampleRate
        return (x / (x + 1.0)).coerceIn(0.0, 1.0)
    }

    private fun triangle(phase: Double): Double {
        val t = phase / (2.0 * PI)
        return 4.0 * abs(t - 0.5) - 1.0
    }

    /** Voce del pad: oscillatore con inviluppo lento attacco/rilascio. */
    private inner class PadVoice {
        var active = false
        var envelope = 0.0
        private var phase = 0.0
        private var frequency = 0.0
        private var attackStep = 0.0
        private var releaseFactor = 0.0
        private var attacking = false

        fun start(freq: Double, attackSeconds: Double, releaseSeconds: Double, rate: Int) {
            frequency = freq
            phase = 0.0
            envelope = 0.0
            attackStep = 1.0 / (attackSeconds * rate)
            releaseFactor = exp(-1.0 / (releaseSeconds * rate))
            attacking = true
            active = true
        }

        fun silence() {
            active = false
            envelope = 0.0
        }

        fun render(): Double {
            if (!active) return 0.0
            if (attacking) {
                envelope += attackStep
                if (envelope >= 1.0) { envelope = 1.0; attacking = false }
            } else {
                envelope *= releaseFactor
                if (envelope < 1e-4) { silence(); return 0.0 }
            }
            phase = advance(phase, frequency)
            // Mescola seno e triangolo: il seno da' corpo, il triangolo presenza.
            val wave = 0.65 * sin(phase) + 0.35 * triangle(phase)
            return wave * envelope
        }
    }

    /** Campana: parziali inarmoniche con decadimenti diversi, come un bronzo reale. */
    private inner class BellVoice {
        private val ratios = doubleArrayOf(1.0, 2.76, 5.40, 8.93)
        private val decays = doubleArrayOf(6.0, 3.2, 1.8, 1.1)
        private val phases = DoubleArray(4)
        private val envelopes = DoubleArray(4)
        private val factors = DoubleArray(4)
        private var frequency = 0.0

        fun strike(freq: Double, rate: Int) {
            frequency = freq
            for (i in ratios.indices) {
                phases[i] = 0.0
                envelopes[i] = 1.0 / (i + 1.0)
                factors[i] = exp(-1.0 / (decays[i] * rate))
            }
        }

        fun silence() {
            envelopes.fill(0.0)
        }

        fun render(): Double {
            var sum = 0.0
            for (i in ratios.indices) {
                if (envelopes[i] < 1e-5) continue
                phases[i] = advance(phases[i], frequency * ratios[i])
                sum += sin(phases[i]) * envelopes[i]
                envelopes[i] *= factors[i]
            }
            return sum * 0.5
        }
    }

    /** Impulso cardiaco: seno grave con caduta rapida di ampiezza e di intonazione. */
    private inner class HeartVoice {
        var active = false
        private var phase = 0.0
        private var frequency = 0.0
        private var envelope = 0.0
        private var factor = 0.0

        fun strike(freq: Double, rate: Int, amplitude: Double) {
            frequency = freq
            phase = 0.0
            envelope = amplitude
            factor = exp(-1.0 / (0.22 * rate))
            active = true
        }

        fun render(): Double {
            if (!active) return 0.0
            phase = advance(phase, frequency)
            val value = sin(phase) * envelope
            envelope *= factor
            frequency *= 0.99994 // la testa del colpo scende di intonazione
            if (envelope < 1e-4) active = false
            return value
        }
    }

    companion object {
        const val DEFAULT_SAMPLE_RATE = 22050
        const val PAD_VOICES = 6

        /** Durata della transizione fra due livelli, in secondi. */
        const val CROSSFADE_SECONDS = 4.0
        const val FADE_IN_SECONDS = 1.5
        const val DELAY_SECONDS = 0.42
    }
}
