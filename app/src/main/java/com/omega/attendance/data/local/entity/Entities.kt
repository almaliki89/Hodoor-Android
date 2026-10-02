package com.omega.attendance.data.local.entity

import androidx.room.*

enum class StudentStatus { ACTIVE, ARCHIVED }
enum class AttendanceStatus { PRESENT, LATE, ABSENT, EXCUSED }
enum class CheckInType { QR, MANUAL }
enum class CalendarDayType { SCHOOL_DAY, WEEKEND, HOLIDAY, EXAM, CLOSED }

@Entity(tableName = "academic_years", indices = [Index(value = ["name"], unique = true)])
data class AcademicYearEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val startDate: Long, val endDate: Long, val isActive: Boolean = false)

@Entity(tableName = "grades", indices = [Index(value = ["name", "academicYearId"], unique = true), Index("academicYearId")], foreignKeys = [ForeignKey(entity = AcademicYearEntity::class, parentColumns = ["id"], childColumns = ["academicYearId"], onDelete = ForeignKey.RESTRICT)])
data class GradeEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val academicYearId: Long, val status: String = "ACTIVE")

@Entity(tableName = "sections", indices = [Index(value = ["name", "academicYearId"], unique = true), Index("academicYearId")], foreignKeys = [ForeignKey(entity = AcademicYearEntity::class, parentColumns = ["id"], childColumns = ["academicYearId"], onDelete = ForeignKey.RESTRICT)])
data class SectionEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val academicYearId: Long, val status: String = "ACTIVE")

@Entity(tableName = "halls", indices = [Index(value = ["number"], unique = true)])
data class HallEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val number: String, val building: String? = null, val floor: String? = null, val capacity: Int? = null, val notes: String? = null)

@Entity(tableName = "classes", indices = [Index(value = ["name", "academicYearId"], unique = true), Index("gradeId"), Index("sectionId"), Index("hallId"), Index("academicYearId")], foreignKeys = [ForeignKey(entity = GradeEntity::class, parentColumns = ["id"], childColumns = ["gradeId"], onDelete = ForeignKey.RESTRICT), ForeignKey(entity = SectionEntity::class, parentColumns = ["id"], childColumns = ["sectionId"], onDelete = ForeignKey.RESTRICT), ForeignKey(entity = HallEntity::class, parentColumns = ["id"], childColumns = ["hallId"], onDelete = ForeignKey.SET_NULL), ForeignKey(entity = AcademicYearEntity::class, parentColumns = ["id"], childColumns = ["academicYearId"], onDelete = ForeignKey.RESTRICT)])
data class ClassEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val gradeId: Long, val sectionId: Long, val hallId: Long? = null, val academicYearId: Long, val status: String = "ACTIVE")

@Entity(tableName = "students", indices = [Index(value = ["uuid"], unique = true), Index(value = ["studentNumber"], unique = true), Index(value = ["qrValue"], unique = true), Index("fullName"), Index("gradeId"), Index("classId"), Index("sectionId"), Index("hallId"), Index("academicYearId")], foreignKeys = [ForeignKey(entity = GradeEntity::class, parentColumns = ["id"], childColumns = ["gradeId"], onDelete = ForeignKey.RESTRICT), ForeignKey(entity = ClassEntity::class, parentColumns = ["id"], childColumns = ["classId"], onDelete = ForeignKey.RESTRICT), ForeignKey(entity = SectionEntity::class, parentColumns = ["id"], childColumns = ["sectionId"], onDelete = ForeignKey.RESTRICT), ForeignKey(entity = HallEntity::class, parentColumns = ["id"], childColumns = ["hallId"], onDelete = ForeignKey.SET_NULL), ForeignKey(entity = AcademicYearEntity::class, parentColumns = ["id"], childColumns = ["academicYearId"], onDelete = ForeignKey.RESTRICT)])
data class StudentEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val uuid: String, val studentNumber: String, val qrValue: String, val fullName: String, val gender: String? = null, val dateOfBirth: Long? = null, val gradeId: Long, val classId: Long, val sectionId: Long, val hallId: Long? = null, val academicYearId: Long, val phone: String? = null, val parentPhone: String? = null, val photoPath: String? = null, val status: StudentStatus = StudentStatus.ACTIVE, val notes: String? = null, val createdAt: Long, val updatedAt: Long)

@Entity(tableName = "attendance", indices = [Index(value = ["studentId", "attendanceDate", "academicYearId"], unique = true), Index("studentId"), Index("attendanceDate"), Index("status"), Index("academicYearId"), Index("timestamp")], foreignKeys = [ForeignKey(entity = StudentEntity::class, parentColumns = ["id"], childColumns = ["studentId"], onDelete = ForeignKey.RESTRICT), ForeignKey(entity = AcademicYearEntity::class, parentColumns = ["id"], childColumns = ["academicYearId"], onDelete = ForeignKey.RESTRICT)])
data class AttendanceEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val uuid: String, val studentId: Long, val studentNameSnapshot: String, val studentNumberSnapshot: String, val gradeSnapshot: String, val classSnapshot: String, val sectionSnapshot: String, val hallSnapshot: String?, val academicYearId: Long, val attendanceDate: Long, val attendanceTime: Long, val timestamp: Long, val status: AttendanceStatus, val checkInType: CheckInType, val deviceIdentifier: String, val notes: String? = null, val createdAt: Long, val updatedAt: Long)

@Entity(tableName = "school_calendar", indices = [Index(value = ["date", "academicYearId"], unique = true), Index("academicYearId")], foreignKeys = [ForeignKey(entity = AcademicYearEntity::class, parentColumns = ["id"], childColumns = ["academicYearId"], onDelete = ForeignKey.CASCADE)])
data class SchoolCalendarEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val date: Long, val academicYearId: Long, val type: CalendarDayType, val title: String? = null, val notes: String? = null)

@Entity(tableName = "app_settings", indices = [Index(value = ["key"], unique = true)])
data class AppSettingEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val key: String, val value: String, val updatedAt: Long)

@Entity(tableName = "audit_logs", indices = [Index("timestamp"), Index("entityType"), Index("entityId")])
data class AuditLogEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val action: String, val timestamp: Long, val user: String, val entityType: String, val entityId: String?, val description: String)
