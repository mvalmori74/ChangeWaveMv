package com.changewave.dungeon.dungeon

/**
 * Campo visivo con recursive shadowcasting (Bjorn Bergstrom), 8 ottanti.
 *
 * Complessita' O(r^2) sulle sole caselle dentro il raggio: adatto ad essere
 * ricalcolato ad ogni turno anche su dispositivi mobili di fascia bassa.
 */
object FieldOfView {

    private val MULTIPLIERS = arrayOf(
        intArrayOf(1, 0, 0, -1, -1, 0, 0, 1),
        intArrayOf(0, 1, -1, 0, 0, -1, 1, 0),
        intArrayOf(0, 1, 1, 0, 0, -1, -1, 0),
        intArrayOf(1, 0, 0, 1, -1, 0, 0, -1),
    )

    /**
     * Calcola la visibilita' dal punto ([originX], [originY]) entro [radius] caselle
     * e la scrive su [level] (visible + explored).
     */
    fun compute(level: DungeonLevel, originX: Int, originY: Int, radius: Int) {
        level.clearVisibility()
        level.setVisible(originX, originY, true)
        level.markExplored(originX, originY)
        for (octant in 0 until 8) {
            castLight(
                level, originX, originY, radius,
                row = 1, startSlope = 1.0, endSlope = 0.0,
                xx = MULTIPLIERS[0][octant], xy = MULTIPLIERS[1][octant],
                yx = MULTIPLIERS[2][octant], yy = MULTIPLIERS[3][octant],
            )
        }
    }

    private fun castLight(
        level: DungeonLevel,
        cx: Int,
        cy: Int,
        radius: Int,
        row: Int,
        startSlope: Double,
        endSlope: Double,
        xx: Int,
        xy: Int,
        yx: Int,
        yy: Int,
    ) {
        var start = startSlope
        if (start < endSlope) return
        val radius2 = radius * radius
        var nextStart = start
        var blocked = false

        for (distance in row..radius) {
            if (blocked) break
            var deltaY = -distance
            for (deltaX in -distance..0) {
                deltaY = -distance
                val currentX = cx + deltaX * xx + deltaY * xy
                val currentY = cy + deltaX * yx + deltaY * yy
                val leftSlope = (deltaX - 0.5) / (deltaY + 0.5)
                val rightSlope = (deltaX + 0.5) / (deltaY - 0.5)

                if (rightSlope > start) continue
                if (leftSlope < endSlope) break

                if (deltaX * deltaX + deltaY * deltaY <= radius2 && level.inBounds(currentX, currentY)) {
                    level.setVisible(currentX, currentY, true)
                    level.markExplored(currentX, currentY)
                }

                val opaque = !level.isTransparent(currentX, currentY)
                if (blocked) {
                    if (opaque) {
                        nextStart = rightSlope
                    } else {
                        blocked = false
                        start = nextStart
                    }
                } else if (opaque && distance < radius) {
                    blocked = true
                    castLight(level, cx, cy, radius, distance + 1, start, leftSlope, xx, xy, yx, yy)
                    nextStart = rightSlope
                }
            }
        }
    }

    /**
     * Linea di vista puntuale (Bresenham): usata dall'IA dei mostri, che non ha
     * bisogno del campo visivo completo ma solo di sapere se vede il bersaglio.
     */
    fun hasLineOfSight(level: DungeonLevel, x0: Int, y0: Int, x1: Int, y1: Int, maxDistance: Int = Int.MAX_VALUE): Boolean {
        if (Pos(x0, y0).chebyshevTo(Pos(x1, y1)) > maxDistance) return false
        var x = x0
        var y = y0
        val dx = kotlin.math.abs(x1 - x0)
        val dy = -kotlin.math.abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1
        val sy = if (y0 < y1) 1 else -1
        var err = dx + dy
        while (true) {
            if (x == x1 && y == y1) return true
            if (!(x == x0 && y == y0) && !level.isTransparent(x, y)) return false
            val e2 = 2 * err
            if (e2 >= dy) { err += dy; x += sx }
            if (e2 <= dx) { err += dx; y += sy }
        }
    }
}
