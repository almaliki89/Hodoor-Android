package com.omega.attendance

import com.omega.attendance.export.ReportText
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ReportTextTest {
    @Test fun attendanceStatusesAreArabic() {
        assertEquals("حاضر", ReportText.status("PRESENT"))
        assertEquals("متأخر", ReportText.status("LATE"))
        assertEquals("غائب", ReportText.status("ABSENT"))
        assertEquals("مجاز", ReportText.status("EXCUSED"))
    }

    @Test fun reportValuesHaveConsistentFormatting() {
        assertEquals("09:05", ReportText.time(9 * 3600L + 5 * 60L))
        assertEquals("91.2%", ReportText.percent(91.2))
        assertEquals("27/09/2026", ReportText.date(LocalDate.of(2026, 9, 27)))
    }
}
