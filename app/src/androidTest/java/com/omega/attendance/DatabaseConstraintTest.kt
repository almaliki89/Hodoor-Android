package com.omega.attendance

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.omega.attendance.data.local.database.OmegaDatabase
import com.omega.attendance.data.local.entity.*
import com.omega.attendance.data.repository.StudentRepository
import com.omega.attendance.domain.model.NewStudent
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseConstraintTest {
    private lateinit var db: OmegaDatabase
    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), OmegaDatabase::class.java).allowMainThreadQueries().build() }
    @After fun close() = db.close()
    @Test fun uniqueStudentNumberIsEnforced() { runBlocking {
        val structure = db.structureDao(); val year = structure.insertYear(AcademicYearEntity(name = "2026", startDate = 1, endDate = 2)); val grade = structure.insertGrade(GradeEntity(name = "G", academicYearId = year)); val section = structure.insertSection(SectionEntity(name = "S", academicYearId = year)); val clazz = structure.insertClass(ClassEntity(name = "C", gradeId = grade, sectionId = section, academicYearId = year)); val now = System.currentTimeMillis()
        val first = StudentEntity(uuid = "u1", studentNumber = "1", qrValue = "STD:u1-1234567890123456", fullName = "Test", gradeId = grade, classId = clazz, sectionId = section, academicYearId = year, createdAt = now, updatedAt = now); db.studentDao().insert(first)
        var constrained = false
        try { db.studentDao().insert(first.copy(uuid = "u2", qrValue = "STD:u2-1234567890123456")) } catch (_: android.database.sqlite.SQLiteConstraintException) { constrained = true }
        Assert.assertTrue(constrained)
    } }

    @Test fun bulkImportGeneratesSeparateStudentNumbersAndQrValues() { runBlocking {
        val structure = db.structureDao(); val year = structure.insertYear(AcademicYearEntity(name = "2027", startDate = 1, endDate = 2)); val grade = structure.insertGrade(GradeEntity(name = "الرابع", academicYearId = year)); val section = structure.insertSection(SectionEntity(name = "أدبي", academicYearId = year)); val hall = structure.insertHall(HallEntity(name = "القاعة 1", number = "1")); val clazz = structure.insertClass(ClassEntity(name = "الرابع أدبي", gradeId = grade, sectionId = section, hallId = hall, academicYearId = year))
        val repository = StudentRepository(db, db.studentDao(), db.attendanceDao(), db.auditDao())
        repository.addAll(listOf(
            NewStudent("", "طالب أول", grade, clazz, section, hall, year),
            NewStudent("", "طالب ثان", grade, clazz, section, hall, year)
        ))

        val rows = repository.all()
        Assert.assertEquals(2, rows.size)
        Assert.assertEquals(2, rows.map { it.studentNumber }.toSet().size)
        Assert.assertTrue(rows.all { it.studentNumber.startsWith("S") && it.qrValue.startsWith("STD:") })
    } }

    @Test fun allStudentsCanBeDeletedInOneAuditedTransaction() { runBlocking {
        val structure = db.structureDao(); val year = structure.insertYear(AcademicYearEntity(name = "2028", startDate = 1, endDate = 2)); val grade = structure.insertGrade(GradeEntity(name = "السادس", academicYearId = year)); val section = structure.insertSection(SectionEntity(name = "علمي", academicYearId = year)); val clazz = structure.insertClass(ClassEntity(name = "السادس علمي", gradeId = grade, sectionId = section, academicYearId = year))
        val repository = StudentRepository(db, db.studentDao(), db.attendanceDao(), db.auditDao())
        repository.addAll(listOf(NewStudent("", "طالب أول كامل", grade, clazz, section, null, year), NewStudent("", "طالب ثان كامل", grade, clazz, section, null, year)))

        val result = repository.deleteAllPermanently()

        Assert.assertEquals(2, result.people)
        Assert.assertTrue(repository.all().isEmpty())
        Assert.assertEquals("STUDENTS_DELETED_ALL", db.auditDao().page(1, 0).single().action)
    } }

    @Test fun studentDirectoryCanBeFilteredByClassAndHall() { runBlocking {
        val structure = db.structureDao(); val year = structure.insertYear(AcademicYearEntity(name = "2029", startDate = 1, endDate = 2)); val grade = structure.insertGrade(GradeEntity(name = "السادس", academicYearId = year)); val section = structure.insertSection(SectionEntity(name = "علمي", academicYearId = year)); val hall1 = structure.insertHall(HallEntity(name = "القاعة 1", number = "1")); val hall2 = structure.insertHall(HallEntity(name = "القاعة 2", number = "2")); val class1 = structure.insertClass(ClassEntity(name = "السادس علمي أ", gradeId = grade, sectionId = section, hallId = hall1, academicYearId = year)); val class2 = structure.insertClass(ClassEntity(name = "السادس علمي ب", gradeId = grade, sectionId = section, hallId = hall2, academicYearId = year))
        val repository = StudentRepository(db, db.studentDao(), db.attendanceDao(), db.auditDao())
        repository.add(NewStudent("", "طالب القاعة الأولى", grade, class1, section, hall1, year))
        repository.add(NewStudent("", "طالب القاعة الثانية", grade, class2, section, hall2, year))

        val filtered = repository.search("", class2, hall2).first()

        Assert.assertEquals(listOf("طالب القاعة الثانية"), filtered.map { it.fullName })
    } }
}
