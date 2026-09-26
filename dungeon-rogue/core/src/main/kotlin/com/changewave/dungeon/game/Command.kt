package com.changewave.dungeon.game

import com.changewave.dungeon.model.SpellId

/** Azioni che il giocatore puo' impartire in un turno. */
sealed interface Command {

    /** Movimento di una casella nelle 8 direzioni; se c'e' un mostro, attacca. */
    data class Move(val dx: Int, val dy: Int) : Command

    /** Passa il turno (e cerca trappole nelle caselle adiacenti). */
    data object Wait : Command

    data object Descend : Command
    data object Ascend : Command
    data object PickUp : Command

    data class UseItem(val inventoryIndex: Int) : Command
    data class DropItem(val inventoryIndex: Int) : Command

    /** Attacco a distanza con l'arma equipaggiata contro il mostro indicato. */
    data class RangedAttack(val targetId: Int) : Command

    /** Lancio di incantesimo; [targetId] richiesto per gli incantesimi offensivi a bersaglio singolo. */
    data class Cast(val spell: SpellId, val targetId: Int? = null, val slotLevel: Int = 1) : Command

    /** Recuperare Energie del guerriero. */
    data object SecondWind : Command
}

/** Esito di un comando: se il tempo e' avanzato e cosa e' successo. */
data class TurnResult(
    val accepted: Boolean,
    val timeAdvanced: Boolean,
    val messages: List<LogMessage>,
    val rejection: String? = null,
)
