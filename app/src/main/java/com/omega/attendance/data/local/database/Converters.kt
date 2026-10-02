package com.omega.attendance.data.local.database

import androidx.room.TypeConverter
import com.omega.attendance.data.local.entity.*

class Converters {
    @TypeConverter fun fromStudentStatus(value: StudentStatus) = value.name
    @TypeConverter fun toStudentStatus(value: String) = StudentStatus.valueOf(value)
    @TypeConverter fun fromAttendanceStatus(value: AttendanceStatus) = value.name
    @TypeConverter fun toAttendanceStatus(value: String) = AttendanceStatus.valueOf(value)
    @TypeConverter fun fromCheckInType(value: CheckInType) = value.name
    @TypeConverter fun toCheckInType(value: String) = CheckInType.valueOf(value)
    @TypeConverter fun fromCalendarDayType(value: CalendarDayType) = value.name
    @TypeConverter fun toCalendarDayType(value: String) = CalendarDayType.valueOf(value)
}
