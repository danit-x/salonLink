package com.group12.salonlink.testing

import com.group12.salonlink.core.util.SalonTime
import com.group12.salonlink.domain.booking.SlotGrid
import com.group12.salonlink.domain.model.Booking
import com.group12.salonlink.domain.model.BookingStatus
import com.group12.salonlink.domain.model.DataError
import com.group12.salonlink.domain.model.DataException
import com.group12.salonlink.domain.model.Salon
import com.group12.salonlink.domain.model.SalonService
import com.group12.salonlink.domain.model.Staff
import com.group12.salonlink.domain.model.StaffBlock
import com.group12.salonlink.domain.repository.BookingRepository
import com.group12.salonlink.domain.repository.SalonRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/*
 * In-memory stand-ins for the salon and booking repositories, used by use-case tests.
 * They behave like a tiny Firestore: the booking fake keeps slot locks and refuses a
 * second booking on a taken slot, just like the real transaction + rules.
 */

class FakeSalonRepository : SalonRepository {
    val salons = MutableStateFlow<Map<String, Salon>>(emptyMap())
    val services = MutableStateFlow<List<SalonService>>(emptyList())
    val staff = MutableStateFlow<List<Staff>>(emptyList())
    val blocks = MutableStateFlow<List<StaffBlock>>(emptyList())

    override fun observeSalons(): Flow<List<Salon>> = salons.map { it.values.sortedBy(Salon::name) }
    override fun observeSalon(salonId: String): Flow<Salon?> = salons.map { it[salonId] }
    override fun observeServices(salonId: String, includeInactive: Boolean): Flow<List<SalonService>> =
        services.map { list -> list.filter { includeInactive || it.active } }
    override fun observeStaff(salonId: String, includeInactive: Boolean): Flow<List<Staff>> =
        staff.map { list -> list.filter { includeInactive || it.active } }
    override fun observeStaffMember(salonId: String, staffId: String): Flow<Staff?> =
        staff.map { list -> list.firstOrNull { it.id == staffId } }
    override fun observeBlocks(salonId: String, staffId: String, from: Instant, to: Instant): Flow<List<StaffBlock>> =
        blocks.map { list -> list.filter { it.overlaps(from, to) } }
}

class FakeBookingRepository : BookingRepository {
    val bookings = MutableStateFlow<Map<String, Booking>>(emptyMap())

    /** slot → booking id, like the slotLocks collection. */
    private val locks = MutableStateFlow<Map<Pair<String, Long>, String>>(emptyMap())

    var createCalls = 0
        private set

    /** When set, the next createBooking fails with it (e.g. NETWORK, or SLOT_TAKEN for a lost race). */
    var nextCreateError: DataException? = null

    override fun observeBooking(salonId: String, bookingId: String) = bookings.map { it[bookingId] }
    override fun observeCustomerBookings(customerId: String) =
        bookings.map { all -> all.values.filter { it.customerId == customerId }.sortedByDescending { it.start } }
    override fun observeStaffDay(salonId: String, staffId: String, date: LocalDate) =
        bookings.map { all -> all.values.filter { it.staffId == staffId && SalonTime.localDateOf(it.start) == date } }
    override fun observeSalonDay(salonId: String, date: LocalDate) =
        bookings.map { all -> all.values.filter { SalonTime.localDateOf(it.start) == date } }

    override fun observeTakenSlots(salonId: String, staffId: String, date: LocalDate): Flow<Set<Long>> =
        locks.map { all -> all.keys.filter { it.first == staffId }.map { it.second }.toSet() }

    override suspend fun createBooking(booking: Booking): Booking {
        createCalls++
        nextCreateError?.let { nextCreateError = null; throw it }
        bookings.value[booking.id]?.let { return it } // idempotent, like the real one
        val slots = SlotGrid.slotsBetween(booking.start, booking.end).map { booking.staffId to it }
        if (slots.any { it in locks.value }) throw DataException(DataError.SLOT_TAKEN)
        locks.value = locks.value + slots.associateWith { booking.id }
        bookings.value = bookings.value + (booking.id to booking)
        return booking
    }

    override suspend fun cancelBooking(salonId: String, bookingId: String) {
        val booking = bookings.value[bookingId] ?: throw DataException(DataError.NOT_FOUND)
        if (booking.status == BookingStatus.CANCELLED) return
        if (!booking.status.canTransitionTo(BookingStatus.CANCELLED)) throw DataException(DataError.NOT_ALLOWED)
        locks.value = locks.value.filterValues { it != bookingId }
        bookings.value = bookings.value + (bookingId to booking.copy(status = BookingStatus.CANCELLED))
    }

    override suspend fun updateStatus(salonId: String, bookingId: String, status: BookingStatus) {
        val booking = bookings.value[bookingId] ?: throw DataException(DataError.NOT_FOUND)
        if (booking.status == status) return
        if (!booking.status.canTransitionTo(status)) throw DataException(DataError.NOT_ALLOWED)
        if (status == BookingStatus.MISSED) locks.value = locks.value.filterValues { it != bookingId }
        bookings.value = bookings.value + (bookingId to booking.copy(status = status))
    }

    /** Test helper: pretend another phone grabbed these slots a moment ago. */
    fun lockExternally(staffId: String, from: Instant, to: Instant, bookingId: String = "someone-else") {
        locks.value = locks.value + SlotGrid.slotsBetween(from, to).associate { (staffId to it) to bookingId }
    }
}
