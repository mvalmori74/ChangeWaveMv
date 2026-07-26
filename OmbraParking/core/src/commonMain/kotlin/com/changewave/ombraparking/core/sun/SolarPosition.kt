package com.changewave.ombraparking.core.sun

import com.changewave.ombraparking.core.math.toRadians
import com.changewave.ombraparking.core.math.toDegrees

import com.changewave.ombraparking.core.geo.LatLng
import com.changewave.ombraparking.core.geo.Vec2
import com.changewave.ombraparking.core.geo.Vec3
import kotlinx.datetime.Instant
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Posizione del sole vista da un punto sulla Terra.
 *
 * @param azimuthDegrees direzione orizzontale, 0 = nord, 90 = est, 180 = sud (senso orario).
 * @param elevationDegrees altezza sull'orizzonte in gradi (negativa se il sole è tramontato).
 */
data class SunPosition(
    val azimuthDegrees: Double,
    val elevationDegrees: Double,
) {

    val isAboveHorizon: Boolean get() = elevationDegrees > 0.0

    /** Versore orizzontale (est, nord) che punta **verso** il sole. */
    fun horizontalDirection(): Vec2 {
        val az = (azimuthDegrees).toRadians()
        return Vec2(sin(az), cos(az))
    }

    /** Versore orizzontale nella direzione in cui si allungano le ombre. */
    fun shadowDirection(): Vec2 = horizontalDirection() * -1.0

    /** Direzione 3D verso il sole (est, nord, su), normalizzata. */
    fun direction3D(): Vec3 {
        val az = (azimuthDegrees).toRadians()
        val el = (elevationDegrees).toRadians()
        val horizontal = cos(el)
        return Vec3(horizontal * sin(az), horizontal * cos(az), sin(el))
    }

    /**
     * Lunghezza al suolo dell'ombra proiettata da un ostacolo alto [heightMeters].
     * Con il sole molto basso l'ombra tende all'infinito: viene troncata a [maxLength]
     * perché oltre quella distanza non abbiamo comunque dati sugli edifici.
     */
    fun shadowLength(heightMeters: Double, maxLength: Double = MAX_SHADOW_LENGTH_M): Double {
        if (heightMeters <= 0.0) return 0.0
        if (elevationDegrees <= 0.0) return maxLength
        val length = heightMeters / tan((elevationDegrees).toRadians())
        return length.coerceIn(0.0, maxLength)
    }

    companion object {
        /** Oltre questa distanza le ombre non vengono più modellate (m). */
        const val MAX_SHADOW_LENGTH_M = 400.0
    }
}

/**
 * Calcolo della posizione solare secondo l'algoritmo NOAA Solar Calculator.
 * Precisione dell'ordine del centesimo di grado per le date di interesse pratico,
 * abbondantemente sufficiente per prevedere l'ombra di un edificio.
 */
object SolarPosition {

    fun at(instant: Instant, location: LatLng): SunPosition {
        val julianDay = instant.toEpochMilliseconds() / 86_400_000.0 + 2_440_587.5
        val t = (julianDay - 2_451_545.0) / 36_525.0 // secoli giuliani dal J2000.0

        val geomMeanLongSun = wrap360(280.46646 + t * (36000.76983 + t * 0.0003032))
        val geomMeanAnomSun = 357.52911 + t * (35999.05029 - 0.0001537 * t)
        val eccentricity = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)

        val anomRad = (geomMeanAnomSun).toRadians()
        val sunEqOfCenter = sin(anomRad) * (1.914602 - t * (0.004817 + 0.000014 * t)) +
            sin(2 * anomRad) * (0.019993 - 0.000101 * t) +
            sin(3 * anomRad) * 0.000289

        val sunTrueLong = geomMeanLongSun + sunEqOfCenter
        val omega = 125.04 - 1934.136 * t
        val sunAppLong = sunTrueLong - 0.00569 - 0.00478 * sin((omega).toRadians())

        val meanObliquity = 23.0 + (26.0 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60.0) / 60.0
        val obliquityCorr = meanObliquity + 0.00256 * cos((omega).toRadians())

        val declination = (
            asin(sin((obliquityCorr).toRadians()) * sin((sunAppLong).toRadians()))
        ).toDegrees()

        val varY = tan((obliquityCorr / 2).toRadians()).let { it * it }
        val meanLongRad = (geomMeanLongSun).toRadians()
        val equationOfTime = 4 * (
            varY * sin(2 * meanLongRad) -
                2 * eccentricity * sin(anomRad) +
                4 * eccentricity * varY * sin(anomRad) * cos(2 * meanLongRad) -
                0.5 * varY * varY * sin(4 * meanLongRad) -
                1.25 * eccentricity * eccentricity * sin(2 * anomRad)
        ).toDegrees()

        // Minuti trascorsi dalla mezzanotte UTC.
        val minutesUtc = (instant.toEpochMilliseconds().mod(86_400_000L)) / 60_000.0
        val trueSolarTime = (minutesUtc + equationOfTime + 4 * location.longitude).mod(1440.0)
        val hourAngle = if (trueSolarTime / 4 < 0) trueSolarTime / 4 + 180 else trueSolarTime / 4 - 180

        val latRad = (location.latitude).toRadians()
        val declRad = (declination).toRadians()
        val hourAngleRad = (hourAngle).toRadians()

        val cosZenith = (sin(latRad) * sin(declRad) + cos(latRad) * cos(declRad) * cos(hourAngleRad))
            .coerceIn(-1.0, 1.0)
        val zenith = (acos(cosZenith)).toDegrees()
        val elevation = 90.0 - zenith + atmosphericRefraction(90.0 - zenith)

        val azimuth = if (abs(cos((zenith).toRadians())) > 0.99999 || abs(cos(latRad)) < 1e-9) {
            // Sole allo zenit o osservatore ai poli: l'azimut è indeterminato.
            if (location.latitude > 0) 180.0 else 0.0
        } else {
            val cosAz = ((sin(latRad) * cos((zenith).toRadians())) - sin(declRad)) /
                (cos(latRad) * sin((zenith).toRadians()))
            val az = (acos(cosAz.coerceIn(-1.0, 1.0))).toDegrees()
            if (hourAngle > 0) wrap360(az + 180.0) else wrap360(540.0 - az)
        }

        return SunPosition(azimuthDegrees = azimuth, elevationDegrees = elevation)
    }

    /**
     * Rifrazione atmosferica: vicino all'orizzonte il sole appare più alto di quanto sia.
     * Formula approssimata usata dal NOAA (gradi).
     */
    private fun atmosphericRefraction(elevationDeg: Double): Double {
        if (elevationDeg > 85.0) return 0.0
        val te = tan((elevationDeg).toRadians())
        val correctionArcSec = when {
            elevationDeg > 5.0 -> 58.1 / te - 0.07 / (te * te * te) + 0.000086 / (te * te * te * te * te)
            elevationDeg > -0.575 -> 1735.0 + elevationDeg *
                (-518.2 + elevationDeg * (103.4 + elevationDeg * (-12.79 + elevationDeg * 0.711)))
            else -> -20.772 / te
        }
        return correctionArcSec / 3600.0
    }

    private fun wrap360(degrees: Double): Double = ((degrees % 360.0) + 360.0) % 360.0
}
