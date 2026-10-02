package com.omega.attendance.data.local.dao

import androidx.room.*
import com.omega.attendance.data.local.entity.*
import kotlinx.coroutines.flow.Flow

data class ClassOption(
    val classId: Long, val className: String,
    val gradeId: Long, val gradeName: String,
    val sectionId: Long, val sectionName: String,
    val hallId: Long?, val hallName: String?, val academicYearId: Long
)

data class DailyTrend(val attendanceDate: Long, val total: Int, val present: Int, val late: Int)
data class DailyReportRow(val studentId: Long, val studentNumber: String, val fullName: String, val grade: String, val className: String, val section: String, val hall: String?, val status: String, val arrivalTime: Long?)
data class RangeReportRow(val studentId: Long, val studentNumber: String, val fullName: String, val grade: String, val className: String, val present: Int, val late: Int, val excused: Int)
data class CalendarOverride(val date: Long, val type: CalendarDayType)

@Dao
interface StudentDao {
    @Query("SELECT * FROM students WHERE status = 'ACTIVE' ORDER BY fullName LIMIT :limit OFFSET :offset") suspend fun page(limit: Int, offset: Int): List<StudentEntity>
    @Query("SELECT * FROM students WHERE qrValue = :qr AND status = 'ACTIVE' LIMIT 1") suspend fun byQr(qr: String): StudentEntity?
    @Query("SELECT * FROM students WHERE id = :id LIMIT 1") fun observe(id: Long): Flow<StudentEntity?>
    @Query("SELECT * FROM students WHERE id = :id LIMIT 1") suspend fun byId(id: Long): StudentEntity?
    @Query("""SELECT * FROM students WHERE (fullName LIKE '%' || :query || '%' OR studentNumber LIKE '%' || :query || '%')
        AND (:classId IS NULL OR classId = :classId) AND (:hallId IS NULL OR hallId = :hallId)
        ORDER BY classId, hallId, fullName LIMIT :limit""")
    fun search(query: String, classId: Long? = null, hallId: Long? = null, limit: Int = 5000): Flow<List<StudentEntity>>
    @Query("SELECT COUNT(*) FROM students WHERE status = 'ACTIVE'") fun activeCount(): Flow<Int>
    @Query("SELECT * FROM students ORDER BY fullName") suspend fun all(): List<StudentEntity>
    @Query("SELECT studentNumber FROM students") suspend fun allStudentNumbers(): List<String>
    @Query("SELECT qrValue FROM students") suspend fun allQrValues(): List<String>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(student: StudentEntity): Long
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertAll(students: List<StudentEntity>): List<Long>
    @Update suspend fun update(student: StudentEntity)
    @Query("UPDATE students SET status = 'ARCHIVED', updatedAt = :now WHERE id = :id") suspend fun archive(id: Long, now: Long)
    @Query("UPDATE students SET status = 'ACTIVE', updatedAt = :now WHERE id = :id") suspend fun restore(id: Long, now: Long)
    @Query("DELETE FROM students WHERE id = :id") suspend fun delete(id: Long): Int
    @Query("DELETE FROM students") suspend fun deleteAll(): Int
}

@Dao
interface AttendanceDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(record: AttendanceEntity): Long
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertAll(records: List<AttendanceEntity>)
    @Query("SELECT * FROM attendance WHERE studentId = :studentId AND attendanceDate = :date AND academicYearId = :yearId LIMIT 1") suspend fun existing(studentId: Long, date: Long, yearId: Long): AttendanceEntity?
    @Query("SELECT * FROM attendance WHERE attendanceDate = :date ORDER BY attendanceTime DESC") fun forDate(date: Long): Flow<List<AttendanceEntity>>
    @Query("SELECT * FROM attendance WHERE studentId = :studentId ORDER BY attendanceDate DESC, attendanceTime DESC LIMIT 500") fun forStudent(studentId: Long): Flow<List<AttendanceEntity>>
    @Query("SELECT COUNT(*) FROM attendance WHERE attendanceDate = :date AND status = :status") fun countByStatus(date: Long, status: AttendanceStatus): Flow<Int>
    @Query("SELECT COUNT(*) FROM attendance WHERE attendanceDate = :date") fun countForDate(date: Long): Flow<Int>
    @Query("""SELECT attendanceDate, COUNT(*) AS total,
        SUM(CASE WHEN status = 'PRESENT' THEN 1 ELSE 0 END) AS present,
        SUM(CASE WHEN status = 'LATE' THEN 1 ELSE 0 END) AS late
        FROM attendance WHERE attendanceDate BETWEEN :from AND :to GROUP BY attendanceDate ORDER BY attendanceDate""")
    fun trend(from: Long, to: Long): Flow<List<DailyTrend>>
    @Query("""SELECT s.id AS studentId, s.studentNumber, s.fullName, g.name AS grade,
        c.name AS className, se.name AS section, h.name AS hall,
        COALESCE(a.status, 'ABSENT') AS status, a.attendanceTime AS arrivalTime
        FROM students s JOIN grades g ON g.id=s.gradeId JOIN classes c ON c.id=s.classId
        JOIN sections se ON se.id=s.sectionId LEFT JOIN halls h ON h.id=s.hallId
        LEFT JOIN attendance a ON a.studentId=s.id AND a.attendanceDate=:date AND a.academicYearId=s.academicYearId
        WHERE s.status='ACTIVE' AND (:status IS NULL OR COALESCE(a.status, 'ABSENT')=:status)
        AND (:classId IS NULL OR s.classId=:classId) AND (:hallId IS NULL OR s.hallId=:hallId)
        ORDER BY g.name, c.name, s.fullName""")
    fun dailyReport(date: Long, status: String?, classId: Long?, hallId: Long?): Flow<List<DailyReportRow>>
    @Query("""SELECT s.id AS studentId, s.studentNumber, s.fullName, g.name AS grade,
        c.name AS className,
        SUM(CASE WHEN a.status='PRESENT' THEN 1 ELSE 0 END) AS present,
        SUM(CASE WHEN a.status='LATE' THEN 1 ELSE 0 END) AS late,
        SUM(CASE WHEN a.status='EXCUSED' THEN 1 ELSE 0 END) AS excused
        FROM students s JOIN grades g ON g.id=s.gradeId JOIN classes c ON c.id=s.classId
        LEFT JOIN attendance a ON a.studentId=s.id AND a.attendanceDate BETWEEN :from AND :to
        WHERE s.status='ACTIVE' AND (:classId IS NULL OR s.classId=:classId)
        AND (:hallId IS NULL OR s.hallId=:hallId)
        GROUP BY s.id ORDER BY g.name, c.name, s.fullName""")
    fun rangeReport(from: Long, to: Long, classId: Long?, hallId: Long?): Flow<List<RangeReportRow>>
    @Query("SELECT * FROM attendance WHERE id = :id LIMIT 1") suspend fun byId(id: Long): AttendanceEntity?
    @Update suspend fun update(record: AttendanceEntity)
    @Query("DELETE FROM attendance WHERE id = :id") suspend fun delete(id: Long): Int
    @Query("DELETE FROM attendance WHERE studentId = :studentId") suspend fun deleteForStudent(studentId: Long): Int
    @Query("SELECT COUNT(*) FROM attendance WHERE studentId = :studentId") suspend fun countForStudent(studentId: Long): Int
    @Query("DELETE FROM attendance") suspend fun deleteAll(): Int
}

@Dao
interface StructureDao {
    @Query("SELECT * FROM academic_years ORDER BY startDate DESC") fun years(): Flow<List<AcademicYearEntity>>
    @Query("SELECT * FROM academic_years WHERE id = :id LIMIT 1") suspend fun yearById(id: Long): AcademicYearEntity?
    @Query("SELECT * FROM academic_years ORDER BY startDate DESC LIMIT 1") suspend fun latestYear(): AcademicYearEntity?
    @Query("SELECT * FROM grades WHERE academicYearId = :yearId ORDER BY name") fun grades(yearId: Long): Flow<List<GradeEntity>>
    @Query("SELECT * FROM sections WHERE academicYearId = :yearId ORDER BY name") fun sections(yearId: Long): Flow<List<SectionEntity>>
    @Query("SELECT * FROM classes WHERE academicYearId = :yearId ORDER BY name") fun classes(yearId: Long): Flow<List<ClassEntity>>
    @Query("SELECT * FROM halls ORDER BY name") fun halls(): Flow<List<HallEntity>>
    @Query("SELECT id FROM halls WHERE number = :number LIMIT 1") suspend fun hallIdByNumber(number: String): Long?
    @Query("""SELECT c.id AS classId, c.name AS className, g.id AS gradeId, g.name AS gradeName,
        s.id AS sectionId, s.name AS sectionName, c.hallId AS hallId, h.name AS hallName,
        c.academicYearId AS academicYearId FROM classes c
        JOIN grades g ON g.id = c.gradeId JOIN sections s ON s.id = c.sectionId
        LEFT JOIN halls h ON h.id = c.hallId WHERE c.status = 'ACTIVE' ORDER BY g.name, c.name""")
    fun classOptions(): Flow<List<ClassOption>>
    @Query("SELECT * FROM classes WHERE id = :id LIMIT 1") suspend fun classById(id: Long): ClassEntity?
    @Query("SELECT * FROM grades WHERE id = :id LIMIT 1") suspend fun gradeById(id: Long): GradeEntity?
    @Query("SELECT * FROM sections WHERE id = :id LIMIT 1") suspend fun sectionById(id: Long): SectionEntity?
    @Query("SELECT * FROM halls WHERE id = :id LIMIT 1") suspend fun hallById(id: Long): HallEntity?
    @Query("SELECT id FROM grades WHERE name = :name AND academicYearId = :yearId LIMIT 1") suspend fun gradeId(name: String, yearId: Long): Long?
    @Query("SELECT id FROM sections WHERE name = :name AND academicYearId = :yearId LIMIT 1") suspend fun sectionId(name: String, yearId: Long): Long?
    @Query("SELECT id FROM classes WHERE name = :name AND academicYearId = :yearId LIMIT 1") suspend fun classId(name: String, yearId: Long): Long?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertYear(value: AcademicYearEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertGrade(value: GradeEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertSection(value: SectionEntity): Long
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertClass(value: ClassEntity): Long
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertHall(value: HallEntity): Long
    @Update suspend fun updateYear(value: AcademicYearEntity)
    @Update suspend fun updateClass(value: ClassEntity)
    @Update suspend fun updateHall(value: HallEntity)
    @Query("UPDATE students SET gradeId = :gradeId, sectionId = :sectionId, updatedAt = :now WHERE classId = :classId")
    suspend fun updateStudentClassPlacement(classId: Long, gradeId: Long, sectionId: Long, now: Long)
    @Query("DELETE FROM academic_years WHERE id = :id") suspend fun deleteYear(id: Long): Int
    @Query("SELECT COUNT(*) FROM students WHERE academicYearId = :yearId") suspend fun studentsForYear(yearId: Long): Int
    @Query("SELECT COUNT(*) FROM attendance WHERE academicYearId = :yearId") suspend fun attendanceForYear(yearId: Long): Int
    @Query("DELETE FROM classes WHERE academicYearId = :yearId") suspend fun deleteClassesForYear(yearId: Long): Int
    @Query("DELETE FROM grades WHERE academicYearId = :yearId") suspend fun deleteGradesForYear(yearId: Long): Int
    @Query("DELETE FROM sections WHERE academicYearId = :yearId") suspend fun deleteSectionsForYear(yearId: Long): Int
    @Query("DELETE FROM classes WHERE id = :id") suspend fun deleteClass(id: Long): Int
    @Query("DELETE FROM halls WHERE id = :id") suspend fun deleteHall(id: Long): Int
    @Query("SELECT value FROM app_settings WHERE key = :key LIMIT 1") suspend fun seedFlag(key: String): String?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertSetting(value: AppSettingEntity): Long
}

@Dao
interface AuditDao {
    @Insert suspend fun insert(log: AuditLogEntity)
    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT :limit OFFSET :offset") suspend fun page(limit: Int, offset: Int): List<AuditLogEntity>
}

@Dao
interface CalendarDao {
    @Query("SELECT date, type FROM school_calendar WHERE date BETWEEN :from AND :to") suspend fun overrides(from: Long, to: Long): List<CalendarOverride>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(day: SchoolCalendarEntity): Long
    @Query("DELETE FROM school_calendar WHERE date = :date AND academicYearId = :yearId") suspend fun delete(date: Long, yearId: Long): Int
}
