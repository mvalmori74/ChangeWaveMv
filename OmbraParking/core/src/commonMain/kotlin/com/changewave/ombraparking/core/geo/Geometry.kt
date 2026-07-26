package com.changewave.ombraparking.core.geo

import com.changewave.ombraparking.core.math.toRadians

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Punto geografico in gradi decimali (WGS84). */
data class LatLng(val latitude: Double, val longitude: Double)

/**
 * Vettore nel piano tangente locale: [x] verso est, [y] verso nord, in metri.
 * Tutta la geometria delle ombre lavora in questo sistema, non in gradi.
 */
data class Vec2(val x: Double, val y: Double) {
    operator fun plus(other: Vec2) = Vec2(x + other.x, y + other.y)
    operator fun minus(other: Vec2) = Vec2(x - other.x, y - other.y)
    operator fun times(scale: Double) = Vec2(x * scale, y * scale)

    fun dot(other: Vec2) = x * other.x + y * other.y

    /** Prodotto vettoriale 2D (componente z). Positivo se [other] è a sinistra di questo vettore. */
    fun cross(other: Vec2) = x * other.y - y * other.x

    val length: Double get() = hypot(x, y)

    fun normalized(): Vec2 {
        val l = length
        return if (l < 1e-12) ZERO else Vec2(x / l, y / l)
    }

    companion object {
        val ZERO = Vec2(0.0, 0.0)
    }
}

/** Punto 3D locale: est, nord, quota rispetto al piano stradale (metri). */
data class Vec3(val east: Double, val north: Double, val up: Double)

/**
 * Piano tangente locale centrato su [origin]: converte lat/lon in metri est/nord.
 *
 * Usa la lunghezza del grado di latitudine/longitudine alla latitudine dell'origine.
 * L'errore resta sotto il decimetro entro qualche chilometro dall'origine, più che
 * sufficiente per ragionare su ombre di edifici.
 */
class LocalPlane(val origin: LatLng) {

    private val latRad = (origin.latitude).toRadians()

    /** Metri per grado di latitudine (serie di Taylor sull'ellissoide WGS84). */
    val metersPerDegreeLatitude: Double =
        111132.92 - 559.82 * cos(2 * latRad) + 1.175 * cos(4 * latRad) - 0.0023 * cos(6 * latRad)

    /** Metri per grado di longitudine alla latitudine dell'origine. */
    val metersPerDegreeLongitude: Double =
        (111412.84 * cos(latRad) - 93.5 * cos(3 * latRad) + 0.118 * cos(5 * latRad))
            .coerceAtLeast(1e-6)

    fun toLocal(point: LatLng): Vec2 = Vec2(
        x = (point.longitude - origin.longitude) * metersPerDegreeLongitude,
        y = (point.latitude - origin.latitude) * metersPerDegreeLatitude,
    )

    fun toLatLng(point: Vec2): LatLng = LatLng(
        latitude = origin.latitude + point.y / metersPerDegreeLatitude,
        longitude = origin.longitude + point.x / metersPerDegreeLongitude,
    )

    fun distanceMeters(a: LatLng, b: LatLng): Double = (toLocal(a) - toLocal(b)).length
}

/** Operazioni su poligoni semplici (lista di vertici, non ripetere il primo in coda). */
object Polygons {

    private const val EPS = 1e-9

    /** Area con segno: positiva se i vertici sono in senso antiorario. */
    fun signedArea(polygon: List<Vec2>): Double {
        if (polygon.size < 3) return 0.0
        var sum = 0.0
        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[(i + 1) % polygon.size]
            sum += a.cross(b)
        }
        return sum / 2.0
    }

    /**
     * Restituisce il poligono orientato in senso antiorario.
     * Serve per il riempimento "non-zero winding": se le parti d'ombra hanno versi
     * opposti le sovrapposizioni si cancellano lasciando buchi nel disegno.
     */
    fun ensureCounterClockwise(polygon: List<Vec2>): List<Vec2> =
        if (signedArea(polygon) < 0) polygon.reversed() else polygon

    fun centroid(polygon: List<Vec2>): Vec2 {
        if (polygon.isEmpty()) return Vec2.ZERO
        val area = signedArea(polygon)
        if (abs(area) < EPS) {
            // Poligono degenere: media dei vertici.
            var sx = 0.0
            var sy = 0.0
            polygon.forEach { sx += it.x; sy += it.y }
            return Vec2(sx / polygon.size, sy / polygon.size)
        }
        var cx = 0.0
        var cy = 0.0
        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[(i + 1) % polygon.size]
            val w = a.cross(b)
            cx += (a.x + b.x) * w
            cy += (a.y + b.y) * w
        }
        return Vec2(cx / (6 * area), cy / (6 * area))
    }

    /** Test di appartenenza con il metodo del raggio (ray casting). */
    fun contains(polygon: List<Vec2>, point: Vec2): Boolean {
        if (polygon.size < 3) return false
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[j]
            val straddles = (a.y > point.y) != (b.y > point.y)
            if (straddles) {
                val xCross = a.x + (point.y - a.y) / (b.y - a.y) * (b.x - a.x)
                if (point.x < xCross) inside = !inside
            }
            j = i
        }
        return inside
    }

    /**
     * Intervallo di distanze `[entrata, uscita]` in cui la semiretta `origin + t * direction`
     * (con [direction] normalizzata) attraversa il poligono, oppure `null` se non lo tocca.
     * Se l'origine è dentro il poligono l'entrata vale 0.
     */
    fun rayInterval(origin: Vec2, direction: Vec2, polygon: List<Vec2>): ClosedFloatingPointRange<Double>? {
        if (polygon.size < 3) return null
        var minT = Double.POSITIVE_INFINITY
        var maxT = Double.NEGATIVE_INFINITY
        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[(i + 1) % polygon.size]
            val edge = b - a
            val denom = direction.cross(edge)
            if (abs(denom) < EPS) continue // raggio parallelo al lato
            val w = a - origin
            val t = w.cross(edge) / denom
            val s = w.cross(direction) / denom
            if (t >= 0.0 && s >= -EPS && s <= 1.0 + EPS) {
                if (t < minT) minT = t
                if (t > maxT) maxT = t
            }
        }
        if (maxT < 0.0) {
            // Nessun lato attraversato davanti all'origine: può comunque essere interna
            // a un poligono che il raggio non riattraversa (caso degenere).
            return if (contains(polygon, origin)) 0.0..0.0 else null
        }
        if (contains(polygon, origin)) minT = 0.0
        if (minT > maxT) return null
        return minT..maxT
    }

    /** Approssima un cerchio (chioma di un albero, rotonda...) con un poligono regolare. */
    fun circle(center: Vec2, radius: Double, segments: Int = 10): List<Vec2> =
        (0 until segments).map { i ->
            val angle = 2 * PI * i / segments
            Vec2(center.x + radius * cos(angle), center.y + radius * sin(angle))
        }

    /** Raggio della circonferenza centrata in [center] che contiene tutto il poligono. */
    fun boundingRadius(polygon: List<Vec2>, center: Vec2): Double =
        polygon.maxOfOrNull { (it - center).length } ?: 0.0
}
