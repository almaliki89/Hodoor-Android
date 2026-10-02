package com.omega.attendance

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.omega.attendance.data.local.entity.StaffEntity
import com.omega.attendance.export.pdf.QrCardsPdfExporter
import com.omega.attendance.scanner.QrCodeGenerator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class StaffQrCardsExportTest {
    @Test
    fun exportedStaffCardHasReadableStaffQr() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "staff-qr-cards-check.pdf")
        val member = StaffEntity(
            id = 1,
            uuid = "staff-1",
            qrValue = "STF:12345678-1234-1234-1234-123456789012",
            fullName = "مصطفى تركي رميض",
            jobTitle = "مدرس",
            createdAt = 1,
            updatedAt = 1
        )

        QrCardsPdfExporter(context, QrCodeGenerator()).exportStaff(Uri.fromFile(file), listOf(member), "مدرستي")

        assertTrue(file.inputStream().use { String(it.readNBytes(4)) } == "%PDF")
        PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
            assertEquals(1, renderer.pageCount)
            renderer.openPage(0).use { page ->
                val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                val qr = Bitmap.createBitmap(bitmap, 105, 155, 220, 220)
                val pixels = IntArray(qr.width * qr.height)
                qr.getPixels(pixels, 0, qr.width, 0, 0, qr.width, qr.height)
                val source = RGBLuminanceSource(qr.width, qr.height, pixels)
                assertEquals("STF:12345678-1234-1234-1234-123456789012", QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source))).text)
                qr.recycle()
                bitmap.recycle()
            }
        }
    }
}
