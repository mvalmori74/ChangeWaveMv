package com.changewave.dungeon.dungeon

import com.changewave.dungeon.model.Actor
import com.changewave.dungeon.model.Item
import com.changewave.dungeon.model.Monster
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Un livello del dungeon: mappa, mostri, oggetti a terra, trappole.
 *
 * La mappa e' un array piatto width*height; l'accesso avviene solo tramite gli
 * helper di questa classe, che validano i limiti (fuori mappa = muro).
 */
@Serializable
class DungeonLevel(
    val depth: Int,
    val width: Int,
    val height: Int,
    val tiles: MutableList<TileType>,
    val rooms: List<Rect> = emptyList(),
    val monsters: MutableList<Monster> = mutableListOf(),
    val items: MutableList<ItemOnGround> = mutableListOf(),
    val traps: MutableList<Trap> = mutableListOf(),
    var stairsDown: Pos? = null,
    var stairsUp: Pos? = null,
    /** Caselle gia' viste dal giocatore (memoria della mappa). */
    val explored: MutableList<Boolean> = MutableList(width * height) { false },
) {

    /** Campo visivo corrente: ricalcolato ad ogni turno, non fa parte del salvataggio. */
    @Transient
    var visible: BooleanArray = BooleanArray(width * height)

    private fun index(x: Int, y: Int) = y * width + x

    fun inBounds(x: Int, y: Int): Boolean = x in 0 until width && y in 0 until height

    fun tileAt(x: Int, y: Int): TileType = if (inBounds(x, y)) tiles[index(x, y)] else TileType.WALL

    fun tileAt(pos: Pos): TileType = tileAt(pos.x, pos.y)

    fun setTile(x: Int, y: Int, type: TileType) {
        if (inBounds(x, y)) tiles[index(x, y)] = type
    }

    fun isWalkable(x: Int, y: Int): Boolean = tileAt(x, y).walkable

    fun isTransparent(x: Int, y: Int): Boolean = tileAt(x, y).transparent

    fun isExplored(x: Int, y: Int): Boolean = inBounds(x, y) && explored[index(x, y)]

    fun markExplored(x: Int, y: Int) {
        if (inBounds(x, y)) explored[index(x, y)] = true
    }

    fun isVisible(x: Int, y: Int): Boolean = inBounds(x, y) && visible[index(x, y)]

    fun setVisible(x: Int, y: Int, value: Boolean) {
        if (inBounds(x, y)) visible[index(x, y)] = value
    }

    fun clearVisibility() {
        visible = BooleanArray(width * height)
    }

    fun monsterAt(x: Int, y: Int): Monster? = monsters.firstOrNull { it.x == x && it.y == y && it.hitPoints > 0 }

    fun trapAt(x: Int, y: Int): Trap? = traps.firstOrNull { it.pos.x == x && it.pos.y == y }

    fun itemsAt(x: Int, y: Int): List<Item> = items.filter { it.pos.x == x && it.pos.y == y }.map { it.item }

    fun removeItemAt(x: Int, y: Int): Item? {
        val idx = items.indexOfFirst { it.pos.x == x && it.pos.y == y }
        if (idx < 0) return null
        return items.removeAt(idx).item
    }

    fun dropItem(x: Int, y: Int, item: Item) {
        items.add(ItemOnGround(Pos(x, y), item))
    }

    /** Casella libera per muoversi/spawnare: calpestabile e senza altri attori. */
    fun isPassableFor(actor: Actor, x: Int, y: Int, blockingActors: List<Actor>): Boolean {
        if (!isWalkable(x, y)) return false
        return blockingActors.none { it !== actor && it.hitPoints > 0 && it.x == x && it.y == y }
    }

    fun randomWalkableIn(room: Rect, rng: com.changewave.dungeon.rules.GameRandom, occupied: Set<Pos> = emptySet()): Pos? {
        val candidates = room.positions().filter { isWalkable(it.x, it.y) && it !in occupied }
        if (candidates.isEmpty()) return null
        return rng.pick(candidates)
    }

    /** Tutte le posizioni calpestabili della mappa (usata dai test di connettivita'). */
    fun walkablePositions(): List<Pos> = buildList {
        for (y in 0 until height) for (x in 0 until width) if (isWalkable(x, y)) add(Pos(x, y))
    }

    companion object {
        fun filled(depth: Int, width: Int, height: Int, type: TileType = TileType.WALL): DungeonLevel =
            DungeonLevel(depth, width, height, MutableList(width * height) { type })
    }
}
