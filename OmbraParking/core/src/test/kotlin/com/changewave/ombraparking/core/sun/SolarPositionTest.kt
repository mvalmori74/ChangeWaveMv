package com.changewave.ombraparking.core.sun

import com.changewave.ombraparking.core.geo.LatLng
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SolarPositionTest {

    private val rome = LatLng(41.9028, 12.4964)
    private val romeZone = ZoneId.of("Europe/Rome")

    @Test
    fun `al solstizio d'estate il sole a mezzogiorno raggiunge 90 - latitudine + declinazione`() {
        val day = LocalDate.of(2024, 6, 20) // solstizio 2024
        val noon = SunTimes.forDay(day, romeZone, rome).solarNoon
        val position = SolarPosition.at(noon, rome)

        val expected = 90.0 - rome.latitude + 23.44
        assertTrue(
            abs(position.elevationDegrees - expected) < 0.6,
            "elevazione ${position.elevationDegrees}, attesa ~$expected",
        )
        assertTrue(
            abs(position.azimuthDegrees - 180.0) < 1.0,
            "a mezzogiorno il sole deve essere a sud, azimut ${position.azimuthDegrees}",
        )
    }

    @Test
    fun `al solstizio d'inverno il sole resta basso`() {
        val day = LocalDate.of(2024, 12, 21)
        val noon = SunTimes.forDay(day, romeZone, rome).solarNoon
        val position = SolarPosition.at(noon, rome)

        val expected = 90.0 - rome.latitude - 23.44
        assertTrue(
            abs(position.elevationDegrees - expected) < 0.6,
            "elevazione ${position.elevationDegrees}, attesa ~$expected",
        )
    }

    @Test
    fun `all'equinozio il sole sorge a est`() {
        val day = LocalDate.of(2024, 3, 20)
        val sunrise = assertNotNull(SunTimes.forDay(day, romeZone, rome).sunrise)
        val azimuth = SolarPosition.at(sunrise, rome).azimuthDegrees

        assertTrue(abs(azimuth - 90.0) < 2.0, "azimut all'alba $azimuth, atteso ~90")
    }

    @Test
    fun `nell'emisfero sud a mezzogiorno il sole sta a nord`() {
        val sydney = LatLng(-33.8688, 151.2093)
        val zone = ZoneId.of("Australia/Sydney")
        val noon = SunTimes.forDay(LocalDate.of(2024, 6, 21), zone, sydney).solarNoon
        val azimuth = SolarPosition.at(noon, sydney).azimuthDegrees

        val distanceFromNorth = minOf(azimuth, 360.0 - azimuth)
        assertTrue(distanceFromNorth < 2.0, "azimut $azimuth, atteso ~0/360")
    }

    @Test
    fun `di notte il sole è sotto l'orizzonte`() {
        val night = LocalDate.of(2024, 1, 15).atTime(2, 0).atZone(romeZone).toInstant()
        val position = SolarPosition.at(night, rome)

        assertTrue(position.elevationDegrees < 0)
        assertTrue(!position.isAboveHorizon)
    }

    @Test
    fun `la lunghezza dell'ombra è pari all'altezza quando il sole è a 45 gradi`() {
        val position = SunPosition(azimuthDegrees = 180.0, elevationDegrees = 45.0)
        assertEquals(10.0, position.shadowLength(10.0), 1e-6)
    }

    @Test
    fun `l'ombra si allunga con il sole basso ma resta limitata`() {
        val low = SunPosition(azimuthDegrees = 270.0, elevationDegrees = 0.2)
        assertEquals(SunPosition.MAX_SHADOW_LENGTH_M, low.shadowLength(10.0), 1e-6)
    }

    @Test
    fun `la direzione dell'ombra è opposta a quella del sole`() {
        val position = SunPosition(azimuthDegrees = 90.0, elevationDegrees = 30.0) // sole a est
        val shadow = position.shadowDirection()

        assertEquals(-1.0, shadow.x, 1e-9) // l'ombra punta a ovest
        assertEquals(0.0, shadow.y, 1e-9)
    }

    @Test
    fun `alba e tramonto a Roma al solstizio d'estate`() {
        val day = LocalDate.of(2024, 6, 21)
        val light = SunTimes.forDay(day, romeZone, rome)

        val sunrise = assertNotNull(light.sunrise).atZone(romeZone).toLocalTime()
        val sunset = assertNotNull(light.sunset).atZone(romeZone).toLocalTime()

        // Valori pubblicati: alba 05:35, tramonto 20:49 (ora legale).
        assertTrue(abs(sunrise.toSecondOfDay() - 5 * 3600 - 35 * 60) < 10 * 60, "alba $sunrise")
        assertTrue(abs(sunset.toSecondOfDay() - 20 * 3600 - 49 * 60) < 10 * 60, "tramonto $sunset")
        assertTrue(light.sunrise!!.isBefore(light.solarNoon))
        assertTrue(light.sunset!!.isAfter(light.solarNoon))
    }
}
