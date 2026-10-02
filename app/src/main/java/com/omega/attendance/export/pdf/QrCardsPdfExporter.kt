package com.omega.attendance.export.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import android.util.Log
import com.omega.attendance.data.local.dao.ClassOption
import com.omega.attendance.data.local.entity.HallEntity
import com.omega.attendance.data.local.entity.StaffEntity
import com.omega.attendance.data.local.entity.StudentEntity
import com.omega.attendance.scanner.QrCodeGenerator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal data class QrCardContent(
    val qrValue: String,
    val name: String,
    val detail: String,
    val reference: String = ""
)

internal fun StaffEntity.toQrCardContent() = QrCardContent(
    qrValue = qrValue,
    name = fullName,
    detail = jobTitle
)

/** A4 sheets of printable cards. QR codes contain opaque local lookup tokens only. */
class QrCardsPdfExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val generator: QrCodeGenerator
) {
    suspend fun export(
        uri: Uri,
        students: List<StudentEntity>,
        classes: List<ClassOption>,
        halls: List<HallEntity>,
        schoolName: String
    ) = withContext(Dispatchers.IO) {
        require(students.isNotEmpty()) { "لا يوجد طلبة لإنشاء البطاقات" }
        val classNames = classes.associate { it.classId to it.className }
        val hallNames = halls.associate { it.id to it.name }
        val cards = students.map { student ->
            val className = classNames[student.classId].orEmpty()
            val hallName = student.hallId?.let(hallNames::get).orEmpty()
            QrCardContent(
                qrValue = student.qrValue,
                name = student.fullName,
                detail = "$className  •  $hallName",
                reference = student.studentNumber
            )
        }
        writeCards(uri, cards, schoolName)
        Log.i(LOG_TAG, "Student QR cards exported: count=${cards.size}")
    }

    suspend fun exportStaff(
        uri: Uri,
        members: List<StaffEntity>,
        schoolName: String
    ) = withContext(Dispatchers.IO) {
        require(members.isNotEmpty()) { "لا يوجد كادر نشط لإنشاء البطاقات" }
        val cards = members.map(StaffEntity::toQrCardContent)
        writeCards(uri, cards, schoolName)
        Log.i(LOG_TAG, "Staff QR cards exported: count=${cards.size}")
    }

    private fun writeCards(uri: Uri, cards: List<QrCardContent>, schoolName: String) {
        val document = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val columns = 3
        val rows = 4
        val perPage = columns * rows
        val margin = 20f
        val gap = 8f
        val top = 49f
        val cardWidth = (pageWidth - margin * 2 - gap * (columns - 1)) / columns
        val cardHeight = 181f
        val green = Color.rgb(15, 118, 101)
        val navy = Color.rgb(12, 45, 88)
        val grey = Color.rgb(220, 231, 229)
        val pages = (cards.size + perPage - 1) / perPage
        try {
            repeat(pages) { pageIndex ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create())
                val canvas = page.canvas
                canvas.drawColor(Color.WHITE)
                rtl(canvas, schoolName, margin, 14f, pageWidth - margin * 2, 25f, 17f, navy, true)
                cards.drop(pageIndex * perPage).take(perPage).forEachIndexed { index, card ->
                    val row = index / columns
                    val column = index % columns
                    val left = margin + column * (cardWidth + gap)
                    val cardTop = top + row * (cardHeight + gap)
                    canvas.drawRoundRect(left, cardTop, left + cardWidth, cardTop + cardHeight, 8f, 8f, outline(grey))
                    canvas.drawRoundRect(left, cardTop, left + cardWidth, cardTop + 28f, 8f, 8f, fill(green))
                    rtl(canvas, schoolName, left + 8f, cardTop + 5f, cardWidth - 16f, 20f, 11f, Color.WHITE, true)
                    val bitmap = generator.generate(card.qrValue, 126)
                    try {
                        val qrLeft = left + (cardWidth - 104f) / 2
                        canvas.drawBitmap(bitmap, null, android.graphics.RectF(qrLeft, cardTop + 31f, qrLeft + 104f, cardTop + 135f), null)
                    } finally {
                        bitmap.recycle()
                    }
                    rtl(canvas, card.name, left + 7f, cardTop + 136f, cardWidth - 14f, 18f, 11f, navy, true)
                    rtl(canvas, card.detail, left + 7f, cardTop + 153f, cardWidth - 14f, 15f, 9f, navy)
                    if (card.reference.isNotBlank()) rtl(canvas, card.reference, left + 7f, cardTop + 168f, cardWidth - 14f, 12f, 8f, navy)
                }
                rtl(canvas, "الصفحة ${pageIndex + 1} من $pages", margin, 814f, pageWidth - margin * 2, 16f, 9f, navy)
                document.finishPage(page)
            }
            context.contentResolver.openOutputStream(uri, "wt")?.use(document::writeTo)
                ?: error("تعذر فتح ملف بطاقات QR")
        } finally {
            document.close()
        }
        val valid = context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize > 100 } == true
        require(valid) { "ملف بطاقات QR الناتج غير صالح" }
    }

    private fun rtl(canvas: Canvas, value: String, left: Float, top: Float, width: Float, height: Float, size: Float, color: Int, bold: Boolean = false) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
        val layout = StaticLayout.Builder.obtain(value, 0, value.length, paint, width.toInt())
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setMaxLines(1)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setIncludePad(false)
            .build()
        canvas.save()
        canvas.clipRect(left, top, left + width, top + height)
        canvas.translate(left, top)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    private fun outline(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = 0.8f
    }

    private companion object {
        const val LOG_TAG = "QrCardsPdfExporter"
    }
}
