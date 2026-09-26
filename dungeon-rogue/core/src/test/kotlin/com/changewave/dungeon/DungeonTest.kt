package com.changewave.dungeon

import com.changewave.dungeon.dungeon.DungeonConfig
import com.changewave.dungeon.dungeon.DungeonGenerator
import com.changewave.dungeon.dungeon.DungeonLevel
import com.changewave.dungeon.dungeon.FieldOfView
import com.changewave.dungeon.dungeon.Pathfinding
import com.changewave.dungeon.dungeon.Pos
import com.changewave.dungeon.dungeon.TileType
import com.changewave.dungeon.rules.GameRandom
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DungeonTest {

    private val config = DungeonConfig()
    private val generator = DungeonGenerator(config)

    private fun levels(count: Int = 30): List<DungeonLevel> =
        (1..count).map { seed ->
            val rng = GameRandom.fromSeed(seed.toLong() * 7919)
            generator.generate(depth = (seed % config.maxDepth) + 1, rng = rng)
        }

    @Test
    fun `ogni livello generato e' interamente connesso`() {
        for (level in levels()) {
            val start = level.stairsUp ?: level.rooms.first().center
            val reachable = Pathfinding.reachable(level, start)
            val walkable = level.walkablePositions().toSet()
            val unreachable = walkable - reachable
            assertTrue(
                unreachable.isEmpty(),
                "Livello ${level.depth}: ${unreachable.size} caselle isolate (es. ${unreachable.firstOrNull()})",
            )
        }
    }

    @Test
    fun `scale presenti e raggiungibili tranne al livello del boss`() {
        for (level in levels()) {
            assertNotNull(level.stairsUp, "manca la scala di salita")
            val up = level.stairsUp!!
            assertEquals(TileType.STAIRS_UP, level.tileAt(up))
            if (level.depth < config.maxDepth) {
                val down = level.stairsDown
                assertNotNull(down, "livello ${level.depth}: manca la scala di discesa")
                assertEquals(TileType.STAIRS_DOWN, level.tileAt(down!!))
                assertTrue(Pathfinding.reachable(level, up).contains(down), "scala di discesa irraggiungibile")
            } else {
                assertTrue(level.monsters.any { it.species.boss }, "l'ultimo livello deve ospitare il boss")
            }
        }
    }

    @Test
    fun `mostri e oggetti sono su caselle calpestabili e non sovrapposti`() {
        for (level in levels()) {
            val positions = mutableSetOf<Pos>()
            for (monster in level.monsters) {
                assertTrue(level.isWalkable(monster.x, monster.y), "mostro dentro un muro a (${monster.x},${monster.y})")
                assertTrue(positions.add(Pos(monster.x, monster.y)), "due mostri sulla stessa casella")
            }
            for (item in level.items) {
                assertTrue(level.isWalkable(item.pos.x, item.pos.y), "oggetto dentro un muro")
            }
            for (trap in level.traps) {
                assertTrue(level.isWalkable(trap.pos.x, trap.pos.y), "trappola dentro un muro")
                assertFalse(level.tileAt(trap.pos) == TileType.STAIRS_DOWN, "trappola sulle scale")
            }
        }
    }

    @Test
    fun `la generazione e' riproducibile a parita' di seed`() {
        val a = DungeonGenerator(config).generate(3, GameRandom.fromSeed(2024))
        val b = DungeonGenerator(config).generate(3, GameRandom.fromSeed(2024))
        assertEquals(a.tiles, b.tiles)
        assertEquals(a.monsters.map { it.species.id to (it.x to it.y) }, b.monsters.map { it.species.id to (it.x to it.y) })
        assertEquals(a.stairsDown, b.stairsDown)
    }

    @Test
    fun `il budget di incontro cresce con la profondita'`() {
        var previous = 0
        for (depth in 1..config.maxDepth) {
            val budget = generator.encounterBudget(depth)
            assertTrue(budget > previous, "il budget deve crescere: profondita' $depth")
            previous = budget
        }
    }

    @Test
    fun `i mostri profondi non compaiono ai primi livelli`() {
        val rng = GameRandom.fromSeed(99)
        repeat(10) {
            val level = generator.generate(1, rng)
            assertTrue(
                level.monsters.all { it.species.challengeRating <= 1.0 },
                "al livello 1 non devono comparire mostri oltre GS 1: ${level.monsters.map { it.species.name }}",
            )
        }
    }

    @Test
    fun `la percentuale di pavimento e' ragionevole`() {
        for (level in levels(10)) {
            val walkable = level.walkablePositions().size
            val ratio = walkable.toDouble() / (level.width * level.height)
            assertTrue(ratio in 0.15..0.65, "densita' di pavimento anomala: $ratio")
        }
    }

    @Test
    fun `il campo visivo si ferma sui muri`() {
        val level = DungeonLevel.filled(1, 21, 21, TileType.FLOOR)
        for (y in 0 until 21) level.setTile(10, y, TileType.WALL)
        FieldOfView.compute(level, 5, 10, radius = 12)
        assertTrue(level.isVisible(9, 10), "la casella prima del muro deve essere visibile")
        assertTrue(level.isVisible(10, 10), "il muro stesso e' visibile")
        assertFalse(level.isVisible(11, 10), "dietro il muro non si vede")
        assertFalse(level.isVisible(15, 10))
    }

    @Test
    fun `il campo visivo rispetta il raggio`() {
        val level = DungeonLevel.filled(1, 31, 31, TileType.FLOOR)
        FieldOfView.compute(level, 15, 15, radius = 5)
        assertTrue(level.isVisible(15, 11))
        assertFalse(level.isVisible(15, 9), "oltre il raggio non si vede")
        assertTrue(level.isExplored(15, 12), "cio' che si vede diventa esplorato")
    }

    @Test
    fun `la linea di vista e' bloccata dai muri`() {
        val level = DungeonLevel.filled(1, 21, 21, TileType.FLOOR)
        assertTrue(FieldOfView.hasLineOfSight(level, 2, 2, 18, 2))
        level.setTile(10, 2, TileType.WALL)
        assertFalse(FieldOfView.hasLineOfSight(level, 2, 2, 18, 2))
        // Una porta aperta non blocca, una chiusa si'
        level.setTile(10, 2, TileType.DOOR_OPEN)
        assertTrue(FieldOfView.hasLineOfSight(level, 2, 2, 18, 2))
        level.setTile(10, 2, TileType.DOOR_CLOSED)
        assertFalse(FieldOfView.hasLineOfSight(level, 2, 2, 18, 2))
    }

    @Test
    fun `A star trova il percorso piu' breve in campo libero`() {
        val level = DungeonLevel.filled(1, 21, 21, TileType.FLOOR)
        val path = Pathfinding.findPath(level, Pos(1, 1), Pos(10, 5))
        // Distanza di Chebyshev con movimento a 8 direzioni
        assertEquals(9, path.size)
        assertEquals(Pos(10, 5), path.last())
    }

    @Test
    fun `A star aggira gli ostacoli e restituisce vuoto se irraggiungibile`() {
        val level = DungeonLevel.filled(1, 21, 21, TileType.FLOOR)
        for (y in 0 until 21) level.setTile(10, y, TileType.WALL)
        assertTrue(Pathfinding.findPath(level, Pos(2, 2), Pos(18, 2)).isEmpty())
        level.setTile(10, 20, TileType.FLOOR) // apre un varco
        val path = Pathfinding.findPath(level, Pos(2, 2), Pos(18, 2))
        assertTrue(path.isNotEmpty(), "con il varco il percorso deve esistere")
        assertTrue(path.size > 18, "il percorso deve aggirare il muro: ${path.size} passi")
    }

    @Test
    fun `le diagonali non tagliano gli angoli tra due muri`() {
        val level = DungeonLevel.filled(1, 11, 11, TileType.FLOOR)
        level.setTile(5, 4, TileType.WALL)
        level.setTile(4, 5, TileType.WALL)
        val path = Pathfinding.findPath(level, Pos(5, 5), Pos(4, 4))
        assertTrue(path.isEmpty() || path.first() != Pos(4, 4), "non si passa in diagonale tra due muri")
    }
}
