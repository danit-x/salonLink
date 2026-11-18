package com.group12.salonlink.data.firestore

import com.group12.salonlink.domain.model.Role
import com.group12.salonlink.domain.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserMappersTest {

    private val kasun = UserProfile(
        uid = "u1",
        name = "Kasun Perera",
        email = "kasun@aura.lk",
        phone = "0771234567",
        role = Role.STAFF,
        salonId = "aura",
        staffId = "kasun",
    )

    @Test
    fun `a profile survives a round trip through Firestore's map format`() {
        assertEquals(kasun, userProfileFromMap("u1", kasun.toFirestoreMap()))
    }

    @Test
    fun `field names match the security rules`() {
        assertEquals(setOf("name", "email", "phone", "role", "salonId", "staffId"), kasun.toFirestoreMap().keys)
    }

    @Test
    fun `an unknown role is treated as the least-privileged CUSTOMER`() {
        val profile = userProfileFromMap("u2", mapOf("name" to "X", "email" to "x@y.lk", "role" to "SUPERADMIN"))
        assertEquals(Role.CUSTOMER, profile.role)
    }

    @Test
    fun `an invite can grant STAFF or OWNER but nothing else`() {
        assertEquals(Role.STAFF, inviteFromMap("a@b.lk", mapOf("role" to "STAFF", "salonId" to "aura"))?.role)
        assertEquals(Role.OWNER, inviteFromMap("a@b.lk", mapOf("role" to "OWNER", "salonId" to "aura"))?.role)
        assertNull(inviteFromMap("a@b.lk", mapOf("role" to "CUSTOMER", "salonId" to "aura")))
        assertNull(inviteFromMap("a@b.lk", mapOf("role" to "STAFF"))) // no salon → invalid
    }

    @Test
    fun `first name is used for greetings`() {
        assertEquals("Kasun", kasun.firstName)
    }
}
