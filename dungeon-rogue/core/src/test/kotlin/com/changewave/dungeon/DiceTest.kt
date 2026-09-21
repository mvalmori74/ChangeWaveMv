package com.changewave.dungeon

import com.changewave.dungeon.rules.DiceExpr
import com.changewave.dungeon.rules.GameRandom
import com.changewave.dungeon.rules.RollMode
import com.changewave.dungeon.rules.rollD20
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class DiceTest {

    @Test
    fun `il generatore e' deterministico a parita' di seed`() {
        val a = GameRandom.fromSeed(12345)
        val b = GameRandom.fromSeed(12345)
        repeat(1000) { assertEquals(a.nextInt(100), b.nextInt(100)) }
    }

    @Test
    fun `seed diversi producono sequenze diverse`() {
        val a = GameRandom.fromSeed(1)
        val b = GameRandom.fromSeed(2)
        val seqA = List(20) { a.nextInt(1000) }
        val seqB = List(20) { b.nextInt(1000) }
        assertNotEquals(seqA, seqB)
    }

    @Test
    fun `i dadi restano nel proprio intervallo`() {
        val rng = GameRandom.fromSeed(7)
        repeat(20000) {
            val d6 = rng.die(6)
            assertTrue(d6 in 1..6, "d6 fuori intervallo: $d6")
            val d20 = rng.die(20)
            assertTrue(d20 in 1..20, "d20 fuori intervallo: $d20")
        }
    }

    @Test
    fun `la distribuzione del d20 e' ragionevolmente uniforme`() {
        val rng = GameRandom.fromSeed(99)
        val counts = IntArray(21)
        val samples = 200_000
        repeat(samples) { counts[rng.die(20)]++ }
        val expected = samples / 20.0
        for (face in 1..20) {
            val deviation = abs(counts[face] - expected) / expected
            assertTrue(deviation < 0.05, "Faccia $face deviata del ${"%.2f".format(deviation * 100)}%")
        }
    }

    @Test
    fun `parsing della notazione dei dadi`() {
        assertEquals(DiceExpr(2, 6, 3), DiceExpr.parse("2d6+3"))
        assertEquals(DiceExpr(1, 8, 0), DiceExpr.parse("1d8"))
        assertEquals(DiceExpr(4, 4, -1), DiceExpr.parse("4d4-1"))
        assertEquals(DiceExpr(1, 12), DiceExpr.parse(" 1 d 12 "))
        assertEquals("2d6+3", DiceExpr(2, 6, 3).toString())
    }

    @Test
    fun `media minimo e massimo di una espressione`() {
        val expr = DiceExpr(2, 6, 3)
        assertEquals(10.0, expr.average, 0.0001)
        assertEquals(5, expr.minimum)
        assertEquals(15, expr.maximum)
    }

    @Test
    fun `il critico raddoppia i dadi ma non il modificatore`() {
        // 1d8+5: normale in 6..13, critico in 7..21 (2d8+5)
        val rng = GameRandom.fromSeed(2024)
        val expr = DiceExpr(1, 8, 0)
        repeat(500) {
            val crit = expr.roll(rng, critical = true, bonusModifier = 5)
            assertTrue(crit in 7..21, "Critico fuori intervallo: $crit")
        }
    }

    @Test
    fun `il vantaggio alza la media e lo svantaggio la abbassa`() {
        val rng = GameRandom.fromSeed(555)
        val normal = (1..20000).sumOf { rng.rollD20().natural }.toDouble() / 20000
        val advantage = (1..20000).sumOf { rng.rollD20(mode = RollMode.ADVANTAGE).natural }.toDouble() / 20000
        val disadvantage = (1..20000).sumOf { rng.rollD20(mode = RollMode.DISADVANTAGE).natural }.toDouble() / 20000
        // Valori teorici: 10.5, 13.825, 7.175
        assertTrue(abs(normal - 10.5) < 0.15, "media normale $normal")
        assertTrue(abs(advantage - 13.825) < 0.15, "media con vantaggio $advantage")
        assertTrue(abs(disadvantage - 7.175) < 0.15, "media con svantaggio $disadvantage")
    }

    @Test
    fun `vantaggio e svantaggio si annullano`() {
        assertEquals(RollMode.NORMAL, RollMode.of(advantage = true, disadvantage = true))
        assertEquals(RollMode.ADVANTAGE, RollMode.ADVANTAGE.combine(RollMode.NORMAL))
        assertEquals(RollMode.NORMAL, RollMode.ADVANTAGE.combine(RollMode.DISADVANTAGE))
    }

    @Test
    fun `il tiro con vantaggio conserva il dado scartato`() {
        val rng = GameRandom.fromSeed(31)
        repeat(200) {
            val roll = rng.rollD20(3, RollMode.ADVANTAGE)
            assertTrue(roll.discarded != null)
            assertTrue(roll.natural >= roll.discarded!!)
            assertEquals(roll.natural + 3, roll.total)
        }
    }

    @Test
    fun `fork produce una sequenza identica senza consumare l'originale`() {
        val rng = GameRandom.fromSeed(404)
        val copy = rng.fork()
        val a = List(50) { rng.nextInt(1000) }
        val b = List(50) { copy.nextInt(1000) }
        assertEquals(a, b)
        assertFalse(a.isEmpty())
    }

    @Test
    fun `l'estrazione pesata rispetta i pesi`() {
        val rng = GameRandom.fromSeed(88)
        data class Entry(val name: String, val weight: Int)
        val items = listOf(Entry("raro", 1), Entry("comune", 9))
        var rare = 0
        repeat(10000) { if (rng.pickWeighted(items) { it.weight }.name == "raro") rare++ }
        assertTrue(rare in 800..1200, "Estrazioni rare: $rare (atteso ~1000)")
    }
}
