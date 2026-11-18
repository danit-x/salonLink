package com.group12.salonlink.domain.booking

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SlotGridTest {

    // 3 Dec 2026, 17:30 in Colombo = 12:00 UTC.
    private val fiveThirty = Instant.parse("2026-12-03T12:00:00Z")

    @Test
    fun `slot numbers match the rules tests (epoch millis divided by 10 minutes)`() {
        assertEquals(fiveThirty.toEpochMilli() / 600_000, SlotGrid.slotOf(fiveThirty))
        assertEquals(fiveThirty, SlotGrid.startOf(SlotGrid.slotOf(fiveThirty)))
    }

    @Test
    fun `a time inside a slot rounds down to that slot`() {
        assertEquals(SlotGrid.slotOf(fiveThirty), SlotGrid.slotOf(fiveThirty.plusSeconds(9 * 60 + 59)))
    }

    @Test
    fun `only exact 10-minute boundaries are aligned`() {
        assertTrue(SlotGrid.isAligned(fiveThirty))
        assertFalse(SlotGrid.isAligned(fiveThirty.plusSeconds(60)))
        assertFalse(SlotGrid.isAligned(fiveThirty.plusNanos(1)))
    }

    @Test
    fun `Haircut plus Beard Trim (50 min) holds five slots`() {
        val slots = SlotGrid.slotsBetween(fiveThirty, fiveThirty.plusSeconds(50 * 60))
        assertEquals(5, slots.count())
        assertEquals(SlotGrid.slotOf(fiveThirty), slots.first)
    }

    @Test
    fun `durations round up to whole slots`() {
        assertEquals(3, SlotGrid.slotsFor(30))
        assertEquals(3, SlotGrid.slotsFor(25))
        assertEquals(1, SlotGrid.slotsFor(1))
    }
}
