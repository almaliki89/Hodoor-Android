package com.omega.attendance

import com.omega.attendance.data.local.entity.AttendanceStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class LoadModelTest {
    @Test fun aggregatesOneHundredThousandRecords() {
        val statuses = List(100_000) { if (it % 10 == 0) AttendanceStatus.LATE else AttendanceStatus.PRESENT }
        assertEquals(90_000, statuses.count { it == AttendanceStatus.PRESENT })
        assertEquals(10_000, statuses.count { it == AttendanceStatus.LATE })
    }
}
