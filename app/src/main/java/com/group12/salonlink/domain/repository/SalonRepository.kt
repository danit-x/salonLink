package com.group12.salonlink.domain.repository

import com.group12.salonlink.domain.model.Salon
import com.group12.salonlink.domain.model.SalonService
import com.group12.salonlink.domain.model.Staff
import com.group12.salonlink.domain.model.StaffBlock
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * Read access to the salon catalogue: salons, services, staff and staff blocks.
 *
 * WHY everything is a Flow: Firestore pushes changes live. If the owner edits a price, every
 * open Discovery or Service-selection screen updates by itself, with no "pull to refresh".
 * Flows fail with [com.group12.salonlink.domain.model.DataException].
 *
 * Write operations (owner CRUD) arrive in Phase 6.
 */
interface SalonRepository {

    /** All salons, by name (Discovery). */
    fun observeSalons(): Flow<List<Salon>>

    /** One salon, or null if it doesn't exist. */
    fun observeSalon(salonId: String): Flow<Salon?>

    /**
     * The salon's services, by name.
     * @param includeInactive true only for the owner's editor, customers never see hidden services.
     */
    fun observeServices(salonId: String, includeInactive: Boolean = false): Flow<List<SalonService>>

    /** Active staff members, by name. */
    fun observeStaff(salonId: String): Flow<List<Staff>>

    /** One staff member (Barber profile), or null if missing. */
    fun observeStaffMember(salonId: String, staffId: String): Flow<Staff?>

    /** Blocks of [staffId] that overlap [from, to), by start time. Used to compute availability. */
    fun observeBlocks(salonId: String, staffId: String, from: Instant, to: Instant): Flow<List<StaffBlock>>
}
