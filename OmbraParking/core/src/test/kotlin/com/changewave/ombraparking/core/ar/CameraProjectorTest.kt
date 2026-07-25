package com.changewave.ombraparking.core.ar

import com.changewave.ombraparking.core.geo.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CameraProjectorTest {

    private val width = 1080f
    private val height = 1920f

    /** Telefono appoggiato sul tavolo, schermo verso l'alto: la camera guarda in basso. */
    private val flatOnTable = doubleArrayOf(
        1.0, 0.0, 0.0,
        0.0, 1.0, 0.0,
        0.0, 0.0, 1.0,
    )

    /**
     * Telefono tenuto verticale con la camera verso nord:
     * x dispositivo = est, y dispositivo = alto, z dispositivo = sud.
     */
    private val uprightFacingNorth = doubleArrayOf(
        1.0, 0.0, 0.0,
        0.0, 0.0, -1.0,
        0.0, 1.0, 0.0,
    )

    private fun projector(rotation: DoubleArray) = CameraProjector(
        viewportWidthPx = width,
        viewportHeightPx = height,
        horizontalFovDegrees = 60.0,
        verticalFovDegrees = 90.0,
        deviceToWorld = rotation,
    )

    @Test
    fun `da fermi in verticale il punto davanti finisce al centro dello schermo`() {
        val point = assertNotNull(projector(uprightFacingNorth).project(Vec3(0.0, 10.0, 0.0)))

        assertEquals(width / 2, point.x, 0.5f)
        assertEquals(height / 2, point.y, 0.5f)
        assertEquals(10.0, point.distanceMeters, 1e-6)
    }

    @Test
    fun `il suolo davanti ai piedi appare nella metà bassa dello schermo`() {
        val point = assertNotNull(projector(uprightFacingNorth).project(Vec3(0.0, 10.0, -1.5)))

        assertEquals(width / 2, point.x, 0.5f)
        assertTrue(point.y > height / 2, "il suolo deve stare sotto l'orizzonte, y=${point.y}")
    }

    @Test
    fun `quello che sta a est appare a destra`() {
        val point = assertNotNull(projector(uprightFacingNorth).project(Vec3(3.0, 10.0, 0.0)))

        assertTrue(point.x > width / 2, "x=${point.x}")
    }

    @Test
    fun `quello che sta dietro non viene proiettato`() {
        assertNull(projector(uprightFacingNorth).project(Vec3(0.0, -10.0, 0.0)))
    }

    @Test
    fun `la direzione di vista segue l'orientamento`() {
        val north = projector(uprightFacingNorth).viewDirection()
        assertEquals(0.0, north.east, 1e-9)
        assertEquals(1.0, north.north, 1e-9)
        assertEquals(0.0, north.up, 1e-9)

        val down = projector(flatOnTable).viewDirection()
        assertEquals(-1.0, down.up, 1e-9)
    }

    @Test
    fun `guardando in basso il centro dello schermo cade ai propri piedi`() {
        val ground = assertNotNull(projector(flatOnTable).groundIntersection(eyeHeightMeters = 1.5))

        assertEquals(0.0, ground.x, 1e-9)
        assertEquals(0.0, ground.y, 1e-9)
    }

    @Test
    fun `guardando all'orizzonte non c'è nessun punto al suolo inquadrato`() {
        assertNull(projector(uprightFacingNorth).groundIntersection(eyeHeightMeters = 1.5))
    }

    @Test
    fun `un poligono a cavallo della camera viene tagliato invece di ribaltarsi`() {
        // Rettangolo al suolo che parte dietro l'osservatore e prosegue davanti.
        val polygon = listOf(
            Vec3(-2.0, -5.0, -1.5),
            Vec3(2.0, -5.0, -1.5),
            Vec3(2.0, 10.0, -1.5),
            Vec3(-2.0, 10.0, -1.5),
        )
        val projected = projector(uprightFacingNorth).projectPolygon(polygon)

        assertTrue(projected.size >= 3, "il taglio deve lasciare un poligono disegnabile")
        projected.forEach { point ->
            assertTrue(point.distanceMeters >= CameraProjector.NEAR_PLANE_METERS - 1e-6)
            assertTrue(point.x.isFinite() && point.y.isFinite())
        }
    }

    @Test
    fun `un poligono interamente dietro non produce nulla`() {
        val polygon = listOf(
            Vec3(-2.0, -20.0, -1.5),
            Vec3(2.0, -20.0, -1.5),
            Vec3(2.0, -10.0, -1.5),
            Vec3(-2.0, -10.0, -1.5),
        )
        assertTrue(projector(uprightFacingNorth).projectPolygon(polygon).isEmpty())
    }
}
