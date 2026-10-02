package com.omega.attendance.data.local.entity

import androidx.room.*

@Entity(tableName = "staff_attendance", indices = [Index(value = ["staffId", "attendanceDate"], unique = true), Index("staffId"), Index("attendanceDate"), Index("status"), Index("timestamp")], foreignKeys = [ForeignKey(entity = StaffEntity::class, parentColumns = ["id"], childColumns = ["staffId"], onDelete = ForeignKey.CASCADE)])
data class StaffAttendanceEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val uuid: String, val staffId: Long, val staffNameSnapshot: String, val jobTitleSnapshot: String, val attendanceDate: Long, val attendanceTime: Long, val timestamp: Long, val status: AttendanceStatus, val checkInType: CheckInType, val createdAt: Long, val updatedAt: Long)
