package com.omega.attendance.data.repository

import androidx.room.withTransaction
import com.omega.attendance.data.local.dao.StructureDao
import com.omega.attendance.data.local.dao.AuditDao
import com.omega.attendance.data.local.database.OmegaDatabase
import com.omega.attendance.data.local.entity.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class SchoolStructureRepository @Inject constructor(
    private val db: OmegaDatabase,
    private val dao: StructureDao,
    private val audit: AuditDao
) {
    fun years(): Flow<List<AcademicYearEntity>> = dao.years()
    fun grades(yearId: Long) = dao.grades(yearId)
    fun sections(yearId: Long) = dao.sections(yearId)
    fun classes(yearId: Long) = dao.classes(yearId)
    fun halls() = dao.halls()
    fun classOptions() = dao.classOptions()

    suspend fun ensureDefaults(preferredYearId: Long): Long = db.withTransaction {
        val year = preferredYearId.takeIf { it > 0 }?.let { dao.yearById(it) }
            ?: dao.latestYear()
            ?: run {
                val today = LocalDate.now()
                val startYear = if (today.monthValue >= 9) today.year else today.year - 1
                val zone = ZoneId.systemDefault()
                val id = dao.insertYear(
                    AcademicYearEntity(
                        name = "$startYear-${startYear + 1}",
                        startDate = LocalDate.of(startYear, 9, 1).atStartOfDay(zone).toInstant().toEpochMilli(),
                        endDate = LocalDate.of(startYear + 1, 8, 31).atStartOfDay(zone).toInstant().toEpochMilli()
                    )
                )
                checkNotNull(dao.yearById(id))
            }

        if (dao.seedFlag("halls_seeded") == null) {
            (1..12).forEach { number ->
                val value = number.toString()
                if (dao.hallIdByNumber(value) == null) {
                    dao.insertHall(HallEntity(name = "القاعة $value", number = value))
                }
            }
            dao.insertSetting(AppSettingEntity(key = "halls_seeded", value = "1", updatedAt = System.currentTimeMillis()))
        }

        val classSeedKey = "classes_seeded_" + year.id
        if (dao.seedFlag(classSeedKey) == null) {
            listOf("الرابع", "الخامس", "السادس").forEach { gradeName ->
                val gradeId = dao.gradeId(gradeName, year.id)
                    ?: dao.insertGrade(GradeEntity(name = gradeName, academicYearId = year.id))
                listOf("أدبي", "علمي").forEach { track ->
                    val className = "$gradeName $track"
                    if (dao.classId(className, year.id) == null) {
                        val sectionId = dao.sectionId(track, year.id)
                            ?: dao.insertSection(SectionEntity(name = track, academicYearId = year.id))
                        dao.insertClass(
                            ClassEntity(
                                name = className,
                                gradeId = gradeId,
                                sectionId = sectionId,
                                academicYearId = year.id
                            )
                        )
                    }
                }
            }
            dao.insertSetting(AppSettingEntity(key = classSeedKey, value = "1", updatedAt = System.currentTimeMillis()))
        }
        year.id
    }

    suspend fun createYear(name: String, startDate: Long, endDate: Long) = dao.insertYear(AcademicYearEntity(name = name.trim(), startDate = startDate, endDate = endDate))
    suspend fun createHall(name: String, number: String, capacity: Int?) = dao.insertHall(HallEntity(name = name.trim(), number = number.trim(), capacity = capacity))
    suspend fun createClass(yearId: Long, gradeName: String, sectionName: String, className: String, hallId: Long?) = db.withTransaction {
        val cleanGrade = gradeName.trim(); val cleanSection = sectionName.trim()
        val gradeId = dao.gradeId(cleanGrade, yearId) ?: dao.insertGrade(GradeEntity(name = cleanGrade, academicYearId = yearId))
        val sectionId = dao.sectionId(cleanSection, yearId) ?: dao.insertSection(SectionEntity(name = cleanSection, academicYearId = yearId))
        dao.insertClass(ClassEntity(name = className.trim(), gradeId = gradeId, sectionId = sectionId, hallId = hallId, academicYearId = yearId))
    }

    suspend fun updateYear(id: Long, name: String) = db.withTransaction {
        val current = requireNotNull(dao.yearById(id)) { "السنة الدراسية غير موجودة" }
        val trimmed = name.trim()
        require(trimmed.isNotBlank()) { "أدخل اسم السنة الدراسية" }
        dao.updateYear(current.copy(name = trimmed))
        log("YEAR_EDITED", "ACADEMIC_YEAR", id)
    }

    suspend fun deleteYear(id: Long) = db.withTransaction {
        require(dao.studentsForYear(id) == 0 && dao.attendanceForYear(id) == 0) { "السنة مرتبطة بطلبة أو حضور" }
        dao.deleteClassesForYear(id)
        dao.deleteGradesForYear(id)
        dao.deleteSectionsForYear(id)
        require(dao.deleteYear(id) == 1) { "السنة الدراسية غير موجودة" }
        log("YEAR_DELETED", "ACADEMIC_YEAR", id)
    }

    suspend fun updateHall(id: Long, name: String, number: String, capacity: Int?) = db.withTransaction {
        val current = requireNotNull(dao.hallById(id)) { "القاعة غير موجودة" }
        require(name.isNotBlank() && number.isNotBlank()) { "أدخل اسم القاعة ورقمها" }
        dao.updateHall(current.copy(name = name.trim(), number = number.trim(), capacity = capacity))
        log("HALL_EDITED", "HALL", id)
    }

    suspend fun deleteHall(id: Long) = db.withTransaction {
        require(dao.deleteHall(id) == 1) { "القاعة غير موجودة" }
        log("HALL_DELETED", "HALL", id)
    }

    suspend fun updateClass(id: Long, gradeName: String, sectionName: String, name: String, hallId: Long?) = db.withTransaction {
        val current = requireNotNull(dao.classById(id)) { "الصف غير موجود" }
        require(gradeName.isNotBlank() && sectionName.isNotBlank() && name.isNotBlank()) { "أكمل بيانات الصف" }
        val grade = gradeName.trim()
        val section = sectionName.trim()
        val gradeId = dao.gradeId(grade, current.academicYearId)
            ?: dao.insertGrade(GradeEntity(name = grade, academicYearId = current.academicYearId))
        val sectionId = dao.sectionId(section, current.academicYearId)
            ?: dao.insertSection(SectionEntity(name = section, academicYearId = current.academicYearId))
        dao.updateClass(current.copy(name = name.trim(), gradeId = gradeId, sectionId = sectionId, hallId = hallId))
        dao.updateStudentClassPlacement(id, gradeId, sectionId, System.currentTimeMillis())
        log("CLASS_EDITED", "CLASS", id)
    }

    suspend fun deleteClass(id: Long) = db.withTransaction {
        require(dao.deleteClass(id) == 1) { "الصف غير موجود" }
        log("CLASS_DELETED", "CLASS", id)
    }

    private suspend fun log(action: String, type: String, id: Long) {
        audit.insert(AuditLogEntity(action = action, timestamp = System.currentTimeMillis(), user = "ADMIN", entityType = type, entityId = id.toString(), description = action))
    }
}
