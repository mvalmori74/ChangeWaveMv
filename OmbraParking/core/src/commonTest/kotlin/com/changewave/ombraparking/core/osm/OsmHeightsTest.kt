package com.changewave.ombraparking.core.osm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OsmHeightsTest {

    @Test
    fun `interpreta i formati di altezza usati in OSM`() {
        assertEquals(12.0, OsmHeights.parseLength("12")!!, 1e-9)
        assertEquals(12.0, OsmHeights.parseLength("12 m")!!, 1e-9)
        assertEquals(12.5, OsmHeights.parseLength("12,5")!!, 1e-9)
        assertEquals(3.048, OsmHeights.parseLength("10 ft")!!, 1e-6)
        assertEquals(3.048, OsmHeights.parseLength("10'")!!, 1e-6)
        assertEquals(3.2004, OsmHeights.parseLength("10'6\"")!!, 1e-6)
    }

    @Test
    fun `valori non interpretabili restituiscono null`() {
        assertNull(OsmHeights.parseLength(null))
        assertNull(OsmHeights.parseLength(""))
        assertNull(OsmHeights.parseLength("alto"))
        assertNull(OsmHeights.parseLength("-3"))
    }

    @Test
    fun `l'altezza esplicita ha la precedenza sui piani`() {
        val tags = mapOf("building" to "apartments", "height" to "18", "building:levels" to "3")
        assertEquals(18.0, OsmHeights.buildingHeight(tags), 1e-9)
    }

    @Test
    fun `senza altezza si usano i piani più l'eventuale tetto`() {
        assertEquals(
            3 * OsmHeights.METERS_PER_LEVEL + 2.0,
            OsmHeights.buildingHeight(mapOf("building:levels" to "3", "roof:height" to "2")),
            1e-9,
        )
    }

    @Test
    fun `senza dati si stima dal tipo di edificio`() {
        val garage = OsmHeights.buildingHeight(mapOf("building" to "garage"))
        val palazzina = OsmHeights.buildingHeight(mapOf("building" to "apartments"))
        val generico = OsmHeights.buildingHeight(mapOf("building" to "yes"))

        assertTrue(garage < 4.0, "un box auto non fa ombra come un palazzo")
        assertTrue(palazzina > 10.0)
        assertTrue(generico in 5.0..15.0)
    }

    @Test
    fun `la chioma di un albero sta fra il tronco e la cima`() {
        val tags = mapOf("natural" to "tree", "height" to "10")

        val base = OsmHeights.treeCrownBase(tags)
        assertTrue(base in 1.5..9.0, "base chioma $base")
        assertTrue(OsmHeights.treeCrownRadius(tags) in 1.5..8.0)
    }

    @Test
    fun `il diametro della chioma mappato vince sulla stima`() {
        assertEquals(3.0, OsmHeights.treeCrownRadius(mapOf("diameter_crown" to "6")), 1e-9)
    }
}
