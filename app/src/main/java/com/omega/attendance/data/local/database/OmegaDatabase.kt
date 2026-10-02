package com.omega.attendance.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.omega.attendance.data.local.dao.*
import com.omega.attendance.data.local.entity.*

@Database(entities = [AcademicYearEntity::class, GradeEntity::class, SectionEntity::class, HallEntity::class, ClassEntity::class, StudentEntity::class, AttendanceEntity::class, SchoolCalendarEntity::class, AppSettingEntity::class, AuditLogEntity::class, StaffEntity::class, StaffAttendanceEntity::class], version = 4, exportSchema = true)
@TypeConverters(Converters::class)
abstract class OmegaDatabase : RoomDatabase() {
    abstract fun studentDao(): StudentDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun structureDao(): StructureDao
    abstract fun auditDao(): AuditDao
    abstract fun calendarDao(): CalendarDao
    abstract fun staffDao(): StaffDao
    abstract fun staffAttendanceDao(): StaffAttendanceDao
}
