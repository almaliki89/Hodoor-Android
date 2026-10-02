package com.omega.attendance

import com.omega.attendance.presentation.dashboard.DashboardUiState
import com.omega.attendance.domain.model.StaffJobTitles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardStaffSummaryTest {
    @Test fun dashboardContainsIndependentStaffSummary() {
        val fields = DashboardUiState::class.java.declaredFields.map { it.name }.toSet()
        assertTrue(fields.containsAll(setOf("staffTotal", "staffPresent", "staffLate", "staffAbsent")))
    }

    @Test fun staffJobTitlesMatchSchoolList() {
        assertEquals(listOf("مدير", "معاون", "مدرس", "مدرسة", "كاتب", "فني", "موظف خدمات", "حرفي"), StaffJobTitles.values)
    }
}
