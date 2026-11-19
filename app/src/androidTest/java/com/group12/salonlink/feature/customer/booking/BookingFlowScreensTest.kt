// Covers: T02 (booking flow UI), T03 (taken times can't be picked), NFR-10 (errors shown)
package com.group12.salonlink.feature.customer.booking

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.group12.salonlink.R
import com.group12.salonlink.core.designsystem.theme.SalonLinkTheme
import com.group12.salonlink.core.util.SalonTime
import com.group12.salonlink.domain.booking.SlotProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests of the two booking steps, without Firebase. The screens are stateless, so each
 * test hosts them with a small state holder that applies events the way the ViewModel does
 * (the ViewModel's own logic is covered by `BookingFlowViewModelTest` on the JVM).
 */
@RunWith(AndroidJUnit4::class)
class BookingFlowScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun s(id: Int, vararg args: Any) = context.getString(id, *args)

    // ---------------------------------------------------------------- Step 1: services

    @Test
    fun ticking_services_updates_total_and_unlocks_continue() {
        var state by mutableStateOf(BookingFlowPreviewData.servicesState.copy(selectedServiceIds = emptySet()))
        var continued = false
        compose.setContent {
            SalonLinkTheme {
                ServiceSelectionScreen(
                    state = state,
                    onEvent = { e ->
                        if (e is BookingFlowEvent.ToggleService) {
                            val ids = state.selectedServiceIds
                            state = state.copy(selectedServiceIds = if (e.serviceId in ids) ids - e.serviceId else ids + e.serviceId)
                        }
                    },
                    onBack = {},
                    onContinue = { continued = true },
                )
            }
        }

        // Nothing chosen yet: the main button is disabled.
        compose.onNodeWithText(s(R.string.services_none_selected)).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.services_continue)).assertIsNotEnabled()

        // Haircut (Rs. 1,500) + Beard Trim (Rs. 800) = Rs. 2,300, 50 minutes.
        compose.onNodeWithText("Haircut").performScrollTo().performClick()
        compose.onNodeWithText("Beard Trim").performScrollTo().performClick()
        assertEquals(setOf("haircut", "beard-trim"), state.selectedServiceIds)
        compose.onNodeWithText(s(R.string.services_duration, 50)).assertIsDisplayed()
        compose.onNodeWithText("Rs. 2,300").assertIsDisplayed()

        compose.onNodeWithText(s(R.string.services_continue)).assertIsEnabled().performClick()
        assertTrue(continued)
    }

    @Test
    fun service_card_is_a_checkbox_for_talkback() {
        compose.setContent {
            SalonLinkTheme {
                ServiceSelectionScreen(BookingFlowPreviewData.servicesState, onEvent = {}, onBack = {}, onContinue = {})
            }
        }
        // The whole card is one toggleable node, so TalkBack reads it as a single checkbox.
        compose.onNode(hasToggleableText("Haircut")).assertIsOn()
        compose.onNode(hasToggleableText("Hair Colour")).assertIsOff()
    }

    // ---------------------------------------------------------------- Step 2: date & time

    @Test
    fun taken_time_is_announced_and_cannot_be_picked() {
        val events = mutableListOf<BookingFlowEvent>()
        val state = BookingFlowPreviewData.dateTimeState.copy(selectedStart = null)
        compose.setContent {
            SalonLinkTheme { DateTimeScreen(state = state, onEvent = { events += it }, onBack = {}) }
        }
        val taken = (state.slots as SlotsState.Ready).options.first { it.problem == SlotProblem.TAKEN }
        val label = SalonTime.formatTime(taken.start)

        compose.onNodeWithContentDescription(s(R.string.cd_time_unavailable, label))
            .performScrollTo()
            .assertIsNotEnabled()
        // No time chosen yet: confirm is off and the hint asks for a time.
        compose.onNodeWithText(s(R.string.datetime_confirm)).assertIsNotEnabled()
        compose.onNodeWithText(s(R.string.datetime_pick_time)).assertIsDisplayed()
        assertTrue(events.none { it is BookingFlowEvent.SelectTime })
    }

    @Test
    fun picking_a_free_time_then_confirming_sends_both_events() {
        var state by mutableStateOf(BookingFlowPreviewData.dateTimeState.copy(selectedStart = null))
        val events = mutableListOf<BookingFlowEvent>()
        compose.setContent {
            SalonLinkTheme {
                DateTimeScreen(
                    state = state,
                    onEvent = { e ->
                        events += e
                        if (e is BookingFlowEvent.SelectTime) state = state.copy(selectedStart = e.start)
                    },
                    onBack = {},
                )
            }
        }
        val free = (state.slots as SlotsState.Ready).options.first { it.isAvailable }

        compose.onNodeWithText(SalonTime.formatTime(free.start)).performScrollTo().performClick()
        compose.onNodeWithText(s(R.string.datetime_confirm)).assertIsEnabled().performClick()

        assertEquals(BookingFlowEvent.SelectTime(free.start), events.first())
        assertEquals(BookingFlowEvent.Confirm, events.last())
    }

    @Test
    fun a_slot_taken_at_confirm_time_shows_a_clear_error() {
        val state = BookingFlowPreviewData.dateTimeState.copy(submitError = SubmitError.Unavailable(SlotProblem.TAKEN))
        compose.setContent {
            SalonLinkTheme { DateTimeScreen(state = state, onEvent = {}, onBack = {}) }
        }
        compose.onNodeWithText(s(R.string.data_error_slot_taken)).assertIsDisplayed()
    }

    /** A toggleable node (e.g. a service card) whose merged text contains [text]. */
    private fun hasToggleableText(text: String): SemanticsMatcher = isToggleable() and hasText(text, substring = true)
}
