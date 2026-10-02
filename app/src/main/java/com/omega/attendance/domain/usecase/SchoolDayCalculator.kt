package com.omega.attendance.domain.usecase

import com.omega.attendance.data.local.dao.CalendarOverride
import com.omega.attendance.data.local.entity.CalendarDayType
import java.time.DayOfWeek
import java.time.LocalDate

object SchoolDayCalculator {
    fun count(from: LocalDate, to: LocalDate, overrides: List<CalendarOverride> = emptyList()): Int {
        if (to < from) return 0
        val map = overrides.associate { it.date to it.type }; var count = 0; var day = from
        while (!day.isAfter(to)) {
            val school = when (map[day.toEpochDay()]) {
                CalendarDayType.SCHOOL_DAY, CalendarDayType.EXAM -> true
                CalendarDayType.WEEKEND, CalendarDayType.HOLIDAY, CalendarDayType.CLOSED -> false
                null -> day.dayOfWeek != DayOfWeek.FRIDAY && day.dayOfWeek != DayOfWeek.SATURDAY
            }
            if (school) count++; day = day.plusDays(1)
        }
        return count
    }
}
