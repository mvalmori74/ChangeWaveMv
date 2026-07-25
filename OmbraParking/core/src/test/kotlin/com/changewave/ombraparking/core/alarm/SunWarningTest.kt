package com.changewave.ombraparking.core.alarm

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SunWarningTest {

    private val now: Instant = Instant.parse("2024-07-15T12:00:00Z")

    @Test
    fun `avvisa il preavviso prima dell'arrivo del sole`() {
        val arrival = now.plus(Duration.ofHours(2))

        assertEquals(
            arrival.minus(Duration.ofMinutes(15)),
            SunWarning.triggerTime(arrival, now, leadMinutes = 15),
        )
    }

    @Test
    fun `se manca meno del preavviso avvisa subito`() {
        val arrival = now.plus(Duration.ofMinutes(5))

        assertEquals(now, SunWarning.triggerTime(arrival, now, leadMinutes = 15))
        assertEquals(Duration.ZERO, SunWarning.delay(arrival, now, leadMinutes = 15))
    }

    @Test
    fun `niente avviso se il sole è già arrivato`() {
        assertNull(SunWarning.triggerTime(now.minus(Duration.ofMinutes(1)), now))
        assertNull(SunWarning.triggerTime(now, now))
    }

    @Test
    fun `niente avviso se il sole non arriva`() {
        assertNull(SunWarning.triggerTime(sunArrivesAt = null, now = now))
        assertNull(SunWarning.delay(sunArrivesAt = null, now = now))
    }

    @Test
    fun `il ritardo non è mai negativo`() {
        val delay = SunWarning.delay(now.plus(Duration.ofMinutes(45)), now, leadMinutes = 15)

        assertEquals(Duration.ofMinutes(30), delay)
    }
}
