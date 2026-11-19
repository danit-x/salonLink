package com.group12.salonlink.feature.customer.booking

import com.group12.salonlink.core.util.SalonTime
import com.group12.salonlink.domain.booking.SlotOption
import com.group12.salonlink.domain.booking.SlotProblem
import com.group12.salonlink.domain.model.Salon
import com.group12.salonlink.domain.model.SalonService
import com.group12.salonlink.domain.model.ServiceCategory
import com.group12.salonlink.domain.model.Staff
import com.group12.salonlink.domain.model.TimeRange
import com.group12.salonlink.domain.model.WeeklyHours
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * Sample data for Compose @Previews only (the Figma demo: Kasun at Aura Salon, Thu 8 Oct 2026).
 * Lets every screen be viewed in Android Studio without Firebase.
 */
internal object BookingFlowPreviewData {
    private val nineToSeven = TimeRange(LocalTime.of(9, 0), LocalTime.of(19, 0))
    private val weekdays = WeeklyHours(DayOfWeek.entries.filter { it != DayOfWeek.SUNDAY }.associateWith { nineToSeven })

    val salon = Salon("aura", "Aura Salon", "24 Havelock Road, Colombo 05", "Colombo 05", "0112345678", weekdays)

    val kasun = Staff(
        id = "kasun", name = "Kasun Perera",
        bio = "Modern cuts, natural texture and attention to detail for every Sri Lankan hair type. Let’s create your signature look.",
        specialties = listOf("Fade", "Colour", "Curly Hair", "Beard Styling"),
        rating = 4.9, reviewCount = 128, yearsExperience = 8, photoUrl = null, portfolioUrls = emptyList(),
        workingHours = weekdays,
    )

    val services = listOf(
        SalonService("haircut", "Haircut", "Consultation, wash, cut & finish", ServiceCategory.HAIRCUT, 30, 1500),
        SalonService("beard-trim", "Beard Trim", "Shape, tidy & finish", ServiceCategory.BEARD, 20, 800),
        SalonService("hair-colour", "Hair Colour", "Full colour application", ServiceCategory.COLOUR, 90, 4500),
        SalonService("hair-treatment", "Hair Treatment", "Nourishing treatment", ServiceCategory.TREATMENT, 60, 4000),
    )

    private val today = LocalDate.of(2026, 10, 6)
    private val thursday = LocalDate.of(2026, 10, 8)
    private fun at(h: Int, m: Int) = SalonTime.toInstant(thursday, LocalTime.of(h, m))

    val servicesState = BookingFlowUiState(
        loading = false, salon = salon, staff = kasun, services = services,
        selectedServiceIds = setOf("haircut", "beard-trim"),
        today = today, lastBookableDate = today.plusDays(60), visibleMonth = YearMonth.of(2026, 10),
    )

    val dateTimeState = servicesState.copy(
        selectedDate = thursday,
        slots = SlotsState.Ready(
            listOf(
                SlotOption(at(16, 0), SlotProblem.TAKEN), SlotOption(at(16, 30), null), SlotOption(at(17, 0), null),
                SlotOption(at(17, 30), null), SlotOption(at(18, 0), null), SlotOption(at(18, 30), SlotProblem.OUTSIDE_HOURS),
            ),
        ),
        selectedStart = at(17, 30),
    )
}
