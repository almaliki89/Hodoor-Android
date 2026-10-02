package com.omega.attendance

import com.omega.attendance.data.local.entity.StaffEntity
import com.omega.attendance.export.pdf.toQrCardContent
import org.junit.Assert.assertEquals
import org.junit.Test

class StaffQrCardContentTest {
    @Test
    fun staffCardContainsNameJobTitleAndOpaqueQrToken() {
        val member = StaffEntity(
            id = 7,
            uuid = "staff-7",
            qrValue = "STF:12345678-1234-1234-1234-123456789012",
            fullName = "مصطفى تركي رميض",
            jobTitle = "مدرس",
            createdAt = 1,
            updatedAt = 1
        )

        val card = member.toQrCardContent()

        assertEquals("مصطفى تركي رميض", card.name)
        assertEquals("مدرس", card.detail)
        assertEquals("STF:12345678-1234-1234-1234-123456789012", card.qrValue)
    }
}
