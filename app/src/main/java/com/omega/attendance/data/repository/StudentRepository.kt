package com.omega.attendance.data.repository

import androidx.room.withTransaction
import com.omega.attendance.data.local.dao.*
import com.omega.attendance.data.local.database.OmegaDatabase
import com.omega.attendance.data.local.entity.*
import com.omega.attendance.domain.model.NewStudent
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject

class StudentRepository @Inject constructor(
    private val db: OmegaDatabase,
    private val students: StudentDao,
    private val attendance: AttendanceDao,
    private val audit: AuditDao
) {
    fun search(query: String, classId: Long? = null, hallId: Long? = null): Flow<List<StudentEntity>> = students.search(query.trim(), classId, hallId)
    fun observe(id: Long) = students.observe(id)
    fun activeCount() = students.activeCount()
    suspend fun all() = students.all()
    suspend fun existingNumbers() = students.allStudentNumbers().toSet()

    suspend fun add(value: NewStudent): Long = db.withTransaction {
        require(value.fullName.isNotBlank())
        val now = System.currentTimeMillis(); val uuid = UUID.randomUUID().toString()
        val number = value.studentNumber.trim().ifBlank { "S${uuid.replace("-", "").take(16).uppercase()}" }
        val id = students.insert(value.copy(studentNumber = number).toEntity(uuid, "STD:$uuid", now))
        audit.insert(AuditLogEntity(action = "STUDENT_CREATED", timestamp = now, user = "ADMIN", entityType = "STUDENT", entityId = id.toString(), description = "Student record created"))
        id
    }

    suspend fun addAll(values: List<NewStudent>) = db.withTransaction {
        val now = System.currentTimeMillis()
        val rows = values.map { value ->
            require(value.fullName.isNotBlank())
            val uuid = UUID.randomUUID().toString()
            val number = value.studentNumber.trim().ifBlank { "S${uuid.replace("-", "").take(16).uppercase()}" }
            value.copy(studentNumber = number).toEntity(uuid, "STD:$uuid", now)
        }
        students.insertAll(rows)
        audit.insert(AuditLogEntity(action = "IMPORT_COMPLETED", timestamp = now, user = "ADMIN", entityType = "STUDENT", entityId = null, description = "Imported ${rows.size} student records"))
    }

    suspend fun archive(id: Long) = db.withTransaction {
        val now = System.currentTimeMillis(); students.archive(id, now)
        audit.insert(AuditLogEntity(action = "STUDENT_ARCHIVED", timestamp = now, user = "ADMIN", entityType = "STUDENT", entityId = id.toString(), description = "Student archived"))
    }
    suspend fun restore(id: Long) = db.withTransaction {
        val now = System.currentTimeMillis(); students.restore(id, now)
        audit.insert(AuditLogEntity(action = "STUDENT_RESTORED", timestamp = now, user = "ADMIN", entityType = "STUDENT", entityId = id.toString(), description = "Student restored"))
    }

    suspend fun edit(id: Long, fullName: String, parentPhone: String?, option: com.omega.attendance.data.local.dao.ClassOption, hallId: Long?) = db.withTransaction {
        val current = students.byId(id) ?: error("الطالب غير موجود"); val now = System.currentTimeMillis()
        require(fullName.isNotBlank()) { "أدخل اسم الطالب" }
        students.update(current.copy(fullName = fullName.trim(), parentPhone = parentPhone?.trim()?.ifBlank { null }, gradeId = option.gradeId, classId = option.classId, sectionId = option.sectionId, hallId = hallId, academicYearId = option.academicYearId, updatedAt = now))
        audit.insert(AuditLogEntity(action = "STUDENT_EDITED", timestamp = now, user = "ADMIN", entityType = "STUDENT", entityId = id.toString(), description = "Student record updated"))
    }

    suspend fun deletePermanently(id: Long) = db.withTransaction {
        requireNotNull(students.byId(id)) { "الطالب غير موجود" }
        val records = attendance.deleteForStudent(id)
        require(students.delete(id) == 1) { "تعذر حذف الطالب" }
        audit.insert(AuditLogEntity(action = "STUDENT_DELETED", timestamp = System.currentTimeMillis(), user = "ADMIN", entityType = "STUDENT", entityId = id.toString(), description = "Student and $records attendance records deleted"))
    }

    suspend fun deleteAllPermanently(): BulkDeleteResult = db.withTransaction {
        val records = attendance.deleteAll()
        val deleted = students.deleteAll()
        audit.insert(AuditLogEntity(action = "STUDENTS_DELETED_ALL", timestamp = System.currentTimeMillis(), user = "ADMIN", entityType = "STUDENT", entityId = null, description = "Deleted $deleted student records and $records attendance records"))
        BulkDeleteResult(deleted, records)
    }

    private fun NewStudent.toEntity(uuid: String, qr: String, now: Long) = StudentEntity(
        uuid = uuid, studentNumber = studentNumber.trim(), qrValue = qr, fullName = fullName.trim(),
        gradeId = gradeId, classId = classId, sectionId = sectionId, hallId = hallId,
        academicYearId = academicYearId, parentPhone = parentPhone?.trim()?.ifBlank { null }, notes = notes,
        createdAt = now, updatedAt = now
    )
}
