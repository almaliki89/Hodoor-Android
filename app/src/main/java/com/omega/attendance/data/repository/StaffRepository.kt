package com.omega.attendance.data.repository

import androidx.room.withTransaction
import com.omega.attendance.data.local.dao.*
import com.omega.attendance.data.local.database.OmegaDatabase
import com.omega.attendance.data.local.entity.*
import com.omega.attendance.data.importer.StaffImportRow
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject

data class StaffImportResult(val added: Int, val updated: Int, val unchanged: Int)
data class BulkDeleteResult(val people: Int, val attendance: Int)

class StaffRepository @Inject constructor(
    private val db: OmegaDatabase,
    private val staff: StaffDao,
    private val attendance: StaffAttendanceDao,
    private val audit: AuditDao
) {
    fun search(query: String): Flow<List<StaffEntity>> = staff.search(query.trim())
    fun activeCount(): Flow<Int> = staff.activeCount()
    fun attendanceForDate(date: Long): Flow<List<StaffAttendanceEntity>> = attendance.forDate(date)
    fun attendanceCount(date: Long): Flow<Int> = attendance.countForDate(date)
    fun attendanceCount(date: Long, status: AttendanceStatus): Flow<Int> = attendance.countByStatus(date, status)
    suspend fun allActive(): List<StaffEntity> = staff.all().filter { it.status == "ACTIVE" }

    suspend fun add(fullName: String, jobTitle: String, phone: String?, notes: String?, specialty: String? = null): Long = db.withTransaction {
        require(fullName.isNotBlank()) { "أدخل اسم الموظف" }; require(jobTitle.isNotBlank()) { "أدخل المسمى الوظيفي" }
        val now = System.currentTimeMillis()
        val uuid = UUID.randomUUID().toString()
        val id = staff.insert(StaffEntity(uuid = uuid, qrValue = "STF:$uuid", fullName = fullName.trim(), jobTitle = jobTitle.trim(), specialty = specialty?.trim()?.ifBlank { null }, phone = phone?.trim()?.ifBlank { null }, notes = notes?.trim()?.ifBlank { null }, createdAt = now, updatedAt = now))
        audit.insert(AuditLogEntity(action = "STAFF_CREATED", timestamp = now, user = "ADMIN", entityType = "STAFF", entityId = id.toString(), description = "Staff record created"))
        id
    }

    suspend fun edit(id: Long, fullName: String, jobTitle: String, phone: String?, notes: String?, specialty: String? = null) = db.withTransaction {
        require(fullName.isNotBlank()) { "أدخل اسم الموظف" }; require(jobTitle.isNotBlank()) { "أدخل المسمى الوظيفي" }
        val current = staff.byId(id) ?: error("الموظف غير موجود"); val now = System.currentTimeMillis()
        staff.update(current.copy(fullName = fullName.trim(), jobTitle = jobTitle.trim(), specialty = specialty?.trim()?.ifBlank { null }, phone = phone?.trim()?.ifBlank { null }, notes = notes?.trim()?.ifBlank { null }, updatedAt = now))
        audit.insert(AuditLogEntity(action = "STAFF_EDITED", timestamp = now, user = "ADMIN", entityType = "STAFF", entityId = id.toString(), description = "Staff record updated"))
    }

    suspend fun archive(id: Long, archived: Boolean) = db.withTransaction {
        val current = staff.byId(id) ?: error("الموظف غير موجود"); val now = System.currentTimeMillis()
        staff.update(current.copy(status = if (archived) "ARCHIVED" else "ACTIVE", updatedAt = now))
        audit.insert(AuditLogEntity(action = if (archived) "STAFF_ARCHIVED" else "STAFF_RESTORED", timestamp = now, user = "ADMIN", entityType = "STAFF", entityId = id.toString(), description = if (archived) "Staff archived" else "Staff restored"))
    }

    suspend fun delete(id: Long) = db.withTransaction {
        require(staff.delete(id) == 1) { "الموظف غير موجود" }
        audit.insert(AuditLogEntity(action = "STAFF_DELETED", timestamp = System.currentTimeMillis(), user = "ADMIN", entityType = "STAFF", entityId = id.toString(), description = "Staff record deleted"))
    }

    suspend fun deleteAll(): BulkDeleteResult = db.withTransaction {
        val attendanceRecords = attendance.countAll()
        val deleted = staff.deleteAll()
        audit.insert(AuditLogEntity(action = "STAFF_DELETED_ALL", timestamp = System.currentTimeMillis(), user = "ADMIN", entityType = "STAFF", entityId = null, description = "Deleted $deleted staff records and $attendanceRecords attendance records"))
        BulkDeleteResult(deleted, attendanceRecords)
    }

    suspend fun import(rows: List<StaffImportRow>): StaffImportResult = db.withTransaction {
        require(rows.isNotEmpty()) { "لم يُعثر على سجلات كادر صالحة في الملف" }
        val current = staff.all().toMutableList()
        var added = 0
        var updated = 0
        var unchanged = 0
        rows.distinctBy { it.fullName.normalizedName() }.forEach { row ->
            val match = current.firstOrNull { existing -> samePerson(existing, row) }
            val now = System.currentTimeMillis()
            if (match == null) {
                val uuid = UUID.randomUUID().toString()
                val entity = StaffEntity(uuid = uuid, qrValue = "STF:$uuid", fullName = row.fullName.trim(), jobTitle = row.jobTitle.trim(), specialty = row.specialty?.trim()?.ifBlank { null }, createdAt = now, updatedAt = now)
                val id = staff.insert(entity)
                current += entity.copy(id = id)
                added++
            } else {
                val changed = match.fullName != row.fullName.trim() || match.jobTitle != row.jobTitle.trim() || match.specialty != row.specialty?.trim()?.ifBlank { null }
                if (changed) {
                    val value = match.copy(fullName = row.fullName.trim(), jobTitle = row.jobTitle.trim(), specialty = row.specialty?.trim()?.ifBlank { null }, updatedAt = now)
                    staff.update(value)
                    current[current.indexOf(match)] = value
                    updated++
                } else unchanged++
            }
        }
        val now = System.currentTimeMillis()
        audit.insert(AuditLogEntity(action = "STAFF_IMPORTED", timestamp = now, user = "ADMIN", entityType = "STAFF", entityId = null, description = "Staff document import: added=$added, updated=$updated, unchanged=$unchanged"))
        StaffImportResult(added, updated, unchanged)
    }

    private fun samePerson(existing: StaffEntity, imported: StaffImportRow): Boolean {
        val oldName = existing.fullName.normalizedName()
        val newName = imported.fullName.normalizedName()
        if (oldName == newName) return true
        if (existing.jobTitle.normalizedName() != imported.jobTitle.normalizedName()) return false
        val oldParts = oldName.split(' ')
        val newParts = newName.split(' ')
        return (oldParts.size == 3 && newParts.size > 3 && newName.startsWith("$oldName ")) ||
            (newParts.size == 3 && oldParts.size > 3 && oldName.startsWith("$newName "))
    }

    private fun String.normalizedName(): String = replace(Regex("\\s+"), " ").trim()
}
