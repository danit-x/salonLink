package com.group12.salonlink.domain.repository

import com.group12.salonlink.domain.model.Salon
import com.group12.salonlink.domain.model.SalonService
import com.group12.salonlink.domain.model.Staff
import com.group12.salonlink.domain.model.StaffBlock

/**
 * Owner-only writes to the salon catalogue (proposal FR-10, US-09). Reads stay in [SalonRepository].
 * The security rules allow these writes only for the OWNER of that salon.
 *
 * All functions throw [com.group12.salonlink.domain.model.DataException] on failure.
 */
interface SalonAdminRepository {

    /** Saves name, address, phone and opening hours. */
    suspend fun saveSalon(salon: Salon)

    /** Creates (blank [SalonService.id]) or updates a service. Returns its id. */
    suspend fun saveService(salonId: String, service: SalonService): String

    /**
     * Creates (blank [Staff.id]) or updates a staff member. Returns the id.
     * Ratings are never written from the app; a new staff member starts at 0.
     */
    suspend fun saveStaff(salonId: String, staff: Staff): String

    /** Adds blocked time (lunch, leave…) for a staff member. */
    suspend fun addBlock(salonId: String, staffId: String, block: StaffBlock)

    suspend fun deleteBlock(salonId: String, staffId: String, blockId: String)

    /**
     * Lets [email] register as STAFF of this salon, linked to the staff record [staffId]
     * (see AuthRepository.register and `invites/{email}`).
     */
    suspend fun inviteStaff(salonId: String, staffId: String, email: String)
}
