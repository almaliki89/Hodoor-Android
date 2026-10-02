package com.omega.attendance

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.omega.attendance.data.local.database.OmegaDatabase
import com.omega.attendance.data.local.entity.*
import com.omega.attendance.data.repository.SchoolStructureRepository
import com.omega.attendance.data.repository.StudentRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataManagementTest {
    private lateinit var db: OmegaDatabase

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            OmegaDatabase::class.java
        ).build()
    }

    @After fun close() = db.close()

    @Test fun deletingStudentAlsoDeletesAttendanceAndWritesAudit() = runBlocking {
        val s = db.structureDao()
        val year = s.insertYear(AcademicYearEntity(name = "2026-2027", startDate = 1, endDate = 2))
        val grade = s.insertGrade(GradeEntity(name = "السادس", academicYearId = year))
        val section = s.insertSection(SectionEntity(name = "علمي", academicYearId = year))
        val clazz = s.insertClass(ClassEntity(name = "السادس علمي", gradeId = grade, sectionId = section, academicYearId = year))
        val studentId = db.studentDao().insert(StudentEntity(
            uuid = "test-student", studentNumber = "100", qrValue = "STD:test-student",
            fullName = "طالب اختبار", gradeId = grade, classId = clazz, sectionId = section,
            academicYearId = year, createdAt = 1, updatedAt = 1
        ))
        db.attendanceDao().insert(AttendanceEntity(
            uuid = "attendance-test", studentId = studentId,
            studentNameSnapshot = "طالب اختبار", studentNumberSnapshot = "100",
            gradeSnapshot = "السادس", classSnapshot = "السادس علمي",
            sectionSnapshot = "علمي", hallSnapshot = null,
            academicYearId = year, attendanceDate = 1, attendanceTime = 1, timestamp = 1,
            status = AttendanceStatus.PRESENT, checkInType = CheckInType.QR,
            deviceIdentifier = "TEST", createdAt = 1, updatedAt = 1
        ))

        StudentRepository(db, db.studentDao(), db.attendanceDao(), db.auditDao()).deletePermanently(studentId)

        assertNull(db.studentDao().byId(studentId))
        assertTrue(db.attendanceDao().forDate(1).first().isEmpty())
        assertTrue(db.auditDao().page(10, 0).any { it.action == "STUDENT_DELETED" })
    }

    @Test fun classEditUpdatesStudentPlacementAndDeleteProtectsStudents() = runBlocking {
        val s = db.structureDao()
        val repository = SchoolStructureRepository(db, s, db.auditDao())
        val year = s.insertYear(AcademicYearEntity(name = "2026-2027", startDate = 1, endDate = 2))
        val clazz = repository.createClass(year, "الرابع", "أدبي", "الرابع أدبي", null)
        val old = s.classById(clazz)!!
        val studentId = db.studentDao().insert(StudentEntity(
            uuid = "test-student", studentNumber = "100", qrValue = "STD:test-student",
            fullName = "طالب اختبار", gradeId = old.gradeId, classId = clazz,
            sectionId = old.sectionId, academicYearId = year, createdAt = 1, updatedAt = 1
        ))

        repository.updateClass(clazz, "الخامس", "علمي", "الخامس علمي", null)
        val changed = s.classById(clazz)!!
        val student = db.studentDao().byId(studentId)!!
        assertEquals(changed.gradeId, student.gradeId)
        assertEquals(changed.sectionId, student.sectionId)
        assertTrue(runCatching { repository.deleteClass(clazz) }.isFailure)

        StudentRepository(db, db.studentDao(), db.attendanceDao(), db.auditDao()).deletePermanently(studentId)
        repository.deleteClass(clazz)
        assertNull(s.classById(clazz))
        repository.deleteYear(year)
        assertNull(s.yearById(year))
    }
}
