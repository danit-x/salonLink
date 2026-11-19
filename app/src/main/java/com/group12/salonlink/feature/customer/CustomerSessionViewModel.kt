// Covers: FR-07 (reminders + notifications) · runs for as long as a customer is signed in
package com.group12.salonlink.feature.customer

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.group12.salonlink.core.notifications.SystemNotifier
import com.group12.salonlink.domain.model.AppNotification
import com.group12.salonlink.domain.model.AuthState
import com.group12.salonlink.domain.model.NotificationType
import com.group12.salonlink.domain.reminder.ReminderPlanner
import com.group12.salonlink.domain.repository.AuthRepository
import com.group12.salonlink.domain.repository.BookingRepository
import com.group12.salonlink.domain.repository.NotificationRepository
import com.group12.salonlink.domain.repository.ReminderScheduler
import com.group12.salonlink.domain.repository.WaitlistRepository
import com.group12.salonlink.domain.usecase.addBestEffort
import com.group12.salonlink.feature.customer.notifications.NotificationText
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Background jobs of a signed-in customer, owned by the customer shell:
 *
 *  1. Reminders: every change to "my bookings" re-plans the local WorkManager reminders
 *     (24 h and 2 h before). Cancelled bookings lose theirs automatically.
 *  2. Salon updates: when salon staff write into the customer's feed while the app is open
 *     (checked in, earlier slot free…), a phone notification is shown too.
 *  3. The unread count for the bell on Explore.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CustomerSessionViewModel @Inject constructor(
    authRepository: AuthRepository,
    bookingRepository: BookingRepository,
    private val notificationRepository: NotificationRepository,
    waitlistRepository: WaitlistRepository,
    private val reminderScheduler: ReminderScheduler,
    private val notifier: SystemNotifier,
    @param:ApplicationContext private val context: Context,
    private val clock: Clock,
) : ViewModel() {

    private val uid = authRepository.authState.filterIsInstance<AuthState.SignedIn>().map { it.user.uid }

    private val notifications = uid
        .flatMapLatest { notificationRepository.observeNotifications(it) }
        // A broken feed must not crash the shell; the feed screen shows the error itself.
        .catch { emit(emptyList()) }

    /** Unread entries for the bell badge. */
    val unreadCount: StateFlow<Int> = notifications
        .map { list -> list.count { !it.read } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Ids already turned into phone notifications (or already present when the app opened). */
    private val announced = mutableSetOf<String>()
    private var firstFeed = true

    init {
        viewModelScope.launch {
            uid.flatMapLatest { bookingRepository.observeCustomerBookings(it) }
                .catch { /* no bookings readable right now: keep the existing reminders */ }
                .collect { bookings -> reminderScheduler.apply(ReminderPlanner.plan(bookings, clock.instant())) }
        }
        viewModelScope.launch {
            notifications.collect { list ->
                if (firstFeed) {
                    // WHY: don't replay old news as phone notifications every time the app opens.
                    announced += list.map { it.id }
                    firstFeed = false
                    return@collect
                }
                list.filter { !it.read && it.id !in announced && it.type.isFromSalon() }.forEach { announce(it) }
                announced += list.map { it.id }
            }
        }
    }

    /** Waitlist offers already announced (entry + offer time), so each is shown once. */
    private val announcedOffers = mutableSetOf<String>()

    init {
        // 4. Waitlist offers: someone cancelled and the freed time is held for this customer.
        viewModelScope.launch {
            uid.flatMapLatest { id -> waitlistRepository.observeMine(id).map { id to it } }
                .catch { /* can't read the waitlist right now; the Bookings tab shows it later */ }
                .collect { (customer, entries) ->
                    val now = clock.instant()
                    entries.forEach { entry ->
                        val offer = entry.activeOffer(now) ?: return@forEach
                        val key = "${entry.id}_${offer.start.epochSecond}"
                        if (!announcedOffers.add(key)) return@forEach
                        val notification = AppNotification(
                            id = AppNotification.idFor(NotificationType.WAITLIST_OFFER, key),
                            type = NotificationType.WAITLIST_OFFER,
                            salonId = entry.salonId,
                            bookingId = entry.id,
                            title = "",
                            staffName = "",
                            start = offer.start,
                        )
                        notificationRepository.addBestEffort(customer, notification)
                        announce(notification)
                    }
                }
        }
    }

    /** Remove reminders on sign-out so the next person on this phone doesn't get them. */
    fun onSignOut() = reminderScheduler.cancelAll()

    private fun announce(notification: AppNotification) {
        val (title, body) = NotificationText.of(context, notification)
        notifier.show(key = notification.id, title = title, text = body, channel = SystemNotifier.CHANNEL_UPDATES)
    }

    /** The customer's own actions (booking, cancelling) don't need a phone notification. */
    private fun NotificationType.isFromSalon() =
        this == NotificationType.STATUS_CHANGED || this == NotificationType.WAITLIST_OFFER
}
