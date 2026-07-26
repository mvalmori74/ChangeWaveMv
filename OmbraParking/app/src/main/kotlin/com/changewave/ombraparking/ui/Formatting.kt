package com.changewave.ombraparking.ui

import androidx.compose.ui.graphics.Color
import com.changewave.ombraparking.core.geo.LatLng
import com.changewave.ombraparking.core.geo.LocalPlane
import com.changewave.ombraparking.core.shadow.ShadeQuality
import com.changewave.ombraparking.ui.theme.ShadeColors
import java.util.Locale
import kotlin.math.atan2
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

fun Instant.asClockTime(zone: TimeZone): String {
    val time = toLocalDateTime(zone).time
    return minuteOfDayAsClock(time.hour * 60 + time.minute)
}

fun minuteOfDayAsClock(minuteOfDay: Int): String =
    String.format(Locale.ITALY, "%02d:%02d", minuteOfDay / 60, minuteOfDay % 60)

/** "2 h 30 min", "45 min". */
fun formatDuration(minutes: Int): String {
    if (minutes < 60) return "$minutes min"
    val hours = minutes / 60
    val rest = minutes % 60
    return if (rest == 0) "$hours h" else "$hours h $rest min"
}

val ShadeQuality.label: String
    get() = when (this) {
        ShadeQuality.SUN -> "Pieno sole"
        ShadeQuality.DAPPLED -> "Ombra degli alberi"
        ShadeQuality.SHADE -> "All'ombra"
        ShadeQuality.NIGHT -> "Notte"
    }

val ShadeQuality.emoji: String
    get() = when (this) {
        ShadeQuality.SUN -> "☀️"
        ShadeQuality.DAPPLED -> "🌤"
        ShadeQuality.SHADE -> "🌑"
        ShadeQuality.NIGHT -> "🌙"
    }

val ShadeQuality.color: Color
    get() = when (this) {
        ShadeQuality.SUN -> ShadeColors.sun
        ShadeQuality.DAPPLED -> ShadeColors.dappled
        ShadeQuality.SHADE -> ShadeColors.shade
        ShadeQuality.NIGHT -> ShadeColors.night
    }

/** Distanza e direzione di un punto rispetto a un altro. */
data class RelativePosition(val distanceMeters: Double, val azimuthDegrees: Double)

fun relativePosition(from: LatLng, to: LatLng): RelativePosition {
    val offset = LocalPlane(from).toLocal(to)
    val azimuth = Math.toDegrees(atan2(offset.x, offset.y))
    return RelativePosition(
        distanceMeters = offset.length,
        azimuthDegrees = ((azimuth % 360.0) + 360.0) % 360.0,
    )
}

/** "80 m", "1,2 km". */
fun formatDistance(meters: Double): String = when {
    meters < 1000 -> "${meters.toInt()} m"
    else -> String.format(Locale.ITALY, "%.1f km", meters / 1000)
}

/** "Nord-est", "Sud"… a partire da un azimut in gradi. */
fun cardinalName(azimuthDegrees: Double): String {
    val names = listOf("Nord", "Nord-est", "Est", "Sud-est", "Sud", "Sud-ovest", "Ovest", "Nord-ovest")
    val index = (((azimuthDegrees % 360.0) + 360.0) % 360.0 / 45.0).let { Math.round(it).toInt() % 8 }
    return names[index]
}
