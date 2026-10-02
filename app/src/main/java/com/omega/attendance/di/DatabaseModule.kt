package com.omega.attendance.di

import android.content.Context
import androidx.room.Room
import com.omega.attendance.data.local.dao.*
import com.omega.attendance.data.local.database.OmegaDatabase
import com.omega.attendance.data.local.database.MIGRATION_1_2
import com.omega.attendance.data.local.database.MIGRATION_2_3
import com.omega.attendance.data.local.database.MIGRATION_3_4
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton fun database(@ApplicationContext context: Context): OmegaDatabase =
        Room.databaseBuilder(context, OmegaDatabase::class.java, "omega_attendance.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
    @Provides fun students(db: OmegaDatabase): StudentDao = db.studentDao()
    @Provides fun attendance(db: OmegaDatabase): AttendanceDao = db.attendanceDao()
    @Provides fun structure(db: OmegaDatabase): StructureDao = db.structureDao()
    @Provides fun audit(db: OmegaDatabase): AuditDao = db.auditDao()
    @Provides fun calendar(db: OmegaDatabase): CalendarDao = db.calendarDao()
    @Provides fun staff(db: OmegaDatabase): StaffDao = db.staffDao()
    @Provides fun staffAttendance(db: OmegaDatabase): StaffAttendanceDao = db.staffAttendanceDao()
}
