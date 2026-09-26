package com.changewave.dungeon

import com.changewave.dungeon.model.Bestiary
import com.changewave.dungeon.model.HeroClass
import com.changewave.dungeon.model.ItemCatalog
import com.changewave.dungeon.model.PlayerCharacter
import com.changewave.dungeon.model.Progression
import com.changewave.dungeon.rules.Ability
import com.changewave.dungeon.rules.AbilityScores
import com.changewave.dungeon.rules.Combat
import com.changewave.dungeon.rules.Condition
import com.changewave.dungeon.rules.DamageType
import com.changewave.dungeon.rules.GameRandom
import com.changewave.dungeon.rules.RollMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CombatTest {

    private fun fighter(): PlayerCharacter =
        PlayerCharacter.create("Test", HeroClass.FIGHTER, AbilityScores(16, 12, 14, 10, 10, 10))

    @Test
    fun `il guerriero di primo livello ha i PF corretti`() {
        val pc = fighter()
        // d10 (10) + mod COS (+2) = 12
        assertEquals(12, pc.maxHitPoints)
        assertEquals(12, pc.hitPoints)
    }

    @Test
    fun `la classe armatura somma armatura destrezza e scudo`() {
        val pc = fighter()
        // Cotta di maglia (16, nessuna DES) + scudo (2) = 18
        assertEquals(18, pc.armorClass)
        pc.armor = ItemCatalog.LEATHER // 11 + DES(+1) = 12, +2 scudo = 14
        assertEquals(14, pc.armorClass)
        pc.shield = null
        assertEquals(12, pc.armorClass)
        pc.armor = null // senza armatura: 10 + DES
        assertEquals(11, pc.armorClass)
    }

    @Test
    fun `il bonus di attacco usa caratteristica piu' competenza`() {
        val pc = fighter()
        // Spada lunga: FOR +3, competenza +2 => +5
        assertEquals(5, pc.attackBonus(ItemCatalog.LONGSWORD))
        // Arma agile con DES piu' bassa: usa comunque la migliore tra FOR e DES
        assertEquals(5, pc.attackBonus(ItemCatalog.DAGGER))
    }

    @Test
    fun `il mago non e' competente con la spada lunga`() {
        val wizard = PlayerCharacter.create("Mago", HeroClass.WIZARD, AbilityScores(8, 14, 12, 16, 12, 10))
        assertFalse(wizard.heroClass.isProficientWithWeapon(ItemCatalog.LONGSWORD))
        // FOR -1, nessuna competenza
        assertEquals(-1, wizard.attackBonus(ItemCatalog.LONGSWORD))
    }

    @Test
    fun `un attacco contro CA molto alta manca quasi sempre e viceversa`() {
        val pc = fighter()
        val rng = GameRandom.fromSeed(31337)
        val tough = Bestiary.DEATH_LORD.instantiate(rng) // CA 17
        var hits = 0
        repeat(1000) {
            tough.hitPoints = 1000
            if (Combat.playerAttack(pc, tough, rng).hit) hits++
        }
        // +5 contro CA 17: serve 12+, cioe' ~45% (piu' i critici naturali)
        assertTrue(hits in 380..520, "colpi a segno: $hits")
    }

    @Test
    fun `il colpo critico raddoppia i dadi di danno`() {
        val pc = fighter()
        val rng = GameRandom.fromSeed(4242)
        var maxNormal = 0
        var maxCritical = 0
        repeat(4000) {
            val goblin = Bestiary.GOBLIN.instantiate(rng)
            goblin.hitPoints = 500
            val outcome = Combat.playerAttack(pc, goblin, rng)
            if (outcome.hit) {
                if (outcome.critical) maxCritical = maxOf(maxCritical, outcome.damage)
                else maxNormal = maxOf(maxNormal, outcome.damage)
            }
        }
        // Spada lunga 1d8+3: normale max 11, critico max 19
        assertEquals(11, maxNormal)
        assertTrue(maxCritical > 11, "danno critico massimo: $maxCritical")
        assertTrue(maxCritical <= 19)
    }

    @Test
    fun `le resistenze dimezzano il danno`() {
        val rng = GameRandom.fromSeed(11)
        val skeleton = Bestiary.SKELETON.instantiate(rng) // resistente al perforante
        assertEquals(5, skeleton.damageAfterDefenses(10, DamageType.PIERCING))
        assertEquals(10, skeleton.damageAfterDefenses(10, DamageType.SLASHING))
        assertEquals(0, skeleton.damageAfterDefenses(10, DamageType.POISON)) // immune
    }

    @Test
    fun `il bersaglio privo di sensi subisce critici automatici in mischia`() {
        val pc = fighter()
        val rng = GameRandom.fromSeed(909)
        val goblin = Bestiary.GOBLIN.instantiate(rng)
        goblin.hitPoints = 500
        goblin.conditions = goblin.conditions.add(Condition.UNCONSCIOUS, 5)
        var criticals = 0
        repeat(50) {
            val outcome = Combat.playerAttack(pc, goblin, rng)
            if (outcome.hit) {
                assertTrue(outcome.critical, "un colpo su bersaglio privo di sensi deve essere critico")
                criticals++
            }
        }
        assertTrue(criticals > 40, "critici: $criticals")
    }

    @Test
    fun `chi e' paralizzato fallisce automaticamente i TS su Forza e Destrezza`() {
        val pc = fighter()
        val rng = GameRandom.fromSeed(77)
        pc.conditions = pc.conditions.add(Condition.PARALYZED, 3)
        repeat(50) {
            assertFalse(Combat.savingThrow(pc, Ability.DEXTERITY, 5, rng).success)
            assertFalse(Combat.savingThrow(pc, Ability.STRENGTH, 5, rng).success)
        }
        // Gli altri TS restano normali
        assertTrue((1..50).any { Combat.savingThrow(pc, Ability.CONSTITUTION, 5, rng).success })
    }

    @Test
    fun `l'avvelenamento impone svantaggio agli attacchi`() {
        val pc = fighter()
        val rng = GameRandom.fromSeed(123)
        val goblin = Bestiary.GOBLIN.instantiate(rng)
        assertEquals(RollMode.NORMAL, Combat.attackMode(pc, goblin))
        pc.conditions = pc.conditions.add(Condition.POISONED, 5)
        assertEquals(RollMode.DISADVANTAGE, Combat.attackMode(pc, goblin))
        // Bersaglio prono: vantaggio dell'attaccante annulla lo svantaggio
        goblin.conditions = goblin.conditions.add(Condition.PRONE, 2)
        assertEquals(RollMode.NORMAL, Combat.attackMode(pc, goblin))
    }

    @Test
    fun `l'attacco furtivo del ladro scatta solo con vantaggio o sorpresa`() {
        val rogue = PlayerCharacter.create("Ladro", HeroClass.ROGUE, AbilityScores(10, 16, 12, 12, 12, 10))
        val rng = GameRandom.fromSeed(5150)
        var sneaksWhenAware = 0
        var sneaksWhenSurprised = 0
        repeat(300) {
            val goblin = Bestiary.GOBLIN.instantiate(rng)
            goblin.hitPoints = 500
            goblin.alerted = true
            if (Combat.playerAttack(rogue, goblin, rng, surprised = false).sneakAttack) sneaksWhenAware++
            if (Combat.playerAttack(rogue, goblin, rng, surprised = true).sneakAttack) sneaksWhenSurprised++
        }
        assertEquals(0, sneaksWhenAware)
        assertTrue(sneaksWhenSurprised > 100, "attacchi furtivi da sorpresa: $sneaksWhenSurprised")
    }

    @Test
    fun `i dadi di attacco furtivo scalano con il livello`() {
        val rogue = PlayerCharacter.create("Ladro", HeroClass.ROGUE, AbilityScores(10, 16, 12, 12, 12, 10))
        val rng = GameRandom.fromSeed(1)
        assertEquals(1, rogue.sneakAttackDice().count)
        rogue.gainExperience(Progression.XP_THRESHOLDS[5], rng) // livello 5
        assertEquals(5, rogue.level)
        assertEquals(3, rogue.sneakAttackDice().count)
    }

    @Test
    fun `l'attacco del mostro puo' applicare condizioni con tiro salvezza`() {
        val pc = PlayerCharacter.create("Vittima", HeroClass.WIZARD, AbilityScores(8, 8, 8, 16, 10, 10))
        pc.hitPointMaximum = 500
        pc.hitPoints = 500
        val rng = GameRandom.fromSeed(2718)
        val ghoul = Bestiary.GHOUL.instantiate(rng)
        val attack = ghoul.meleeAttack()!!
        var paralyzed = false
        repeat(60) {
            Combat.monsterAttack(ghoul, attack, pc, rng)
            if (Condition.PARALYZED in pc.conditions) paralyzed = true
            pc.conditions = pc.conditions.remove(Condition.PARALYZED)
        }
        assertTrue(paralyzed, "il ghoul deve poter paralizzare con l'artiglio")
    }

    @Test
    fun `progressione dei livelli secondo la tabella PX`() {
        val pc = fighter()
        val rng = GameRandom.fromSeed(31)
        assertEquals(1, pc.level)
        pc.gainExperience(299, rng)
        assertEquals(1, pc.level)
        pc.gainExperience(1, rng)
        assertEquals(2, pc.level)
        pc.gainExperience(64_000, rng)
        assertEquals(Progression.MAX_LEVEL, pc.level)
        assertEquals(4, pc.proficiency)
        assertTrue(pc.hitPointMaximum > 12 + 9, "PF cresciuti: ${pc.hitPointMaximum}")
        // Incremento di caratteristica ai livelli 4 e 8: +2 FOR due volte
        assertEquals(20, pc.abilities[Ability.STRENGTH])
    }

    @Test
    fun `gli slot incantesimo seguono la tabella dell'incantatore completo`() {
        val wizard = PlayerCharacter.create("Mago", HeroClass.WIZARD, AbilityScores(8, 14, 12, 16, 12, 10))
        assertEquals(2, wizard.maxSlots(1))
        assertEquals(0, wizard.maxSlots(2))
        val rng = GameRandom.fromSeed(5)
        wizard.gainExperience(Progression.XP_THRESHOLDS[4], rng)
        assertEquals(4, wizard.level)
        assertEquals(4, wizard.maxSlots(1))
        assertEquals(3, wizard.maxSlots(2)) // tabella PHB: al 4o livello 4 slot di 1o e 3 di 2o
        assertTrue(wizard.consumeSlot(1))
        assertEquals(3, wizard.availableSlots(1))
        // Il guerriero non e' incantatore
        val fighter = fighter()
        assertEquals(0, fighter.maxSlots(1))
        assertFalse(fighter.consumeSlot(1))
    }
}
