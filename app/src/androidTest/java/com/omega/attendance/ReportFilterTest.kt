package com.omega.attendance

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.omega.attendance.data.local.database.OmegaDatabase
import com.omega.attendance.data.local.entity.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReportFilterTest {
    private lateinit var db: OmegaDatabase

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), OmegaDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun close() = db.close()

    @Test fun classAndHallFiltersApplyToDailyAndRangeQueries() = runBlocking {
        val structure = db.structureDao()
        val year = structure.insertYear(AcademicYearEntity(name = "2026-2027", startDate = 1, endDate = 365))
        val grade = structure.insertGrade(GradeEntity(name = "السادس", academicYearId = year))
        val section = structure.insertSection(SectionEntity(name = "علمي", academicYearId = year))
        val hall1 = structure.insertHall(HallEntity(name = "القاعة 1", number = "1"))
        val hall2 = structure.insertHall(HallEntity(name = "القاعة 2", number = "2"))
        val class1 = structure.insertClass(ClassEntity(name = "السادس علمي", gradeId = grade, sectionId = section, academicYearId = year))
        val class2 = structure.insertClass(ClassEntity(name = "السادس أدبي", gradeId = grade, sectionId = section, academicYearId = year))
        val students = db.studentDao()
        students.insert(StudentEntity(uuid = "one", studentNumber = "1", qrValue = "STD:one-1234567890123456", fullName = "أحمد", gradeId = grade, classId = class1, sectionId = section, hallId = hall1, academicYearId = year, createdAt = 0, updatedAt = 0))
        students.insert(StudentEntity(uuid = "two", studentNumber = "2", qrValue = "STD:two-1234567890123456", fullName = "علي", gradeId = grade, classId = class1, sectionId = section, hallId = hall2, academicYearId = year, createdAt = 0, updatedAt = 0))
        students.insert(StudentEntity(uuid = "three", studentNumber = "3", qrValue = "STD:three-1234567890123456", fullName = "حسن", gradeId = grade, classId = class2, sectionId = section, hallId = hall1, academicYearId = year, createdAt = 0, updatedAt = 0))
        val reports = db.attendanceDao()
        assertEquals(3, reports.dailyReport(10, null, null, null).first().size)
        assertEquals(listOf("أحمد"), reports.dailyReport(10, null, class1, hall1).first().map { it.fullName })
        assertEquals(listOf("علي"), reports.dailyReport(10, null, class1, hall2).first().map { it.fullName })
        assertEquals(listOf("أحمد"), reports.rangeReport(1, 20, class1, hall1).first().map { it.fullName })
        assertEquals(2, reports.rangeReport(1, 20, null, hall1).first().size)
    }
}
