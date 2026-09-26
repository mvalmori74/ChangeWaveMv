package com.changewave.dungeon.game

import com.changewave.dungeon.dungeon.DungeonLevel
import com.changewave.dungeon.model.PlayerCharacter
import com.changewave.dungeon.rules.GameRandom
import kotlinx.serialization.Serializable

enum class GameStatus { PLAYING, DEAD, VICTORY }

/**
 * Stato completo della partita: e' l'unica cosa che viene serializzata nel
 * salvataggio, compreso lo stato del generatore casuale (partita riproducibile).
 */
@Serializable
class GameState(
    val seed: Long,
    val player: PlayerCharacter,
    val rng: GameRandom,
    val levels: MutableMap<Int, DungeonLevel> = mutableMapOf(),
    var depth: Int = 1,
    var turn: Int = 0,
    var deepestDepth: Int = 1,
    var status: GameStatus = GameStatus.PLAYING,
    var monstersKilled: Int = 0,
    var nextActorId: Int = 1,
    val log: MessageLog = MessageLog(),
) {
    val currentLevel: DungeonLevel get() = levels.getValue(depth)

    val isOver: Boolean get() = status != GameStatus.PLAYING

    /** Raggio di vista (torcia). Ridotto quando il giocatore e' accecato. */
    val sightRadius: Int get() = if (com.changewave.dungeon.rules.Condition.BLINDED in player.conditions) 1 else 8

    fun score(): Int = player.score(deepestDepth) + monstersKilled * 10
}
