package com.omega.attendance.domain.usecase

import androidx.room.withTransaction
import com.omega.attendance.data.local.dao.*
import com.omega.attendance.data.local.database.OmegaDatabase
import com.omega.attendance.data.local.entity.*
import com.omega.attendance.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import java.time.*
import java.util.UUID
import javax.inject.Inject

sealed interface RegistrationResult {
    data class Success(val status: AttendanceStatus) : RegistrationResult
    data object Duplicate : RegistrationResult
    data object Unknown : RegistrationResult
    data class Failure(val message: String) : RegistrationResult
}

object AttendancePolicy {
    fun status(time: LocalTime, lateAfterMinutes: Int): AttendanceStatus =
        if (time.toSecondOfDay() >= lateAfterMinutes * 60) AttendanceStatus.LATE else AttendanceStatus.PRESENT
}

class RegisterAttendanceUseCase @Inject constructor(
    private val db: OmegaDatabase,
    private val students: StudentDao,
    private val attendance: AttendanceDao,
    private val staff: StaffDao,
    private val staffAttendance: StaffAttendanceDao,
    private val structure: StructureDao,
    private val settings: SettingsRepository
) {
    suspend operator fun invoke(rawToken: String, instant: Instant = Instant.now()): RegistrationResult {
        val token = rawToken.trim()
        if ((!token.startsWith("STD:") && !token.startsWith("STF:")) || token.length > 128) return RegistrationResult.Unknown
        val prefs = settings.preferences.first(); val zone = ZoneId.systemDefault(); val local = instant.atZone(zone)
        return runCatching {
            db.withTransaction {
                if (token.startsWith("STF:")) {
                    val member = staff.byQr(token) ?: return@withTransaction RegistrationResult.Unknown
                    val date = local.toLocalDate().toEpochDay()
                    if (staffAttendance.existing(member.id, date) != null) return@withTransaction RegistrationResult.Duplicate
                    val now = instant.toEpochMilli(); val status = AttendancePolicy.status(local.toLocalTime(), prefs.lateMinutes)
                    val record = StaffAttendanceEntity(uuid = UUID.randomUUID().toString(), staffId = member.id, staffNameSnapshot = member.fullName, jobTitleSnapshot = member.jobTitle, attendanceDate = date, attendanceTime = local.toLocalTime().toSecondOfDay().toLong(), timestamp = now, status = status, checkInType = CheckInType.QR, createdAt = now, updatedAt = now)
                    return@withTransaction if (staffAttendance.insert(record) == -1L) RegistrationResult.Duplicate else RegistrationResult.Success(status)
                }
                val student = students.byQr(token) ?: return@withTransaction RegistrationResult.Unknown
                val date = local.toLocalDate().toEpochDay()
                attendance.existing(student.id, date, student.academicYearId)?.let { return@withTransaction RegistrationResult.Duplicate }
                val grade = structure.gradeById(student.gradeId)?.name.orEmpty()
                val schoolClass = structure.classById(student.classId)?.name.orEmpty()
                val section = structure.sectionById(student.sectionId)?.name.orEmpty()
                val hall = student.hallId?.let { structure.hallById(it)?.name }
                val now = instant.toEpochMilli()
                val record = AttendanceEntity(
                    uuid = UUID.randomUUID().toString(), studentId = student.id,
                    studentNameSnapshot = student.fullName, studentNumberSnapshot = student.studentNumber,
                    gradeSnapshot = grade, classSnapshot = schoolClass, sectionSnapshot = section, hallSnapshot = hall,
                    academicYearId = student.academicYearId, attendanceDate = date,
                    attendanceTime = local.toLocalTime().toSecondOfDay().toLong(), timestamp = now,
                    status = AttendancePolicy.status(local.toLocalTime(), prefs.lateMinutes),
                    checkInType = CheckInType.QR, deviceIdentifier = "LOCAL_ANDROID_DEVICE",
                    createdAt = now, updatedAt = now
                )
                val id = attendance.insert(record)
                if (id == -1L) RegistrationResult.Duplicate
                else RegistrationResult.Success(record.status)
            }
        }.getOrElse { RegistrationResult.Failure("تعذر تسجيل الحضور") }
    }
}
