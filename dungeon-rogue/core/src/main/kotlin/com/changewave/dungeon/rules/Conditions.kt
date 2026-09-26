package com.changewave.dungeon.rules

import kotlinx.serialization.Serializable

/**
 * Sottoinsieme delle condizioni SRD effettivamente rilevanti in un roguelike a turni.
 * Gli effetti meccanici sono applicati in [com.changewave.dungeon.rules.Combat].
 */
enum class Condition(val italian: String) {
    POISONED("Avvelenato"),       // svantaggio ai tiri per colpire
    PRONE("Prono"),               // svantaggio ai propri attacchi; vantaggio agli attacchi in mischia contro
    BLINDED("Accecato"),          // svantaggio ai propri attacchi; vantaggio agli attacchi contro
    FRIGHTENED("Spaventato"),     // svantaggio ai tiri per colpire, non puo' avvicinarsi alla fonte
    PARALYZED("Paralizzato"),     // non agisce; gli attacchi in mischia contro sono critici automatici
    STUNNED("Stordito"),          // non agisce; vantaggio agli attacchi contro
    RESTRAINED("Trattenuto"),     // velocita' 0, svantaggio ai propri attacchi, vantaggio contro
    UNCONSCIOUS("Privo di sensi"),// non agisce, cade prono, critici automatici in mischia
    BLESSED("Benedetto"),         // +1d4 a tiri per colpire e TS (semplificazione di Bless)
    HASTED("Accelerato");         // azione extra: nel modello a energia, +100% velocita'

    val preventsAction: Boolean
        get() = this == PARALYZED || this == STUNNED || this == UNCONSCIOUS

    val givesAttackDisadvantage: Boolean
        get() = this == POISONED || this == PRONE || this == BLINDED ||
            this == FRIGHTENED || this == RESTRAINED

    val givesAdvantageToAttackers: Boolean
        get() = this == PRONE || this == BLINDED || this == PARALYZED ||
            this == STUNNED || this == RESTRAINED || this == UNCONSCIOUS

    val autoCriticalInMelee: Boolean
        get() = this == PARALYZED || this == UNCONSCIOUS
}

/** Condizione attiva con durata residua espressa in turni di gioco. */
@Serializable
data class ActiveCondition(val condition: Condition, val turnsLeft: Int) {
    val expired: Boolean get() = turnsLeft <= 0
    fun tick(): ActiveCondition = copy(turnsLeft = turnsLeft - 1)
}

/** Insieme di condizioni attive su una creatura; immutabile, sostituito ad ogni turno. */
@Serializable
data class ConditionSet(val active: List<ActiveCondition> = emptyList()) {

    operator fun contains(condition: Condition): Boolean = active.any { it.condition == condition }

    val conditions: Set<Condition> get() = active.map { it.condition }.toSet()

    fun add(condition: Condition, turns: Int): ConditionSet {
        require(turns > 0)
        val existing = active.firstOrNull { it.condition == condition }
        return if (existing == null) {
            ConditionSet(active + ActiveCondition(condition, turns))
        } else {
            // Durate che si sovrappongono: vale la piu' lunga (PHB, effetti non cumulativi).
            ConditionSet(active.map { if (it.condition == condition) it.copy(turnsLeft = maxOf(it.turnsLeft, turns)) else it })
        }
    }

    fun remove(condition: Condition): ConditionSet = ConditionSet(active.filterNot { it.condition == condition })

    /** Decrementa le durate; restituisce il nuovo set e le condizioni appena scadute. */
    fun tick(): Pair<ConditionSet, List<Condition>> {
        val ticked = active.map { it.tick() }
        val expired = ticked.filter { it.expired }.map { it.condition }
        return ConditionSet(ticked.filterNot { it.expired }) to expired
    }

    fun preventsAction(): Boolean = active.any { it.condition.preventsAction }

    companion object {
        val EMPTY = ConditionSet()
    }
}

/** Tipi di danno SRD usati dal bestiario e dagli oggetti. */
enum class DamageType(val italian: String) {
    BLUDGEONING("contundente"),
    PIERCING("perforante"),
    SLASHING("tagliente"),
    FIRE("da fuoco"),
    COLD("da freddo"),
    LIGHTNING("da fulmine"),
    ACID("da acido"),
    POISON("da veleno"),
    NECROTIC("necrotico"),
    RADIANT("radiante"),
    FORCE("da forza"),
    PSYCHIC("psichico");
}
