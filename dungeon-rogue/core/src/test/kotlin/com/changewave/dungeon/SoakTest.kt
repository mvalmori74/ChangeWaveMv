package com.changewave.dungeon

import com.changewave.dungeon.dungeon.Pathfinding
import com.changewave.dungeon.dungeon.Pos
import com.changewave.dungeon.dungeon.TileType
import com.changewave.dungeon.game.Command
import com.changewave.dungeon.game.GameEngine
import com.changewave.dungeon.game.GameStatus
import com.changewave.dungeon.model.HeroClass
import com.changewave.dungeon.model.SpellId
import com.changewave.dungeon.rules.GameRandom
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.system.measureTimeMillis

/**
 * Prove "a lungo termine": un bot gioca partite intere con comandi casuali ma
 * sensati. Servono a far emergere eccezioni, stati incoerenti e regressioni di
 * prestazioni che i test unitari non intercettano.
 */
class SoakTest {

    /**
     * Bot "ragionevole": scende verso il basso, combatte cio' che incontra, beve
     * pozioni quando e' ferito. Non gioca bene, ma esercita tutte le meccaniche
     * (movimento, combattimento, incantesimi, oggetti, scale, trappole, boss).
     */
    private fun botCommand(engine: GameEngine, rng: GameRandom): Command {
        val player = engine.player
        val potionIndex = player.inventory.indexOfFirst { it is com.changewave.dungeon.model.Potion }
        if (player.hitPoints < player.maxHitPoints * 0.4 && potionIndex >= 0) {
            return Command.UseItem(potionIndex)
        }
        if (player.heroClass == HeroClass.FIGHTER && player.secondWindAvailable &&
            player.hitPoints < player.maxHitPoints * 0.35
        ) {
            return Command.SecondWind
        }

        val target = engine.nearestVisibleMonster()
        if (target != null) {
            val distance = player.distanceTo(target)
            if (distance == 1) return Command.Move(target.x - player.x, target.y - player.y)
            if (player.heroClass == HeroClass.WIZARD && distance <= 10) {
                return if (player.availableSlots(1) > 0 && rng.chance(0.3)) {
                    Command.Cast(SpellId.MAGIC_MISSILE, target.id)
                } else {
                    Command.Cast(SpellId.FIRE_BOLT, target.id)
                }
            }
            if (player.heroClass == HeroClass.CLERIC && distance <= 10 && rng.chance(0.5)) {
                return Command.Cast(SpellId.SACRED_FLAME, target.id)
            }
            if (player.weapon.ranged && distance <= player.weapon.range) {
                return Command.RangedAttack(target.id)
            }
        }

        if (engine.level.itemsAt(player.x, player.y).isNotEmpty()) return Command.PickUp
        if (engine.level.tileAt(player.x, player.y) == TileType.STAIRS_DOWN) return Command.Descend

        val stairs = engine.level.stairsDown
        if (stairs != null && !rng.chance(0.1)) {
            val blocked = engine.level.monsters.map { Pos(it.x, it.y) }.toSet()
            val step = Pathfinding.findPath(engine.level, Pos(player.x, player.y), stairs, blocked).firstOrNull()
            if (step != null) return Command.Move(step.x - player.x, step.y - player.y)
        }
        val dir = rng.pick(Pathfinding.DIRECTIONS)
        return Command.Move(dir.x, dir.y)
    }

    private fun assertInvariants(engine: GameEngine) {
        val level = engine.level
        assertTrue(level.inBounds(engine.player.x, engine.player.y), "giocatore fuori mappa")
        assertTrue(level.isWalkable(engine.player.x, engine.player.y), "giocatore dentro un muro")
        assertTrue(engine.player.hitPoints <= engine.player.maxHitPoints, "PF oltre il massimo")
        assertTrue(engine.player.hitPoints >= 0, "PF negativi")
        assertTrue(engine.player.availableSlots(1) >= 0, "slot negativi")
        assertTrue(engine.player.inventory.size <= GameEngine.MAX_INVENTORY, "inventario oltre il limite")
        val positions = mutableSetOf<Pos>()
        for (monster in level.monsters) {
            assertTrue(monster.hitPoints > 0, "cadavere non rimosso")
            assertTrue(level.isWalkable(monster.x, monster.y), "${monster.name} dentro un muro")
            assertTrue(positions.add(Pos(monster.x, monster.y)), "mostri sovrapposti")
        }
    }

    @Test
    fun `sessanta partite complete senza eccezioni ne' stati incoerenti`() {
        var deaths = 0
        var victories = 0
        var maxDepth = 1
        val depths = mutableListOf<Int>()
        val elapsed = measureTimeMillis {
            for (seed in 1L..60L) {
                val heroClass = HeroClass.entries[(seed % 4).toInt()]
                val engine = GameEngine.newGame("Bot$seed", heroClass, seed = seed * 104729)
                val rng = GameRandom.fromSeed(seed * 31 + 7)
                var steps = 0
                while (!engine.state.isOver && steps++ < 6000) {
                    // Un comando rifiutato (muro, bersaglio coperto) non consuma tempo:
                    // il bot ripiega su un passo casuale, come farebbe un giocatore.
                    var attempts = 0
                    while (attempts++ < 4 && !engine.execute(botCommand(engine, rng)).accepted) {
                        val dir = rng.pick(Pathfinding.DIRECTIONS)
                        if (engine.execute(Command.Move(dir.x, dir.y)).accepted) break
                    }
                    if (steps % 100 == 0) assertInvariants(engine)
                }
                depths.add(engine.state.deepestDepth)
                assertInvariants(engine)
                maxDepth = maxOf(maxDepth, engine.state.deepestDepth)
                when (engine.state.status) {
                    GameStatus.DEAD -> deaths++
                    GameStatus.VICTORY -> victories++
                    GameStatus.PLAYING -> {}
                }
            }
        }
        println(
            "Soak test: $deaths morti, $victories vittorie, profondita' massima $maxDepth, " +
                "profondita' media ${"%.1f".format(depths.average())}, distribuzione ${depths.groupingBy { it }.eachCount().toSortedMap()}, ${elapsed}ms",
        )
        // Curva di difficolta': un bot che scende senza strategia deve quasi sempre
        // morire, ma deve anche riuscire a raggiungere i livelli profondi.
        assertTrue(deaths >= 40, "morti: $deaths su 60 partite (il gioco e' troppo facile?)")
        assertTrue(maxDepth >= 5, "profondita' massima raggiunta: $maxDepth (il gioco e' troppo difficile?)")
    }

    @Test
    fun `un turno completo resta ampiamente sotto il budget di frame`() {
        // Personaggio invulnerabile: misura il costo del motore, non la sopravvivenza.
        fun immortalRun(seed: Long, steps: Int): Pair<Int, Long> {
            val engine = GameEngine.newGame("Perf", HeroClass.FIGHTER, seed = seed)
            val rng = GameRandom.fromSeed(seed)
            var turns = 0
            val ms = measureTimeMillis {
                while (turns++ < steps) {
                    engine.player.hitPoints = engine.player.maxHitPoints
                    if (engine.state.isOver) break
                    engine.execute(botCommand(engine, rng))
                }
            }
            return turns to ms
        }
        immortalRun(8080, 1000) // riscaldamento JIT
        val (turns, elapsed) = immortalRun(9090, 3000)
        val perTurn = elapsed.toDouble() / turns
        println("Tempo medio per turno: ${"%.3f".format(perTurn)} ms su $turns turni")
        // Su JVM desktop: largamente sotto il millisecondo. Su Android ci si aspetta
        // un fattore 5-10, comunque entro il budget di 16 ms per frame.
        assertTrue(perTurn < 2.0, "turno troppo lento: $perTurn ms")
    }

    @Test
    fun `la generazione di un livello e' rapida`() {
        val generator = com.changewave.dungeon.dungeon.DungeonGenerator()
        val rng = GameRandom.fromSeed(12345)
        repeat(20) { generator.generate(5, rng) } // riscaldamento
        val elapsed = measureTimeMillis {
            repeat(100) { generator.generate((it % 10) + 1, rng) }
        }
        val perLevel = elapsed / 100.0
        println("Generazione livello: ${"%.2f".format(perLevel)} ms")
        assertTrue(perLevel < 50.0, "generazione troppo lenta: $perLevel ms")
    }
}
