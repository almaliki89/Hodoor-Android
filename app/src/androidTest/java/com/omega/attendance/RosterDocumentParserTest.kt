package com.omega.attendance

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.omega.attendance.data.importer.RosterRowsParser
import com.omega.attendance.data.importer.isStaffQrReady
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RosterDocumentParserTest {
    @Test fun studentNamesCanBeReadFromSimpleWordOrPdfRows() {
        val rows = listOf(
            listOf("ت", "اسم الطالب"),
            listOf("1", "مصطفى تركي رميض"),
            listOf("2", "أحمد محمد علي")
        )

        val result = RosterRowsParser.students(rows, emptySet())

        assertEquals(listOf("مصطفى تركي رميض", "أحمد محمد علي"), result.valid.map { it.fullName })
    }

    @Test fun staffColumnsCanBeReadFromExcelWordOrPdfRows() {
        val rows = listOf(
            listOf("ت", "الاسم", "المسمى الوظيفي", "الاختصاص"),
            listOf("1", "مصطفى تركي رميض", "مدرس", "اللغة الإنجليزية")
        )

        val result = RosterRowsParser.staff(rows)

        assertEquals("مصطفى تركي رميض", result.single().fullName)
        assertEquals("مدرس", result.single().jobTitle)
        assertEquals("اللغة الإنجليزية", result.single().specialty)
    }

    @Test fun namesOnlyImportIgnoresAllOtherFileColumns() {
        val rows = listOf(
            listOf("ت", "الاسم الرباعي", "المسمى الوظيفي", "الاختصاص"),
            listOf("1", "مصطفى تركي رميض", "مدرس", "اللغة الإنجليزية")
        )

        val result = RosterRowsParser.names(rows)

        assertEquals(listOf("مصطفى تركي رميض"), result.map { it.second })
    }

    @Test fun importedStaffQrWaitsUntilJobTitleIsAssigned() {
        assertEquals(false, isStaffQrReady(""))
        assertEquals(true, isStaffQrReady("مدرس"))
    }
}
