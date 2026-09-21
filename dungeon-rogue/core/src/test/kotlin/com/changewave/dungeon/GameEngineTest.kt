package com.changewave.dungeon

import com.changewave.dungeon.dungeon.Pathfinding
import com.changewave.dungeon.dungeon.Pos
import com.changewave.dungeon.dungeon.TileType
import com.changewave.dungeon.game.Command
import com.changewave.dungeon.game.GameEngine
import com.changewave.dungeon.game.GameStatus
import com.changewave.dungeon.model.Bestiary
import com.changewave.dungeon.model.HeroClass
import com.changewave.dungeon.model.ItemCatalog
import com.changewave.dungeon.model.Monster
import com.changewave.dungeon.model.SpellId
import com.changewave.dungeon.rules.AbilityScores
import com.changewave.dungeon.rules.Condition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GameEngineTest {

    private fun engine(seed: Long = 1234, heroClass: HeroClass = HeroClass.FIGHTER): GameEngine =
        GameEngine.newGame("Eroe", heroClass, seed = seed)

    /** Rimuove i mostri dal livello per isolare il comportamento sotto test. */
    private fun GameEngine.clearMonsters() = level.monsters.clear()

    private fun GameEngine.freeNeighbour(): Pos? = Pathfinding.DIRECTIONS
        .map { Pos(player.x + it.x, player.y + it.y) }
        .firstOrNull { level.isWalkable(it.x, it.y) && level.monsterAt(it.x, it.y) == null }

    private fun GameEngine.spawnAdjacent(species: com.changewave.dungeon.model.MonsterSpecies): Monster {
        val pos = freeNeighbour() ?: error("nessuna casella libera adiacente")
        val monster = species.instantiate(state.rng)
        monster.x = pos.x
        monster.y = pos.y
        monster.id = state.nextActorId++
        level.monsters.add(monster)
        return monster
    }

    @Test
    fun `una nuova partita parte sulla scala di salita con la mappa visibile`() {
        val engine = engine()
        assertEquals(1, engine.state.depth)
        assertEquals(TileType.STAIRS_UP, engine.level.tileAt(engine.player.x, engine.player.y))
        assertTrue(engine.level.isVisible(engine.player.x, engine.player.y))
        assertEquals(GameStatus.PLAYING, engine.state.status)
        assertTrue(engine.level.monsters.isNotEmpty())
        assertTrue(engine.level.monsters.all { it.id > 0 }, "ogni mostro deve avere un id")
    }

    @Test
    fun `muoversi contro un muro non consuma il turno`() {
        val engine = engine()
        engine.clearMonsters()
        val turnBefore = engine.state.turn
        // Cerca una direzione bloccata da un muro
        val blocked = Pathfinding.DIRECTIONS.firstOrNull { !engine.level.isWalkable(engine.player.x + it.x, engine.player.y + it.y) }
        assertNotNull(blocked, "il giocatore deve avere almeno un muro adiacente")
        val result = engine.execute(Command.Move(blocked!!.x, blocked.y))
        assertFalse(result.accepted)
        assertFalse(result.timeAdvanced)
        assertEquals(turnBefore, engine.state.turn)
    }

    @Test
    fun `muoversi consuma il turno e fa avanzare il tempo`() {
        val engine = engine()
        engine.clearMonsters()
        val target = engine.freeNeighbour()!!
        val turnBefore = engine.state.turn
        val result = engine.execute(Command.Move(target.x - engine.player.x, target.y - engine.player.y))
        assertTrue(result.accepted)
        assertEquals(target.x, engine.player.x)
        assertEquals(target.y, engine.player.y)
        assertTrue(engine.state.turn > turnBefore)
    }

    @Test
    fun `attaccare un mostro adiacente lo danneggia e alla morte da' PX`() {
        val engine = engine(seed = 777)
        engine.clearMonsters()
        val rat = engine.spawnAdjacent(Bestiary.GIANT_RAT)
        val xpBefore = engine.player.experience
        var guard = 0
        while (rat.hitPoints > 0 && guard++ < 40 && !engine.state.isOver) {
            engine.execute(Command.Move(rat.x - engine.player.x, rat.y - engine.player.y))
        }
        assertTrue(rat.hitPoints <= 0, "il ratto doveva morire in meno di 40 attacchi")
        assertTrue(engine.player.experience > xpBefore, "uccidere deve dare PX")
        assertEquals(1, engine.state.monstersKilled)
    }

    @Test
    fun `raccogliere un oggetto lo sposta nell'inventario`() {
        val engine = engine()
        engine.clearMonsters()
        engine.level.dropItem(engine.player.x, engine.player.y, ItemCatalog.POTION_HEALING)
        val sizeBefore = engine.player.inventory.size
        val result = engine.execute(Command.PickUp)
        assertTrue(result.accepted)
        assertEquals(sizeBefore + 1, engine.player.inventory.size)
        assertTrue(engine.level.itemsAt(engine.player.x, engine.player.y).isEmpty())
    }

    @Test
    fun `l'oro non occupa spazio nell'inventario`() {
        val engine = engine()
        engine.clearMonsters()
        val gold = com.changewave.dungeon.model.Treasure("gold_100", "100 monete d'oro", 100)
        engine.level.dropItem(engine.player.x, engine.player.y, gold)
        val inventoryBefore = engine.player.inventory.size
        val goldBefore = engine.player.gold
        engine.execute(Command.PickUp)
        assertEquals(inventoryBefore, engine.player.inventory.size)
        assertEquals(goldBefore + 100, engine.player.gold)
    }

    @Test
    fun `bere una pozione cura ma non oltre i PF massimi`() {
        val engine = engine()
        engine.clearMonsters()
        engine.player.inventory.add(ItemCatalog.POTION_HEALING)
        engine.player.hitPoints = 1
        val index = engine.player.inventory.lastIndex
        engine.execute(Command.UseItem(index))
        assertTrue(engine.player.hitPoints > 1)
        assertTrue(engine.player.hitPoints <= engine.player.maxHitPoints)

        engine.player.inventory.add(ItemCatalog.POTION_HEALING)
        engine.player.hitPoints = engine.player.maxHitPoints
        engine.execute(Command.UseItem(engine.player.inventory.lastIndex))
        assertEquals(engine.player.maxHitPoints, engine.player.hitPoints)
    }

    @Test
    fun `equipaggiare un'arma scambia quella impugnata`() {
        val engine = engine()
        engine.clearMonsters()
        val previous = engine.player.weapon
        engine.player.inventory.add(ItemCatalog.GREATAXE)
        engine.execute(Command.UseItem(engine.player.inventory.lastIndex))
        assertEquals(ItemCatalog.GREATAXE.id, engine.player.weapon.id)
        assertTrue(engine.player.inventory.any { it.id == previous.id })
    }

    @Test
    fun `scendere richiede di essere sulle scale e ricarica Recuperare Energie`() {
        val engine = engine(seed = 2468)
        engine.clearMonsters()
        val refused = engine.execute(Command.Descend)
        assertFalse(refused.accepted)
        assertNotNull(refused.rejection)

        // Teletrasporto sulle scale di discesa per isolare il test dalla navigazione
        val down = engine.level.stairsDown!!
        engine.player.x = down.x
        engine.player.y = down.y
        engine.player.secondWindAvailable = false
        val result = engine.execute(Command.Descend)
        assertTrue(result.accepted)
        assertEquals(2, engine.state.depth)
        assertEquals(2, engine.state.deepestDepth)
        assertTrue(engine.player.secondWindAvailable, "scendere vale come riposo breve")
        assertEquals(TileType.STAIRS_UP, engine.level.tileAt(engine.player.x, engine.player.y))
        assertTrue(engine.level.monsters.all { it.id > 0 }, "i mostri del nuovo livello devono avere id")
    }

    @Test
    fun `risalire riporta al livello precedente gia' generato`() {
        val engine = engine(seed = 13579)
        engine.clearMonsters()
        val down = engine.level.stairsDown!!
        engine.player.x = down.x; engine.player.y = down.y
        engine.execute(Command.Descend)
        assertEquals(2, engine.state.depth)
        engine.clearMonsters()
        engine.execute(Command.Ascend)
        assertEquals(1, engine.state.depth)
        assertEquals(2, engine.state.levels.size, "i livelli visitati restano in memoria")
    }

    @Test
    fun `Recuperare Energie e' esclusiva del guerriero e una volta sola`() {
        val fighter = engine(seed = 42, heroClass = HeroClass.FIGHTER)
        fighter.clearMonsters()
        fighter.player.hitPoints = 1
        assertTrue(fighter.execute(Command.SecondWind).accepted)
        assertTrue(fighter.player.hitPoints > 1)
        assertFalse(fighter.execute(Command.SecondWind).accepted)

        val wizard = engine(seed = 42, heroClass = HeroClass.WIZARD)
        wizard.clearMonsters()
        assertFalse(wizard.execute(Command.SecondWind).accepted)
    }

    @Test
    fun `il mago lancia dardo incantato consumando uno slot`() {
        val engine = engine(seed = 8080, heroClass = HeroClass.WIZARD)
        engine.clearMonsters()
        val target = engine.spawnAdjacent(Bestiary.GIANT_RAT)
        target.hitPoints = 50
        engine.recomputeVisibility()
        val slotsBefore = engine.player.availableSlots(1)
        val result = engine.execute(Command.Cast(SpellId.MAGIC_MISSILE, target.id))
        assertTrue(result.accepted, result.rejection)
        assertEquals(slotsBefore - 1, engine.player.availableSlots(1))
        assertTrue(target.hitPoints < 50, "il dardo incantato colpisce sempre")
    }

    @Test
    fun `senza slot l'incantesimo di livello 1 viene rifiutato`() {
        val engine = engine(seed = 8081, heroClass = HeroClass.WIZARD)
        engine.clearMonsters()
        val target = engine.spawnAdjacent(Bestiary.GIANT_RAT)
        target.hitPoints = 100
        engine.recomputeVisibility()
        engine.player.usedSlotsLevel1 = engine.player.maxSlots(1)
        val result = engine.execute(Command.Cast(SpellId.MAGIC_MISSILE, target.id))
        assertFalse(result.accepted)
        assertNotNull(result.rejection)
        // Il trucchetto resta sempre disponibile
        assertTrue(engine.execute(Command.Cast(SpellId.FIRE_BOLT, target.id)).accepted)
    }

    @Test
    fun `una classe non puo' lanciare incantesimi che non conosce`() {
        val engine = engine(seed = 99, heroClass = HeroClass.FIGHTER)
        engine.clearMonsters()
        val target = engine.spawnAdjacent(Bestiary.GIANT_RAT)
        engine.recomputeVisibility()
        assertFalse(engine.execute(Command.Cast(SpellId.FIRE_BOLT, target.id)).accepted)
    }

    @Test
    fun `l'arma da mischia non puo' essere usata come attacco a distanza`() {
        val engine = engine(seed = 31)
        engine.clearMonsters()
        val target = engine.spawnAdjacent(Bestiary.GIANT_RAT)
        val result = engine.execute(Command.RangedAttack(target.id))
        assertFalse(result.accepted)
    }

    @Test
    fun `a zero PF senza nemici attorno partono i tiri salvezza contro morte`() {
        val engine = engine(seed = 31415)
        engine.clearMonsters()
        // Trappola inevitabile su una casella adiacente: porta il personaggio a 0 PF
        val pit = engine.freeNeighbour()!!
        engine.level.traps.add(
            com.changewave.dungeon.dungeon.Trap(pit, com.changewave.dungeon.dungeon.TrapKind.PIT, saveDc = 99),
        )
        engine.player.hitPoints = 1
        engine.execute(Command.Move(pit.x - engine.player.x, pit.y - engine.player.y))

        // La trappola porta a 0 PF: da li' il motore risolve i tiri contro morte
        // senza attendere altri comandi (il personaggio non puo' agire).
        val log = engine.state.log.all().joinToString("\n") { it.text }
        assertTrue(log.contains("Cadi privo di sensi"), "a 0 PF si cade privi di sensi:\n$log")
        assertTrue(log.contains("Tiro contro morte"), "devono comparire i tiri salvezza contro morte:\n$log")
        assertTrue(
            engine.state.status == GameStatus.DEAD || engine.player.hitPoints > 0,
            "l'esito deve essere morte oppure stabilizzazione a 1 PF",
        )
        if (engine.state.status != GameStatus.DEAD) {
            assertEquals(1, engine.player.hitPoints, "chi si stabilizza riparte da 1 PF")
            assertFalse(Condition.UNCONSCIOUS in engine.player.conditions)
        }
    }

    @Test
    fun `circondato da ogre il personaggio muore per fallimenti automatici`() {
        val engine = engine(seed = 4711)
        engine.clearMonsters()
        repeat(3) {
            val pos = engine.freeNeighbour() ?: return@repeat
            val ogre = Bestiary.OGRE.instantiate(engine.state.rng)
            ogre.x = pos.x; ogre.y = pos.y; ogre.alerted = true
            ogre.id = engine.state.nextActorId++
            engine.level.monsters.add(ogre)
        }
        engine.player.hitPoints = 1
        var sawUnconscious = false
        var guard = 0
        while (!engine.state.isOver && guard++ < 200) {
            engine.execute(Command.Wait)
            if (Condition.UNCONSCIOUS in engine.player.conditions) sawUnconscious = true
        }
        assertEquals(GameStatus.DEAD, engine.state.status)
        assertTrue(sawUnconscious, "prima della morte ci deve essere lo stato di incoscienza")
        assertTrue(engine.player.isDead)
    }

    @Test
    fun `a partita finita i comandi vengono rifiutati`() {
        val engine = engine()
        engine.state.status = GameStatus.DEAD
        val result = engine.execute(Command.Wait)
        assertFalse(result.accepted)
        assertEquals("La partita e' finita.", result.rejection)
    }

    @Test
    fun `uccidere il boss vince la partita`() {
        val engine = engine(seed = 606)
        engine.clearMonsters()
        val boss = engine.spawnAdjacent(Bestiary.DEATH_LORD)
        boss.hitPoints = 1
        engine.player.weapon = ItemCatalog.GREATAXE
        var guard = 0
        while (boss.hitPoints > 0 && guard++ < 60 && !engine.state.isOver) {
            engine.execute(Command.Move(boss.x - engine.player.x, boss.y - engine.player.y))
            engine.player.hitPoints = engine.player.maxHitPoints // isola il test dalla sopravvivenza
        }
        assertEquals(GameStatus.VICTORY, engine.state.status)
    }

    @Test
    fun `i mostri veloci agiscono piu' spesso di quelli lenti`() {
        // Verifica del modello a punti azione: 50 ft contro 20 ft
        val engine = engine(seed = 31337)
        engine.clearMonsters()
        val wolf = Bestiary.DIRE_WOLF.instantiate(engine.state.rng)
        val zombie = Bestiary.ZOMBIE.instantiate(engine.state.rng)
        assertEquals(50, wolf.speed)
        assertEquals(20, zombie.speed)
        // In 10 turni del giocatore: lupo ~16 azioni, zombi ~6
        val cost = com.changewave.dungeon.model.Actor.ENERGY_PER_ACTION
        var wolfActions = 0
        var zombieActions = 0
        repeat(10) {
            wolf.energy += wolf.speed
            while (wolf.energy >= cost) { wolf.energy -= cost; wolfActions++ }
            zombie.energy += zombie.speed
            while (zombie.energy >= cost) { zombie.energy -= cost; zombieActions++ }
        }
        assertEquals(16, wolfActions)
        assertEquals(6, zombieActions)
    }

    @Test
    fun `nessun mostro resta su una casella occupata o dentro un muro`() {
        val engine = engine(seed = 2222)
        repeat(300) {
            if (engine.state.isOver) return@repeat
            engine.execute(Command.Wait)
        }
        val positions = mutableSetOf<Pos>()
        for (monster in engine.level.monsters) {
            assertTrue(engine.level.isWalkable(monster.x, monster.y), "${monster.name} dentro un muro")
            assertTrue(positions.add(Pos(monster.x, monster.y)), "due mostri sovrapposti")
            assertFalse(monster.x == engine.player.x && monster.y == engine.player.y, "mostro sopra il giocatore")
        }
        assertNull(engine.level.monsters.firstOrNull { it.hitPoints <= 0 }, "i cadaveri devono essere rimossi")
    }

    @Test
    fun `personaggio consigliato per ogni classe`() {
        for (heroClass in HeroClass.entries) {
            val scores: AbilityScores = com.changewave.dungeon.model.PlayerCharacter.recommendedScores(heroClass)
            assertEquals(15, scores[heroClass.primaryAbility], "${heroClass.italian}: la primaria deve essere 15")
            val engine = GameEngine.newGame("Test", heroClass, scores, seed = 1)
            assertTrue(engine.player.maxHitPoints >= heroClass.hitDie)
            assertTrue(engine.player.inventory.isNotEmpty())
        }
    }
}
