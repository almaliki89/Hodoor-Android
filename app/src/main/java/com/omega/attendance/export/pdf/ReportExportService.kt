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
import com.omega.attendance.data.local.dao.DailyReportRow
import com.omega.attendance.data.local.dao.RangeReportRow
import com.omega.attendance.data.local.dao.StaffDailyReportRow
import com.omega.attendance.data.local.dao.StaffRangeReportRow
import com.omega.attendance.data.settings.SettingsRepository
import com.omega.attendance.export.ReportText
import com.omega.attendance.export.excel.XlsxCodec
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReportExportService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val xlsx: XlsxCodec,
    private val settings: SettingsRepository
) {
    suspend fun dailyXlsx(uri: Uri, date: LocalDate, rows: List<DailyReportRow>, statusFilter: String? = null, filters: String = "") = withContext(Dispatchers.IO) {
        val school = settings.preferences.first().schoolName
        val headers = listOf("#", "رقم الطالب", "اسم الطالب", "المرحلة", "الصف", "الشعبة", "القاعة", "الحالة", "وقت الوصول", "التاريخ")
        val data = rows.mapIndexed { index, row ->
            listOf(
                (index + 1).toString(), row.studentNumber, row.fullName, row.grade,
                row.className, row.section, row.hall.orEmpty(), ReportText.status(row.status),
                row.arrivalTime?.let(ReportText::time).orEmpty(), ReportText.date(date)
            )
        }
        val present = rows.count { it.status == "PRESENT" }
        val late = rows.count { it.status == "LATE" }
        val absent = rows.count { it.status == "ABSENT" }
        xlsx.writeTable(
            uri, listOf(headers) + data,
            title = "التقرير اليومي للحضور",
            subtitle = school + "  •  " + ReportText.date(date) + (statusFilter?.let { "  •  الحالة: " + ReportText.status(it) } ?: "") + filters,
            summary = "عدد الطلبة: ${rows.size}    الحاضرون: $present    المتأخرون: $late    الغائبون: $absent"
        )
    }

    suspend fun rangeXlsx(uri: Uri, from: LocalDate, to: LocalDate, schoolDays: Int, rows: List<RangeReportRow>, title: String = "تقرير الحضور للفترة", filters: String = "") = withContext(Dispatchers.IO) {
        val school = settings.preferences.first().schoolName
        val headers = listOf("رقم الطالب", "اسم الطالب", "المرحلة", "الصف", "أيام الحضور", "أيام التأخر", "أيام الغياب", "أيام الإجازة", "نسبة الحضور")
        val data = rows.map { row ->
            val absent = (schoolDays - row.present - row.late - row.excused).coerceAtLeast(0)
            val rate = if (schoolDays == 0) 0.0 else (row.present + row.late) * 100.0 / schoolDays
            listOf(row.studentNumber, row.fullName, row.grade, row.className, row.present.toString(), row.late.toString(), absent.toString(), row.excused.toString(), ReportText.percent(rate))
        }
        xlsx.writeTable(
            uri, listOf(headers) + data,
            title = title,
            subtitle = school + "  •  من " + ReportText.date(from) + " إلى " + ReportText.date(to) + filters,
            summary = "عدد الطلبة: ${rows.size}    أيام الدوام: $schoolDays"
        )
    }

    suspend fun staffDailyXlsx(uri: Uri, date: LocalDate, rows: List<StaffDailyReportRow>, statusFilter: String? = null, jobTitle: String? = null) = withContext(Dispatchers.IO) {
        val school = settings.preferences.first().schoolName
        val headers = listOf("#", "اسم الموظف", "المسمى الوظيفي", "الحالة", "وقت الوصول", "طريقة التسجيل", "التاريخ")
        val data = rows.mapIndexed { index, row ->
            listOf(
                (index + 1).toString(), row.fullName, row.jobTitle, ReportText.status(row.status),
                row.arrivalTime?.let(ReportText::time).orEmpty(), checkInType(row.checkInType), ReportText.date(date)
            )
        }
        xlsx.writeTable(
            uri, listOf(headers) + data,
            title = "التقرير اليومي لحضور الكادر",
            subtitle = school + "  •  " + ReportText.date(date) + (statusFilter?.let { "  •  الحالة: " + ReportText.status(it) } ?: "") + (jobTitle?.let { "  •  الوظيفة: $it" } ?: ""),
            summary = "عدد الموظفين: ${rows.size}    الحاضرون: ${rows.count { it.status == "PRESENT" }}    المتأخرون: ${rows.count { it.status == "LATE" }}    الغائبون: ${rows.count { it.status == "ABSENT" }}"
        )
    }

    suspend fun staffRangeXlsx(uri: Uri, from: LocalDate, to: LocalDate, schoolDays: Int, rows: List<StaffRangeReportRow>, title: String, jobTitle: String? = null) = withContext(Dispatchers.IO) {
        val school = settings.preferences.first().schoolName
        val headers = listOf("اسم الموظف", "المسمى الوظيفي", "أيام الحضور", "أيام التأخر", "أيام الغياب", "أيام الإجازة", "نسبة الحضور")
        val data = rows.map { row ->
            val absent = (schoolDays - row.present - row.late - row.excused).coerceAtLeast(0)
            val rate = if (schoolDays == 0) 0.0 else (row.present + row.late) * 100.0 / schoolDays
            listOf(row.fullName, row.jobTitle, row.present.toString(), row.late.toString(), absent.toString(), row.excused.toString(), ReportText.percent(rate))
        }
        xlsx.writeTable(
            uri, listOf(headers) + data,
            title = title,
            subtitle = school + "  •  من " + ReportText.date(from) + " إلى " + ReportText.date(to) + (jobTitle?.let { "  •  الوظيفة: $it" } ?: ""),
            summary = "عدد الموظفين: ${rows.size}    أيام الدوام: $schoolDays"
        )
    }

    suspend fun pdf(
        uri: Uri,
        title: String,
        dateRange: String,
        headers: List<String>,
        rows: List<List<String>>,
        summary: List<Pair<String, String>> = emptyList()
    ) = withContext(Dispatchers.IO) {
        require(headers.isNotEmpty())
        val school = settings.preferences.first().schoolName
        val document = PdfDocument()
        val pageWidth = 842
        val pageHeight = 595
        val margin = 30f
        val tableWidth = pageWidth - margin * 2
        val tableTop = 150f
        val rowHeight = 23f
        val rowsPerPage = 16
        val pages = maxOf(1, (rows.size + rowsPerPage - 1) / rowsPerPage)
        val weights = headers.map { header ->
            when (header) {
                "اسم الطالب" -> 2.5f
                "رقم الطالب" -> 1.55f
                "الصف" -> 1.65f
                "التاريخ" -> 1.25f
                else -> 1f
            }
        }
        val weightTotal = weights.sum()
        val widths = weights.map { tableWidth * it / weightTotal }
        val green = Color.rgb(22, 103, 91)
        val light = Color.rgb(239, 246, 243)
        val dark = Color.rgb(32, 46, 44)
        val white = Color.WHITE
        val generated = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy  HH:mm"))

        try {
            repeat(pages) { pageIndex ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create())
                val canvas = page.canvas
                canvas.drawColor(white)
                canvas.drawRect(0f, 0f, pageWidth.toFloat(), 82f, fill(green))
                drawRtl(canvas, school, margin, 19f, tableWidth, 20f, 16f, white, bold = true)
                drawRtl(canvas, title, margin, 45f, tableWidth, 25f, 20f, white, bold = true)
                drawRtl(canvas, dateRange, margin, 88f, tableWidth, 20f, 11f, dark)

                val visibleSummary = summary.take(4)
                if (visibleSummary.isNotEmpty()) {
                    val width = (tableWidth - 8f * (visibleSummary.size - 1)) / visibleSummary.size
                    visibleSummary.forEachIndexed { index, (label, value) ->
                        val right = pageWidth - margin - index * (width + 8f)
                        canvas.drawRoundRect(right - width, 112f, right, 140f, 6f, 6f, fill(light))
                        drawRtl(canvas, "$label: $value", right - width + 6f, 118f, width - 12f, 18f, 10f, dark, bold = true)
                    }
                }

                canvas.drawRect(margin, tableTop, pageWidth - margin, tableTop + rowHeight, fill(green))
                var right = pageWidth - margin
                headers.forEachIndexed { index, header ->
                    val width = widths[index]
                    drawRtl(canvas, header, right - width + 3f, tableTop + 4f, width - 6f, rowHeight - 5f, 9f, white, bold = true)
                    right -= width
                }

                val pageRows = rows.drop(pageIndex * rowsPerPage).take(rowsPerPage)
                if (pageRows.isEmpty()) {
                    drawRtl(canvas, "لا توجد بيانات ضمن الفترة المحددة", margin + 8f, tableTop + rowHeight + 14f, tableWidth - 16f, 24f, 12f, dark)
                }
                pageRows.forEachIndexed { rowIndex, values ->
                    val top = tableTop + rowHeight * (rowIndex + 1)
                    if (rowIndex % 2 == 0) canvas.drawRect(margin, top, pageWidth - margin, top + rowHeight, fill(light))
                    canvas.drawLine(margin, top + rowHeight, pageWidth - margin, top + rowHeight, stroke(Color.rgb(220, 231, 227)))
                    var cellRight = pageWidth - margin
                    headers.indices.forEach { index ->
                        val width = widths[index]
                        drawRtl(canvas, values.getOrNull(index).orEmpty(), cellRight - width + 3f, top + 4f, width - 6f, rowHeight - 5f, 9f, dark)
                        cellRight -= width
                    }
                }
                canvas.drawLine(margin, 562f, pageWidth - margin, 562f, stroke(Color.rgb(220, 231, 227)))
                drawRtl(canvas, "أُنشئ في $generated", margin, 568f, tableWidth * .65f, 16f, 9f, dark)
                drawRtl(canvas, "الصفحة ${pageIndex + 1} من $pages", pageWidth - margin - 150f, 568f, 150f, 16f, 9f, dark)
                document.finishPage(page)
            }
            context.contentResolver.openOutputStream(uri, "wt")?.use(document::writeTo)
                ?: error("تعذر فتح ملف PDF للكتابة")
        } finally {
            document.close()
        }
        val valid = context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize > 100 } == true
        require(valid) { "ملف PDF الناتج غير صالح" }
    }

    private fun drawRtl(
        canvas: Canvas, value: String, left: Float, top: Float, width: Float, height: Float,
        textSize: Float, color: Int, bold: Boolean = false
    ) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.textSize = textSize
            typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
        val layout = StaticLayout.Builder.obtain(value, 0, value.length, paint, width.toInt().coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
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
    private fun stroke(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; strokeWidth = 0.7f }
    private fun checkInType(value: String?) = when (value) { "QR" -> "QR"; "MANUAL" -> "يدوي"; else -> "" }
}
