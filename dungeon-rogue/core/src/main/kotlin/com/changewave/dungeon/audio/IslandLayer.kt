package com.changewave.dungeon.audio

import com.changewave.dungeon.rules.GameRandom
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/**
 * Strato ritmico-melodico del primo livello: materiale originale in stile
 * avventura caraibica, generato a partire da un giro armonico e da un motivo
 * che si ripete variandosi. Nessuna trascrizione di brani esistenti: melodia e
 * accompagnamento nascono dalle regole implementate qui.
 *
 * Impianto musicale:
 *  - modo misolidio (maggiore con settima abbassata): colore da partenza
 *    avventurosa, non da minaccia;
 *  - giro di quattro battute I - IV - v - IV, una battuta per accordo;
 *  - arpeggio percussivo tipo marimba con accenti in controtempo (la sincope
 *    e' cio' che rende "caraibica" una sequenza altrimenti neutra);
 *  - basso camminante in semiminime su fondamentale, quinta e terza;
 *  - shaker sui contrattempi, molto arretrato nel mix;
 *  - melodia costruita su una cellula ritmica di due battute, ripetuta con
 *    variazioni di altezza vincolate alla scala e agli accordi.
 */
class IslandLayer(
    private val sampleRate: Int,
    private val rng: GameRandom,
) {

    /** Gradi (in semitoni) delle fondamentali del giro armonico. */
    private val progression = intArrayOf(0, 5, 7, 5)

    /** Accenti dell'arpeggio sugli otto ottavi della battuta. */
    private val marimbaPattern = booleanArrayOf(true, false, true, true, false, true, true, false)

    /** Ottavi su cui cade lo shaker: solo contrattempi. */
    private val shakerPattern = booleanArrayOf(false, true, false, true, false, true, false, true)

    private val marimba = Array(4) { PluckVoice(0.006, 0.42) }
    private val bass = PluckVoice(0.010, 0.55)
    private val melody = Array(3) { PluckVoice(0.012, 0.85) }
    private val shaker = NoiseBurst()

    private var samplesToNextEighth = 0
    private var eighthIndex = 0

    /** Cellula ritmica della melodia: quali ottavi della coppia di battute suonano. */
    private var melodyCell = generateCell()

    /** Ultimo grado suonato dalla melodia, per privilegiare il moto per gradi. */
    private var lastMelodyDegree = 4

    private var marimbaCursor = 0
    private var melodyCursor = 0

    fun reset() {
        marimba.forEach { it.silence() }
        bass.silence()
        melody.forEach { it.silence() }
        shaker.silence()
        samplesToNextEighth = 0
        eighthIndex = 0
        melodyCell = generateCell()
        lastMelodyDegree = 4
    }

    fun render(palette: MusicPalette): Double {
        val tempo = palette.tempoBpm.coerceAtLeast(30.0)
        val samplesPerEighth = (60.0 / tempo / 2.0 * sampleRate).toInt().coerceAtLeast(1)

        if (samplesToNextEighth <= 0) {
            onEighth(palette)
            samplesToNextEighth = samplesPerEighth
        }
        samplesToNextEighth--

        var sum = 0.0
        for (voice in marimba) sum += voice.render() * palette.marimbaLevel
        sum += bass.render() * palette.bassLevel * 1.15
        for (voice in melody) sum += voice.render() * palette.melodyLevel
        sum += shaker.render() * palette.shakerLevel
        return sum * 0.55
    }

    /** Un passo della griglia: decide cosa attaccare su questo ottavo. */
    private fun onEighth(palette: MusicPalette) {
        val bar = (eighthIndex / 8) % progression.size
        val positionInBar = eighthIndex % 8
        val chordRoot = progression[bar]
        val scale = palette.scale.degrees
        val root = palette.rootHz

        // Triade dell'accordo, costruita prendendo i gradi della scala a distanza
        // di terza: in misolidio produce I e IV maggiori e v minore.
        val chordTones = chordTones(chordRoot, scale)

        if (marimbaPattern[positionInBar]) {
            val tone = chordTones[marimbaCursor % chordTones.size]
            marimbaCursor++
            val octave = if (positionInBar >= 5) 2 else 1
            marimba[marimbaCursor % marimba.size]
                .start(frequencyOf(root, tone + 12 * octave), 0.9)
        }

        if (positionInBar % 2 == 0) {
            // Basso camminante: fondamentale, quinta, fondamentale, terza.
            val degree = when (positionInBar) {
                0 -> chordTones[0]
                2 -> chordTones[2]
                4 -> chordTones[0]
                else -> chordTones[1]
            }
            bass.start(frequencyOf(root, degree - 12), 1.0)
        }

        if (shakerPattern[positionInBar]) {
            shaker.strike(sampleRate, if (positionInBar == 3 || positionInBar == 7) 1.0 else 0.6)
        }

        // Melodia: due battute di cellula ritmica, poi variazione.
        val cellPosition = eighthIndex % 16
        if (melodyCell[cellPosition]) {
            val degree = nextMelodyDegree(scale, chordTones)
            melody[melodyCursor % melody.size].start(frequencyOf(root, degree + 24), 0.8)
            melodyCursor++
        }
        if (cellPosition == 15 && rng.chance(0.5)) melodyCell = generateCell()

        eighthIndex = (eighthIndex + 1) % (8 * progression.size)
    }

    private fun chordTones(chordRoot: Int, scale: IntArray): IntArray {
        val index = scale.indexOfFirst { it == chordRoot }.let { if (it < 0) 0 else it }
        fun degreeAt(step: Int): Int {
            val position = index + step
            val octaves = position / scale.size
            return scale[position % scale.size] + 12 * octaves
        }
        return intArrayOf(degreeAt(0), degreeAt(2), degreeAt(4))
    }

    /**
     * Sceglie il grado successivo della melodia: moto per gradi nella maggior
     * parte dei casi, salto su una nota dell'accordo di tanto in tanto. E' la
     * regola minima che distingue una melodia da una sequenza casuale.
     */
    private fun nextMelodyDegree(scale: IntArray, chordTones: IntArray): Int {
        val useChordTone = rng.chance(0.4)
        if (useChordTone) {
            val tone = chordTones[rng.nextInt(chordTones.size)]
            lastMelodyDegree = scale.indexOfFirst { it == tone % 12 }.let { if (it < 0) lastMelodyDegree else it }
            return tone
        }
        val direction = if (rng.chance(0.5)) 1 else -1
        val stepSize = if (rng.chance(0.75)) 1 else 2
        lastMelodyDegree = (lastMelodyDegree + direction * stepSize).coerceIn(0, scale.size - 1)
        return scale[lastMelodyDegree]
    }

    /** Cellula ritmica su 16 ottavi (due battute), con la prima nota sempre in battere. */
    private fun generateCell(): BooleanArray {
        val cell = BooleanArray(16)
        cell[0] = true
        var position = 0
        while (position < 15) {
            // Durate di uno, due o tre ottavi: irregolarita' controllata.
            position += 1 + rng.nextInt(3)
            if (position < 16 && rng.chance(0.72)) cell[position] = true
        }
        return cell
    }

    private fun frequencyOf(root: Double, semitones: Int): Double =
        root * Math.pow(2.0, semitones / 12.0)

    /**
     * Voce pizzicata: attacco rapido e decadimento esponenziale, con una parziale
     * superiore che da' il timbro legnoso della marimba.
     */
    private inner class PluckVoice(
        private val attackSeconds: Double,
        private val decaySeconds: Double,
    ) {
        private var phase = 0.0
        private var overtonePhase = 0.0
        private var frequency = 0.0
        private var envelope = 0.0
        private var attackStep = 0.0
        private var decayFactor = 0.0
        private var attacking = false
        private var active = false

        fun start(freq: Double, amplitude: Double) {
            frequency = freq
            phase = 0.0
            overtonePhase = 0.0
            envelope = 0.0
            attackStep = amplitude / (attackSeconds * sampleRate)
            decayFactor = exp(-1.0 / (decaySeconds * sampleRate))
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
                envelope *= decayFactor
                if (envelope < 1e-4) { silence(); return 0.0 }
            }
            phase = advance(phase, frequency)
            overtonePhase = advance(overtonePhase, frequency * 4.0)
            val wave = sin(phase) + 0.22 * sin(overtonePhase) + 0.12 * triangle(phase)
            return wave * envelope * 0.6
        }
    }

    /** Colpo di shaker: rumore filtrato con decadimento molto rapido. */
    private inner class NoiseBurst {
        private var envelope = 0.0
        private var factor = 0.0
        private var highpassState = 0.0

        fun strike(rate: Int, amplitude: Double) {
            envelope = amplitude
            factor = exp(-1.0 / (0.045 * rate))
        }

        fun silence() {
            envelope = 0.0
        }

        fun render(): Double {
            if (envelope < 1e-4) return 0.0
            val white = rng.nextDouble() * 2.0 - 1.0
            // Passa-alto a un polo: lo shaker vive sopra i 2 kHz.
            highpassState += 0.35 * (white - highpassState)
            val value = (white - highpassState) * envelope
            envelope *= factor
            return value * 0.7
        }
    }

    private fun advance(phase: Double, frequency: Double): Double {
        var next = phase + 2.0 * PI * frequency / sampleRate
        if (next > 2.0 * PI) next -= 2.0 * PI
        return next
    }

    private fun triangle(phase: Double): Double {
        val t = phase / (2.0 * PI)
        return 4.0 * abs(t - 0.5) - 1.0
    }
}
