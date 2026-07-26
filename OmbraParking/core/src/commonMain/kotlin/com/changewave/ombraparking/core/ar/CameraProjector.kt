package com.changewave.ombraparking.core.ar

import com.changewave.ombraparking.core.math.toRadians

import com.changewave.ombraparking.core.geo.Vec2
import com.changewave.ombraparking.core.geo.Vec3
import kotlin.math.tan

/** Punto proiettato sullo schermo, con la distanza dalla camera in metri. */
data class ScreenPoint(val x: Float, val y: Float, val distanceMeters: Double)

/**
 * Punto nel sistema del dispositivo: [x] a destra dello schermo, [y] verso il bordo alto,
 * [z] uscente dallo schermo (la camera posteriore guarda lungo `-z`).
 */
data class DeviceVec(val x: Double, val y: Double, val z: Double) {
    /** Distanza davanti alla camera. */
    val depth: Double get() = -z
}

/**
 * Proiezione prospettica dei punti del mondo sullo schermo, per la vista in realtà aumentata.
 *
 * Non serve ARCore: l'app deve solo sovrapporre poligoni ancorati al suolo, e per quello
 * bastano l'orientamento del telefono (sensore di rotazione) e il campo visivo della camera.
 *
 * Il sistema del mondo è ENU (est / nord / alto) con origine sull'osservatore e quota 0
 * all'altezza della camera.
 *
 * @param deviceToWorld matrice 3x3 row-major che porta un vettore dal sistema del dispositivo
 *   a quello del mondo: è esattamente la matrice di `SensorManager.getRotationMatrix`,
 *   eventualmente già rimappata per la rotazione del display.
 */
class CameraProjector(
    val viewportWidthPx: Float,
    val viewportHeightPx: Float,
    horizontalFovDegrees: Double,
    verticalFovDegrees: Double,
    private val deviceToWorld: DoubleArray,
) {

    init {
        require(deviceToWorld.size >= 9) { "serve una matrice di rotazione 3x3 (9 valori)" }
    }

    private val tanHalfHorizontal = tan((horizontalFovDegrees / 2.0).toRadians())
    private val tanHalfVertical = tan((verticalFovDegrees / 2.0).toRadians())

    /** Trasforma un punto dal mondo al sistema del dispositivo (trasposta della matrice). */
    fun toDevice(world: Vec3): DeviceVec = DeviceVec(
        x = deviceToWorld[0] * world.east + deviceToWorld[3] * world.north + deviceToWorld[6] * world.up,
        y = deviceToWorld[1] * world.east + deviceToWorld[4] * world.north + deviceToWorld[7] * world.up,
        z = deviceToWorld[2] * world.east + deviceToWorld[5] * world.north + deviceToWorld[8] * world.up,
    )

    /** Direzione nel mondo verso cui punta la camera posteriore. */
    fun viewDirection(): Vec3 = Vec3(-deviceToWorld[2], -deviceToWorld[5], -deviceToWorld[8])

    /** Proietta un punto del mondo; null se è dietro la camera o troppo vicino. */
    fun project(world: Vec3): ScreenPoint? = projectDevice(toDevice(world))

    /**
     * Proietta un poligono del mondo tagliandolo sul piano di clipping vicino, così le
     * sagome che passano sotto ai piedi dell'utente non si ribaltano sullo schermo.
     * Restituisce lista vuota se il poligono è interamente fuori campo.
     */
    fun projectPolygon(world: List<Vec3>): List<ScreenPoint> {
        if (world.size < 3) return emptyList()
        val clipped = clipToNearPlane(world.map { toDevice(it) })
        if (clipped.size < 3) return emptyList()
        // Dopo il taglio ogni vertice è al più sul piano vicino: la proiezione è sempre definita.
        return clipped.map { projectClipped(it) }
    }

    /**
     * Punto al suolo inquadrato dal centro dello schermo, in coordinate locali (est, nord).
     * Null se la camera guarda verso l'alto o se il punto cade oltre [maxDistanceMeters].
     *
     * @param eyeHeightMeters altezza da terra della camera.
     */
    fun groundIntersection(eyeHeightMeters: Double, maxDistanceMeters: Double = 120.0): Vec2? {
        val direction = viewDirection()
        if (direction.up >= -1e-3) return null // la camera non guarda verso il basso
        val t = -eyeHeightMeters / direction.up
        if (t <= 0 || t > maxDistanceMeters) return null
        return Vec2(direction.east * t, direction.north * t)
    }

    private fun projectDevice(device: DeviceVec): ScreenPoint? =
        if (device.depth < NEAR_PLANE_METERS) null else projectClipped(device)

    private fun projectClipped(device: DeviceVec): ScreenPoint {
        val depth = device.depth
        val ndcX = (device.x / depth) / tanHalfHorizontal
        val ndcY = (device.y / depth) / tanHalfVertical
        return ScreenPoint(
            x = ((0.5 + 0.5 * ndcX) * viewportWidthPx).toFloat(),
            y = ((0.5 - 0.5 * ndcY) * viewportHeightPx).toFloat(),
            distanceMeters = depth,
        )
    }

    /** Sutherland–Hodgman su un solo piano: tiene la parte con profondità > NEAR. */
    private fun clipToNearPlane(polygon: List<DeviceVec>): List<DeviceVec> {
        val result = ArrayList<DeviceVec>(polygon.size + 2)
        for (i in polygon.indices) {
            val current = polygon[i]
            val next = polygon[(i + 1) % polygon.size]
            val currentInside = current.depth > NEAR_PLANE_METERS
            val nextInside = next.depth > NEAR_PLANE_METERS
            if (currentInside) result += current
            if (currentInside != nextInside) {
                val t = (current.depth - NEAR_PLANE_METERS) / (current.depth - next.depth)
                result += DeviceVec(
                    x = current.x + (next.x - current.x) * t,
                    y = current.y + (next.y - current.y) * t,
                    z = current.z + (next.z - current.z) * t,
                )
            }
        }
        return result
    }

    companion object {
        /** Sotto questa distanza i punti vengono tagliati (m). */
        const val NEAR_PLANE_METERS = 0.30
    }
}
