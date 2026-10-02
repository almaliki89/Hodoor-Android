package com.omega.attendance

import com.omega.attendance.data.local.entity.AttendanceStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class FoundationTest {
    @Test fun attendanceStatusesRemainStable() {
        assertEquals(listOf("PRESENT", "LATE", "ABSENT", "EXCUSED"), AttendanceStatus.entries.map { it.name })
    }
}
