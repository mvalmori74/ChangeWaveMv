package com.changewave.dungeon.rules

import kotlinx.serialization.Serializable
import kotlin.math.floor

/** Le sei caratteristiche del regolamento D&D 5e (SRD 5.1). */
enum class Ability(val italian: String, val short: String) {
    STRENGTH("Forza", "FOR"),
    DEXTERITY("Destrezza", "DES"),
    CONSTITUTION("Costituzione", "COS"),
    INTELLIGENCE("Intelligenza", "INT"),
    WISDOM("Saggezza", "SAG"),
    CHARISMA("Carisma", "CAR");
}

@Serializable
data class AbilityScores(
    val strength: Int = 10,
    val dexterity: Int = 10,
    val constitution: Int = 10,
    val intelligence: Int = 10,
    val wisdom: Int = 10,
    val charisma: Int = 10,
) {
    operator fun get(ability: Ability): Int = when (ability) {
        Ability.STRENGTH -> strength
        Ability.DEXTERITY -> dexterity
        Ability.CONSTITUTION -> constitution
        Ability.INTELLIGENCE -> intelligence
        Ability.WISDOM -> wisdom
        Ability.CHARISMA -> charisma
    }

    fun modifier(ability: Ability): Int = modifierOf(this[ability])

    fun with(ability: Ability, value: Int): AbilityScores = when (ability) {
        Ability.STRENGTH -> copy(strength = value)
        Ability.DEXTERITY -> copy(dexterity = value)
        Ability.CONSTITUTION -> copy(constitution = value)
        Ability.INTELLIGENCE -> copy(intelligence = value)
        Ability.WISDOM -> copy(wisdom = value)
        Ability.CHARISMA -> copy(charisma = value)
    }

    fun plus(ability: Ability, delta: Int): AbilityScores = with(ability, this[ability] + delta)

    companion object {
        const val MIN_SCORE = 1
        const val MAX_SCORE = 20

        /** PHB p.173: modificatore = floor((punteggio - 10) / 2). */
        fun modifierOf(score: Int): Int = floor((score - 10) / 2.0).toInt()

        /** Standard array del PHB, da assegnare liberamente in creazione personaggio. */
        val STANDARD_ARRAY = listOf(15, 14, 13, 12, 10, 8)

        /** 4d6 scarta il piu' basso, metodo classico. */
        fun roll4d6DropLowest(rng: GameRandom): Int {
            val dice = List(4) { rng.die(6) }.sortedDescending()
            return dice.take(3).sum()
        }

        fun rolled(rng: GameRandom): AbilityScores = AbilityScores(
            strength = roll4d6DropLowest(rng),
            dexterity = roll4d6DropLowest(rng),
            constitution = roll4d6DropLowest(rng),
            intelligence = roll4d6DropLowest(rng),
            wisdom = roll4d6DropLowest(rng),
            charisma = roll4d6DropLowest(rng),
        )
    }
}

/** PHB: bonus di competenza = 2 + floor((livello - 1) / 4). */
fun proficiencyBonus(level: Int): Int = 2 + (level.coerceAtLeast(1) - 1) / 4

/** CD di una prova/tiro salvezza da incantesimo: 8 + competenza + mod. caratteristica. */
fun spellSaveDc(level: Int, abilityModifier: Int): Int = 8 + proficiencyBonus(level) + abilityModifier
