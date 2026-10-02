package com.omega.attendance

import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.omega.attendance.data.local.dao.StaffDailyReportRow
import com.omega.attendance.data.settings.SettingsRepository
import com.omega.attendance.export.excel.XlsxCodec
import com.omega.attendance.export.pdf.ReportExportService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.util.zip.ZipFile

@RunWith(AndroidJUnit4::class)
class StaffReportExportTest {
    @Test
    fun staffReportExportsReadableArabicExcelAndPdf() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val service = ReportExportService(context, XlsxCodec(context), SettingsRepository(context))
        val rows = listOf(StaffDailyReportRow(1, "مصطفى تركي رميض", "مدرس", "PRESENT", 9 * 3600L, "QR"))
        val xlsx = File(context.cacheDir, "staff-report-check.xlsx")
        val pdf = File(context.cacheDir, "staff-report-check.pdf")

        service.staffDailyXlsx(Uri.fromFile(xlsx), LocalDate.of(2026, 10, 2), rows)
        ZipFile(xlsx).use { zip ->
            val sheet = zip.getInputStream(zip.getEntry("xl/worksheets/sheet1.xml")).bufferedReader().readText()
            assertTrue(sheet.contains("مصطفى تركي رميض"))
            assertTrue(sheet.contains("مدرس"))
        }

        service.pdf(
            Uri.fromFile(pdf),
            "التقرير اليومي لحضور الكادر",
            "02/10/2026",
            listOf("اسم الموظف", "المسمى الوظيفي", "الحالة"),
            listOf(listOf("مصطفى تركي رميض", "مدرس", "حاضر"))
        )
        PdfRenderer(ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
            assertEquals(1, renderer.pageCount)
        }
    }
}
