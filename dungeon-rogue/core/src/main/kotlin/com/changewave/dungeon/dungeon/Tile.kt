package com.changewave.dungeon.dungeon

import kotlinx.serialization.Serializable

enum class TileType(val glyph: Char, val walkable: Boolean, val transparent: Boolean) {
    WALL('#', walkable = false, transparent = false),
    FLOOR('.', walkable = true, transparent = true),
    DOOR_CLOSED('+', walkable = false, transparent = false),
    DOOR_OPEN('\'', walkable = true, transparent = true),
    STAIRS_DOWN('>', walkable = true, transparent = true),
    STAIRS_UP('<', walkable = true, transparent = true),
    RUBBLE(',', walkable = true, transparent = true);
}

@Serializable
data class Pos(val x: Int, val y: Int) {
    operator fun plus(other: Pos) = Pos(x + other.x, y + other.y)
    fun chebyshevTo(other: Pos): Int =
        maxOf(kotlin.math.abs(x - other.x), kotlin.math.abs(y - other.y))
}

/** Rettangolo inclusivo usato dal generatore BSP. */
@Serializable
data class Rect(val x1: Int, val y1: Int, val x2: Int, val y2: Int) {
    val width: Int get() = x2 - x1 + 1
    val height: Int get() = y2 - y1 + 1
    val center: Pos get() = Pos((x1 + x2) / 2, (y1 + y2) / 2)
    val area: Int get() = width * height

    fun contains(x: Int, y: Int): Boolean = x in x1..x2 && y in y1..y2

    fun intersects(other: Rect): Boolean =
        x1 <= other.x2 && x2 >= other.x1 && y1 <= other.y2 && y2 >= other.y1

    fun positions(): List<Pos> = (y1..y2).flatMap { y -> (x1..x2).map { x -> Pos(x, y) } }
}

enum class TrapKind(val italian: String) {
    DART("Trappola a dardi"),
    PIT("Fossa"),
    POISON_GAS("Gas velenoso"),
    ALARM("Allarme");
}

@Serializable
data class Trap(
    val pos: Pos,
    val kind: TrapKind,
    val saveDc: Int,
    var discovered: Boolean = false,
    var triggered: Boolean = false,
)

@Serializable
data class ItemOnGround(val pos: Pos, val item: com.changewave.dungeon.model.Item)
