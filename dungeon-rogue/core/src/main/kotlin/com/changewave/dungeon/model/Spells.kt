package com.changewave.dungeon.model

import com.changewave.dungeon.rules.Ability
import com.changewave.dungeon.rules.Condition
import com.changewave.dungeon.rules.DamageType
import com.changewave.dungeon.rules.DiceExpr
import kotlinx.serialization.Serializable

enum class SpellId {
    FIRE_BOLT, SACRED_FLAME, MAGIC_MISSILE, BURNING_HANDS, CURE_WOUNDS, BLESS
}

/** Come l'incantesimo colpisce il bersaglio. */
enum class SpellDelivery {
    /** Tiro per colpire con incantesimo contro CA. */
    SPELL_ATTACK,
    /** Il bersaglio effettua un tiro salvezza; successo = danno dimezzato o nullo. */
    SAVING_THROW,
    /** Colpisce automaticamente (es. dardo incantato). */
    AUTOMATIC,
    /** Effetto su se stessi. */
    SELF,
}

enum class SpellArea { SINGLE, BURST }

@Serializable
data class Spell(
    val id: SpellId,
    val name: String,
    /** 0 = trucchetto. */
    val level: Int,
    val delivery: SpellDelivery,
    val area: SpellArea = SpellArea.SINGLE,
    /** Gittata in caselle. */
    val range: Int = 1,
    /** Raggio dell'area in caselle, per [SpellArea.BURST]. */
    val radius: Int = 0,
    val damage: DiceExpr = DiceExpr.NONE,
    val damageType: DamageType = DamageType.FORCE,
    val healing: DiceExpr = DiceExpr.NONE,
    /** Caratteristica del tiro salvezza, se [SpellDelivery.SAVING_THROW]. */
    val save: Ability? = null,
    /** true = il tiro salvezza riuscito dimezza il danno invece di annullarlo. */
    val halfOnSave: Boolean = false,
    /** Numero di proiettili che colpiscono automaticamente (dardo incantato). */
    val missiles: Int = 0,
    val selfCondition: Condition? = null,
    val conditionTurns: Int = 0,
    val description: String = "",
)

object SpellBook {

    val FIRE_BOLT = Spell(
        id = SpellId.FIRE_BOLT, name = "Dardo di fuoco", level = 0,
        delivery = SpellDelivery.SPELL_ATTACK, range = 12,
        damage = DiceExpr(1, 10), damageType = DamageType.FIRE,
        description = "Trucchetto: tiro per colpire a distanza, 1d10 danni da fuoco.",
    )

    val SACRED_FLAME = Spell(
        id = SpellId.SACRED_FLAME, name = "Fiamma sacra", level = 0,
        delivery = SpellDelivery.SAVING_THROW, range = 12,
        damage = DiceExpr(1, 8), damageType = DamageType.RADIANT,
        save = Ability.DEXTERITY,
        description = "Trucchetto: TS su Destrezza, 1d8 danni radiosi.",
    )

    val MAGIC_MISSILE = Spell(
        id = SpellId.MAGIC_MISSILE, name = "Dardo incantato", level = 1,
        delivery = SpellDelivery.AUTOMATIC, range = 12,
        damage = DiceExpr(1, 4, 1), damageType = DamageType.FORCE, missiles = 3,
        description = "3 dardi da 1d4+1 danni da forza, colpiscono sempre (+1 dardo per livello di slot superiore).",
    )

    val BURNING_HANDS = Spell(
        id = SpellId.BURNING_HANDS, name = "Mani brucianti", level = 1,
        delivery = SpellDelivery.SAVING_THROW, area = SpellArea.BURST, range = 3, radius = 2,
        damage = DiceExpr(3, 6), damageType = DamageType.FIRE,
        save = Ability.DEXTERITY, halfOnSave = true,
        description = "Area: 3d6 danni da fuoco, TS su Destrezza dimezza (+1d6 per livello di slot superiore).",
    )

    val CURE_WOUNDS = Spell(
        id = SpellId.CURE_WOUNDS, name = "Cura ferite", level = 1,
        delivery = SpellDelivery.SELF,
        healing = DiceExpr(1, 8),
        description = "Recupera 1d8 + mod. da incantatore PF (+1d8 per livello di slot superiore).",
    )

    val BLESS = Spell(
        id = SpellId.BLESS, name = "Benedizione", level = 1,
        delivery = SpellDelivery.SELF,
        selfCondition = Condition.BLESSED, conditionTurns = 10,
        description = "Per 10 turni aggiunge 1d4 ai tiri per colpire e ai tiri salvezza.",
    )

    val all: List<Spell> = listOf(FIRE_BOLT, SACRED_FLAME, MAGIC_MISSILE, BURNING_HANDS, CURE_WOUNDS, BLESS)

    operator fun get(id: SpellId): Spell = all.first { it.id == id }
}

/**
 * Slot incantesimo disponibili per livello personaggio (tabella incantatore completo
 * PHB, troncata ai livelli 1 e 2: il gioco arriva a livello 10 e usa solo questi slot).
 */
object SpellSlots {
    private val LEVEL1 = intArrayOf(0, 2, 3, 4, 4, 4, 4, 4, 4, 4, 4)
    private val LEVEL2 = intArrayOf(0, 0, 0, 2, 3, 3, 3, 3, 3, 3, 3)

    fun maxSlots(characterLevel: Int, slotLevel: Int): Int {
        val lvl = characterLevel.coerceIn(1, 10)
        return when (slotLevel) {
            1 -> LEVEL1[lvl]
            2 -> LEVEL2[lvl]
            else -> 0
        }
    }
}
