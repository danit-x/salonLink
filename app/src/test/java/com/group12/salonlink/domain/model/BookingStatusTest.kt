package com.group12.salonlink.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookingStatusTest {

    @Test
    fun `the normal visit moves forward step by step`() {
        assertTrue(BookingStatus.BOOKED.canTransitionTo(BookingStatus.CHECKED_IN))
        assertTrue(BookingStatus.CHECKED_IN.canTransitionTo(BookingStatus.WAITING))
        assertTrue(BookingStatus.WAITING.canTransitionTo(BookingStatus.IN_SERVICE))
        assertTrue(BookingStatus.IN_SERVICE.canTransitionTo(BookingStatus.COMPLETED))
    }

    @Test
    fun `a service cannot be undone or skipped to completed`() {
        assertFalse(BookingStatus.COMPLETED.canTransitionTo(BookingStatus.BOOKED))
        assertFalse(BookingStatus.IN_SERVICE.canTransitionTo(BookingStatus.CANCELLED))
        assertFalse(BookingStatus.BOOKED.canTransitionTo(BookingStatus.COMPLETED))
    }

    @Test
    fun `finished statuses are terminal`() {
        listOf(BookingStatus.COMPLETED, BookingStatus.CANCELLED, BookingStatus.MISSED).forEach {
            assertTrue("$it should be terminal", it.isTerminal)
        }
        assertFalse(BookingStatus.BOOKED.isTerminal)
    }

    @Test
    fun `only live bookings hold their time slot`() {
        assertTrue(BookingStatus.BOOKED.holdsSlot)
        assertTrue(BookingStatus.IN_SERVICE.holdsSlot)
        assertFalse(BookingStatus.CANCELLED.holdsSlot)
        assertFalse(BookingStatus.MISSED.holdsSlot)
    }

    @Test
    fun `an unknown status keeps the slot protected`() {
        assertEquals(BookingStatus.BOOKED, BookingStatus.fromValue("EXPLODED"))
        assertEquals(BookingStatus.BOOKED, BookingStatus.fromValue(null))
    }
}
