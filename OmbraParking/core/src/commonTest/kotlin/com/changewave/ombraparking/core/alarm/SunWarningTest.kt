package com.changewave.ombraparking.core.alarm

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SunWarningTest {

    private val now: Instant = Instant.parse("2024-07-15T12:00:00Z")

    @Test
    fun `avvisa il preavviso prima dell'arrivo del sole`() {
        val arrival = now + 2.hours

        assertEquals(
            arrival - 15.minutes,
            SunWarning.triggerTime(arrival, now, leadMinutes = 15),
        )
    }

    @Test
    fun `se manca meno del preavviso avvisa subito`() {
        val arrival = now + 5.minutes

        assertEquals(now, SunWarning.triggerTime(arrival, now, leadMinutes = 15))
        assertEquals(Duration.ZERO, SunWarning.delay(arrival, now, leadMinutes = 15))
    }

    @Test
    fun `niente avviso se il sole è già arrivato`() {
        assertNull(SunWarning.triggerTime(now - 1.minutes, now))
        assertNull(SunWarning.triggerTime(now, now))
    }

    @Test
    fun `niente avviso se il sole non arriva`() {
        assertNull(SunWarning.triggerTime(sunArrivesAt = null, now = now))
        assertNull(SunWarning.delay(sunArrivesAt = null, now = now))
    }

    @Test
    fun `il ritardo non è mai negativo`() {
        val delay = SunWarning.delay(now + 45.minutes, now, leadMinutes = 15)

        assertEquals(30.minutes, delay)
    }
}
