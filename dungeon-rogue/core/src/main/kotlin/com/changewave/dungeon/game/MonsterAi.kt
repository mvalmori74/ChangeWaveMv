package com.changewave.dungeon.game

import com.changewave.dungeon.dungeon.DungeonLevel
import com.changewave.dungeon.dungeon.FieldOfView
import com.changewave.dungeon.dungeon.Pathfinding
import com.changewave.dungeon.dungeon.Pos
import com.changewave.dungeon.dungeon.TileType
import com.changewave.dungeon.rules.Ability
import com.changewave.dungeon.model.AiProfile
import com.changewave.dungeon.model.Monster
import com.changewave.dungeon.model.PlayerCharacter
import com.changewave.dungeon.rules.GameRandom

/**
 * IA dei mostri: deliberatamente semplice e leggibile, un comportamento per
 * profilo. Ogni chiamata consuma esattamente una azione del mostro.
 */
object MonsterAi {

    /** Distanza entro cui un mostro puo' accorgersi del giocatore (vista/udito). */
    private const val NOTICE_RADIUS = 8
    private const val AMBUSH_RADIUS = 3

    interface Actions {
        fun attackPlayer(monster: Monster, melee: Boolean)
        fun move(monster: Monster, to: Pos)
        fun openDoor(monster: Monster, at: Pos)
        fun message(text: String, kind: MessageKind = MessageKind.INFO)
    }

    fun takeTurn(
        monster: Monster,
        player: PlayerCharacter,
        level: DungeonLevel,
        rng: GameRandom,
        actions: Actions,
    ) {
        if (monster.hitPoints <= 0) return
        if (monster.conditions.preventsAction()) return

        val distance = monster.distanceTo(player)
        val sees = FieldOfView.hasLineOfSight(level, monster.x, monster.y, player.x, player.y, NOTICE_RADIUS)

        if (!monster.alerted) {
            val noticeRange = if (monster.species.ai == AiProfile.AMBUSHER) AMBUSH_RADIUS else NOTICE_RADIUS
            if (sees && distance <= noticeRange) {
                monster.alerted = true
                actions.message("${monster.name} ti ha avvistato!", MessageKind.BAD)
            } else {
                wander(monster, level, rng, actions)
                return
            }
        }

        // Codardo ferito: fugge per qualche turno.
        if (monster.species.ai == AiProfile.COWARD &&
            monster.hitPoints <= monster.maxHitPoints * 0.3 &&
            monster.fleeingTurns <= 0 &&
            rng.chance(0.6)
        ) {
            monster.fleeingTurns = 5
            actions.message("${monster.name} e' in rotta!", MessageKind.GOOD)
        }
        if (monster.fleeingTurns > 0) {
            monster.fleeingTurns--
            flee(monster, player, level, actions)
            return
        }

        val ranged = monster.rangedAttack()
        if (monster.species.ai == AiProfile.RANGED && ranged != null && sees &&
            distance in 2..ranged.range
        ) {
            actions.attackPlayer(monster, melee = false)
            return
        }

        if (distance == 1) {
            actions.attackPlayer(monster, melee = true)
            return
        }

        if (ranged != null && sees && distance <= ranged.range && rng.chance(0.4)) {
            actions.attackPlayer(monster, melee = false)
            return
        }

        approach(monster, player, level, rng, actions)
    }

    private fun approach(
        monster: Monster,
        player: PlayerCharacter,
        level: DungeonLevel,
        rng: GameRandom,
        actions: Actions,
    ) {
        val blocked = level.monsters
            .filter { it !== monster && it.hitPoints > 0 }
            .map { Pos(it.x, it.y) }
            .toSet()
        val path = Pathfinding.findPath(level, Pos(monster.x, monster.y), Pos(player.x, player.y), blocked)
        val step = path.firstOrNull()
        if (step == null) {
            // Porta chiusa sul percorso: i mostri abbastanza intelligenti la aprono.
            val door = adjacentClosedDoor(monster, level)
            if (door != null && monster.abilityModifier(Ability.INTELLIGENCE) >= -2) {
                actions.openDoor(monster, door)
                return
            }
            wander(monster, level, rng, actions)
            return
        }
        if (level.tileAt(step) == TileType.DOOR_CLOSED) {
            actions.openDoor(monster, step)
            return
        }
        actions.move(monster, step)
    }

    private fun flee(monster: Monster, player: PlayerCharacter, level: DungeonLevel, actions: Actions) {
        val candidates = Pathfinding.DIRECTIONS
            .map { Pos(monster.x + it.x, monster.y + it.y) }
            .filter { level.isWalkable(it.x, it.y) && level.monsterAt(it.x, it.y) == null }
            .filter { it.x != player.x || it.y != player.y }
        val best = candidates.maxByOrNull { it.chebyshevTo(Pos(player.x, player.y)) }
        if (best != null && best.chebyshevTo(Pos(player.x, player.y)) > monster.distanceTo(player)) {
            actions.move(monster, best)
        }
    }

    private fun wander(monster: Monster, level: DungeonLevel, rng: GameRandom, actions: Actions) {
        if (monster.species.ai == AiProfile.AMBUSHER || monster.species.ai == AiProfile.MINDLESS) return
        if (!rng.chance(0.35)) return
        val dir = rng.pick(Pathfinding.DIRECTIONS)
        val target = Pos(monster.x + dir.x, monster.y + dir.y)
        if (level.isWalkable(target.x, target.y) && level.monsterAt(target.x, target.y) == null) {
            actions.move(monster, target)
        }
    }

    private fun adjacentClosedDoor(monster: Monster, level: DungeonLevel): Pos? =
        Pathfinding.DIRECTIONS
            .map { Pos(monster.x + it.x, monster.y + it.y) }
            .firstOrNull { level.tileAt(it) == TileType.DOOR_CLOSED }
}
