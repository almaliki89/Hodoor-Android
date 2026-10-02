package com.omega.attendance.data.local.dao

import androidx.room.*
import com.omega.attendance.data.local.entity.*
import kotlinx.coroutines.flow.Flow

data class StaffDailyReportRow(val staffId: Long, val fullName: String, val jobTitle: String, val status: String, val arrivalTime: Long?, val checkInType: String?)
data class StaffRangeReportRow(val staffId: Long, val fullName: String, val jobTitle: String, val present: Int, val late: Int, val excused: Int)

@Dao
interface StaffDao {
    @Query("SELECT * FROM school_staff WHERE fullName LIKE '%' || :query || '%' OR jobTitle LIKE '%' || :query || '%' OR specialty LIKE '%' || :query || '%' ORDER BY fullName LIMIT :limit") fun search(query: String, limit: Int = 100): Flow<List<StaffEntity>>
    @Query("SELECT * FROM school_staff ORDER BY fullName") suspend fun all(): List<StaffEntity>
    @Query("SELECT * FROM school_staff WHERE id = :id LIMIT 1") suspend fun byId(id: Long): StaffEntity?
    @Query("SELECT * FROM school_staff WHERE qrValue = :qr AND status = 'ACTIVE' LIMIT 1") suspend fun byQr(qr: String): StaffEntity?
    @Query("SELECT COUNT(*) FROM school_staff WHERE status = 'ACTIVE'") fun activeCount(): Flow<Int>
    @Query("SELECT DISTINCT jobTitle FROM school_staff WHERE status = 'ACTIVE' ORDER BY jobTitle") fun jobTitles(): Flow<List<String>>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(value: StaffEntity): Long
    @Update suspend fun update(value: StaffEntity)
    @Query("DELETE FROM school_staff WHERE id = :id") suspend fun delete(id: Long): Int
    @Query("DELETE FROM school_staff") suspend fun deleteAll(): Int
}

@Dao
interface StaffAttendanceDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(value: StaffAttendanceEntity): Long
    @Query("SELECT * FROM staff_attendance WHERE staffId = :staffId AND attendanceDate = :date LIMIT 1") suspend fun existing(staffId: Long, date: Long): StaffAttendanceEntity?
    @Query("SELECT * FROM staff_attendance WHERE attendanceDate = :date ORDER BY attendanceTime DESC") fun forDate(date: Long): Flow<List<StaffAttendanceEntity>>
    @Query("SELECT COUNT(*) FROM staff_attendance WHERE attendanceDate = :date") fun countForDate(date: Long): Flow<Int>
    @Query("SELECT COUNT(*) FROM staff_attendance WHERE attendanceDate = :date AND status = :status") fun countByStatus(date: Long, status: AttendanceStatus): Flow<Int>
    @Query("SELECT COUNT(*) FROM staff_attendance") suspend fun countAll(): Int
    @Query("""SELECT s.id AS staffId, s.fullName, s.jobTitle,
        COALESCE(a.status, 'ABSENT') AS status, a.attendanceTime AS arrivalTime,
        a.checkInType AS checkInType
        FROM school_staff s
        LEFT JOIN staff_attendance a ON a.staffId=s.id AND a.attendanceDate=:date
        WHERE s.status='ACTIVE'
        AND (:status IS NULL OR COALESCE(a.status, 'ABSENT')=:status)
        AND (:jobTitle IS NULL OR s.jobTitle=:jobTitle)
        ORDER BY s.jobTitle, s.fullName""")
    fun dailyReport(date: Long, status: String?, jobTitle: String?): Flow<List<StaffDailyReportRow>>
    @Query("""SELECT s.id AS staffId, s.fullName, s.jobTitle,
        SUM(CASE WHEN a.status='PRESENT' THEN 1 ELSE 0 END) AS present,
        SUM(CASE WHEN a.status='LATE' THEN 1 ELSE 0 END) AS late,
        SUM(CASE WHEN a.status='EXCUSED' THEN 1 ELSE 0 END) AS excused
        FROM school_staff s
        LEFT JOIN staff_attendance a ON a.staffId=s.id AND a.attendanceDate BETWEEN :from AND :to
        WHERE s.status='ACTIVE' AND (:jobTitle IS NULL OR s.jobTitle=:jobTitle)
        GROUP BY s.id ORDER BY s.jobTitle, s.fullName""")
    fun rangeReport(from: Long, to: Long, jobTitle: String?): Flow<List<StaffRangeReportRow>>
}
