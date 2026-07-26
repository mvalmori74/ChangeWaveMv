package com.changewave.ombraparking.core.shadow

import com.changewave.ombraparking.core.geo.LatLng
import com.changewave.ombraparking.core.geo.Vec2
import com.changewave.ombraparking.core.sun.SolarPosition
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.Instant

/** Un intervallo omogeneo di illuminazione su un punto. */
data class ShadeSlot(
    val start: Instant,
    val end: Instant,
    val quality: ShadeQuality,
    val obstacleName: String?,
) {
    val duration: Duration get() = end - start
}

/** Andamento dell'ombra su un punto lungo un arco di tempo. */
data class ShadeForecast(
    val point: Vec2,
    val slots: List<ShadeSlot>,
    val stepMinutes: Int,
) {
    val start: Instant? get() = slots.firstOrNull()?.start
    val end: Instant? get() = slots.lastOrNull()?.end

    fun qualityAt(instant: Instant): ShadeQuality? =
        slots.firstOrNull { instant >= it.start && instant < it.end }?.quality
            ?: slots.lastOrNull()?.takeIf { instant == it.end }?.quality

    /** Fine del periodo d'ombra che contiene [instant], oppure null se lì non c'è ombra. */
    fun shadeEndsAfter(instant: Instant): Instant? {
        val index = slots.indexOfFirst { instant >= it.start && instant < it.end }
        if (index < 0 || !slots[index].quality.isShaded) return null
        var end = slots[index].end
        var next = index + 1
        while (next < slots.size && slots[next].quality.isShaded) {
            end = slots[next].end
            next++
        }
        return end
    }

    /** Inizio della prossima ombra dopo [instant] (utile quando adesso è pieno sole). */
    fun nextShadeStart(instant: Instant): Instant? =
        slots.firstOrNull { it.quality.isShaded && it.start > instant }?.start

    /**
     * Prossimo momento in cui il punto torna in pieno sole dopo [instant].
     * È la domanda dell'auto parcheggiata: fra quanto la macchina prende sole?
     */
    fun nextSunStart(instant: Instant): Instant? =
        slots.firstOrNull { it.quality == ShadeQuality.SUN && it.start > instant }?.start

    /** Minuti totali di ombra (piena o filtrata) nella finestra analizzata, notte esclusa. */
    val shadedMinutes: Int
        get() = slots.filter { it.quality.isShaded }
            .sumOf { it.duration.inWholeMinutes }
            .toInt()

    val daylightMinutes: Int
        get() = slots.filter { it.quality != ShadeQuality.NIGHT }
            .sumOf { it.duration.inWholeMinutes }
            .toInt()
}

/**
 * Campiona [ShadowEngine.shadeAt] a passi regolari e comprime i campioni uguali in
 * intervalli: è quello che alimenta la barra oraria "ombra fino alle 17:30".
 */
object ShadeTimeline {

    fun compute(
        point: Vec2,
        obstacles: List<Obstacle>,
        location: LatLng,
        from: Instant,
        to: Instant,
        stepMinutes: Int = 10,
    ): ShadeForecast {
        require(stepMinutes > 0) { "stepMinutes deve essere positivo" }
        if (to <= from) return ShadeForecast(point, emptyList(), stepMinutes)

        val step = stepMinutes.minutes
        val slots = mutableListOf<ShadeSlot>()
        var slotStart = from
        var currentQuality: ShadeQuality? = null
        var currentName: String? = null

        var cursor = from
        while (cursor < to) {
            val sun = SolarPosition.at(cursor, location)
            val info = ShadowEngine.shadeAt(point, obstacles, sun)
            if (currentQuality == null) {
                currentQuality = info.quality
                currentName = info.obstacle?.name
            } else if (info.quality != currentQuality) {
                slots += ShadeSlot(slotStart, cursor, currentQuality, currentName)
                slotStart = cursor
                currentQuality = info.quality
                currentName = info.obstacle?.name
            } else if (currentName == null) {
                currentName = info.obstacle?.name
            }
            cursor += step
        }
        if (currentQuality != null) {
            slots += ShadeSlot(slotStart, to, currentQuality, currentName)
        }
        return ShadeForecast(point, slots, stepMinutes)
    }
}
