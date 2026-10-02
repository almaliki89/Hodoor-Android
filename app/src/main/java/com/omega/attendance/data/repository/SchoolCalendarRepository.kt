package com.omega.attendance.data.repository

import androidx.room.withTransaction
import com.omega.attendance.data.local.dao.AuditDao
import com.omega.attendance.data.local.dao.CalendarDao
import com.omega.attendance.data.local.database.OmegaDatabase
import com.omega.attendance.data.local.entity.AuditLogEntity
import com.omega.attendance.data.local.entity.CalendarDayType
import com.omega.attendance.data.local.entity.SchoolCalendarEntity
import java.time.LocalDate
import javax.inject.Inject

class SchoolCalendarRepository @Inject constructor(
    private val db: OmegaDatabase,
    private val calendar: CalendarDao,
    private val audit: AuditDao
) {
    suspend fun save(date: LocalDate, yearId: Long, type: CalendarDayType, title: String) = db.withTransaction {
        require(yearId > 0) { "اختر السنة الدراسية" }
        calendar.upsert(SchoolCalendarEntity(
            date = date.toEpochDay(),
            academicYearId = yearId,
            type = type,
            title = title.trim().ifBlank { null }
        ))
        log("CALENDAR_UPDATED", date, yearId)
    }

    suspend fun delete(date: LocalDate, yearId: Long) = db.withTransaction {
        require(yearId > 0) { "اختر السنة الدراسية" }
        require(calendar.delete(date.toEpochDay(), yearId) > 0) { "لا يوجد يوم مخصص لهذا التاريخ" }
        log("CALENDAR_DELETED", date, yearId)
    }

    private suspend fun log(action: String, date: LocalDate, yearId: Long) {
        audit.insert(AuditLogEntity(
            action = action,
            timestamp = System.currentTimeMillis(),
            user = "ADMIN",
            entityType = "SCHOOL_CALENDAR",
            entityId = yearId.toString() + ":" + date,
            description = action
        ))
    }
}
