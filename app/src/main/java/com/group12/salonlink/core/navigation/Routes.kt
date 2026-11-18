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

// ---- 2b. Staff tabs ----------------------------------------------------------------
@Serializable data object StaffTodayRoute
@Serializable data object StaffWalkInsRoute
@Serializable data object StaffSalonRoute

// ---- 2c. Owner tabs ----------------------------------------------------------------
@Serializable data object OwnerDashboardRoute
@Serializable data object OwnerServicesRoute
@Serializable data object OwnerStaffRoute
@Serializable data object OwnerSalonRoute
