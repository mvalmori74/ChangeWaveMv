package com.changewave.ombraparking.core.osm

import com.changewave.ombraparking.core.geo.LatLng
import com.changewave.ombraparking.core.geo.LocalPlane
import com.changewave.ombraparking.core.geo.Polygons
import com.changewave.ombraparking.core.geo.Vec2
import com.changewave.ombraparking.core.shadow.Obstacle
import com.changewave.ombraparking.core.shadow.ObstacleKind
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Costruisce le query Overpass QL per gli ostacoli intorno a un punto. */
object OverpassQuery {

    /**
     * Sei decimali, senza dipendere dalla formattazione di piattaforma: `String.format` non
     * esiste nel codice condiviso, e la virgola di alcune impostazioni locali romperebbe la
     * query.
     */
    private fun Double.toFixed6(): String {
        val scaled = kotlin.math.round(this * 1_000_000.0).toLong()
        val sign = if (scaled < 0) "-" else ""
        val absolute = kotlin.math.abs(scaled)
        val whole = absolute / 1_000_000
        val fraction = absolute % 1_000_000
        return sign + whole.toString() + "." + fraction.toString().padStart(6, '0')
    }


    /**
     * Edifici, alberi e muri entro [radiusMeters] dal centro.
     *
     * `out geom` fa restituire le coordinate dei vertici direttamente dentro ogni way,
     * evitando un secondo giro per risolvere i riferimenti ai nodi.
     */
    fun obstaclesAround(center: LatLng, radiusMeters: Int, timeoutSeconds: Int = 25): String {
        val lat = center.latitude.toFixed6()
        val lon = center.longitude.toFixed6()
        val around = "(around:$radiusMeters,$lat,$lon)"
        return """
            [out:json][timeout:$timeoutSeconds];
            (
              way["building"]$around;
              way["building:part"]$around;
              relation["building"]["type"="multipolygon"]$around;
              way["barrier"~"^(wall|city_wall|retaining_wall|hedge)$"]$around;
              node["natural"="tree"]$around;
            );
            out geom;
        """.trimIndent()
    }
}

@Serializable
internal data class OverpassResponse(
    val elements: List<OverpassElement> = emptyList(),
)

@Serializable
internal data class OverpassElement(
    val type: String,
    val id: Long,
    val lat: Double? = null,
    val lon: Double? = null,
    val tags: Map<String, String> = emptyMap(),
    val geometry: List<OverpassPoint>? = null,
    val members: List<OverpassMember>? = null,
)

@Serializable
internal data class OverpassPoint(val lat: Double, val lon: Double)

@Serializable
internal data class OverpassMember(
    val type: String,
    @SerialName("ref") val ref: Long = 0,
    val role: String? = null,
    val geometry: List<OverpassPoint>? = null,
)

/**
 * Trasforma la risposta Overpass negli ostacoli usati dal motore delle ombre,
 * già proiettati nel piano locale in metri.
 */
object OverpassParser {

    private val json = Json { ignoreUnknownKeys = true }

    /** Impronte più piccole di così sono rumore (pilastrini, tettoie minuscole). */
    private const val MIN_FOOTPRINT_AREA_M2 = 4.0

    fun parseObstacles(rawJson: String, plane: LocalPlane): List<Obstacle> {
        val response = json.decodeFromString(OverpassResponse.serializer(), rawJson)
        val obstacles = ArrayList<Obstacle>(response.elements.size)
        val seen = HashSet<String>()

        for (element in response.elements) {
            val converted = when {
                element.type == "node" && element.tags["natural"] == "tree" -> tree(element, plane)
                element.type == "way" -> way(element, plane)
                element.type == "relation" -> relation(element, plane)
                else -> emptyList()
            }
            for (obstacle in converted) {
                if (seen.add(obstacle.id)) obstacles += obstacle
            }
        }
        return obstacles
    }

    private fun tree(element: OverpassElement, plane: LocalPlane): List<Obstacle> {
        val lat = element.lat ?: return emptyList()
        val lon = element.lon ?: return emptyList()
        val center = plane.toLocal(LatLng(lat, lon))
        val height = OsmHeights.treeHeight(element.tags)
        return listOf(
            Obstacle(
                id = "tree/${element.id}",
                kind = ObstacleKind.TREE,
                footprint = Polygons.circle(center, OsmHeights.treeCrownRadius(element.tags)),
                heightMeters = height,
                baseHeightMeters = OsmHeights.treeCrownBase(element.tags),
                name = element.tags["species"] ?: element.tags["name"] ?: "Albero",
            )
        )
    }

    private fun way(element: OverpassElement, plane: LocalPlane): List<Obstacle> {
        val geometry = element.geometry ?: return emptyList()
        val barrier = element.tags["barrier"]
        val points = geometry.map { plane.toLocal(LatLng(it.lat, it.lon)) }

        if (barrier != null && element.tags["building"] == null) {
            val footprint = thickenLine(points, halfWidth = 0.3)
            if (footprint.size < 3) return emptyList()
            return listOf(
                Obstacle(
                    id = "wall/${element.id}",
                    kind = ObstacleKind.WALL,
                    footprint = footprint,
                    heightMeters = OsmHeights.barrierHeight(element.tags),
                    name = element.tags["name"] ?: "Muro",
                )
            )
        }

        val footprint = closedRing(points) ?: return emptyList()
        return listOf(
            buildingFrom(footprint, element.tags, "way/${element.id}") ?: return emptyList()
        )
    }

    private fun relation(element: OverpassElement, plane: LocalPlane): List<Obstacle> {
        val members = element.members ?: return emptyList()
        val outers = members.filter { it.role == "outer" && it.geometry != null }
        if (outers.isEmpty()) return emptyList()
        // Ogni anello esterno diventa un ostacolo a sé: i cortili interni vengono
        // ignorati, ma per il calcolo dell'ombra al suolo la differenza è trascurabile.
        return outers.mapIndexedNotNull { index, member ->
            val points = member.geometry!!.map { plane.toLocal(LatLng(it.lat, it.lon)) }
            val ring = closedRing(points) ?: return@mapIndexedNotNull null
            buildingFrom(ring, element.tags, "relation/${element.id}/$index")
        }
    }

    private fun buildingFrom(footprint: List<Vec2>, tags: Map<String, String>, id: String): Obstacle? {
        if (kotlin.math.abs(Polygons.signedArea(footprint)) < MIN_FOOTPRINT_AREA_M2) return null
        return Obstacle(
            id = id,
            kind = ObstacleKind.BUILDING,
            footprint = Polygons.ensureCounterClockwise(footprint),
            heightMeters = OsmHeights.buildingHeight(tags),
            name = tags["name"] ?: tags["addr:street"]?.let { street ->
                listOfNotNull(street, tags["addr:housenumber"]).joinToString(" ")
            },
        )
    }

    /** Toglie il vertice ripetuto di chiusura e scarta le geometrie non poligonali. */
    private fun closedRing(points: List<Vec2>): List<Vec2>? {
        if (points.size < 4) return null
        val first = points.first()
        val last = points.last()
        val ring = if ((first - last).length < 0.1) points.dropLast(1) else points
        return if (ring.size >= 3) ring else null
    }

    /** Dà spessore a una polilinea (un muro) per poterla trattare come volume. */
    private fun thickenLine(points: List<Vec2>, halfWidth: Double): List<Vec2> {
        if (points.size < 2) return emptyList()
        val left = ArrayList<Vec2>(points.size)
        val right = ArrayList<Vec2>(points.size)
        for (i in points.indices) {
            val previous = points[(i - 1).coerceAtLeast(0)]
            val next = points[(i + 1).coerceAtMost(points.size - 1)]
            val tangent = (next - previous).normalized()
            val normal = Vec2(-tangent.y, tangent.x) * halfWidth
            left += points[i] + normal
            right += points[i] - normal
        }
        return Polygons.ensureCounterClockwise(left + right.reversed())
    }
}
