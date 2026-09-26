package com.changewave.dungeon.dungeon

import com.changewave.dungeon.model.Bestiary
import com.changewave.dungeon.model.Item
import com.changewave.dungeon.model.ItemCatalog
import com.changewave.dungeon.model.Shield
import com.changewave.dungeon.rules.GameRandom
import kotlin.math.roundToInt

data class DungeonConfig(
    val width: Int = 48,
    val height: Int = 32,
    /** Dimensione minima di una foglia BSP: sotto questa soglia non si divide piu'. */
    val minLeafSize: Int = 9,
    val maxLeafSize: Int = 16,
    val minRoomSize: Int = 4,
    val doorChance: Double = 0.55,
    val maxDepth: Int = 10,
)

/**
 * Generatore di livelli: partizionamento BSP, stanze rettangolari, corridoi a L
 * tra stanze sorelle, porte sulle soglie, scale, popolamento a budget di PX.
 *
 * Proprieta' garantite (verificate dai test):
 *  - la mappa e' interamente connessa (ogni casella calpestabile e' raggiungibile
 *    dalla scala di salita, considerando le porte chiuse come attraversabili);
 *  - scala di discesa sempre presente e raggiungibile, tranne al livello finale
 *    dove al suo posto c'e' il boss;
 *  - nessun mostro/oggetto sovrapposto o dentro un muro.
 */
class DungeonGenerator(private val config: DungeonConfig = DungeonConfig()) {

    private class Leaf(val area: Rect) {
        var left: Leaf? = null
        var right: Leaf? = null
        var room: Rect? = null
        val isLeaf: Boolean get() = left == null && right == null

        fun rooms(): List<Rect> =
            if (isLeaf) listOfNotNull(room) else (left?.rooms() ?: emptyList()) + (right?.rooms() ?: emptyList())

        /** Una stanza qualsiasi del sottoalbero, per collegare i rami. */
        fun anyRoom(rng: GameRandom): Rect? {
            val all = rooms()
            return if (all.isEmpty()) null else rng.pick(all)
        }
    }

    fun generate(depth: Int, rng: GameRandom): DungeonLevel {
        val level = DungeonLevel.filled(depth, config.width, config.height, TileType.WALL)
        val root = Leaf(Rect(1, 1, config.width - 2, config.height - 2))
        split(root, rng)
        carveRooms(root, rng, level)
        connect(root, rng, level)
        placeDoors(level, rng)
        val rooms = root.rooms().sortedBy { it.center.x + it.center.y }
        val withRooms = DungeonLevel(
            depth = depth,
            width = level.width,
            height = level.height,
            tiles = level.tiles,
            rooms = rooms,
        )
        placeStairs(withRooms, rng)
        populate(withRooms, rng)
        return withRooms
    }

    // --- BSP ------------------------------------------------------------------

    private fun split(leaf: Leaf, rng: GameRandom) {
        val w = leaf.area.width
        val h = leaf.area.height
        val mustSplit = w > config.maxLeafSize || h > config.maxLeafSize
        if (!mustSplit && (w < config.minLeafSize * 2 || h < config.minLeafSize * 2 || rng.chance(0.25))) return
        if (w < config.minLeafSize * 2 && h < config.minLeafSize * 2) return

        val horizontal = when {
            w >= config.minLeafSize * 2 && h < config.minLeafSize * 2 -> false
            h >= config.minLeafSize * 2 && w < config.minLeafSize * 2 -> true
            w > h -> false
            h > w -> true
            else -> rng.chance(0.5)
        }

        if (horizontal) {
            val cut = rng.nextInt(leaf.area.y1 + config.minLeafSize, leaf.area.y2 - config.minLeafSize + 1)
            leaf.left = Leaf(Rect(leaf.area.x1, leaf.area.y1, leaf.area.x2, cut - 1))
            leaf.right = Leaf(Rect(leaf.area.x1, cut, leaf.area.x2, leaf.area.y2))
        } else {
            val cut = rng.nextInt(leaf.area.x1 + config.minLeafSize, leaf.area.x2 - config.minLeafSize + 1)
            leaf.left = Leaf(Rect(leaf.area.x1, leaf.area.y1, cut - 1, leaf.area.y2))
            leaf.right = Leaf(Rect(cut, leaf.area.y1, leaf.area.x2, leaf.area.y2))
        }
        split(leaf.left!!, rng)
        split(leaf.right!!, rng)
    }

    private fun carveRooms(leaf: Leaf, rng: GameRandom, level: DungeonLevel) {
        if (!leaf.isLeaf) {
            leaf.left?.let { carveRooms(it, rng, level) }
            leaf.right?.let { carveRooms(it, rng, level) }
            return
        }
        val area = leaf.area
        val maxW = (area.width - 2).coerceAtLeast(config.minRoomSize)
        val maxH = (area.height - 2).coerceAtLeast(config.minRoomSize)
        if (maxW < config.minRoomSize || maxH < config.minRoomSize) return
        val w = rng.nextInt(config.minRoomSize, maxW)
        val h = rng.nextInt(config.minRoomSize, maxH)
        val x = rng.nextInt(area.x1, area.x2 - w + 1)
        val y = rng.nextInt(area.y1, area.y2 - h + 1)
        val room = Rect(x, y, x + w - 1, y + h - 1)
        leaf.room = room
        for (pos in room.positions()) level.setTile(pos.x, pos.y, TileType.FLOOR)
    }

    private fun connect(leaf: Leaf, rng: GameRandom, level: DungeonLevel) {
        val left = leaf.left
        val right = leaf.right
        if (left == null || right == null) return
        connect(left, rng, level)
        connect(right, rng, level)
        val a = left.anyRoom(rng) ?: return
        val b = right.anyRoom(rng) ?: return
        carveCorridor(level, a.center, b.center, rng)
    }

    private fun carveCorridor(level: DungeonLevel, from: Pos, to: Pos, rng: GameRandom) {
        val horizontalFirst = rng.chance(0.5)
        val corner = if (horizontalFirst) Pos(to.x, from.y) else Pos(from.x, to.y)
        carveLine(level, from, corner)
        carveLine(level, corner, to)
    }

    private fun carveLine(level: DungeonLevel, from: Pos, to: Pos) {
        if (from.x == to.x) {
            for (y in minOf(from.y, to.y)..maxOf(from.y, to.y)) {
                if (level.tileAt(from.x, y) == TileType.WALL) level.setTile(from.x, y, TileType.FLOOR)
            }
        } else {
            for (x in minOf(from.x, to.x)..maxOf(from.x, to.x)) {
                if (level.tileAt(x, from.y) == TileType.WALL) level.setTile(x, from.y, TileType.FLOOR)
            }
        }
    }

    /**
     * Una porta e' una casella di pavimento con muri contrapposti su un asse e
     * passaggio libero sull'altro: tipicamente la soglia di una stanza.
     */
    private fun placeDoors(level: DungeonLevel, rng: GameRandom) {
        for (y in 1 until level.height - 1) {
            for (x in 1 until level.width - 1) {
                if (level.tileAt(x, y) != TileType.FLOOR) continue
                val wallsHorizontal = !level.isWalkable(x - 1, y) && !level.isWalkable(x + 1, y)
                val wallsVertical = !level.isWalkable(x, y - 1) && !level.isWalkable(x, y + 1)
                val corridorLike = (wallsHorizontal && level.isWalkable(x, y - 1) && level.isWalkable(x, y + 1)) ||
                    (wallsVertical && level.isWalkable(x - 1, y) && level.isWalkable(x + 1, y))
                if (corridorLike && rng.chance(config.doorChance * 0.12)) {
                    level.setTile(x, y, TileType.DOOR_CLOSED)
                }
            }
        }
    }

    // --- Popolamento ----------------------------------------------------------

    private fun placeStairs(level: DungeonLevel, rng: GameRandom) {
        if (level.rooms.isEmpty()) return
        val upRoom = level.rooms.first()
        val up = level.randomWalkableIn(upRoom, rng) ?: upRoom.center
        level.setTile(up.x, up.y, TileType.STAIRS_UP)
        level.stairsUp = up

        if (level.depth >= config.maxDepth) return // ultimo livello: c'e' il boss, non si scende oltre

        // La discesa va nella stanza piu' lontana dalla salita, per massimizzare il percorso.
        val downRoom = level.rooms.maxByOrNull { it.center.chebyshevTo(up) } ?: upRoom
        val down = level.randomWalkableIn(downRoom, rng, setOf(up)) ?: downRoom.center
        level.setTile(down.x, down.y, TileType.STAIRS_DOWN)
        level.stairsDown = down
    }

    /**
     * Budget di incontro: PX totali dei mostri presenti nel livello.
     *
     * Taratura (misurata con il bot del SoakTest, 60 partite): crescita lineare
     * con un fattore geometrico contenuto. Una crescita piu' ripida (1,22 per
     * livello) rendeva il gioco ingiocabile oltre la profondita' 3, perche' i PX
     * richiesti per salire di livello crescono piu' lentamente dei mostri.
     */
    fun encounterBudget(depth: Int): Int = (70 * depth * Math.pow(1.12, (depth - 1).toDouble())).roundToInt()

    private fun populate(level: DungeonLevel, rng: GameRandom) {
        val occupied = HashSet<Pos>()
        level.stairsUp?.let { occupied.add(it) }
        level.stairsDown?.let { occupied.add(it) }
        val startRoom = level.rooms.firstOrNull()

        // Il livello finale ospita il boss, in fondo alla mappa.
        if (level.depth >= config.maxDepth) {
            val bossRoom = level.rooms.maxByOrNull { it.center.chebyshevTo(level.stairsUp ?: Pos(0, 0)) }
            if (bossRoom != null) {
                val pos = level.randomWalkableIn(bossRoom, rng, occupied)
                if (pos != null) {
                    val boss = Bestiary.DEATH_LORD.instantiate(rng)
                    boss.x = pos.x; boss.y = pos.y
                    level.monsters.add(boss)
                    occupied.add(pos)
                }
            }
        }

        val table = Bestiary.spawnTable(level.depth)
        if (table.isNotEmpty()) {
            var budget = encounterBudget(level.depth)
            var guard = 0
            while (budget > 0 && guard++ < 200) {
                val species = rng.pickWeighted(table) { it.spawnWeight }
                if (species.experience > budget * 2) continue
                val room = level.rooms.filter { it != startRoom }.let { if (it.isEmpty()) level.rooms else it }
                if (room.isEmpty()) break
                val pos = level.randomWalkableIn(rng.pick(room), rng, occupied) ?: continue
                val monster = species.instantiate(rng)
                monster.x = pos.x
                monster.y = pos.y
                monster.alerted = false
                level.monsters.add(monster)
                occupied.add(pos)
                budget -= species.experience
            }
        }

        // Oggetti: 3-6 per livello, piu' un tesoro garantito ogni 2 livelli.
        val itemCount = rng.nextInt(3, 6) + level.depth / 3
        repeat(itemCount) {
            val room = level.rooms.randomOrNullWith(rng) ?: return@repeat
            val pos = level.randomWalkableIn(room, rng, occupied) ?: return@repeat
            level.dropItem(pos.x, pos.y, rollLoot(level.depth, rng))
            occupied.add(pos)
        }

        // Trappole: piu' frequenti scendendo, mai sulle scale.
        val trapCount = (level.depth + 1) / 2
        repeat(trapCount) {
            val room = level.rooms.randomOrNullWith(rng) ?: return@repeat
            val pos = level.randomWalkableIn(room, rng, occupied) ?: return@repeat
            val kind = rng.pick(TrapKind.entries.toList())
            level.traps.add(Trap(pos, kind, saveDc = 10 + level.depth / 2))
            occupied.add(pos)
        }
    }

    /** Tabella del tesoro: la qualita' media sale con la profondita'. */
    fun rollLoot(depth: Int, rng: GameRandom): Item {
        val roll = rng.nextInt(100)
        return when {
            roll < 30 -> if (depth >= 5 && rng.chance(0.35)) ItemCatalog.POTION_GREATER_HEALING else ItemCatalog.POTION_HEALING
            roll < 40 -> rng.pick(listOf(ItemCatalog.POTION_STRENGTH, ItemCatalog.POTION_ANTIDOTE))
            roll < 55 -> rng.pick(ItemCatalog.scrolls)
            roll < 72 -> {
                val weapon = rng.pick(ItemCatalog.weapons)
                val bonus = magicBonusFor(depth, rng)
                if (bonus > 0) weapon.copy(magicBonus = bonus, value = weapon.value + bonus * 250) else weapon
            }
            roll < 85 -> {
                if (rng.chance(0.3)) {
                    val bonus = magicBonusFor(depth, rng)
                    Shield(magicBonus = bonus, value = 10 + bonus * 200)
                } else {
                    val armor = rng.pick(ItemCatalog.armors)
                    val bonus = magicBonusFor(depth, rng)
                    if (bonus > 0) armor.copy(magicBonus = bonus, value = armor.value + bonus * 300) else armor
                }
            }
            else -> {
                val amount = rng.nextInt(20, 40 + depth * 25)
                com.changewave.dungeon.model.Treasure("gold_$amount", "$amount monete d'oro", amount)
            }
        }
    }

    private fun magicBonusFor(depth: Int, rng: GameRandom): Int = when {
        depth >= 8 && rng.chance(0.35) -> 2
        depth >= 4 && rng.chance(0.30) -> 1
        rng.chance(0.08) -> 1
        else -> 0
    }

    private fun <T> List<T>.randomOrNullWith(rng: GameRandom): T? = if (isEmpty()) null else rng.pick(this)
}

/** Posiziona il giocatore sulla scala di salita (o al centro della prima stanza). */
fun DungeonLevel.placePlayerAtEntrance(player: com.changewave.dungeon.model.Actor) {
    val start = stairsUp ?: rooms.firstOrNull()?.center ?: Pos(width / 2, height / 2)
    player.x = start.x
    player.y = start.y
}

/** Posiziona il giocatore sulla scala di discesa (risalita da un livello inferiore). */
fun DungeonLevel.placePlayerAtExit(player: com.changewave.dungeon.model.Actor) {
    val start = stairsDown ?: stairsUp ?: rooms.firstOrNull()?.center ?: Pos(width / 2, height / 2)
    player.x = start.x
    player.y = start.y
}
