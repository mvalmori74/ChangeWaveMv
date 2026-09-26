package com.changewave.dungeon.model

import com.changewave.dungeon.rules.Ability
import com.changewave.dungeon.rules.AbilityScores
import com.changewave.dungeon.rules.Condition
import com.changewave.dungeon.rules.ConditionSet
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.max

/**
 * Base comune a personaggio e mostri: tutto cio' su cui il motore di combattimento
 * opera senza sapere chi sta agendo.
 *
 * Lo stato mutabile (PF, posizione, energia, condizioni) sta qui; le statistiche
 * derivate sono astratte e calcolate dalle sottoclassi.
 */
@Serializable
sealed class Actor {

    abstract val name: String
    abstract val abilities: AbilityScores
    abstract val maxHitPoints: Int
    abstract val armorClass: Int

    /** Velocita' in piedi per turno; 30 e' lo standard umanoide (6 caselle). */
    abstract val speed: Int
    abstract val glyph: Char

    var id: Int = 0
    var x: Int = 0
    var y: Int = 0
    var hitPoints: Int = 1
    var conditions: ConditionSet = ConditionSet.EMPTY

    /**
     * Energia accumulata nel sistema a punti-azione: ogni creatura spende
     * [ENERGY_PER_ACTION] per agire e ne guadagna [speed] per tick globale.
     * Rende naturali velocita' diverse (lupo 50 ft, zombi 20 ft) senza turni "saltati".
     */
    var energy: Int = 0

    val isAlive: Boolean get() = hitPoints > 0 || (this is PlayerCharacter && !isDead)
    val isConscious: Boolean get() = hitPoints > 0 && Condition.UNCONSCIOUS !in conditions

    fun abilityModifier(ability: Ability): Int = abilities.modifier(ability)

    open fun savingThrowBonus(ability: Ability): Int = abilityModifier(ability)

    /** Bonus al tiro per colpire "generico" (usato dall'IA per stimare la minaccia). */
    abstract fun defaultAttackBonus(): Int

    /** Applica il danno, restituisce i PF effettivamente persi. */
    open fun applyDamage(amount: Int): Int {
        val dealt = amount.coerceAtLeast(0).coerceAtMost(hitPoints)
        hitPoints -= dealt
        return dealt
    }

    /** Cura, restituisce i PF effettivamente recuperati. */
    open fun heal(amount: Int): Int {
        val healed = amount.coerceAtLeast(0).coerceAtMost(maxHitPoints - hitPoints)
        hitPoints += healed
        return healed
    }

    fun distanceTo(other: Actor): Int = chebyshev(x, y, other.x, other.y)

    fun isAdjacentTo(other: Actor): Boolean = distanceTo(other) == 1

    companion object {
        /** Costo in energia di una azione completa (1 turno a velocita' 30 ft). */
        const val ENERGY_PER_ACTION = 30

        /** Distanza di Chebyshev: il movimento e' a 8 direzioni. */
        fun chebyshev(x1: Int, y1: Int, x2: Int, y2: Int): Int = max(abs(x1 - x2), abs(y1 - y2))
    }
}
