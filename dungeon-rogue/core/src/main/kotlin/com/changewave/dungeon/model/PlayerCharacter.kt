package com.changewave.dungeon.model

import com.changewave.dungeon.rules.Ability
import com.changewave.dungeon.rules.AbilityScores
import com.changewave.dungeon.rules.DiceExpr
import com.changewave.dungeon.rules.GameRandom
import com.changewave.dungeon.rules.proficiencyBonus
import com.changewave.dungeon.rules.spellSaveDc
import kotlinx.serialization.Serializable
import kotlin.math.max

/** Tabella PX -> livello del PHB (troncata al livello massimo di gioco). */
object Progression {
    val XP_THRESHOLDS = intArrayOf(0, 0, 300, 900, 2700, 6500, 14000, 23000, 34000, 48000, 64000)
    const val MAX_LEVEL = 10

    fun levelForExperience(xp: Int): Int {
        var level = 1
        for (l in 2..MAX_LEVEL) if (xp >= XP_THRESHOLDS[l]) level = l
        return level
    }

    fun experienceToNextLevel(xp: Int): Int? {
        val level = levelForExperience(xp)
        if (level >= MAX_LEVEL) return null
        return XP_THRESHOLDS[level + 1] - xp
    }
}

@Serializable
class PlayerCharacter(
    override val name: String,
    val heroClass: HeroClass,
    override var abilities: AbilityScores,
) : Actor() {

    var level: Int = 1
        private set
    var experience: Int = 0
        private set

    /** PF massimi accumulati livello per livello (non ricalcolabili: i dadi vita sono tirati). */
    var hitPointMaximum: Int = 1

    var weapon: Weapon = ItemCatalog.UNARMED
    var armor: Armor? = null
    var shield: Shield? = null
    val inventory: MutableList<Item> = mutableListOf()
    var gold: Int = 0

    /** Slot incantesimo consumati, indicizzati per livello di slot (1..2). */
    var usedSlotsLevel1: Int = 0
    var usedSlotsLevel2: Int = 0

    /** Recuperare Energie del guerriero: si ricarica scendendo di livello (riposo breve). */
    var secondWindAvailable: Boolean = true

    var deathSaveSuccesses: Int = 0
    var deathSaveFailures: Int = 0
    var isDead: Boolean = false

    /** Turni residui dell'elisir di forza (+2 Forza). */
    var strengthElixirTurns: Int = 0

    override val maxHitPoints: Int get() = hitPointMaximum
    override val glyph: Char get() = '@'
    override val speed: Int get() = 30

    override val armorClass: Int
        get() {
            val dex = abilityModifier(Ability.DEXTERITY)
            val body = armor
            val base = if (body == null) {
                10 + dex
            } else {
                val cappedDex = body.dexterityCap?.let { minOf(dex, it) } ?: dex
                body.baseArmorClass + cappedDex + body.magicBonus
            }
            val shieldBonus = shield?.let { it.armorClassBonus + it.magicBonus } ?: 0
            return base + shieldBonus
        }

    val proficiency: Int get() = proficiencyBonus(level)

    override fun savingThrowBonus(ability: Ability): Int =
        abilityModifier(ability) + if (ability in heroClass.savingThrowProficiencies) proficiency else 0

    override fun defaultAttackBonus(): Int = attackBonus(weapon)

    /** Caratteristica usata dall'arma: finesse => la migliore tra FOR e DES; a distanza => DES. */
    fun weaponAbility(w: Weapon): Ability = when {
        w.ranged -> Ability.DEXTERITY
        w.finesse -> if (abilityModifier(Ability.STRENGTH) >= abilityModifier(Ability.DEXTERITY)) Ability.STRENGTH else Ability.DEXTERITY
        else -> Ability.STRENGTH
    }

    fun attackBonus(w: Weapon): Int {
        val abilityMod = abilityModifier(weaponAbility(w))
        val prof = if (heroClass.isProficientWithWeapon(w)) proficiency else 0
        return abilityMod + prof + w.magicBonus
    }

    fun damageBonus(w: Weapon): Int = abilityModifier(weaponAbility(w)) + w.magicBonus

    val spellAttackBonus: Int
        get() = heroClass.spellcastingAbility?.let { abilityModifier(it) + proficiency } ?: 0

    val spellSaveDifficulty: Int
        get() = heroClass.spellcastingAbility?.let { spellSaveDc(level, abilityModifier(it)) } ?: 0

    val spellcastingModifier: Int
        get() = heroClass.spellcastingAbility?.let { abilityModifier(it) } ?: 0

    fun maxSlots(slotLevel: Int): Int =
        if (!heroClass.isSpellcaster) 0 else SpellSlots.maxSlots(level, slotLevel)

    fun availableSlots(slotLevel: Int): Int = when (slotLevel) {
        1 -> maxSlots(1) - usedSlotsLevel1
        2 -> maxSlots(2) - usedSlotsLevel2
        else -> 0
    }

    fun consumeSlot(slotLevel: Int): Boolean {
        if (availableSlots(slotLevel) <= 0) return false
        if (slotLevel == 1) usedSlotsLevel1++ else usedSlotsLevel2++
        return true
    }

    fun knownSpells(): List<Spell> = heroClass.knownSpells.map { SpellBook[it] }

    /** Dadi dell'Attacco Furtivo del ladro: ceil(livello / 2) d6. */
    fun sneakAttackDice(): DiceExpr =
        if (heroClass == HeroClass.ROGUE) DiceExpr((level + 1) / 2, 6) else DiceExpr.NONE

    /** Riposo breve: si attiva scendendo di un livello del dungeon. */
    fun shortRest() {
        secondWindAvailable = true
    }

    /** Aggiunge PX e restituisce i livelli guadagnati. */
    fun gainExperience(amount: Int, rng: GameRandom): Int {
        if (isDead) return 0
        experience += amount.coerceAtLeast(0)
        var levelsGained = 0
        while (level < Progression.MAX_LEVEL && experience >= Progression.XP_THRESHOLDS[level + 1]) {
            levelUp(rng)
            levelsGained++
        }
        return levelsGained
    }

    private fun levelUp(rng: GameRandom) {
        level++
        // PHB: PF per livello = tiro del dado vita + modificatore di Costituzione (minimo 1).
        val gained = max(1, rng.die(heroClass.hitDie) + abilityModifier(Ability.CONSTITUTION))
        hitPointMaximum += gained
        hitPoints += gained
        // Incremento dei punteggi di caratteristica ai livelli 4 e 8 (semplificato: +2 alla primaria).
        if (level == 4 || level == 8) {
            val primary = heroClass.primaryAbility
            val newScore = minOf(AbilityScores.MAX_SCORE, abilities[primary] + 2)
            abilities = abilities.with(primary, newScore)
        }
        // Livellando si recuperano gli slot (equivalente narrativo del riposo lungo).
        usedSlotsLevel1 = 0
        usedSlotsLevel2 = 0
        secondWindAvailable = true
    }

    override fun applyDamage(amount: Int): Int {
        val dealt = super.applyDamage(amount)
        if (hitPoints <= 0) {
            hitPoints = 0
        }
        return dealt
    }

    /** Punteggio finale: PX, oro, profondita' raggiunta. */
    fun score(deepestDepth: Int): Int = experience + gold + deepestDepth * 250

    companion object {
        /** Crea un personaggio di 1o livello: PF = massimo del dado vita + mod. COS (PHB). */
        fun create(name: String, heroClass: HeroClass, abilities: AbilityScores): PlayerCharacter {
            val pc = PlayerCharacter(name, heroClass, abilities)
            val con = abilities.modifier(Ability.CONSTITUTION)
            pc.hitPointMaximum = max(1, heroClass.hitDie + con)
            pc.hitPoints = pc.hitPointMaximum
            val equipment = heroClass.startingEquipment()
            pc.weapon = equipment.weapon
            pc.armor = equipment.armor
            pc.shield = equipment.shield
            pc.inventory.addAll(equipment.pack)
            pc.gold = 25
            return pc
        }

        /**
         * Assegna lo standard array in ordine di priorita' per la classe scelta.
         * E' la scelta "sensata" proposta di default in creazione personaggio.
         */
        fun recommendedScores(heroClass: HeroClass): AbilityScores {
            val order: List<Ability> = when (heroClass) {
                HeroClass.FIGHTER -> listOf(Ability.STRENGTH, Ability.CONSTITUTION, Ability.DEXTERITY, Ability.WISDOM, Ability.CHARISMA, Ability.INTELLIGENCE)
                HeroClass.ROGUE -> listOf(Ability.DEXTERITY, Ability.CONSTITUTION, Ability.INTELLIGENCE, Ability.WISDOM, Ability.CHARISMA, Ability.STRENGTH)
                HeroClass.CLERIC -> listOf(Ability.WISDOM, Ability.CONSTITUTION, Ability.STRENGTH, Ability.CHARISMA, Ability.DEXTERITY, Ability.INTELLIGENCE)
                HeroClass.WIZARD -> listOf(Ability.INTELLIGENCE, Ability.CONSTITUTION, Ability.DEXTERITY, Ability.WISDOM, Ability.CHARISMA, Ability.STRENGTH)
            }
            var scores = AbilityScores()
            order.forEachIndexed { index, ability ->
                scores = scores.with(ability, AbilityScores.STANDARD_ARRAY[index])
            }
            return scores
        }
    }
}
