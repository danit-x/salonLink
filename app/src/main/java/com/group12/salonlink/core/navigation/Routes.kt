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
 *  1. Top-level, full-screen destinations (Welcome, Sign in, booking flow...).
 *  2. Tabs inside each role's shell (the screens that show a bottom bar).
 * =====================================================================================
 */

// ---- 1. Top level ------------------------------------------------------------------
@Serializable data object WelcomeRoute
@Serializable data object SignInRoute

/** Shell = bottom-bar container for one role. Only that role's tabs exist inside it. */
@Serializable data object CustomerShellRoute
@Serializable data object StaffShellRoute
@Serializable data object OwnerShellRoute

// ---- 2a. Customer tabs (match the Figma bottom navigation) --------------------------
@Serializable data object ExploreRoute
@Serializable data object BookingsRoute
@Serializable data object ConciergeRoute
@Serializable data object MyStyleRoute

// ---- 2b. Staff tabs ----------------------------------------------------------------
@Serializable data object StaffTodayRoute
@Serializable data object StaffWalkInsRoute
@Serializable data object StaffSalonRoute

// ---- 2c. Owner tabs ----------------------------------------------------------------
@Serializable data object OwnerDashboardRoute
@Serializable data object OwnerServicesRoute
@Serializable data object OwnerStaffRoute
@Serializable data object OwnerSalonRoute
