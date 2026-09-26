package com.changewave.dungeon.cli

import com.changewave.dungeon.dungeon.Pathfinding
import com.changewave.dungeon.dungeon.Pos
import com.changewave.dungeon.dungeon.TileType
import com.changewave.dungeon.game.Command
import com.changewave.dungeon.game.GameEngine
import com.changewave.dungeon.game.GameStatus
import com.changewave.dungeon.model.HeroClass
import com.changewave.dungeon.model.Potion
import com.changewave.dungeon.model.Progression
import com.changewave.dungeon.model.SpellId
import com.changewave.dungeon.rules.GameRandom

/**
 * Interfaccia testuale del motore: serve per giocare e collaudare su PC senza
 * passare dall'APK Android. Stessa logica, stesso stato, stessi salvataggi.
 *
 *   ./gradlew :core:run --console=plain --args="--class guerriero --seed 42"
 *   ./gradlew :core:run --console=plain --args="--demo 400"   (bot automatico)
 */
object TerminalGame {

    private val CLASSES = mapOf(
        "guerriero" to HeroClass.FIGHTER,
        "ladro" to HeroClass.ROGUE,
        "chierico" to HeroClass.CLERIC,
        "mago" to HeroClass.WIZARD,
    )

    @JvmStatic
    fun main(args: Array<String>) {
        var heroClass = HeroClass.FIGHTER
        var seed = System.nanoTime()
        var demoTurns = 0
        var name = "Avventuriero"

        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "--class" -> CLASSES[args.getOrNull(i + 1)?.lowercase()]?.let { heroClass = it }
                "--seed" -> args.getOrNull(i + 1)?.toLongOrNull()?.let { seed = it }
                "--demo" -> demoTurns = args.getOrNull(i + 1)?.toIntOrNull() ?: 300
                "--name" -> args.getOrNull(i + 1)?.let { name = it }
                "--help" -> { printHelp(); return }
            }
            i += 2
        }

        val engine = GameEngine.newGame(name, heroClass, seed = seed)
        println("Seed della partita: $seed")

        if (demoTurns > 0) {
            runDemo(engine, demoTurns)
            return
        }
        runInteractive(engine)
    }

    private fun printHelp() {
        println(
            """
            Dungeon d20 - runner testuale
              --class guerriero|ladro|chierico|mago
              --seed <numero>      partita riproducibile
              --demo <turni>       fa giocare il bot e stampa il risultato
              --name <nome>
            Comandi in partita: y k u / h . l / b j n (direzioni), '.' attendi,
              '>' scendi, ',' raccogli, 'i' zaino, 'q' bevi pozione,
              'f' incantesimo sul nemico piu' vicino, 'x' esci.
            """.trimIndent(),
        )
    }

    // --- Modalita' interattiva -------------------------------------------------

    private fun runInteractive(engine: GameEngine) {
        render(engine)
        while (!engine.state.isOver) {
            print("> ")
            val line = readlnOrNull()?.trim() ?: break
            if (line == "x") { println("Uscita."); return }
            val command = parse(line, engine) ?: continue
            val result = engine.execute(command)
            result.rejection?.let { println("! $it") }
            render(engine)
        }
        printEnding(engine)
    }

    private fun parse(input: String, engine: GameEngine): Command? = when (input) {
        "h" -> Command.Move(-1, 0)
        "l" -> Command.Move(1, 0)
        "k" -> Command.Move(0, -1)
        "j" -> Command.Move(0, 1)
        "y" -> Command.Move(-1, -1)
        "u" -> Command.Move(1, -1)
        "b" -> Command.Move(-1, 1)
        "n" -> Command.Move(1, 1)
        "." -> Command.Wait
        ">" -> Command.Descend
        "<" -> Command.Ascend
        "," -> Command.PickUp
        "i" -> { printInventory(engine); null }
        "q" -> {
            val index = engine.player.inventory.indexOfFirst { it is Potion }
            if (index < 0) { println("Nessuna pozione nello zaino."); null } else Command.UseItem(index)
        }
        "f" -> {
            val target = engine.nearestVisibleMonster()
            val spell = engine.player.knownSpells().firstOrNull()
            when {
                spell == null -> { println("Non conosci incantesimi."); null }
                target == null -> { println("Nessun bersaglio in vista."); null }
                else -> Command.Cast(spell.id, target.id, maxOf(1, spell.level))
            }
        }
        "s" -> Command.SecondWind
        else -> {
            input.toIntOrNull()?.let { Command.UseItem(it) } ?: run { println("Comando sconosciuto (--help)."); null }
        }
    }

    private fun printInventory(engine: GameEngine) {
        val inventory = engine.player.inventory
        if (inventory.isEmpty()) { println("Zaino vuoto."); return }
        println("Zaino (digita il numero per usare/equipaggiare):")
        inventory.forEachIndexed { index, item -> println("  $index) ${item.name}") }
    }

    // --- Modalita' dimostrativa ------------------------------------------------

    private fun runDemo(engine: GameEngine, turns: Int) {
        val rng = GameRandom.fromSeed(engine.state.seed xor 0x5DEECE66DL)
        var steps = 0
        while (!engine.state.isOver && steps++ < turns) {
            var attempts = 0
            while (attempts++ < 4 && !engine.execute(botCommand(engine, rng)).accepted) {
                val dir = rng.pick(Pathfinding.DIRECTIONS)
                if (engine.execute(Command.Move(dir.x, dir.y)).accepted) break
            }
        }
        render(engine)
        printEnding(engine)
    }

    /** Bot minimale: combatte cio' che incontra e punta alle scale. */
    private fun botCommand(engine: GameEngine, rng: GameRandom): Command {
        val player = engine.player
        val potion = player.inventory.indexOfFirst { it is Potion }
        if (player.hitPoints < player.maxHitPoints * 0.4 && potion >= 0) return Command.UseItem(potion)

        val target = engine.nearestVisibleMonster()
        if (target != null) {
            val distance = player.distanceTo(target)
            if (distance == 1) return Command.Move(target.x - player.x, target.y - player.y)
            if (player.heroClass.isSpellcaster && distance <= 10) {
                val spell = if (player.availableSlots(1) > 0) {
                    player.knownSpells().firstOrNull { it.level == 1 }
                } else {
                    player.knownSpells().firstOrNull { it.level == 0 }
                }
                if (spell != null) return Command.Cast(spell.id, target.id, maxOf(1, spell.level))
            }
            if (player.weapon.ranged && distance <= player.weapon.range) return Command.RangedAttack(target.id)
        }
        if (engine.level.itemsAt(player.x, player.y).isNotEmpty()) return Command.PickUp
        if (engine.level.tileAt(player.x, player.y) == TileType.STAIRS_DOWN) return Command.Descend

        val stairs = engine.level.stairsDown
        if (stairs != null) {
            val blocked = engine.level.monsters.map { Pos(it.x, it.y) }.toSet()
            val step = Pathfinding.findPath(engine.level, Pos(player.x, player.y), stairs, blocked).firstOrNull()
            if (step != null) return Command.Move(step.x - player.x, step.y - player.y)
        }
        val dir = rng.pick(Pathfinding.DIRECTIONS)
        return Command.Move(dir.x, dir.y)
    }

    // --- Rendering -------------------------------------------------------------

    fun render(engine: GameEngine) {
        val level = engine.level
        val player = engine.player
        val builder = StringBuilder()
        for (y in 0 until level.height) {
            for (x in 0 until level.width) {
                builder.append(glyphAt(engine, x, y))
            }
            builder.append('\n')
        }
        println(builder)
        val xpLeft = Progression.experienceToNextLevel(player.experience)?.let { "-$it al livello ${player.level + 1}" } ?: "livello massimo"
        println(
            "${player.name} ${player.heroClass.italian} liv.${player.level}  " +
                "PF ${player.hitPoints}/${player.maxHitPoints}  CA ${player.armorClass}  " +
                "Prof.${engine.state.depth}  PX ${player.experience} ($xpLeft)  Oro ${player.gold}  Turno ${engine.state.turn}",
        )
        if (player.conditions.active.isNotEmpty()) {
            println("Condizioni: " + player.conditions.active.joinToString { "${it.condition.italian}(${it.turnsLeft})" })
        }
        engine.state.log.last(6).forEach { println("  ${it.text}") }
    }

    private fun glyphAt(engine: GameEngine, x: Int, y: Int): Char {
        val level = engine.level
        if (engine.player.x == x && engine.player.y == y) return '@'
        if (!level.isExplored(x, y)) return ' '
        level.monsterAt(x, y)?.let { if (level.isVisible(x, y)) return it.glyph }
        level.items.firstOrNull { it.pos.x == x && it.pos.y == y }?.let { return it.item.glyph }
        level.trapAt(x, y)?.let { if (it.discovered) return '^' }
        return level.tileAt(x, y).glyph
    }

    private fun printEnding(engine: GameEngine) {
        when (engine.state.status) {
            GameStatus.VICTORY -> println("=== VITTORIA! Punteggio ${engine.state.score()} ===")
            GameStatus.DEAD -> println("=== Morto al livello ${engine.state.depth}. Punteggio ${engine.state.score()} ===")
            GameStatus.PLAYING -> println("=== Partita interrotta. Punteggio provvisorio ${engine.state.score()} ===")
        }
    }
}
