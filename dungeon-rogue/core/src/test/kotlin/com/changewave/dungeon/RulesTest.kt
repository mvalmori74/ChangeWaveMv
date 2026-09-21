package com.changewave.dungeon

import com.changewave.dungeon.rules.Ability
import com.changewave.dungeon.rules.AbilityScores
import com.changewave.dungeon.rules.Condition
import com.changewave.dungeon.rules.ConditionSet
import com.changewave.dungeon.rules.proficiencyBonus
import com.changewave.dungeon.rules.spellSaveDc
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RulesTest {

    @Test
    fun `modificatori di caratteristica come da PHB`() {
        val expected = mapOf(
            1 to -5, 2 to -4, 3 to -4, 8 to -1, 9 to -1, 10 to 0, 11 to 0,
            12 to 1, 13 to 1, 14 to 2, 15 to 2, 16 to 3, 17 to 3, 18 to 4, 20 to 5,
        )
        expected.forEach { (score, modifier) ->
            assertEquals(modifier, AbilityScores.modifierOf(score), "punteggio $score")
        }
    }

    @Test
    fun `bonus di competenza per livello`() {
        val expected = listOf(1 to 2, 4 to 2, 5 to 3, 8 to 3, 9 to 4, 12 to 4, 13 to 5, 17 to 6, 20 to 6)
        expected.forEach { (level, bonus) -> assertEquals(bonus, proficiencyBonus(level), "livello $level") }
    }

    @Test
    fun `CD incantesimi = 8 + competenza + modificatore`() {
        assertEquals(13, spellSaveDc(1, 3))
        assertEquals(15, spellSaveDc(5, 4))
    }

    @Test
    fun `le condizioni scadono al momento giusto`() {
        var set = ConditionSet.EMPTY.add(Condition.POISONED, 2)
        assertTrue(Condition.POISONED in set)
        var expired: List<Condition>
        val first = set.tick(); set = first.first; expired = first.second
        assertTrue(expired.isEmpty())
        assertTrue(Condition.POISONED in set)
        val second = set.tick(); set = second.first; expired = second.second
        assertEquals(listOf(Condition.POISONED), expired)
        assertFalse(Condition.POISONED in set)
    }

    @Test
    fun `durate sovrapposte non si sommano ma prevale la piu' lunga`() {
        val set = ConditionSet.EMPTY.add(Condition.FRIGHTENED, 3).add(Condition.FRIGHTENED, 6)
        assertEquals(1, set.active.size)
        assertEquals(6, set.active.first().turnsLeft)
    }

    @Test
    fun `le condizioni incapacitanti bloccano l'azione`() {
        assertTrue(ConditionSet.EMPTY.add(Condition.PARALYZED, 1).preventsAction())
        assertTrue(ConditionSet.EMPTY.add(Condition.UNCONSCIOUS, 1).preventsAction())
        assertFalse(ConditionSet.EMPTY.add(Condition.POISONED, 1).preventsAction())
    }

    @Test
    fun `standard array e caratteristiche consigliate`() {
        val scores = AbilityScores().with(Ability.STRENGTH, 15)
        assertEquals(15, scores[Ability.STRENGTH])
        assertEquals(2, scores.modifier(Ability.STRENGTH))
        assertEquals(listOf(15, 14, 13, 12, 10, 8), AbilityScores.STANDARD_ARRAY)
    }
}
