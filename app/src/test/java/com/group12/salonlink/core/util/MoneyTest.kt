package com.group12.salonlink.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTest {

    @Test
    fun `rupees use thousands separators like the Figma price labels`() {
        assertEquals("Rs. 800", Money.formatLkr(800))
        assertEquals("Rs. 2,300", Money.formatLkr(2_300))
        assertEquals("Rs. 125,000", Money.formatLkr(125_000))
    }

    @Test
    fun `zero is shown, not hidden`() {
        assertEquals("Rs. 0", Money.formatLkr(0))
    }
}
