package com.omega.attendance.export

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object ReportText {
    fun status(value: String): String = when (value) {
        "PRESENT" -> "حاضر"
        "LATE" -> "متأخر"
        "ABSENT" -> "غائب"
        "EXCUSED" -> "مجاز"
        "MANUAL" -> "يدوي"
        else -> value
    }

    fun time(seconds: Long): String = String.format(Locale.ROOT, "%02d:%02d", seconds / 3600, (seconds % 3600) / 60)

    fun percent(value: Double): String = String.format(Locale.ROOT, "%.1f%%", value)

    fun date(value: LocalDate): String = value.format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT))
}
