package com.group12.salonlink.domain.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormValidatorsTest {

    @Test
    fun `valid emails pass and typos are caught`() {
        assertNull(FormValidators.email("kavindu@example.com"))
        assertNull(FormValidators.email("  Kasun.Perera@aura.lk  ")) // surrounding spaces are trimmed
        assertEquals(FieldError.REQUIRED, FormValidators.email(""))
        assertEquals(FieldError.INVALID_EMAIL, FormValidators.email("kavindu@"))
        assertEquals(FieldError.INVALID_EMAIL, FormValidators.email("kavindu.example.com"))
        assertEquals(FieldError.INVALID_EMAIL, FormValidators.email("ka vindu@example.com"))
    }

    @Test
    fun `new passwords need at least eight characters`() {
        assertEquals(FieldError.REQUIRED, FormValidators.password(""))
        assertEquals(FieldError.PASSWORD_TOO_SHORT, FormValidators.password("1234567"))
        assertNull(FormValidators.password("12345678"))
    }

    @Test
    fun `sign-in only requires that a password was typed`() {
        assertEquals(FieldError.REQUIRED, FormValidators.passwordPresent(""))
        assertNull(FormValidators.passwordPresent("short")) // old accounts may have short passwords
    }

    @Test
    fun `names are required and limited in length`() {
        assertEquals(FieldError.REQUIRED, FormValidators.name("   "))
        assertEquals(FieldError.TOO_LONG, FormValidators.name("x".repeat(61)))
        assertNull(FormValidators.name("Kavindu Perera"))
    }

    @Test
    fun `phone is optional but must be a Sri Lankan mobile when given`() {
        assertNull(FormValidators.phone(""))
        assertNull(FormValidators.phone("077 123 4567"))
        assertNull(FormValidators.phone("+94 77 123-4567"))
        assertEquals(FieldError.INVALID_PHONE, FormValidators.phone("011 234 5678")) // landline
        assertEquals(FieldError.INVALID_PHONE, FormValidators.phone("12345"))
    }

    @Test
    fun `emails are normalised so invites match regardless of capitals`() {
        assertEquals("kasun@aura.lk", FormValidators.normaliseEmail("  Kasun@Aura.LK "))
    }
}
