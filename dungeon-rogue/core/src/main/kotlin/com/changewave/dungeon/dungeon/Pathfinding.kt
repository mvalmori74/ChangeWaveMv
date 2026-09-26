package com.changewave.dungeon.dungeon

import java.util.PriorityQueue

/** A* su griglia a 8 direzioni, senza taglio degli angoli attraverso i muri. */
object Pathfinding {

    val DIRECTIONS: List<Pos> = listOf(
        Pos(0, -1), Pos(1, -1), Pos(1, 0), Pos(1, 1),
        Pos(0, 1), Pos(-1, 1), Pos(-1, 0), Pos(-1, -1),
    )

    private data class Node(val pos: Pos, val f: Int)

    /** Costo in turni per attraversare una porta chiusa: uno per aprirla, uno per entrare. */
    private const val DOOR_COST = 2

    /**
     * Percorso da [start] a [goal] escluso [start]; lista vuota se irraggiungibile.
     *
     * [blocked] marca le caselle occupate da altre creature (attraversabili solo
     * se coincidono con il goal, cosi' un mostro puo' comunque puntare al bersaglio).
     *
     * Le porte chiuse sono attraversabili ma costano di piu': sia il giocatore sia
     * i mostri le aprono spendendo un turno. Trattarle come muri spezzerebbe la
     * mappa in regioni isolate, benche' il dungeon sia generato connesso.
     */
    fun findPath(
        level: DungeonLevel,
        start: Pos,
        goal: Pos,
        blocked: Set<Pos> = emptySet(),
        maxNodes: Int = 2000,
        doorsPassable: Boolean = true,
    ): List<Pos> {
        if (start == goal) return emptyList()
        val open = PriorityQueue<Node>(compareBy { it.f })
        val cameFrom = HashMap<Pos, Pos>()
        val gScore = HashMap<Pos, Int>()
        gScore[start] = 0
        open.add(Node(start, heuristic(start, goal)))
        var expanded = 0

        while (open.isNotEmpty()) {
            val current = open.poll().pos
            if (current == goal) return reconstruct(cameFrom, current)
            if (++expanded > maxNodes) return emptyList()

            for (dir in DIRECTIONS) {
                val next = Pos(current.x + dir.x, current.y + dir.y)
                val tile = level.tileAt(next.x, next.y)
                val closedDoor = tile == TileType.DOOR_CLOSED && doorsPassable
                if (!tile.walkable && !closedDoor) continue
                if (next != goal && next in blocked) continue
                // Niente diagonali che "tagliano" uno spigolo tra due muri; una porta
                // chiusa si attraversa solo in linea retta (come nelle regole di movimento).
                if (dir.x != 0 && dir.y != 0) {
                    if (closedDoor) continue
                    if (!passable(level, current.x + dir.x, current.y, doorsPassable) &&
                        !passable(level, current.x, current.y + dir.y, doorsPassable)
                    ) continue
                }
                val tentative = (gScore[current] ?: Int.MAX_VALUE) + if (closedDoor) DOOR_COST else 1
                if (tentative < (gScore[next] ?: Int.MAX_VALUE)) {
                    cameFrom[next] = current
                    gScore[next] = tentative
                    open.add(Node(next, tentative + heuristic(next, goal)))
                }
            }
        }
        return emptyList()
    }

    /** Insieme delle caselle raggiungibili da [start] (flood fill, per i test di connettivita'). */
    fun reachable(level: DungeonLevel, start: Pos): Set<Pos> {
        val seen = HashSet<Pos>()
        val stack = ArrayDeque<Pos>()
        stack.addLast(start)
        seen.add(start)
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            for (dir in DIRECTIONS) {
                val next = Pos(current.x + dir.x, current.y + dir.y)
                if (next in seen) continue
                val tile = level.tileAt(next.x, next.y)
                // Le porte chiuse sono apribili, quindi contano come attraversabili.
                if (!tile.walkable && tile != TileType.DOOR_CLOSED) continue
                seen.add(next)
                stack.addLast(next)
            }
        }
        return seen
    }

    private fun passable(level: DungeonLevel, x: Int, y: Int, doorsPassable: Boolean): Boolean {
        val tile = level.tileAt(x, y)
        return tile.walkable || (doorsPassable && tile == TileType.DOOR_CLOSED)
    }

    private fun heuristic(a: Pos, b: Pos): Int = a.chebyshevTo(b)

    private fun reconstruct(cameFrom: Map<Pos, Pos>, goal: Pos): List<Pos> {
        val path = ArrayDeque<Pos>()
        var current = goal
        while (cameFrom.containsKey(current)) {
            path.addFirst(current)
            current = cameFrom.getValue(current)
        }
        return path.toList()
    }
}
