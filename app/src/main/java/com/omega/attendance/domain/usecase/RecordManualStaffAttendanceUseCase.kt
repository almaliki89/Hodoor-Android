package com.omega.attendance.domain.usecase

import androidx.room.withTransaction
import com.omega.attendance.data.local.dao.AuditDao
import com.omega.attendance.data.local.dao.StaffAttendanceDao
import com.omega.attendance.data.local.dao.StaffDao
import com.omega.attendance.data.local.database.OmegaDatabase
import com.omega.attendance.data.local.entity.*
import java.time.*
import java.util.UUID
import javax.inject.Inject

class RecordManualStaffAttendanceUseCase @Inject constructor(
    private val db: OmegaDatabase, private val staff: StaffDao,
    private val attendance: StaffAttendanceDao, private val audit: AuditDao
) {
    suspend operator fun invoke(staffId: Long, status: AttendanceStatus = AttendanceStatus.PRESENT, instant: Instant = Instant.now()): Boolean = db.withTransaction {
        val member = staff.byId(staffId) ?: error("الموظف غير موجود")
        require(member.status == "ACTIVE") { "الموظف مؤرشف" }
        val local = instant.atZone(ZoneId.systemDefault()); val date = local.toLocalDate().toEpochDay()
        if (attendance.existing(staffId, date) != null) return@withTransaction false
        val now = instant.toEpochMilli()
        val row = StaffAttendanceEntity(uuid = UUID.randomUUID().toString(), staffId = member.id, staffNameSnapshot = member.fullName, jobTitleSnapshot = member.jobTitle, attendanceDate = date, attendanceTime = local.toLocalTime().toSecondOfDay().toLong(), timestamp = now, status = status, checkInType = CheckInType.MANUAL, createdAt = now, updatedAt = now)
        val id = attendance.insert(row); if (id == -1L) return@withTransaction false
        audit.insert(AuditLogEntity(action = "STAFF_ATTENDANCE_MANUAL_ADDED", timestamp = now, user = "ADMIN", entityType = "STAFF_ATTENDANCE", entityId = id.toString(), description = "Manual staff attendance added")); true
    }
}
