package com.changewave.dungeon.audio

/**
 * Modi musicali usati dalla colonna sonora, ordinati per dissonanza crescente.
 *
 * La progressione non e' arbitraria: eoliana (minore naturale) suona malinconica,
 * frigia aggiunge la seconda minore, locria toglie anche la quinta giusta
 * sostituendola con il tritono, e l'ultima scala e' costruita sul tritono per
 * negare qualunque centro tonale stabile.
 */
enum class MusicScale(val degrees: IntArray, val dissonance: Double, val italian: String) {
    /** Maggiore con settima abbassata: il colore da avventura, non da minaccia. */
    MIXOLYDIAN(intArrayOf(0, 2, 4, 5, 7, 9, 10), 0.00, "misolidia"),
    AEOLIAN(intArrayOf(0, 2, 3, 5, 7, 8, 10), 0.00, "eoliana"),
    PHRYGIAN(intArrayOf(0, 1, 3, 5, 7, 8, 10), 0.35, "frigia"),
    LOCRIAN(intArrayOf(0, 1, 3, 5, 6, 8, 10), 0.70, "locria"),
    DIABOLUS(intArrayOf(0, 1, 3, 6, 7, 9, 10), 1.00, "diabolus in musica");
}

/**
 * Famiglia di strumentazione. Il primo livello e' ancora "in superficie": suona
 * come una partenza per un'avventura, non come una tomba. Dal secondo in giu'
 * comanda la progressione cavernosa.
 */
enum class MusicStyle { ISLAND, CAVERN }

/**
 * Tutti i parametri del sintetizzatore per una data profondita'. Nessun campo e'
 * "magico": ognuno corrisponde a una manopola del motore audio.
 */
data class MusicPalette(
    val depth: Int,
    val style: MusicStyle,
    /** Fondamentale del bordone, in Hz. Scende con la profondita'. */
    val rootHz: Double,
    val scale: MusicScale,
    /** Livelli dei singoli strati, 0..1. */
    val droneLevel: Double,
    val padLevel: Double,
    val noiseLevel: Double,
    val bellLevel: Double,
    val heartLevel: Double,
    /** Scordatura fra gli oscillatori del bordone, in centesimi di semitono. */
    val detuneCents: Double,
    /** Battiti al minuto del pulsare cardiaco. */
    val heartBpm: Double,
    /** Riverbero a eco: quanto del segnale ritorna nella linea di ritardo, 0..0,85. */
    val delayFeedback: Double,
    /** Taglio del filtro passa-basso, in Hz: piu' basso = suono piu' cupo e ovattato. */
    val lowpassHz: Double,
    /** Frequenza media degli eventi melodici, in eventi al secondo. */
    val noteRateHz: Double,
    /** Probabilita' che una nota diventi un cluster di seconde minori. */
    val clusterChance: Double,
    /** Indice sintetico di "macabrita'", 0..1: usato per test e diagnostica. */
    val macabreIndex: Double,
    // --- parametri usati dal solo stile ISLAND --------------------------------
    /** Tempo metronomico del brano ritmico. */
    val tempoBpm: Double = 96.0,
    /** Livello dell'arpeggio percussivo tipo marimba. */
    val marimbaLevel: Double = 0.0,
    /** Livello del basso camminante. */
    val bassLevel: Double = 0.0,
    /** Livello della linea melodica. */
    val melodyLevel: Double = 0.0,
    /** Livello dello shaker (rumore filtrato in controtempo). */
    val shakerLevel: Double = 0.0,
) {
    /** Interpolazione lineare dei soli parametri continui (la scala cambia di scatto). */
    fun blend(other: MusicPalette, factor: Double): MusicPalette {
        val f = factor.coerceIn(0.0, 1.0)
        fun mix(a: Double, b: Double) = a + (b - a) * f
        return MusicPalette(
            depth = if (f >= 0.5) other.depth else depth,
            style = if (f >= 0.5) other.style else style,
            rootHz = mix(rootHz, other.rootHz),
            scale = if (f >= 0.5) other.scale else scale,
            droneLevel = mix(droneLevel, other.droneLevel),
            padLevel = mix(padLevel, other.padLevel),
            noiseLevel = mix(noiseLevel, other.noiseLevel),
            bellLevel = mix(bellLevel, other.bellLevel),
            heartLevel = mix(heartLevel, other.heartLevel),
            detuneCents = mix(detuneCents, other.detuneCents),
            heartBpm = mix(heartBpm, other.heartBpm),
            delayFeedback = mix(delayFeedback, other.delayFeedback),
            lowpassHz = mix(lowpassHz, other.lowpassHz),
            noteRateHz = mix(noteRateHz, other.noteRateHz),
            clusterChance = mix(clusterChance, other.clusterChance),
            macabreIndex = mix(macabreIndex, other.macabreIndex),
            tempoBpm = mix(tempoBpm, other.tempoBpm),
            marimbaLevel = mix(marimbaLevel, other.marimbaLevel),
            bassLevel = mix(bassLevel, other.bassLevel),
            melodyLevel = mix(melodyLevel, other.melodyLevel),
            shakerLevel = mix(shakerLevel, other.shakerLevel),
        )
    }

    /** Peso dello stile ISLAND durante una transizione, 0..1. */
    fun islandWeight(): Double = if (style == MusicStyle.ISLAND) 1.0 else 0.0
}

/**
 * Traduce la profondita' del dungeon in parametri musicali.
 *
 * Criterio di progettazione: tutto cio' che rende un suono inquietante cresce in
 * modo monotono con la profondita' (dissonanza, rumore, riverbero, battito,
 * scordatura), mentre tutto cio' che lo rende rassicurante diminuisce (chiarezza
 * del filtro, stabilita' tonale, registro).
 *
 *  - livelli 1-2: bordone lento in minore naturale, quasi nessun rumore
 *  - livelli 3-4: entra il battito cardiaco, il filtro si chiude
 *  - livelli 5-6: scala frigia, rintocchi di campana, letto di rumore
 *  - livelli 7-8: scala locria, cluster di seconde minori, riverbero cavernoso
 *  - livelli 9-10: tritono come centro, sub-basso, battito accelerato
 */
object MusicDirector {

    const val MAX_DEPTH = 10

    fun paletteFor(depth: Int): MusicPalette {
        val d = depth.coerceIn(1, MAX_DEPTH)
        if (d == 1) return islandPalette()
        // Progressione 0..1 sulla profondita' cavernosa (dal livello 2 al 10):
        // e' la variabile che comanda tutto.
        val t = (d - 2).toDouble() / (MAX_DEPTH - 2)

        val scale = when {
            d <= 2 -> MusicScale.AEOLIAN
            d <= 4 -> MusicScale.AEOLIAN
            d <= 6 -> MusicScale.PHRYGIAN
            d <= 8 -> MusicScale.LOCRIAN
            else -> MusicScale.DIABOLUS
        }

        // La fondamentale scende di circa 5 semitoni dal primo all'ultimo livello:
        // il terreno sonoro si abbassa mentre si scende fisicamente.
        val rootHz = 55.0 * Math.pow(2.0, -(5.0 * t) / 12.0)

        return MusicPalette(
            depth = d,
            style = MusicStyle.CAVERN,
            rootHz = rootHz,
            scale = scale,
            droneLevel = 0.42 + 0.18 * t,
            // Il pad cresce leggermente con la profondita': e' lo strato che porta
            // il contenuto nei medi, dove i piccoli altoparlanti lavorano.
            padLevel = 0.34 + 0.06 * t,
            noiseLevel = 0.05 + 0.33 * t,
            bellLevel = if (d >= 5) 0.18 + 0.22 * t else 0.0,
            // Il battito vive tutto sotto gli 80 Hz: in cuffia si sente eccome, su
            // altoparlante quasi no, quindi non gli si affida la tensione sonora.
            heartLevel = if (d >= 3) 0.10 + 0.14 * t else 0.0,
            detuneCents = 4.0 + 26.0 * t,
            heartBpm = 44.0 + 30.0 * t,
            delayFeedback = 0.30 + 0.42 * t,
            // Il filtro si chiude scendendo, ma non sotto i 1700 Hz: sotto quella
            // soglia sparirebbe tutto cio' che un altoparlante di telefono riesce
            // a riprodurre, e la musica risulterebbe muta senza cuffie.
            lowpassHz = 3400.0 - 1700.0 * t,
            // Note piu' frequenti scendendo: il pad si sovrappone a se stesso e
            // copre in modo continuo la banda dei medi, senza vuoti di silenzio.
            noteRateHz = 0.17 + 0.23 * t,
            clusterChance = 0.05 + 0.55 * t,
            // Parte da 0,06 e non da 0: il livello 1, in stile isola, e' lo zero
            // della scala di macabrita'.
            macabreIndex = (0.06 + 0.49 * t + 0.45 * scale.dissonance).coerceIn(0.0, 1.0),
        )
    }

    /**
     * Tema del primo livello: materiale originale in stile avventura caraibica.
     * Modo misolidio, giro armonico I-IV-V-IV, arpeggio percussivo, basso
     * camminante e shaker in controtempo. Filtro aperto ed eco corta: il primo
     * livello e' luminoso, e il contrasto con la discesa successiva e' il punto.
     */
    private fun islandPalette(): MusicPalette = MusicPalette(
        depth = 1,
        style = MusicStyle.ISLAND,
        rootHz = 110.0,
        scale = MusicScale.MIXOLYDIAN,
        droneLevel = 0.10,
        padLevel = 0.12,
        noiseLevel = 0.0,
        bellLevel = 0.0,
        heartLevel = 0.0,
        detuneCents = 5.0,
        heartBpm = 0.0,
        delayFeedback = 0.22,
        lowpassHz = 6200.0,
        noteRateHz = 0.0,
        clusterChance = 0.0,
        macabreIndex = 0.0,
        tempoBpm = 96.0,
        marimbaLevel = 0.46,
        bassLevel = 0.38,
        melodyLevel = 0.36,
        shakerLevel = 0.12,
    )

    /** Descrizione leggibile, mostrata nella schermata delle impostazioni. */
    fun describe(depth: Int): String {
        val p = paletteFor(depth)
        if (p.style == MusicStyle.ISLAND) {
            return "Livello ${p.depth}: tema d'avventura, scala ${p.scale.italian}, " +
                "${p.tempoBpm.toInt()} bpm, macabrita' 0%"
        }
        return "Livello ${p.depth}: scala ${p.scale.italian}, fondamentale ${"%.1f".format(p.rootHz)} Hz, " +
            "macabrita' ${(p.macabreIndex * 100).toInt()}%"
    }
}
