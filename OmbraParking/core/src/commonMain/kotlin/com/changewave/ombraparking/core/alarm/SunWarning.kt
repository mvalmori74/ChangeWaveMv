package com.changewave.ombraparking.core.alarm

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.Instant

/**
 * Quando avvisare che il sole sta per arrivare sull'auto.
 *
 * La regola sta qui, e non nel codice che parla con WorkManager, perché è l'unica parte
 * che può sbagliare in modo silenzioso: un avviso che arriva quando il sole è già sul
 * cofano non serve a niente, e uno programmato nel passato non parte proprio.
 */
object SunWarning {

    /** Preavviso: quanto prima dell'arrivo del sole ha senso essere avvisati. */
    const val DEFAULT_LEAD_MINUTES = 15L

    /**
     * Istante in cui far scattare la notifica, o null se non c'è niente da annunciare
     * (sole già arrivato, oppure nessun arrivo previsto in giornata).
     *
     * Se manca meno del preavviso l'avviso scatta subito: tardi è meglio che mai, purché
     * il sole non sia già lì.
     */
    fun triggerTime(
        sunArrivesAt: Instant?,
        now: Instant,
        leadMinutes: Long = DEFAULT_LEAD_MINUTES,
    ): Instant? {
        val arrival = sunArrivesAt ?: return null
        if (arrival <= now) return null
        val ideal = arrival - leadMinutes.toInt().minutes
        return if (ideal < now) now else ideal
    }

    /** Ritardo da dare allo scheduler, mai negativo. */
    fun delay(
        sunArrivesAt: Instant?,
        now: Instant,
        leadMinutes: Long = DEFAULT_LEAD_MINUTES,
    ): Duration? {
        val trigger = triggerTime(sunArrivesAt, now, leadMinutes) ?: return null
        val delay = trigger - now
        return if (delay.isNegative()) Duration.ZERO else delay
    }
}
