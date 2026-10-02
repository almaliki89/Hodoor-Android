package com.omega.attendance.data.repository

import com.omega.attendance.data.local.dao.CalendarDao
import com.omega.attendance.data.local.dao.StaffAttendanceDao
import com.omega.attendance.data.local.dao.StaffDao
import com.omega.attendance.domain.usecase.SchoolDayCalculator
import java.time.LocalDate
import javax.inject.Inject

class ReportRepository @Inject constructor(private val attendance: AttendanceRepository, private val staff: StaffDao, private val staffAttendance: StaffAttendanceDao, private val calendar: CalendarDao) {
    fun daily(date: LocalDate, status: String?, classId: Long?, hallId: Long?) = attendance.dailyReport(date.toEpochDay(), status, classId, hallId)
    fun range(from: LocalDate, to: LocalDate, classId: Long?, hallId: Long?) = attendance.rangeReport(from.toEpochDay(), to.toEpochDay(), classId, hallId)
    fun staffDaily(date: LocalDate, status: String?, jobTitle: String?) = staffAttendance.dailyReport(date.toEpochDay(), status, jobTitle)
    fun staffRange(from: LocalDate, to: LocalDate, jobTitle: String?) = staffAttendance.rangeReport(from.toEpochDay(), to.toEpochDay(), jobTitle)
    fun staffJobTitles() = staff.jobTitles()
    suspend fun schoolDays(from: LocalDate, to: LocalDate) = SchoolDayCalculator.count(from, to, calendar.overrides(from.toEpochDay(), to.toEpochDay()))
}
