package com.group12.salonlink.core.util

import java.text.NumberFormat
import java.util.Locale

/**
 * Money helpers.
 *
 * WHY prices are whole rupees stored as Long (not Double): floating-point maths can turn
 * 0.1 + 0.2 into 0.30000000000000004. Salon prices are whole rupees, so an integer is exact.
 */
object Money {

    private val grouping: NumberFormat = NumberFormat.getIntegerInstance(Locale.ENGLISH).apply {
        isGroupingUsed = true
    }

    /** 2300 → "Rs. 2,300" (the format used throughout the Figma design). */
    fun formatLkr(rupees: Long): String = "Rs. ${grouping.format(rupees)}"
}
