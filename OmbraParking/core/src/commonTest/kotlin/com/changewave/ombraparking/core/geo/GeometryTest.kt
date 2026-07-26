package com.changewave.ombraparking.core.geo

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GeometryTest {

    private val square = listOf(Vec2(0.0, 0.0), Vec2(10.0, 0.0), Vec2(10.0, 10.0), Vec2(0.0, 10.0))

    @Test
    fun `area con segno positiva in senso antiorario`() {
        assertEquals(100.0, Polygons.signedArea(square), 1e-9)
        assertEquals(-100.0, Polygons.signedArea(square.reversed()), 1e-9)
    }

    @Test
    fun `ensureCounterClockwise raddrizza il verso`() {
        val clockwise = square.reversed()
        assertTrue(Polygons.signedArea(Polygons.ensureCounterClockwise(clockwise)) > 0)
        assertTrue(Polygons.signedArea(Polygons.ensureCounterClockwise(square)) > 0)
    }

    @Test
    fun `centroide di un quadrato`() {
        val center = Polygons.centroid(square)
        assertEquals(5.0, center.x, 1e-9)
        assertEquals(5.0, center.y, 1e-9)
    }

    @Test
    fun `contains distingue dentro e fuori`() {
        assertTrue(Polygons.contains(square, Vec2(5.0, 5.0)))
        assertFalse(Polygons.contains(square, Vec2(15.0, 5.0)))
        assertFalse(Polygons.contains(square, Vec2(-0.1, 5.0)))
    }

    @Test
    fun `il raggio entra ed esce dal poligono alle distanze giuste`() {
        val interval = assertNotNull(
            Polygons.rayInterval(origin = Vec2(-5.0, 5.0), direction = Vec2(1.0, 0.0), polygon = square)
        )
        assertEquals(5.0, interval.start, 1e-9)
        assertEquals(15.0, interval.endInclusive, 1e-9)
    }

    @Test
    fun `un raggio che parte dall'interno ha distanza di entrata nulla`() {
        val interval = assertNotNull(
            Polygons.rayInterval(origin = Vec2(5.0, 5.0), direction = Vec2(1.0, 0.0), polygon = square)
        )
        assertEquals(0.0, interval.start, 1e-9)
        assertEquals(5.0, interval.endInclusive, 1e-9)
    }

    @Test
    fun `un raggio che si allontana non interseca`() {
        assertNull(Polygons.rayInterval(Vec2(-5.0, 5.0), Vec2(-1.0, 0.0), square))
        assertNull(Polygons.rayInterval(Vec2(-5.0, 50.0), Vec2(1.0, 0.0), square))
    }

    @Test
    fun `il cerchio approssimato ha il raggio richiesto`() {
        val circle = Polygons.circle(Vec2(3.0, 4.0), radius = 2.0, segments = 16)
        assertEquals(16, circle.size)
        circle.forEach { point ->
            assertEquals(2.0, (point - Vec2(3.0, 4.0)).length, 1e-9)
        }
        assertTrue(Polygons.contains(circle, Vec2(3.0, 4.0)))
    }

    @Test
    fun `la proiezione locale è reversibile`() {
        val origin = LatLng(45.4642, 9.1900) // Milano
        val plane = LocalPlane(origin)
        val point = LatLng(45.4700, 9.2000)

        val local = plane.toLocal(point)
        val back = plane.toLatLng(local)

        assertEquals(point.latitude, back.latitude, 1e-9)
        assertEquals(point.longitude, back.longitude, 1e-9)
    }

    @Test
    fun `cento metri a nord danno una distanza di cento metri`() {
        val plane = LocalPlane(LatLng(45.4642, 9.1900))
        val north = plane.toLatLng(Vec2(0.0, 100.0))

        assertTrue(abs(plane.distanceMeters(plane.origin, north) - 100.0) < 0.01)
        assertTrue(north.latitude > plane.origin.latitude)
    }

    @Test
    fun `est e nord hanno il verso giusto`() {
        val plane = LocalPlane(LatLng(41.9, 12.5))
        val east = plane.toLocal(LatLng(41.9, 12.6))
        val north = plane.toLocal(LatLng(42.0, 12.5))

        assertTrue(east.x > 0 && abs(east.y) < 1e-6)
        assertTrue(north.y > 0 && abs(north.x) < 1e-6)
    }
}
