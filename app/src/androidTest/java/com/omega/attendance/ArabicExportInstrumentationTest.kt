package com.omega.attendance

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.omega.attendance.data.local.dao.ClassOption
import com.omega.attendance.data.local.entity.HallEntity
import com.omega.attendance.data.local.entity.StudentEntity
import com.omega.attendance.data.settings.SettingsRepository
import com.omega.attendance.export.excel.XlsxCodec
import com.omega.attendance.export.pdf.ReportExportService
import com.omega.attendance.export.pdf.QrCardsPdfExporter
import com.omega.attendance.scanner.QrCodeGenerator
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipFile

@RunWith(AndroidJUnit4::class)
class ArabicExportInstrumentationTest {
    @Test fun thousandQrCardsProduceReadablePages() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "qr-cards-check.pdf")
        val cards = (1..1000).map { index ->
            StudentEntity(
                uuid = "student-" + index,
                studentNumber = index.toString(),
                qrValue = "STD:student-1234567890123456-" + index,
                fullName = "أحمد محمد " + index,
                gradeId = 1,
                classId = 2,
                sectionId = 3,
                hallId = 4,
                academicYearId = 5,
                createdAt = 0,
                updatedAt = 0
            )
        }
        QrCardsPdfExporter(context, QrCodeGenerator()).export(
            Uri.fromFile(file),
            cards,
            listOf(ClassOption(2, "الرابع أدبي", 1, "الرابع", 3, "أدبي", null, null, 5)),
            listOf(HallEntity(id = 4, name = "القاعة 1", number = "1")),
            "مدرستي"
        )
        assertTrue(file.inputStream().use { String(it.readNBytes(4)) } == "%PDF")
        PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
            assertTrue(renderer.pageCount == 84)
            renderer.openPage(0).use { page ->
                val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                val qr = Bitmap.createBitmap(bitmap, 105, 155, 220, 220)
                val pixels = IntArray(qr.width * qr.height)
                qr.getPixels(pixels, 0, qr.width, 0, 0, qr.width, qr.height)
                val source = RGBLuminanceSource(qr.width, qr.height, pixels)
                assertTrue(QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source))).text.startsWith("STD:"))
                qr.recycle()
                bitmap.recycle()
            }
        }
    }

    @Test fun excelAndPdfAreReadableAndArabic() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val codec = XlsxCodec(context)
        val excel = File(context.cacheDir, "arabic-export-check.xlsx")
        codec.writeTable(
            Uri.fromFile(excel),
            listOf(listOf("رقم الطالب", "اسم الطالب", "الحالة"), listOf("10001", "أحمد محمد علي", "حاضر")),
            "التقرير اليومي للحضور", "مدرستي • 27/09/2026", "عدد الطلبة: 1"
        )
        ZipFile(excel).use { zip ->
            val sheet = zip.getInputStream(zip.getEntry("xl/worksheets/sheet1.xml")).bufferedReader().readText()
            assertTrue(sheet.contains("rightToLeft=\"1\""))
            assertTrue(sheet.contains("أحمد محمد علي"))
            assertTrue(zip.getEntry("xl/styles.xml") != null)
        }

        val template = File(context.cacheDir, "arabic-import-template.xlsx")
        codec.writeTemplate(Uri.fromFile(template))
        val templateRows = codec.readRows(Uri.fromFile(template))
        assertTrue(templateRows.any { it.contains("اسم الطالب") })
        assertTrue(templateRows.any { it.contains("أحمد محمد علي") })

        val students = File(context.cacheDir, "arabic-students-check.xlsx")
        codec.writeStudents(
            Uri.fromFile(students),
            listOf(StudentEntity(uuid = "test-uuid", studentNumber = "10001", qrValue = "STD:test-uuid", fullName = "أحمد محمد علي", gradeId = 1, classId = 2, sectionId = 3, hallId = 4, academicYearId = 5, createdAt = 0, updatedAt = 0)),
            listOf(ClassOption(2, "الرابع أدبي", 1, "الرابع", 3, "أدبي", null, null, 5)),
            listOf(HallEntity(id = 4, name = "القاعة 1", number = "1")),
            "مدرستي"
        )
        ZipFile(students).use { zip ->
            val sheet = zip.getInputStream(zip.getEntry("xl/worksheets/sheet1.xml")).bufferedReader().readText()
            assertTrue(sheet.contains("الرابع أدبي"))
            assertTrue(sheet.contains("القاعة 1"))
            assertTrue(sheet.contains("هاتف ولي الأمر"))
        }

        val pdf = File(context.cacheDir, "arabic-report-check.pdf")
        ReportExportService(context, codec, SettingsRepository(context)).pdf(
            Uri.fromFile(pdf), "التقرير اليومي للحضور", "27/09/2026",
            listOf("#", "رقم الطالب", "اسم الطالب", "المرحلة", "الصف", "الشعبة", "القاعة", "الحالة", "وقت الوصول"),
            (1..40).map { index -> listOf(index.toString(), "10001", "أحمد محمد علي", "الرابع", "الرابع أدبي", "أدبي", "القاعة 1", "حاضر", "07:35") },
            listOf("عدد الطلبة" to "40", "الحاضرون" to "40", "المتأخرون" to "0", "الغائبون" to "0")
        )
        assertTrue(pdf.inputStream().use { String(it.readNBytes(4)) } == "%PDF")
        PdfRenderer(ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
            assertTrue(renderer.pageCount == 3)
            renderer.openPage(0).use { page ->
                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                File(context.cacheDir, "arabic-report-preview.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
    }

    @Test fun schoolRosterWithNamesOnlyInfersClassAndLeavesHallForSelection() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val codec = XlsxCodec(context)
        val file = File(context.cacheDir, "names-only-roster.xlsx")
        codec.writeTable(
            Uri.fromFile(file),
            listOf(listOf("ت", "اسم الطالب"), listOf("1", "طالب تجريبي رباعي")),
            "الصف السادس الادبي للعام الدراسي 2027/2026 الشعبة (1)"
        )

        val imported = codec.readImport(Uri.fromFile(file), emptySet())

        assertTrue(imported.valid.size == 1)
        assertTrue(imported.valid.single().className == "السادس أدبي")
        assertTrue(imported.valid.single().hall.isBlank())
    }
}
