package com.changewave.dungeon.rules

import com.changewave.dungeon.model.Actor
import com.changewave.dungeon.model.Monster
import com.changewave.dungeon.model.MonsterAttack
import com.changewave.dungeon.model.PlayerCharacter
import com.changewave.dungeon.model.Weapon

/**
 * Risoluzione dei tiri per colpire, del danno e dei tiri salvezza secondo le
 * regole SRD 5.1. Nessuna dipendenza dallo stato di gioco: prende attori e dado,
 * restituisce risultati. Questo rende il modulo interamente testabile a seed fisso.
 */
object Combat {

    data class AttackOutcome(
        val attackerName: String,
        val targetName: String,
        val roll: D20Roll,
        val targetArmorClass: Int,
        val hit: Boolean,
        val critical: Boolean,
        val damage: Int,
        val damageType: DamageType,
        val targetDefeated: Boolean,
        val sneakAttack: Boolean = false,
        val note: String? = null,
    ) {
        fun describe(): String = buildString {
            append("$attackerName -> $targetName: $roll vs CA $targetArmorClass. ")
            when {
                !hit && roll.isCriticalMiss -> append("Fallimento critico!")
                !hit -> append("Mancato.")
                critical -> append("COLPO CRITICO! $damage danni ${damageType.italian}.")
                else -> append("Colpito per $damage danni ${damageType.italian}.")
            }
            if (sneakAttack) append(" (Attacco Furtivo)")
            if (targetDefeated) append(" $targetName cade!")
            note?.let { append(" $it") }
        }
    }

    data class SaveOutcome(val roll: D20Roll, val dc: Int, val success: Boolean)

    /** Bonus di Benedizione: +1d4 a tiri per colpire e tiri salvezza finche' attiva. */
    private fun blessBonus(actor: Actor, rng: GameRandom): Int =
        if (Condition.BLESSED in actor.conditions) rng.die(4) else 0

    /**
     * Modalita' del tiro per colpire derivata dalle condizioni di attaccante e
     * bersaglio (PHB: le fonti multiple non si sommano, basta una per lato).
     */
    fun attackMode(attacker: Actor, target: Actor, extraAdvantage: Boolean = false): RollMode {
        val disadvantage = attacker.conditions.conditions.any { it.givesAttackDisadvantage }
        val advantage = extraAdvantage || target.conditions.conditions.any { it.givesAdvantageToAttackers }
        return RollMode.of(advantage, disadvantage)
    }

    /** Critico automatico in mischia contro bersagli paralizzati o privi di sensi. */
    private fun autoCritical(target: Actor, melee: Boolean): Boolean =
        melee && target.conditions.conditions.any { it.autoCriticalInMelee }

    fun savingThrow(actor: Actor, ability: Ability, dc: Int, rng: GameRandom): SaveOutcome {
        val mode = when {
            Condition.PARALYZED in actor.conditions && (ability == Ability.STRENGTH || ability == Ability.DEXTERITY) ->
                RollMode.NORMAL // fallimento automatico gestito sotto
            else -> RollMode.NORMAL
        }
        // PHB: chi e' paralizzato o privo di sensi fallisce automaticamente TS su FOR e DES.
        if ((Condition.PARALYZED in actor.conditions || Condition.UNCONSCIOUS in actor.conditions) &&
            (ability == Ability.STRENGTH || ability == Ability.DEXTERITY)
        ) {
            return SaveOutcome(D20Roll(1, null, 0, RollMode.NORMAL), dc, success = false)
        }
        val bonus = actor.savingThrowBonus(ability) + blessBonus(actor, rng)
        val roll = rng.rollD20(bonus, mode)
        return SaveOutcome(roll, dc, roll.total >= dc)
    }

    /**
     * Attacco del giocatore con l'arma equipaggiata.
     *
     * [surprised] indica un bersaglio che non ha ancora avvistato il giocatore:
     * concede vantaggio e abilita l'Attacco Furtivo del ladro (PHB).
     */
    fun playerAttack(
        player: PlayerCharacter,
        target: Monster,
        rng: GameRandom,
        weapon: Weapon = player.weapon,
        surprised: Boolean = false,
    ): AttackOutcome {
        val melee = !weapon.ranged
        val mode = attackMode(player, target, extraAdvantage = surprised)
        val bonus = player.attackBonus(weapon) + blessBonus(player, rng)
        val roll = rng.rollD20(bonus, mode)
        val forcedCrit = autoCritical(target, melee)
        val hit = !roll.isCriticalMiss && (roll.isCriticalHit || forcedCrit || roll.total >= target.armorClass)
        if (!hit) {
            return AttackOutcome(
                player.name, target.name, roll, target.armorClass,
                hit = false, critical = false, damage = 0, damageType = weapon.damageType,
                targetDefeated = false,
            )
        }
        val critical = roll.isCriticalHit || forcedCrit
        // L'Attacco Furtivo richiede vantaggio (o bersaglio ignaro): PHB, Ladro 1o livello.
        val sneakDice = player.sneakAttackDice()
        val sneak = sneakDice.count > 0 && (mode == RollMode.ADVANTAGE || surprised)
        var raw = weapon.damage.roll(rng, critical, bonusModifier = player.damageBonus(weapon))
        if (sneak) raw += sneakDice.roll(rng, critical)
        val effective = target.damageAfterDefenses(raw, weapon.damageType)
        val dealt = target.applyDamage(effective)
        return AttackOutcome(
            player.name, target.name, roll, target.armorClass,
            hit = true, critical = critical, damage = dealt, damageType = weapon.damageType,
            targetDefeated = target.hitPoints <= 0, sneakAttack = sneak,
            note = if (effective < raw) "(danno ridotto dalle resistenze)" else null,
        )
    }

    /** Attacco di un mostro contro il giocatore (o teoricamente un altro attore). */
    fun monsterAttack(
        monster: Monster,
        attack: MonsterAttack,
        target: Actor,
        rng: GameRandom,
    ): AttackOutcome {
        val melee = attack.range <= 1
        val mode = attackMode(monster, target)
        val bonus = attack.attackBonus + blessBonus(monster, rng)
        val roll = rng.rollD20(bonus, mode)
        val forcedCrit = autoCritical(target, melee)
        val hit = !roll.isCriticalMiss && (roll.isCriticalHit || forcedCrit || roll.total >= target.armorClass)
        if (!hit) {
            return AttackOutcome(
                monster.name, target.name, roll, target.armorClass,
                hit = false, critical = false, damage = 0, damageType = attack.damageType,
                targetDefeated = false,
            )
        }
        val critical = roll.isCriticalHit || forcedCrit
        var total = attack.damage.roll(rng, critical)
        var note: String? = null

        attack.onHit?.let { effect ->
            val save = savingThrow(target, effect.save, effect.dc, rng)
            if (!save.success) {
                if (effect.extraDamage.count > 0) total += effect.extraDamage.roll(rng, critical)
                target.conditions = target.conditions.add(effect.condition, effect.turns)
                note = "${target.name} subisce: ${effect.condition.italian} (TS ${effect.save.short} ${save.roll.total} vs CD ${effect.dc})"
            } else {
                note = "${target.name} resiste a ${effect.condition.italian} (TS ${save.roll.total} vs CD ${effect.dc})"
            }
        }

        val dealt = target.applyDamage(total)
        return AttackOutcome(
            monster.name, target.name, roll, target.armorClass,
            hit = true, critical = critical, damage = dealt, damageType = attack.damageType,
            targetDefeated = target.hitPoints <= 0, note = note,
        )
    }

    /** Danno diretto (trappole, incantesimi ad area) con eventuali difese del bersaglio. */
    fun applyTypedDamage(target: Actor, amount: Int, type: DamageType): Int {
        val effective = if (target is Monster) target.damageAfterDefenses(amount, type) else amount
        return target.applyDamage(effective)
    }
}
