package com.omega.attendance.data.repository

import androidx.room.withTransaction
import com.omega.attendance.data.local.dao.*
import com.omega.attendance.data.local.database.OmegaDatabase
import com.omega.attendance.data.local.entity.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class AttendanceRepository @Inject constructor(private val db: OmegaDatabase, private val dao: AttendanceDao, private val audit: AuditDao) {
    fun forDate(date: Long): Flow<List<AttendanceEntity>> = dao.forDate(date)
    fun forStudent(studentId: Long) = dao.forStudent(studentId)
    fun count(date: Long) = dao.countForDate(date)
    fun count(date: Long, status: AttendanceStatus) = dao.countByStatus(date, status)
    fun trend(from: Long, to: Long) = dao.trend(from, to)
    fun dailyReport(date: Long, status: String?, classId: Long?, hallId: Long?) = dao.dailyReport(date, status, classId, hallId)
    fun rangeReport(from: Long, to: Long, classId: Long?, hallId: Long?) = dao.rangeReport(from, to, classId, hallId)
    suspend fun changeStatus(id: Long, status: AttendanceStatus) = db.withTransaction {
        val old = dao.byId(id) ?: error("السجل غير موجود"); val now = System.currentTimeMillis()
        dao.update(old.copy(status = status, updatedAt = now))
        audit.insert(AuditLogEntity(action = "ATTENDANCE_EDITED", timestamp = now, user = "ADMIN", entityType = "ATTENDANCE", entityId = id.toString(), description = "Status changed from ${old.status} to $status"))
    }
    suspend fun delete(id: Long) = db.withTransaction {
        requireNotNull(dao.byId(id)) { "السجل غير موجود" }
        require(dao.delete(id) == 1) { "تعذر حذف السجل" }
        audit.insert(AuditLogEntity(action = "ATTENDANCE_DELETED", timestamp = System.currentTimeMillis(), user = "ADMIN", entityType = "ATTENDANCE", entityId = id.toString(), description = "Attendance record deleted"))
    }
}
