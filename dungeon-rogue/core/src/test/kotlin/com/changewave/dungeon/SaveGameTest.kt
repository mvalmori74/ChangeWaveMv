package com.changewave.dungeon

import com.changewave.dungeon.game.Command
import com.changewave.dungeon.game.GameEngine
import com.changewave.dungeon.game.SaveGame
import com.changewave.dungeon.model.HeroClass
import com.changewave.dungeon.rules.GameRandom
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SaveGameTest {

    private fun playRandomly(engine: GameEngine, steps: Int, rng: GameRandom) {
        repeat(steps) {
            if (engine.state.isOver) return
            val dir = rng.pick(com.changewave.dungeon.dungeon.Pathfinding.DIRECTIONS)
            engine.execute(Command.Move(dir.x, dir.y))
        }
    }

    @Test
    fun `salvataggio e ricarica conservano lo stato`() {
        val engine = GameEngine.newGame("Salva", HeroClass.CLERIC, seed = 999)
        playRandomly(engine, 120, GameRandom.fromSeed(5))

        val text = SaveGame.encode(engine.state)
        val reloaded = SaveGame.loadEngine(text)
        assertNotNull(reloaded)
        reloaded!!

        assertEquals(engine.state.turn, reloaded.state.turn)
        assertEquals(engine.state.depth, reloaded.state.depth)
        assertEquals(engine.player.hitPoints, reloaded.player.hitPoints)
        assertEquals(engine.player.experience, reloaded.player.experience)
        assertEquals(engine.player.x, reloaded.player.x)
        assertEquals(engine.player.y, reloaded.player.y)
        assertEquals(engine.player.inventory.size, reloaded.player.inventory.size)
        assertEquals(engine.level.monsters.size, reloaded.level.monsters.size)
        assertEquals(engine.level.tiles, reloaded.level.tiles)
        assertEquals(engine.state.log.size, reloaded.state.log.size)
        assertEquals(
            engine.level.monsters.map { it.id to it.hitPoints },
            reloaded.level.monsters.map { it.id to it.hitPoints },
        )
    }

    @Test
    fun `la partita ricaricata prosegue in modo identico`() {
        val original = GameEngine.newGame("Deterministico", HeroClass.ROGUE, seed = 4242)
        playRandomly(original, 80, GameRandom.fromSeed(11))

        val reloaded = SaveGame.loadEngine(SaveGame.encode(original.state))!!

        // Stessa sequenza di comandi su entrambi i motori: lo stato del RNG e' salvato,
        // quindi i tiri di dado successivi devono coincidere casella per casella.
        playRandomly(original, 60, GameRandom.fromSeed(77))
        playRandomly(reloaded, 60, GameRandom.fromSeed(77))

        assertEquals(original.state.turn, reloaded.state.turn)
        assertEquals(original.player.hitPoints, reloaded.player.hitPoints)
        assertEquals(original.player.x to original.player.y, reloaded.player.x to reloaded.player.y)
        assertEquals(original.state.monstersKilled, reloaded.state.monstersKilled)
        assertEquals(
            original.level.monsters.map { Triple(it.id, it.x, it.y) },
            reloaded.level.monsters.map { Triple(it.id, it.x, it.y) },
        )
    }

    @Test
    fun `i livelli gia' visitati sopravvivono al salvataggio`() {
        val engine = GameEngine.newGame("Esploratore", HeroClass.FIGHTER, seed = 31415)
        val down = engine.level.stairsDown!!
        engine.player.x = down.x
        engine.player.y = down.y
        engine.execute(Command.Descend)
        assertEquals(2, engine.state.levels.size)

        val reloaded = SaveGame.loadEngine(SaveGame.encode(engine.state))!!
        assertEquals(2, reloaded.state.levels.size)
        assertEquals(2, reloaded.state.depth)
        assertTrue(reloaded.state.levels.containsKey(1))
        assertEquals(engine.state.levels.getValue(1).tiles, reloaded.state.levels.getValue(1).tiles)
    }

    @Test
    fun `un salvataggio corrotto o di versione sbagliata viene rifiutato`() {
        assertNull(SaveGame.decode(""))
        assertNull(SaveGame.decode("non-un-numero\n{}"))
        assertNull(SaveGame.decode("999\n{}"))
        assertNull(SaveGame.decode("${SaveGame.CURRENT_VERSION}\n{ questo non e' json valido"))
    }

    @Test
    fun `la memoria della mappa esplorata viene conservata`() {
        val engine = GameEngine.newGame("Mappa", HeroClass.WIZARD, seed = 606)
        playRandomly(engine, 100, GameRandom.fromSeed(3))
        val exploredBefore = engine.level.explored.count { it }
        assertTrue(exploredBefore > 0)
        val reloaded = SaveGame.loadEngine(SaveGame.encode(engine.state))!!
        assertEquals(exploredBefore, reloaded.level.explored.count { it })
    }
}
