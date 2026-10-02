package com.omega.attendance

import com.omega.attendance.data.local.dao.CalendarOverride
import com.omega.attendance.data.local.entity.CalendarDayType
import com.omega.attendance.domain.usecase.SchoolDayCalculator
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SchoolDayCalculatorTest {
    @Test fun excludesIraqiWeekend() { assertEquals(5, SchoolDayCalculator.count(LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 26))) }
    @Test fun honorsHolidayOverride() { val day = LocalDate.of(2026, 9, 20); assertEquals(0, SchoolDayCalculator.count(day, day, listOf(CalendarOverride(day.toEpochDay(), CalendarDayType.HOLIDAY)))) }
}
