package com.omega.attendance

import com.omega.attendance.data.local.entity.AttendanceStatus
import com.omega.attendance.domain.usecase.AttendancePolicy
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class AttendancePolicyTest {
    @Test fun beforeThresholdIsPresent() { assertEquals(AttendanceStatus.PRESENT, AttendancePolicy.status(LocalTime.of(7, 44, 59), 465)) }
    @Test fun thresholdIsLate() { assertEquals(AttendanceStatus.LATE, AttendancePolicy.status(LocalTime.of(7, 45), 465)) }
}
