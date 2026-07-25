package com.changewave.ombraparking.core.shadow

import com.changewave.ombraparking.core.geo.LatLng
import com.changewave.ombraparking.core.geo.Polygons
import com.changewave.ombraparking.core.geo.Vec2
import com.changewave.ombraparking.core.sun.SunPosition
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShadowEngineTest {

    /** Palazzo di 10 m di lato e 10 m di altezza, centrato nell'origine. */
    private val building = Obstacle(
        id = "test/palazzo",
        kind = ObstacleKind.BUILDING,
        footprint = listOf(Vec2(-5.0, -5.0), Vec2(5.0, -5.0), Vec2(5.0, 5.0), Vec2(-5.0, 5.0)),
        heightMeters = 10.0,
        name = "Palazzo",
    )

    /** Sole a sud, 45° sull'orizzonte: le ombre si allungano verso nord quanto l'altezza. */
    private val sunSouth45 = SunPosition(azimuthDegrees = 180.0, elevationDegrees = 45.0)

    @Test
    fun `il punto dentro il cono d'ombra è in ombra`() {
        val info = ShadowEngine.shadeAt(Vec2(0.0, 10.0), listOf(building), sunSouth45)

        assertEquals(ShadeQuality.SHADE, info.quality)
        assertEquals("Palazzo", info.obstacle?.name)
    }

    @Test
    fun `oltre la punta dell'ombra si torna al sole`() {
        // Il bordo nord è a y=5, l'ombra arriva a y=15: a y=16 il sole passa sopra il tetto.
        val info = ShadowEngine.shadeAt(Vec2(0.0, 16.0), listOf(building), sunSouth45)

        assertEquals(ShadeQuality.SUN, info.quality)
    }

    @Test
    fun `dalla parte del sole non c'è ombra`() {
        val info = ShadowEngine.shadeAt(Vec2(0.0, -20.0), listOf(building), sunSouth45)

        assertEquals(ShadeQuality.SUN, info.quality)
    }

    @Test
    fun `di lato al cono d'ombra c'è sole`() {
        val info = ShadowEngine.shadeAt(Vec2(20.0, 8.0), listOf(building), sunSouth45)

        assertEquals(ShadeQuality.SUN, info.quality)
    }

    @Test
    fun `con il sole a ovest l'ombra si sposta a est`() {
        val sunWest = SunPosition(azimuthDegrees = 270.0, elevationDegrees = 30.0)

        val east = ShadowEngine.shadeAt(Vec2(10.0, 0.0), listOf(building), sunWest)
        val west = ShadowEngine.shadeAt(Vec2(-10.0, 0.0), listOf(building), sunWest)

        assertEquals(ShadeQuality.SHADE, east.quality)
        assertEquals(ShadeQuality.SUN, west.quality)
    }

    @Test
    fun `di notte tutto è in ombra ma etichettato come notte`() {
        val night = SunPosition(azimuthDegrees = 0.0, elevationDegrees = -10.0)
        val info = ShadowEngine.shadeAt(Vec2(0.0, 10.0), listOf(building), night)

        assertEquals(ShadeQuality.NIGHT, info.quality)
    }

    @Test
    fun `la chioma di un albero dà ombra filtrata e lascia passare la luce sotto`() {
        val tree = Obstacle(
            id = "test/albero",
            kind = ObstacleKind.TREE,
            footprint = Polygons.circle(Vec2.ZERO, radius = 2.0),
            heightMeters = 8.0,
            baseHeightMeters = 3.0,
            name = "Albero",
        )

        val underCrownShadow = ShadowEngine.shadeAt(Vec2(0.0, 4.0), listOf(tree), sunSouth45)
        val beyondShadow = ShadowEngine.shadeAt(Vec2(0.0, 12.0), listOf(tree), sunSouth45)

        assertEquals(ShadeQuality.DAPPLED, underCrownShadow.quality)
        assertEquals(ShadeQuality.SUN, beyondShadow.quality)
    }

    @Test
    fun `l'ombra piena di un edificio prevale su quella di un albero`() {
        val tree = Obstacle(
            id = "test/albero",
            kind = ObstacleKind.TREE,
            footprint = Polygons.circle(Vec2(0.0, 3.0), radius = 2.0),
            heightMeters = 8.0,
            baseHeightMeters = 2.0,
        )
        val info = ShadowEngine.shadeAt(Vec2(0.0, 10.0), listOf(tree, building), sunSouth45)

        assertEquals(ShadeQuality.SHADE, info.quality)
        assertEquals("Palazzo", info.obstacle?.name)
    }

    @Test
    fun `la sagoma dell'ombra copre i punti in ombra`() {
        val parts = ShadowEngine.shadowShape(building, sunSouth45)
        assertTrue(parts.isNotEmpty())

        val shadedPoint = Vec2(0.0, 12.0)
        val sunnyPoint = Vec2(0.0, 20.0)

        assertTrue(parts.any { Polygons.contains(it, shadedPoint) }, "il punto in ombra deve stare nella sagoma")
        assertTrue(parts.none { Polygons.contains(it, sunnyPoint) }, "il punto al sole non deve stare nella sagoma")
    }

    @Test
    fun `sagoma e test puntuale sono coerenti su una griglia`() {
        val sun = SunPosition(azimuthDegrees = 225.0, elevationDegrees = 35.0)
        val parts = ShadowEngine.shadowShape(building, sun)

        var checked = 0
        // Griglia sfalsata di un valore irregolare: evita di campionare esattamente sui bordi,
        // dove "dentro o fuori" è una questione di virgola e non dice nulla sulla correttezza.
        for (x in -40..40 step 2) {
            for (y in -40..40 step 2) {
                val point = Vec2(x + 0.31, y + 0.17)
                if (Polygons.contains(building.footprint, point)) continue // sotto l'edificio
                val pointIsShaded = ShadowEngine.shadeAt(point, listOf(building), sun).quality.isShaded
                val insideShape = parts.any { Polygons.contains(it, point) }
                assertEquals(pointIsShaded, insideShape, "incoerenza in $point")
                checked++
            }
        }
        assertTrue(checked > 1000)
    }

    @Test
    fun `la previsione oraria trova l'ombra del pomeriggio a est dell'edificio`() {
        val rome = LatLng(41.9028, 12.4964)
        val zone = ZoneId.of("Europe/Rome")
        val day = LocalDate.of(2024, 7, 15)
        // Punto a est dell'edificio: al mattino il sole viene da est e lo illumina,
        // nel pomeriggio il sole passa a ovest e il palazzo gli fa ombra.
        val point = Vec2(12.0, 0.0)

        val forecast = ShadeTimeline.compute(
            point = point,
            obstacles = listOf(building),
            location = rome,
            from = day.atStartOfDay(zone).toInstant(),
            to = day.plusDays(1).atStartOfDay(zone).toInstant(),
            stepMinutes = 5,
        )

        val morning = day.atTime(8, 0).atZone(zone).toInstant()
        val afternoon = day.atTime(17, 0).atZone(zone).toInstant()

        assertEquals(ShadeQuality.SUN, forecast.qualityAt(morning))
        assertEquals(ShadeQuality.SHADE, forecast.qualityAt(afternoon))
        assertTrue(forecast.shadedMinutes > 0)
        assertNull(forecast.shadeEndsAfter(morning), "al mattino quel punto non è in ombra")
        assertNotNull(forecast.nextShadeStart(morning), "l'ombra deve arrivare nel pomeriggio")
        assertNotNull(forecast.shadeEndsAfter(afternoon))
        assertTrue(forecast.slots.zipWithNext().all { (a, b) -> a.end == b.start }, "gli intervalli devono essere contigui")
    }

    @Test
    fun `la previsione dice quando il sole tornerà a colpire un punto in ombra`() {
        val rome = LatLng(41.9028, 12.4964)
        val zone = ZoneId.of("Europe/Rome")
        val day = LocalDate.of(2024, 7, 15)
        // Punto a ovest dell'edificio: in ombra al mattino, al sole nel pomeriggio.
        val point = Vec2(-12.0, 0.0)

        val forecast = ShadeTimeline.compute(
            point = point,
            obstacles = listOf(building),
            location = rome,
            from = day.atStartOfDay(zone).toInstant(),
            to = day.plusDays(1).atStartOfDay(zone).toInstant(),
            stepMinutes = 5,
        )

        val morning = day.atTime(8, 0).atZone(zone).toInstant()
        assertEquals(ShadeQuality.SHADE, forecast.qualityAt(morning))

        val sunArrival = assertNotNull(forecast.nextSunStart(morning), "il sole deve arrivare in giornata")
        assertTrue(sunArrival.isAfter(morning))
        assertEquals(ShadeQuality.SUN, forecast.qualityAt(sunArrival))
        // Al momento appena precedente il punto è ancora in ombra.
        assertEquals(ShadeQuality.SHADE, forecast.qualityAt(sunArrival.minusSeconds(60)))
    }

    @Test
    fun `senza sole in arrivo la previsione non inventa un orario`() {
        val night = LocalDate.of(2024, 12, 21)
        val zone = ZoneId.of("Europe/Rome")
        val rome = LatLng(41.9028, 12.4964)
        // Un metro a nord del muro: con il sole basso di dicembre non lo raggiunge mai.
        val point = Vec2(0.0, 6.0)

        val forecast = ShadeTimeline.compute(
            point = point,
            obstacles = listOf(building),
            location = rome,
            from = night.atStartOfDay(zone).toInstant(),
            to = night.plusDays(1).atStartOfDay(zone).toInstant(),
            stepMinutes = 10,
        )

        assertNull(forecast.nextSunStart(night.atTime(9, 0).atZone(zone).toInstant()))
    }
}
