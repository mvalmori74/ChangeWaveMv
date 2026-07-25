package com.changewave.ombraparking.core.shadow

import com.changewave.ombraparking.core.geo.Polygons
import com.changewave.ombraparking.core.geo.Vec2
import com.changewave.ombraparking.core.sun.SunPosition
import kotlin.math.tan

/** Quanto è illuminato un punto. */
enum class ShadeQuality {
    /** Sole sotto l'orizzonte. */
    NIGHT,

    /** Ombra piena di un edificio o di un muro. */
    SHADE,

    /** Ombra filtrata da una chioma. */
    DAPPLED,

    /** Pieno sole. */
    SUN;

    val isShaded: Boolean get() = this == SHADE || this == DAPPLED
}

/** Esito della verifica su un singolo punto. */
data class ShadeInfo(
    val quality: ShadeQuality,
    val sun: SunPosition,
    /** Ostacolo che genera l'ombra, se c'è. */
    val obstacle: Obstacle? = null,
)

/**
 * Il cuore dell'app: dato il sole e gli ostacoli intorno, dice se un punto è in ombra
 * e produce le sagome d'ombra da disegnare su mappa e in realtà aumentata.
 */
object ShadowEngine {

    /**
     * Un punto è in ombra se il raggio che lo collega al sole entra in un volume.
     *
     * Il raggio parte dal suolo e sale con pendenza `tan(elevazione)`: entrando
     * nell'impronta a distanza `d` si trova a quota `d * tan(elevazione)`. C'è
     * intercettazione se quella quota resta dentro la fascia verticale dell'ostacolo
     * mentre il raggio attraversa l'impronta.
     */
    fun shadeAt(point: Vec2, obstacles: List<Obstacle>, sun: SunPosition): ShadeInfo {
        if (!sun.isAboveHorizon) return ShadeInfo(ShadeQuality.NIGHT, sun)

        val toSun = sun.horizontalDirection()
        val slope = tan(Math.toRadians(sun.elevationDegrees))
        var best: Obstacle? = null
        var bestOpacity = 0.0

        for (obstacle in obstacles) {
            // Scarto rapido: l'ostacolo deve stare dalla parte del sole e a portata d'ombra.
            val toCenter = obstacle.center - point
            val maxReach = sun.shadowLength(obstacle.heightMeters) + obstacle.boundingRadius
            if (toCenter.length > maxReach) continue
            if (toCenter.dot(toSun) < -obstacle.boundingRadius) continue

            val interval = Polygons.rayInterval(point, toSun, obstacle.footprint) ?: continue
            val heightAtEntry = interval.start * slope
            val heightAtExit = interval.endInclusive * slope
            val blocks = heightAtEntry <= obstacle.heightMeters &&
                heightAtExit >= obstacle.baseHeightMeters
            if (!blocks) continue

            if (obstacle.opacity > bestOpacity) {
                best = obstacle
                bestOpacity = obstacle.opacity
            }
            if (bestOpacity >= 1.0) break // ombra piena: non serve cercare oltre
        }

        val quality = when {
            best == null -> ShadeQuality.SUN
            bestOpacity >= 1.0 -> ShadeQuality.SHADE
            else -> ShadeQuality.DAPPLED
        }
        return ShadeInfo(quality, sun, best)
    }

    /**
     * Sagoma dell'ombra di un ostacolo, come insieme di poligoni che vanno disegnati
     * insieme (la loro unione è l'ombra reale).
     *
     * È la somma di Minkowski dell'impronta con il segmento che va dall'ombra della base
     * a quella della sommità: l'impronta traslata due volte più i quadrilateri generati
     * dallo scorrimento di ogni lato. Per un edificio la traslazione della base è nulla,
     * quindi la sagoma include anche il sedime.
     */
    fun shadowShape(obstacle: Obstacle, sun: SunPosition): List<List<Vec2>> {
        if (!sun.isAboveHorizon || obstacle.footprint.size < 3) return emptyList()

        val direction = sun.shadowDirection()
        val near = direction * sun.shadowLength(obstacle.baseHeightMeters)
        val far = direction * sun.shadowLength(obstacle.heightMeters)
        if ((far - near).length < 0.05) return emptyList()

        val footprint = Polygons.ensureCounterClockwise(obstacle.footprint)
        val parts = ArrayList<List<Vec2>>(footprint.size + 2)
        parts += footprint.map { it + near }
        parts += footprint.map { it + far }

        for (i in footprint.indices) {
            val a = footprint[i]
            val b = footprint[(i + 1) % footprint.size]
            val quad = listOf(a + near, b + near, b + far, a + far)
            parts += Polygons.ensureCounterClockwise(quad)
        }
        return parts
    }

    /** Sagome d'ombra di tutti gli ostacoli, pronte per essere riempite in un colpo solo. */
    fun shadowShapes(obstacles: List<Obstacle>, sun: SunPosition): List<List<Vec2>> =
        obstacles.flatMap { shadowShape(it, sun) }
}
