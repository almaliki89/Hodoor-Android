package com.omega.attendance.presentation.reports

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omega.attendance.data.local.dao.*
import com.omega.attendance.data.local.entity.HallEntity
import com.omega.attendance.data.repository.ReportRepository
import com.omega.attendance.data.repository.SchoolStructureRepository
import com.omega.attendance.export.ReportText
import com.omega.attendance.export.pdf.ReportExportService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class ReportPeriod { DAILY, WEEKLY, MONTHLY, CUSTOM }
enum class ReportAudience { STUDENTS, STAFF }

private data class ReportQuery(
    val audience: ReportAudience,
    val period: ReportPeriod,
    val from: LocalDate,
    val to: LocalDate,
    val status: String?,
    val classId: Long?,
    val hallId: Long?,
    val jobTitle: String?
)

private data class ReportRows(
    val daily: List<DailyReportRow> = emptyList(),
    val range: List<RangeReportRow> = emptyList(),
    val staffDaily: List<StaffDailyReportRow> = emptyList(),
    val staffRange: List<StaffRangeReportRow> = emptyList()
)

data class ReportsUiState(
    val audience: ReportAudience = ReportAudience.STUDENTS,
    val period: ReportPeriod = ReportPeriod.DAILY,
    val from: LocalDate = LocalDate.now(),
    val to: LocalDate = LocalDate.now(),
    val status: String? = null,
    val classId: Long? = null,
    val hallId: Long? = null,
    val jobTitle: String? = null,
    val classes: List<ClassOption> = emptyList(),
    val halls: List<HallEntity> = emptyList(),
    val jobTitles: List<String> = emptyList(),
    val daily: List<DailyReportRow> = emptyList(),
    val range: List<RangeReportRow> = emptyList(),
    val staffDaily: List<StaffDailyReportRow> = emptyList(),
    val staffRange: List<StaffRangeReportRow> = emptyList(),
    val schoolDays: Int = 1,
    val busy: Boolean = false,
    val lastFile: Uri? = null,
    val lastMime: String? = null,
    val message: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val reports: ReportRepository,
    private val exporter: ReportExportService,
    private val structure: SchoolStructureRepository
) : ViewModel() {
    private val selection = MutableStateFlow(ReportsUiState())
    private val rows = selection
        .map { ReportQuery(it.audience, it.period, it.from, it.to, it.status, it.classId, it.hallId, it.jobTitle) }
        .distinctUntilChanged()
        .flatMapLatest { q ->
            when {
                q.audience == ReportAudience.STAFF && q.period == ReportPeriod.DAILY -> reports.staffDaily(q.from, q.status, q.jobTitle).map { ReportRows(staffDaily = it) }
                q.audience == ReportAudience.STAFF -> reports.staffRange(q.from, q.to, q.jobTitle).map { ReportRows(staffRange = it) }
                q.period == ReportPeriod.DAILY -> reports.daily(q.from, q.status, q.classId, q.hallId).map { ReportRows(daily = it) }
                else -> reports.range(q.from, q.to, q.classId, q.hallId).map { ReportRows(range = it) }
            }
        }
    val state = combine(selection, rows, structure.classOptions(), structure.halls(), reports.staffJobTitles()) { s, data, classes, halls, jobTitles ->
        s.copy(
            daily = data.daily,
            range = data.range,
            staffDaily = data.staffDaily,
            staffRange = data.staffRange,
            classes = classes,
            halls = halls,
            jobTitles = jobTitles
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportsUiState())

    fun audience(value: ReportAudience) = update(selection.value.copy(audience = value, classId = null, hallId = null, jobTitle = null, lastFile = null, lastMime = null))
    fun select(period: ReportPeriod) {
        val today = LocalDate.now()
        val from = when (period) {
            ReportPeriod.DAILY -> today
            ReportPeriod.WEEKLY -> today.minusDays(6)
            ReportPeriod.MONTHLY -> today.withDayOfMonth(1)
            ReportPeriod.CUSTOM -> selection.value.from
        }
        val to = if (period == ReportPeriod.CUSTOM) selection.value.to else today
        update(selection.value.copy(period = period, from = from, to = to))
    }
    fun shiftFrom(days: Long) = update(selection.value.copy(from = selection.value.from.plusDays(days)))
    fun shiftTo(days: Long) = update(selection.value.copy(to = selection.value.to.plusDays(days)))
    fun status(value: String?) { selection.value = selection.value.copy(status = value) }
    fun classFilter(value: Long?) { selection.value = selection.value.copy(classId = value) }
    fun hallFilter(value: Long?) { selection.value = selection.value.copy(hallId = value) }
    fun jobTitleFilter(value: String?) { selection.value = selection.value.copy(jobTitle = value) }

    fun exportExcel(uri: Uri) = export(uri, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") { s ->
        when {
            s.audience == ReportAudience.STAFF && s.period == ReportPeriod.DAILY -> exporter.staffDailyXlsx(uri, s.from, s.staffDaily, s.status, s.jobTitle)
            s.audience == ReportAudience.STAFF -> exporter.staffRangeXlsx(uri, s.from, s.to, s.schoolDays, s.staffRange, reportTitle(s.period, s.audience), s.jobTitle)
            s.period == ReportPeriod.DAILY -> exporter.dailyXlsx(uri, s.from, s.daily, s.status, reportFilters(s))
            else -> exporter.rangeXlsx(uri, s.from, s.to, s.schoolDays, s.range, reportTitle(s.period, s.audience), reportFilters(s))
        }
    }

    fun exportPdf(uri: Uri) = export(uri, "application/pdf") { s ->
        when {
            s.audience == ReportAudience.STAFF && s.period == ReportPeriod.DAILY -> exporter.pdf(
                uri,
                reportTitle(s.period, s.audience),
                "التاريخ: ${ReportText.date(s.from)}" + (s.status?.let { "  •  الحالة: ${ReportText.status(it)}" } ?: "") + staffFilter(s),
                listOf("#", "اسم الموظف", "المسمى الوظيفي", "الحالة", "وقت الوصول", "طريقة التسجيل"),
                s.staffDaily.mapIndexed { index, row -> listOf((index + 1).toString(), row.fullName, row.jobTitle, ReportText.status(row.status), row.arrivalTime?.let(ReportText::time).orEmpty(), checkInType(row.checkInType)) },
                dailySummary(s.staffDaily)
            )
            s.audience == ReportAudience.STAFF -> exporter.pdf(
                uri,
                reportTitle(s.period, s.audience),
                "من ${ReportText.date(s.from)} إلى ${ReportText.date(s.to)}" + staffFilter(s),
                listOf("#", "اسم الموظف", "المسمى الوظيفي", "حاضر", "متأخر", "غائب", "مجاز", "نسبة الحضور"),
                s.staffRange.mapIndexed { index, row ->
                    val absent = (s.schoolDays - row.present - row.late - row.excused).coerceAtLeast(0)
                    val rate = if (s.schoolDays == 0) 0.0 else (row.present + row.late) * 100.0 / s.schoolDays
                    listOf((index + 1).toString(), row.fullName, row.jobTitle, row.present.toString(), row.late.toString(), absent.toString(), row.excused.toString(), ReportText.percent(rate))
                },
                listOf("عدد الموظفين" to s.staffRange.size.toString(), "أيام الدوام" to s.schoolDays.toString())
            )
            s.period == ReportPeriod.DAILY -> exporter.pdf(
                uri, reportTitle(s.period, s.audience), "التاريخ: " + ReportText.date(s.from) + (s.status?.let { "  •  الحالة: " + ReportText.status(it) } ?: "") + reportFilters(s),
                listOf("#", "رقم الطالب", "اسم الطالب", "المرحلة", "الصف", "الشعبة", "القاعة", "الحالة", "وقت الوصول"),
                s.daily.mapIndexed { index, row -> listOf((index + 1).toString(), row.studentNumber, row.fullName, row.grade, row.className, row.section, row.hall.orEmpty(), ReportText.status(row.status), row.arrivalTime?.let(ReportText::time).orEmpty()) },
                listOf("عدد النتائج" to s.daily.size.toString(), "الحاضرون" to s.daily.count { it.status == "PRESENT" }.toString(), "المتأخرون" to s.daily.count { it.status == "LATE" }.toString(), "الغائبون" to s.daily.count { it.status == "ABSENT" }.toString())
            )
            else -> exporter.pdf(
                uri, reportTitle(s.period, s.audience), "من " + ReportText.date(s.from) + " إلى " + ReportText.date(s.to) + reportFilters(s),
                listOf("#", "رقم الطالب", "اسم الطالب", "المرحلة", "الصف", "حاضر", "متأخر", "غائب", "مجاز", "نسبة الحضور"),
                s.range.mapIndexed { index, row ->
                    val absent = (s.schoolDays - row.present - row.late - row.excused).coerceAtLeast(0)
                    val rate = if (s.schoolDays == 0) 0.0 else (row.present + row.late) * 100.0 / s.schoolDays
                    listOf((index + 1).toString(), row.studentNumber, row.fullName, row.grade, row.className, row.present.toString(), row.late.toString(), absent.toString(), row.excused.toString(), ReportText.percent(rate))
                },
                listOf("عدد الطلبة" to s.range.size.toString(), "أيام الدوام" to s.schoolDays.toString())
            )
        }
    }

    fun dismissMessage() { selection.update { it.copy(message = null) } }

    private fun export(uri: Uri, mime: String, block: suspend (ReportsUiState) -> Unit) = viewModelScope.launch {
        selection.update { it.copy(busy = true) }
        runCatching {
            val selected = selection.value
            val snapshot = when {
                selected.audience == ReportAudience.STAFF && selected.period == ReportPeriod.DAILY -> selected.copy(staffDaily = reports.staffDaily(selected.from, selected.status, selected.jobTitle).first())
                selected.audience == ReportAudience.STAFF -> selected.copy(staffRange = reports.staffRange(selected.from, selected.to, selected.jobTitle).first(), schoolDays = reports.schoolDays(selected.from, selected.to))
                selected.period == ReportPeriod.DAILY -> selected.copy(daily = reports.daily(selected.from, selected.status, selected.classId, selected.hallId).first())
                else -> selected.copy(range = reports.range(selected.from, selected.to, selected.classId, selected.hallId).first(), schoolDays = reports.schoolDays(selected.from, selected.to))
            }
            block(snapshot)
            Log.i(LOG_TAG, "Report exported: audience=${snapshot.audience}, period=${snapshot.period}, mime=$mime")
        }.onSuccess {
            selection.update { it.copy(busy = false, lastFile = uri, lastMime = mime, message = "تم إنشاء الملف والتحقق منه بنجاح") }
        }.onFailure {
            selection.update { it.copy(busy = false, message = it.message ?: "تعذر إنشاء الملف") }
        }
    }

    private fun reportTitle(period: ReportPeriod, audience: ReportAudience): String {
        val subject = if (audience == ReportAudience.STAFF) "الكادر" else "الطلبة"
        return when (period) {
            ReportPeriod.DAILY -> "التقرير اليومي لحضور $subject"
            ReportPeriod.WEEKLY -> "التقرير الأسبوعي لحضور $subject"
            ReportPeriod.MONTHLY -> "التقرير الشهري لحضور $subject"
            ReportPeriod.CUSTOM -> "تقرير حضور $subject المخصص"
        }
    }
    private fun reportFilters(s: ReportsUiState): String {
        val names = listOfNotNull(
            s.classes.firstOrNull { it.classId == s.classId }?.className?.let { "الصف: $it" },
            s.halls.firstOrNull { it.id == s.hallId }?.name?.let { "القاعة: $it" }
        )
        return if (names.isEmpty()) "" else "  •  " + names.joinToString("  •  ")
    }
    private fun staffFilter(s: ReportsUiState) = s.jobTitle?.let { "  •  الوظيفة: $it" } ?: ""
    private fun dailySummary(rows: List<StaffDailyReportRow>) = listOf(
        "عدد الموظفين" to rows.size.toString(),
        "الحاضرون" to rows.count { it.status == "PRESENT" }.toString(),
        "المتأخرون" to rows.count { it.status == "LATE" }.toString(),
        "الغائبون" to rows.count { it.status == "ABSENT" }.toString()
    )
    private fun checkInType(value: String?) = when (value) { "QR" -> "QR"; "MANUAL" -> "يدوي"; else -> "" }
    private fun update(value: ReportsUiState) {
        selection.value = value
        viewModelScope.launch {
            val days = reports.schoolDays(value.from, value.to)
            selection.update { if (it.from == value.from && it.to == value.to) it.copy(schoolDays = days) else it }
        }
    }

    private companion object { const val LOG_TAG = "ReportsViewModel" }
}
