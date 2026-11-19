package com.group12.salonlink.core.navigation

import kotlinx.serialization.Serializable

/*
 * =====================================================================================
 * Type-safe navigation routes
 * -------------------------------------------------------------------------------------
 * Each screen is a @Serializable object (or data class when it needs arguments, e.g.
 * `BarberProfileRoute(staffId)` in Phase 4). Navigation Compose turns them into routes for
 * us, so a typo or a missing argument is a *compile* error instead of a runtime crash.
 *
 * Two levels:
 *  1. Signed-out screens (AuthNavHost).
 *  2. Tabs inside each role's shell (the screens that show a bottom bar).
 * Which level is visible is decided by SalonLinkRoot from the AuthState, not by routes.
 * =====================================================================================
 */

// ---- 1. Signed out -----------------------------------------------------------------
@Serializable data object WelcomeRoute
@Serializable data object SignInRoute
@Serializable data object RegisterRoute

// ---- 2a. Customer tabs (match the Figma bottom navigation) --------------------------
@Serializable data object ExploreRoute
@Serializable data object BookingsRoute
@Serializable data object ConciergeRoute
@Serializable data object MyStyleRoute

// ---- 2a-i. Customer booking journey (full-screen, no bottom bar, as in Figma) --------

/** The notification feed behind the bell on Explore. */
@Serializable data object NotificationsRoute

/** Figma "Barber profile". */
@Serializable data class BarberProfileRoute(val salonId: String, val staffId: String)

/**
 * The 3-step booking flow as a nested navigation graph. Its two screens (Services, Date & time)
 * share ONE BookingFlowViewModel that lives as long as this graph is on the back stack, so the
 * chosen services survive going forward and back.
 *
 * @param preselectedServiceIds ticks these services on arrival ("Book again" on Discovery).
 */
@Serializable data class BookingFlowRoute(
    val salonId: String,
    val staffId: String,
    val preselectedServiceIds: List<String> = emptyList(),
)

/** Step 1 of 3, Figma "Service selection". Arguments come from the parent [BookingFlowRoute]. */
@Serializable data object ServiceSelectionRoute

/** Step 2 of 3, Figma "Date & time". Its "Confirm appointment" button creates the booking. */
@Serializable data object DateTimeRoute

/**
 * Step 3 of 3, Figma "Booking confirmation". Also opens an existing booking (from Discovery).
 *
 * @param justBooked true right after "Confirm appointment": shows the celebration and "All booked".
 */
@Serializable data class BookingConfirmationRoute(
    val salonId: String,
    val bookingId: String,
    val justBooked: Boolean = false,
)

// ---- 2b. Staff tabs ----------------------------------------------------------------
@Serializable data object StaffTodayRoute
@Serializable data object StaffWalkInsRoute
@Serializable data object StaffSalonRoute

// ---- 2c. Owner tabs ----------------------------------------------------------------
@Serializable data object OwnerDashboardRoute
@Serializable data object OwnerServicesRoute
@Serializable data object OwnerStaffRoute
@Serializable data object OwnerSalonRoute
