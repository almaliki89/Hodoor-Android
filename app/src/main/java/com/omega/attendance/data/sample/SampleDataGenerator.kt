package com.omega.attendance.data.sample

import androidx.room.withTransaction
import com.omega.attendance.data.local.dao.*
import com.omega.attendance.data.local.database.OmegaDatabase
import com.omega.attendance.data.local.entity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

class SampleDataGenerator @Inject constructor(private val db: OmegaDatabase, private val students: StudentDao, private val attendance: AttendanceDao, private val structure: StructureDao) {
    suspend fun generate(studentCount: Int = 1_000, attendanceCount: Int = 100_000) = withContext(Dispatchers.IO) {
        require(studentCount in 1..2_000 && attendanceCount in 0..200_000)
        db.withTransaction {
            require(students.allStudentNumbers().isEmpty()) { "يجب أن تكون قاعدة الطلبة فارغة قبل توليد البيانات" }
            val now = System.currentTimeMillis(); val start = LocalDate.now().minusYears(1).toEpochDay(); val end = LocalDate.now().plusYears(1).toEpochDay()
            val yearId = structure.insertYear(AcademicYearEntity(name = "بيانات تجريبية", startDate = start, endDate = end, isActive = true))
            val gradeId = structure.insertGrade(GradeEntity(name = "المرحلة التجريبية", academicYearId = yearId))
            val sectionId = structure.insertSection(SectionEntity(name = "أ", academicYearId = yearId))
            val hallId = structure.insertHall(HallEntity(name = "قاعة الاختبار", number = "TEST", capacity = studentCount))
            val classId = structure.insertClass(ClassEntity(name = "A", gradeId = gradeId, sectionId = sectionId, hallId = hallId, academicYearId = yearId))
            val fakeStudents = (1..studentCount).map { index -> val uuid = UUID.randomUUID().toString(); StudentEntity(uuid = uuid, studentNumber = "T%05d".format(index), qrValue = "STD:$uuid", fullName = "طالب تجريبي %04d".format(index), gradeId = gradeId, classId = classId, sectionId = sectionId, hallId = hallId, academicYearId = yearId, createdAt = now, updatedAt = now) }
            val ids = students.insertAll(fakeStudents)
            var remaining = attendanceCount; var dayOffset = 0L
            while (remaining > 0) {
                val count = minOf(studentCount, remaining); val date = LocalDate.now().minusDays(dayOffset).toEpochDay()
                attendance.insertAll((0 until count).map { i -> AttendanceEntity(uuid = UUID.randomUUID().toString(), studentId = ids[i], studentNameSnapshot = fakeStudents[i].fullName, studentNumberSnapshot = fakeStudents[i].studentNumber, gradeSnapshot = "المرحلة التجريبية", classSnapshot = "A", sectionSnapshot = "أ", hallSnapshot = "قاعة الاختبار", academicYearId = yearId, attendanceDate = date, attendanceTime = (7 * 3600 + 20 + i % 3600).toLong(), timestamp = now - dayOffset * 86_400_000, status = if (i % 10 == 0) AttendanceStatus.LATE else AttendanceStatus.PRESENT, checkInType = CheckInType.QR, deviceIdentifier = "LOAD_TEST", createdAt = now, updatedAt = now) })
                remaining -= count; dayOffset++
            }
        }
    }
}
