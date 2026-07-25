package com.changewave.ombraparking.core.geo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DataCoverageTest {

    private val center = LatLng(44.4949, 11.3426)
    private val radius = 300

    @Test
    fun `il centro è sempre coperto`() {
        assertTrue(DataCoverage.isReliable(center, center, radius))
    }

    @Test
    fun `un punto ben dentro l'area è affidabile`() {
        val plane = LocalPlane(center)
        val nearby = plane.toLatLng(Vec2(100.0, 0.0))

        assertTrue(DataCoverage.isReliable(center, nearby, radius))
    }

    @Test
    fun `un punto al bordo dell'area scaricata non lo è`() {
        val plane = LocalPlane(center)
        val edge = plane.toLatLng(Vec2(0.0, 280.0))

        assertFalse(
            DataCoverage.isReliable(center, edge, radius),
            "a 280 m su 300 mancano gli edifici che potrebbero fargli ombra",
        )
    }

    @Test
    fun `il limite segue il raggio scaricato`() {
        assertEquals(180.0, DataCoverage.reliableRadiusMeters(300), 1e-9)
        assertEquals(60.0, DataCoverage.reliableRadiusMeters(100), 1e-9)
    }

    @Test
    fun `la versione in coordinate locali dà lo stesso esito`() {
        val plane = LocalPlane(center)
        val point = plane.toLatLng(Vec2(120.0, 120.0)) // ~170 m dal centro

        assertEquals(
            DataCoverage.isReliable(center, point, radius),
            DataCoverage.isReliable(plane.toLocal(point), radius),
        )
    }
}
