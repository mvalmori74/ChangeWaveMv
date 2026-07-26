package com.changewave.ombraparking.core.sun

import com.changewave.ombraparking.core.geo.LatLng
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus

/**
 * Alba, tramonto e mezzogiorno solare di una giornata.
 * [sunrise] e [sunset] sono nulli nei giorni di sole di mezzanotte o notte polare.
 */
data class DayLight(
    val date: LocalDate,
    val sunrise: Instant?,
    val sunset: Instant?,
    val solarNoon: Instant,
    val maxElevationDegrees: Double,
)

/**
 * Ricava alba e tramonto campionando l'elevazione solare sull'arco della giornata e
 * raffinando gli attraversamenti dell'orizzonte per bisezione.
 *
 * È qualche riga in più di una formula chiusa ma riusa lo stesso [SolarPosition] del
 * resto dell'app, quindi orari e ombre non possono divergere fra loro.
 */
object SunTimes {

    /** Il disco solare tocca l'orizzonte quando il centro è a -0.833° (rifrazione + semidiametro). */
    private const val HORIZON_DEG = -0.833
    private const val SAMPLE_MINUTES = 10

    fun forDay(date: LocalDate, zone: TimeZone, location: LatLng): DayLight {
        val dayStart = date.atStartOfDayIn(zone)
        val dayEnd = date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)
        val step = SAMPLE_MINUTES.minutes

        var sunrise: Instant? = null
        var sunset: Instant? = null
        var noon = dayStart
        var maxElevation = Double.NEGATIVE_INFINITY

        var previous = dayStart
        var previousElevation = elevationAt(previous, location)
        if (previousElevation > maxElevation) {
            maxElevation = previousElevation
            noon = previous
        }

        var current = previous + step
        while (current <= dayEnd) {
            val elevation = elevationAt(current, location)
            if (elevation > maxElevation) {
                maxElevation = elevation
                noon = current
            }
            val wasBelow = previousElevation < HORIZON_DEG
            val isBelow = elevation < HORIZON_DEG
            if (wasBelow && !isBelow && sunrise == null) {
                sunrise = refine(previous, current, location, rising = true)
            } else if (!wasBelow && isBelow && sunrise != null && sunset == null) {
                sunset = refine(previous, current, location, rising = false)
            } else if (!wasBelow && isBelow && sunrise == null && sunset == null) {
                // Il sole era già alto a mezzanotte (fusi orari spostati): è comunque un tramonto.
                sunset = refine(previous, current, location, rising = false)
            }
            previous = current
            previousElevation = elevation
            current += step
        }

        val preciseNoon = refineNoon(noon, location)
        return DayLight(date, sunrise, sunset, preciseNoon, elevationAt(preciseNoon, location))
    }

    /**
     * Il campionamento a passi di 10 minuti individua il mezzogiorno con quella precisione:
     * l'elevazione cambia pochissimo intorno al massimo, ma l'azimut si sposta di un quarto
     * di grado al minuto, quindi il massimo va raffinato con una ricerca ternaria.
     */
    private fun refineNoon(approximate: Instant, location: LatLng): Instant {
        var low = approximate - SAMPLE_MINUTES.minutes
        var high = approximate + SAMPLE_MINUTES.minutes
        repeat(40) {
            val span = high.toEpochMilliseconds() - low.toEpochMilliseconds()
            val first = Instant.fromEpochMilliseconds(low.toEpochMilliseconds() + span / 3)
            val second = Instant.fromEpochMilliseconds(high.toEpochMilliseconds() - span / 3)
            if (elevationAt(first, location) < elevationAt(second, location)) {
                low = first
            } else {
                high = second
            }
        }
        return Instant.fromEpochMilliseconds(
            (low.toEpochMilliseconds() + high.toEpochMilliseconds()) / 2
        )
    }

    private fun refine(before: Instant, after: Instant, location: LatLng, rising: Boolean): Instant {
        var low = before
        var high = after
        repeat(20) { // ~0.6 s di precisione partendo da un intervallo di 10 minuti
            val mid = Instant.fromEpochMilliseconds(
                (low.toEpochMilliseconds() + high.toEpochMilliseconds()) / 2
            )
            val above = elevationAt(mid, location) >= HORIZON_DEG
            if (above == rising) high = mid else low = mid
        }
        return high
    }

    private fun elevationAt(instant: Instant, location: LatLng): Double =
        SolarPosition.at(instant, location).elevationDegrees
}
