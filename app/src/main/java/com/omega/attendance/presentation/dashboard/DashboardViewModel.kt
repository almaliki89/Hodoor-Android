package com.omega.attendance.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omega.attendance.data.local.dao.DailyTrend
import com.omega.attendance.data.local.entity.AttendanceStatus
import com.omega.attendance.data.repository.*
import com.omega.attendance.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import javax.inject.Inject

data class DashboardUiState(
    val schoolName: String = "مدرستي", val total: Int = 0, val arrived: Int = 0,
    val present: Int = 0, val late: Int = 0, val absent: Int = 0,
    val rate: Double = 0.0, val trend: List<DailyTrend> = emptyList(),
    val staffTotal: Int = 0, val staffArrived: Int = 0, val staffPresent: Int = 0,
    val staffLate: Int = 0, val staffAbsent: Int = 0
)

@HiltViewModel
class DashboardViewModel @Inject constructor(students: StudentRepository, attendance: AttendanceRepository, staff: StaffRepository, settings: SettingsRepository) : ViewModel() {
    private val today = LocalDate.now().toEpochDay()
    val state = combine(settings.preferences, students.activeCount(), attendance.count(today), attendance.count(today, AttendanceStatus.PRESENT), attendance.count(today, AttendanceStatus.LATE), attendance.trend(today - 6, today), staff.activeCount(), staff.attendanceCount(today), staff.attendanceCount(today, AttendanceStatus.PRESENT), staff.attendanceCount(today, AttendanceStatus.LATE)) { values ->
        val prefs = values[0] as com.omega.attendance.data.settings.AppPreferences; val total = values[1] as Int; val arrived = values[2] as Int; val present = values[3] as Int; val late = values[4] as Int
        @Suppress("UNCHECKED_CAST") val trend = values[5] as List<DailyTrend>
        val staffTotal = values[6] as Int; val staffArrived = values[7] as Int; val staffPresent = values[8] as Int; val staffLate = values[9] as Int
        DashboardUiState(prefs.schoolName, total, arrived, present, late, (total - arrived).coerceAtLeast(0), if (total == 0) 0.0 else arrived * 100.0 / total, trend, staffTotal, staffArrived, staffPresent, staffLate, (staffTotal - staffArrived).coerceAtLeast(0))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())
}
