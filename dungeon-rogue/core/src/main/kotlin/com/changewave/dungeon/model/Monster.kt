package com.changewave.dungeon.model

import com.changewave.dungeon.rules.Ability
import com.changewave.dungeon.rules.AbilityScores
import com.changewave.dungeon.rules.Condition
import com.changewave.dungeon.rules.DamageType
import com.changewave.dungeon.rules.DiceExpr
import com.changewave.dungeon.rules.GameRandom
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** Profilo comportamentale usato dall'IA. */
enum class AiProfile {
    /** Avanza e attacca in mischia. */
    MELEE,

    /** Mantiene la distanza e usa l'attacco a gittata. */
    RANGED,

    /** Fugge sotto il 30% dei PF. */
    COWARD,

    /** Non fugge mai, ignora il dolore (non morti, costrutti). */
    MINDLESS,

    /** Attacca solo se il giocatore si avvicina (imboscata). */
    AMBUSHER,
}

/** Effetto secondario di un attacco, risolto con tiro salvezza. */
@Serializable
data class OnHitEffect(
    val save: Ability,
    val dc: Int,
    val condition: Condition,
    val turns: Int,
    val extraDamage: DiceExpr = DiceExpr.NONE,
    val extraDamageType: DamageType = DamageType.POISON,
)

@Serializable
data class MonsterAttack(
    val name: String,
    val attackBonus: Int,
    val damage: DiceExpr,
    val damageType: DamageType,
    /** Gittata in caselle: 1 = mischia. */
    val range: Int = 1,
    val onHit: OnHitEffect? = null,
)

@Serializable
data class MonsterSpecies(
    val id: String,
    val name: String,
    val glyph: Char,
    val challengeRating: Double,
    val armorClass: Int,
    val hitDice: DiceExpr,
    val speed: Int,
    val abilities: AbilityScores,
    val attacks: List<MonsterAttack>,
    val ai: AiProfile = AiProfile.MELEE,
    val saveProficiencies: Set<Ability> = emptySet(),
    val immunities: Set<DamageType> = emptySet(),
    val resistances: Set<DamageType> = emptySet(),
    /** Profondita' minima/massima di comparsa e peso relativo nella tabella incontri. */
    val minDepth: Int = 1,
    val maxDepth: Int = 99,
    val spawnWeight: Int = 10,
    val boss: Boolean = false,
) {
    val experience: Int get() = Bestiary.xpForChallengeRating(challengeRating)
    val averageHitPoints: Int get() = hitDice.average.roundToInt()

    fun instantiate(rng: GameRandom, rollHitPoints: Boolean = true): Monster {
        val hp = if (rollHitPoints) hitDice.roll(rng).coerceAtLeast(1) else averageHitPoints
        return Monster(this, hp).also { it.hitPoints = hp }
    }
}

@Serializable
class Monster(
    val species: MonsterSpecies,
    override val maxHitPoints: Int,
) : Actor() {

    override val name: String get() = species.name
    override val abilities: AbilityScores get() = species.abilities
    override val armorClass: Int get() = species.armorClass
    override val speed: Int get() = species.speed
    override val glyph: Char get() = species.glyph

    /** Impostato dall'IA: true quando il mostro ha gia' avvistato il giocatore. */
    var alerted: Boolean = false

    /** Turni residui di fuga (profilo [AiProfile.COWARD]). */
    var fleeingTurns: Int = 0

    override fun savingThrowBonus(ability: Ability): Int {
        val base = abilityModifier(ability)
        val prof = if (ability in species.saveProficiencies) proficiency() else 0
        return base + prof
    }

    /** Competenza derivata dal GS, come da DMG (GS 0-4 => +2, 5-8 => +3, ...). */
    fun proficiency(): Int = 2 + (species.challengeRating.toInt().coerceAtLeast(0)) / 5

    override fun defaultAttackBonus(): Int = species.attacks.maxOfOrNull { it.attackBonus } ?: 0

    fun meleeAttack(): MonsterAttack? = species.attacks.firstOrNull { it.range <= 1 }
    fun rangedAttack(): MonsterAttack? = species.attacks.firstOrNull { it.range > 1 }

    override fun applyDamage(amount: Int): Int = super.applyDamage(amount)

    /** Applica immunita'/resistenze SRD prima del danno effettivo. */
    fun damageAfterDefenses(amount: Int, type: DamageType): Int = when (type) {
        in species.immunities -> 0
        in species.resistances -> amount / 2
        else -> amount
    }
}
