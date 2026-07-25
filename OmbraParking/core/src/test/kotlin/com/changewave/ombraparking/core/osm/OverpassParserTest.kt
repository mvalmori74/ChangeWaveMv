package com.changewave.ombraparking.core.osm

import com.changewave.ombraparking.core.geo.LatLng
import com.changewave.ombraparking.core.geo.LocalPlane
import com.changewave.ombraparking.core.geo.Polygons
import com.changewave.ombraparking.core.shadow.ObstacleKind
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OverpassParserTest {

    private val plane = LocalPlane(LatLng(45.0, 9.0))

    private val response = """
        {
          "version": 0.6,
          "generator": "Overpass API",
          "elements": [
            {
              "type": "way",
              "id": 111,
              "tags": { "building": "apartments", "building:levels": "4", "name": "Condominio Aurora" },
              "geometry": [
                { "lat": 45.0000, "lon": 9.0000 },
                { "lat": 45.0000, "lon": 9.0002 },
                { "lat": 45.0002, "lon": 9.0002 },
                { "lat": 45.0002, "lon": 9.0000 },
                { "lat": 45.0000, "lon": 9.0000 }
              ]
            },
            {
              "type": "node",
              "id": 222,
              "lat": 45.0005,
              "lon": 9.0005,
              "tags": { "natural": "tree", "height": "12 m" }
            },
            {
              "type": "way",
              "id": 333,
              "tags": { "barrier": "wall", "height": "3" },
              "geometry": [
                { "lat": 45.0010, "lon": 9.0000 },
                { "lat": 45.0010, "lon": 9.0004 }
              ]
            },
            {
              "type": "way",
              "id": 444,
              "tags": { "building": "yes" },
              "geometry": [
                { "lat": 45.0020, "lon": 9.0000 },
                { "lat": 45.0020, "lon": 9.00001 },
                { "lat": 45.00201, "lon": 9.00001 },
                { "lat": 45.0020, "lon": 9.0000 }
              ]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `estrae edifici alberi e muri dalla risposta Overpass`() {
        val obstacles = OverpassParser.parseObstacles(response, plane)

        val building = assertNotNull(obstacles.find { it.id == "way/111" })
        assertEquals(ObstacleKind.BUILDING, building.kind)
        assertEquals("Condominio Aurora", building.name)
        assertEquals(4 * OsmHeights.METERS_PER_LEVEL, building.heightMeters, 1e-9)
        assertEquals(4, building.footprint.size, "il vertice di chiusura va rimosso")

        val tree = assertNotNull(obstacles.find { it.id == "tree/222" })
        assertEquals(ObstacleKind.TREE, tree.kind)
        assertEquals(12.0, tree.heightMeters, 1e-9)
        assertTrue(tree.baseHeightMeters > 0, "la chioma parte staccata da terra")
        assertTrue(tree.baseHeightMeters < tree.heightMeters)

        val wall = assertNotNull(obstacles.find { it.id == "wall/333" })
        assertEquals(ObstacleKind.WALL, wall.kind)
        assertEquals(3.0, wall.heightMeters, 1e-9)
        assertTrue(Polygons.signedArea(wall.footprint) > 0, "anche il muro deve avere un'area")
    }

    @Test
    fun `scarta le impronte troppo piccole per fare ombra`() {
        val obstacles = OverpassParser.parseObstacles(response, plane)

        assertTrue(obstacles.none { it.id == "way/444" }, "il ripostiglio da 1 m² va ignorato")
    }

    @Test
    fun `le impronte sono orientate in senso antiorario e in metri`() {
        val building = assertNotNull(
            OverpassParser.parseObstacles(response, plane).find { it.id == "way/111" }
        )

        assertTrue(Polygons.signedArea(building.footprint) > 0)
        // 0.0002° di latitudine sono circa 22 m, 0.0002° di longitudine circa 16 m a 45°N.
        val area = Polygons.signedArea(building.footprint)
        assertTrue(abs(area - 22.2 * 15.7) < 30, "area calcolata $area m²")
    }

    @Test
    fun `una risposta vuota non genera ostacoli`() {
        assertTrue(OverpassParser.parseObstacles("""{"elements":[]}""", plane).isEmpty())
    }

    @Test
    fun `la query chiede edifici alberi e muri intorno al punto`() {
        val query = OverpassQuery.obstaclesAround(LatLng(45.4642, 9.19), radiusMeters = 250)

        assertTrue(query.contains("around:250,45.464200,9.190000"))
        assertTrue(query.contains("""way["building"]"""))
        assertTrue(query.contains("""node["natural"="tree"]"""))
        assertTrue(query.contains("out geom;"))
    }
}
