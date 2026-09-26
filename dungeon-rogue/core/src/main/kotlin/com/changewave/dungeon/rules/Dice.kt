package com.changewave.dungeon.rules

import kotlinx.serialization.Serializable

/**
 * Generatore pseudo-casuale deterministico (xorshift64*).
 *
 * Non usa [kotlin.random.Random] perche' lo stato interno di quest'ultimo non e'
 * serializzabile in modo portabile: qui lo stato e' un singolo Long, quindi un
 * salvataggio ricaricato riproduce esattamente la stessa sequenza di tiri.
 */
@Serializable
class GameRandom(private var state: Long) {

    init {
        require(state != 0L) { "Il seed xorshift non puo' essere 0" }
    }

    /** Copia indipendente, utile per simulazioni "what-if" senza consumare lo stato. */
    fun fork(): GameRandom = GameRandom(state)

    fun nextLong(): Long {
        var x = state
        x = x xor (x shl 13)
        x = x xor (x ushr 7)
        x = x xor (x shl 17)
        state = x
        return x * -0x61c8864680b583ebL
    }

    /** Intero uniforme in [0, bound). */
    fun nextInt(bound: Int): Int {
        require(bound > 0) { "bound deve essere positivo, ricevuto $bound" }
        // Rejection sampling: evita il bias del modulo su bound non potenza di 2.
        val limit = Long.MAX_VALUE - (Long.MAX_VALUE % bound)
        while (true) {
            val v = nextLong() and Long.MAX_VALUE
            if (v < limit) return (v % bound).toInt()
        }
    }

    /** Intero uniforme in [min, max], estremi inclusi. */
    fun nextInt(min: Int, max: Int): Int {
        require(max >= min)
        return min + nextInt(max - min + 1)
    }

    fun nextDouble(): Double = (nextLong() ushr 11).toDouble() / (1L shl 53).toDouble()

    fun chance(probability: Double): Boolean = nextDouble() < probability

    fun <T> pick(items: List<T>): T {
        require(items.isNotEmpty()) { "Lista vuota" }
        return items[nextInt(items.size)]
    }

    /** Estrazione pesata; i pesi devono essere >= 0 e non tutti nulli. */
    fun <T> pickWeighted(items: List<T>, weight: (T) -> Int): T {
        val total = items.sumOf { weight(it).coerceAtLeast(0) }
        require(total > 0) { "Somma dei pesi nulla" }
        var roll = nextInt(total)
        for (item in items) {
            roll -= weight(item).coerceAtLeast(0)
            if (roll < 0) return item
        }
        return items.last()
    }

    fun <T> shuffled(items: List<T>): List<T> {
        val out = items.toMutableList()
        for (i in out.indices.reversed()) {
            val j = nextInt(i + 1)
            val tmp = out[i]; out[i] = out[j]; out[j] = tmp
        }
        return out
    }

    /** Singolo dado a [sides] facce, risultato in [1, sides]. */
    fun die(sides: Int): Int = nextInt(sides) + 1

    companion object {
        fun fromSeed(seed: Long): GameRandom = GameRandom(if (seed == 0L) 0x2545F4914F6CDD1DL else seed)
    }
}

/** Modalita' di tiro d20 (PHB: vantaggio/svantaggio non si sommano, si annullano). */
enum class RollMode {
    NORMAL, ADVANTAGE, DISADVANTAGE;

    fun combine(other: RollMode): RollMode = when {
        this == other -> this
        this == NORMAL -> other
        other == NORMAL -> this
        else -> NORMAL // vantaggio + svantaggio => tiro normale
    }

    companion object {
        fun of(advantage: Boolean, disadvantage: Boolean): RollMode = when {
            advantage && !disadvantage -> ADVANTAGE
            disadvantage && !advantage -> DISADVANTAGE
            else -> NORMAL
        }
    }
}

/** Esito di un tiro di d20 con i dadi grezzi conservati per il log di combattimento. */
data class D20Roll(
    val natural: Int,
    val discarded: Int?,
    val modifier: Int,
    val mode: RollMode,
) {
    val total: Int get() = natural + modifier
    val isCriticalHit: Boolean get() = natural == 20
    val isCriticalMiss: Boolean get() = natural == 1

    override fun toString(): String {
        val dice = if (discarded == null) "$natural" else "$natural/$discarded"
        val sign = if (modifier >= 0) "+" else "-"
        return "d20($dice)$sign${kotlin.math.abs(modifier)} = $total"
    }
}

fun GameRandom.rollD20(modifier: Int = 0, mode: RollMode = RollMode.NORMAL): D20Roll {
    val first = die(20)
    return when (mode) {
        RollMode.NORMAL -> D20Roll(first, null, modifier, mode)
        RollMode.ADVANTAGE -> {
            val second = die(20)
            if (second >= first) D20Roll(second, first, modifier, mode)
            else D20Roll(first, second, modifier, mode)
        }
        RollMode.DISADVANTAGE -> {
            val second = die(20)
            if (second <= first) D20Roll(second, first, modifier, mode)
            else D20Roll(first, second, modifier, mode)
        }
    }
}

/**
 * Espressione di dado in notazione standard D&D: `1d8`, `2d6+3`, `4d4-1`, `3`.
 */
@Serializable
data class DiceExpr(val count: Int, val sides: Int, val modifier: Int = 0) {

    init {
        require(count >= 0) { "count negativo" }
        require(sides > 0) { "sides deve essere > 0" }
    }

    /** Media matematica (usata per HP dei mostri e stime di bilanciamento). */
    val average: Double get() = count * (sides + 1) / 2.0 + modifier

    val minimum: Int get() = count + modifier
    val maximum: Int get() = count * sides + modifier

    fun roll(rng: GameRandom, critical: Boolean = false, bonusModifier: Int = 0): Int {
        val dice = if (critical) count * 2 else count // PHB: il critico raddoppia i dadi, non il modificatore
        var total = 0
        repeat(dice) { total += rng.die(sides) }
        return (total + modifier + bonusModifier).coerceAtLeast(0)
    }

    override fun toString(): String = buildString {
        if (count > 0) append("${count}d$sides")
        if (modifier != 0) {
            if (count > 0) append(if (modifier > 0) "+" else "-")
            append(kotlin.math.abs(modifier))
        }
        if (count == 0 && modifier == 0) append("0")
    }

    companion object {
        private val PATTERN = Regex("""^\s*(?:(\d+)\s*[dD]\s*(\d+))?\s*(?:([+-])\s*(\d+))?\s*$""")

        fun parse(text: String): DiceExpr {
            val m = PATTERN.matchEntire(text) ?: error("Espressione di dado non valida: '$text'")
            val (c, s, sign, mod) = m.destructured
            if (c.isEmpty() && mod.isEmpty()) error("Espressione di dado non valida: '$text'")
            val modifier = if (mod.isEmpty()) 0 else mod.toInt() * (if (sign == "-") -1 else 1)
            if (c.isEmpty()) return DiceExpr(0, 1, modifier)
            return DiceExpr(c.toInt(), s.toInt(), modifier)
        }

        val NONE = DiceExpr(0, 1, 0)
    }
}
