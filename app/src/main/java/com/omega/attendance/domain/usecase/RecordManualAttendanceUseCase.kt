package com.omega.attendance.domain.usecase

import androidx.room.withTransaction
import com.omega.attendance.data.local.dao.*
import com.omega.attendance.data.local.database.OmegaDatabase
import com.omega.attendance.data.local.entity.*
import java.time.*
import java.util.UUID
import javax.inject.Inject

class RecordManualAttendanceUseCase @Inject constructor(
    private val db: OmegaDatabase, private val students: StudentDao,
    private val attendance: AttendanceDao, private val structure: StructureDao,
    private val audit: AuditDao
) {
    suspend operator fun invoke(studentId: Long, status: AttendanceStatus, instant: Instant = Instant.now()): Boolean = db.withTransaction {
        val student = students.byId(studentId) ?: error("الطالب غير موجود")
        require(student.status == StudentStatus.ACTIVE) { "الطالب مؤرشف" }
        val local = instant.atZone(ZoneId.systemDefault()); val date = local.toLocalDate().toEpochDay()
        if (attendance.existing(studentId, date, student.academicYearId) != null) return@withTransaction false
        val now = instant.toEpochMilli()
        val row = AttendanceEntity(uuid = UUID.randomUUID().toString(), studentId = student.id, studentNameSnapshot = student.fullName, studentNumberSnapshot = student.studentNumber, gradeSnapshot = structure.gradeById(student.gradeId)?.name.orEmpty(), classSnapshot = structure.classById(student.classId)?.name.orEmpty(), sectionSnapshot = structure.sectionById(student.sectionId)?.name.orEmpty(), hallSnapshot = student.hallId?.let { structure.hallById(it)?.name }, academicYearId = student.academicYearId, attendanceDate = date, attendanceTime = local.toLocalTime().toSecondOfDay().toLong(), timestamp = now, status = status, checkInType = CheckInType.MANUAL, deviceIdentifier = "LOCAL_ANDROID_DEVICE", createdAt = now, updatedAt = now)
        val id = attendance.insert(row); if (id == -1L) return@withTransaction false
        audit.insert(AuditLogEntity(action = "ATTENDANCE_MANUAL_ADDED", timestamp = now, user = "ADMIN", entityType = "ATTENDANCE", entityId = id.toString(), description = "Manual attendance added")); true
    }
}
