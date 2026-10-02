@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.omega.attendance.presentation.reports

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.omega.attendance.export.ReportText
import com.omega.attendance.export.pdf.FileActions
import com.omega.attendance.presentation.components.ResponsivePair
import com.omega.attendance.presentation.components.uniqueLazyItemKey

@Composable
fun ReportsScreen(vm: ReportsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val xlsx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { it?.let(vm::exportExcel) }
    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { it?.let(vm::exportPdf) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxHeight().widthIn(max = 1100.dp).align(Alignment.TopCenter),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("التقارير", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("أنشئ تقرير حضور واضحًا للطلبة أو الكادر", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                ReportSetupPanel(
                    state = state,
                    onAudience = vm::audience,
                    onPeriod = vm::select,
                    onClassFilter = vm::classFilter,
                    onHallFilter = vm::hallFilter,
                    onJobTitleFilter = vm::jobTitleFilter,
                    onStatus = vm::status,
                    onShiftFrom = vm::shiftFrom,
                    onShiftTo = vm::shiftTo
                )
            }
            item { ReportSummary(state) }
            item {
                ExportCard(
                    hasLastFile = state.lastFile != null,
                    canPrint = state.lastMime == "application/pdf",
                    onExcel = { xlsx.launch(reportName(state, "xlsx")) },
                    onPdf = { pdf.launch(reportName(state, "pdf")) },
                    onShare = { state.lastFile?.let { FileActions.share(context, it, state.lastMime ?: "*/*") } },
                    onPrint = { state.lastFile?.let { FileActions.print(context, it, "تقرير الحضور") } }
                )
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("النتائج", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text("${resultCount(state)} سجل", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
            if (resultCount(state) == 0) item { EmptyReportState() }
            if (state.period == ReportPeriod.DAILY) {
                if (state.audience == ReportAudience.STAFF) itemsIndexed(state.staffDaily, key = { index, row -> uniqueLazyItemKey(row.staffId, index) }) { _, row ->
                    ReportResultCard(
                        title = row.fullName,
                        subtitle = row.jobTitle,
                        status = row.status,
                        time = row.arrivalTime?.let(ReportText::time),
                        method = when (row.checkInType) { "QR" -> "QR"; "MANUAL" -> "يدوي"; else -> null }
                    )
                } else itemsIndexed(state.daily, key = { index, row -> uniqueLazyItemKey(row.studentId, index) }) { _, row ->
                    ReportResultCard(
                        title = row.fullName,
                        subtitle = "${row.studentNumber} • ${row.className}",
                        status = row.status,
                        time = row.arrivalTime?.let(ReportText::time)
                    )
                }
            } else {
                if (state.audience == ReportAudience.STAFF) itemsIndexed(state.staffRange, key = { index, row -> uniqueLazyItemKey(row.staffId, index) }) { _, row ->
                    val absent = (state.schoolDays - row.present - row.late - row.excused).coerceAtLeast(0)
                    val rate = if (state.schoolDays == 0) 0.0 else (row.present + row.late) * 100.0 / state.schoolDays
                    RangeResultCard(row.fullName, row.jobTitle, row.present, row.late, absent, ReportText.percent(rate))
                } else itemsIndexed(state.range, key = { index, row -> uniqueLazyItemKey(row.studentId, index) }) { _, row ->
                    val absent = (state.schoolDays - row.present - row.late - row.excused).coerceAtLeast(0)
                    val rate = if (state.schoolDays == 0) 0.0 else (row.present + row.late) * 100.0 / state.schoolDays
                    RangeResultCard(row.fullName, "${row.studentNumber} • ${row.className}", row.present, row.late, absent, ReportText.percent(rate))
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
        if (state.busy) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.2f)) {}
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }
    state.message?.let { AlertDialog(onDismissRequest = vm::dismissMessage, text = { Text(it) }, confirmButton = { TextButton(vm::dismissMessage) { Text("حسنًا") } }) }
}

@Composable
fun ReportSetupPanel(
    state: ReportsUiState,
    onAudience: (ReportAudience) -> Unit = {},
    onPeriod: (ReportPeriod) -> Unit = {},
    onClassFilter: (Long?) -> Unit = {},
    onHallFilter: (Long?) -> Unit = {},
    onJobTitleFilter: (String?) -> Unit = {},
    onStatus: (String?) -> Unit = {},
    onShiftFrom: (Long) -> Unit = {},
    onShiftTo: (Long) -> Unit = {}
) {
    var showFilters by remember { mutableStateOf(false) }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("خيارات التقرير", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            SectionLabel("الفئة")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.audience == ReportAudience.STUDENTS,
                    onClick = { onAudience(ReportAudience.STUDENTS) },
                    label = { Text("الطلبة") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = state.audience == ReportAudience.STAFF,
                    onClick = { onAudience(ReportAudience.STAFF) },
                    label = { Text("الكادر التدريسي") },
                    modifier = Modifier.weight(1f)
                )
            }

            SectionLabel("الفترة")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ReportPeriod.entries.forEach { period ->
                    FilterChip(selected = state.period == period, onClick = { onPeriod(period) }, label = { Text(periodName(period)) })
                }
            }
            if (state.period == ReportPeriod.CUSTOM) {
                ResponsivePair(
                    modifier = Modifier.fillMaxWidth(),
                    first = { modifier -> DateStepper("من", ReportText.date(state.from), { onShiftFrom(-1) }, { onShiftFrom(1) }, modifier) },
                    second = { modifier -> DateStepper("إلى", ReportText.date(state.to), { onShiftTo(-1) }, { onShiftTo(1) }, modifier) }
                )
            } else {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(dateRangeText(state), Modifier.fillMaxWidth().padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            OutlinedButton({ showFilters = true }, Modifier.fillMaxWidth()) { Text("الفلاتر") }
            Text(filterSummary(state), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (showFilters) {
        ModalBottomSheet(onDismissRequest = { showFilters = false }) {
            ReportFiltersContent(
                state = state,
                onClassFilter = onClassFilter,
                onHallFilter = onHallFilter,
                onJobTitleFilter = onJobTitleFilter,
                onStatus = onStatus,
                onClose = { showFilters = false }
            )
        }
    }
}

@Composable
private fun ReportFiltersContent(
    state: ReportsUiState,
    onClassFilter: (Long?) -> Unit,
    onHallFilter: (Long?) -> Unit,
    onJobTitleFilter: (String?) -> Unit,
    onStatus: (String?) -> Unit,
    onClose: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("الفلاتر", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (state.audience == ReportAudience.STUDENTS) {
            ReportDropdown(
                label = "الصف",
                selected = state.classes.firstOrNull { it.classId == state.classId }?.className ?: "كل الصفوف",
                options = listOf(null to "كل الصفوف") + state.classes.distinctBy { it.classId }.map { it.classId to it.className },
                onSelect = onClassFilter,
                modifier = Modifier.fillMaxWidth()
            )
            ReportDropdown(
                label = "القاعة",
                selected = state.halls.firstOrNull { it.id == state.hallId }?.name ?: "كل القاعات",
                options = listOf(null to "كل القاعات") + state.halls.map { it.id to it.name },
                onSelect = onHallFilter,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            ReportStringDropdown(
                label = "الوظيفة",
                selected = state.jobTitle ?: "كل الوظائف",
                options = listOf(null to "كل الوظائف") + state.jobTitles.map { it to it },
                onSelect = onJobTitleFilter,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (state.period == ReportPeriod.DAILY) {
            SectionLabel("حالة الحضور")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(null to "الكل", "PRESENT" to "حاضر", "LATE" to "متأخر", "ABSENT" to "غائب", "EXCUSED" to "مجاز").forEach { (status, label) ->
                    FilterChip(selected = state.status == status, onClick = { onStatus(status) }, label = { Text(label) })
                }
            }
        }
        Button(onClose, Modifier.fillMaxWidth()) { Text("عرض النتائج") }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
}

@Composable
private fun ReportSummary(state: ReportsUiState) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("ملخص التقرير", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (state.period != ReportPeriod.DAILY) Text("${state.schoolDays} يوم دوام", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.period == ReportPeriod.DAILY) {
                val statuses = if (state.audience == ReportAudience.STAFF) state.staffDaily.map { it.status } else state.daily.map { it.status }
                SummaryGrid(
                    listOf(
                        SummaryValue("النتائج", statuses.size.toString(), MaterialTheme.colorScheme.primary),
                        SummaryValue("حاضر", statuses.count { it == "PRESENT" }.toString(), Color(0xFF187A4D)),
                        SummaryValue("متأخر", statuses.count { it == "LATE" }.toString(), Color(0xFF9A6700)),
                        SummaryValue("غائب", statuses.count { it == "ABSENT" }.toString(), MaterialTheme.colorScheme.error)
                    )
                )
            } else {
                SummaryGrid(
                    listOf(
                        SummaryValue(if (state.audience == ReportAudience.STAFF) "أفراد الكادر" else "الطلبة", resultCount(state).toString(), MaterialTheme.colorScheme.primary),
                        SummaryValue("أيام الدوام", state.schoolDays.toString(), MaterialTheme.colorScheme.secondary)
                    )
                )
            }
        }
    }
}

private data class SummaryValue(val label: String, val value: String, val color: Color)

@Composable
private fun SummaryGrid(values: List<SummaryValue>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth < 600.dp) 2 else 4
        val itemWidth = (maxWidth - (8.dp * (columns - 1))) / columns
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = columns) {
            values.forEach { value ->
                Surface(Modifier.width(itemWidth), shape = MaterialTheme.shapes.medium, color = value.color.copy(alpha = 0.10f)) {
                    Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(value.value, style = MaterialTheme.typography.headlineSmall, color = value.color, fontWeight = FontWeight.Bold)
                        Text(value.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportCard(hasLastFile: Boolean, canPrint: Boolean, onExcel: () -> Unit, onPdf: () -> Unit, onShare: () -> Unit, onPrint: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("تصدير التقرير", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        ResponsivePair(
            modifier = Modifier.fillMaxWidth(),
            first = { modifier -> OutlinedButton(onExcel, modifier) { Text("Excel") } },
            second = { modifier -> Button(onPdf, modifier) { Text("PDF") } }
        )
        if (hasLastFile) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onShare) { Text("مشاركة") }
                if (canPrint) TextButton(onPrint) { Text("طباعة") }
            }
        }
    }
}

@Composable
private fun ReportDropdown(label: String, selected: String, options: List<Pair<Long?, String>>, onSelect: (Long?) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton({ expanded = true }, Modifier.fillMaxWidth()) { Text("$label: $selected") }
        DropdownMenu(expanded, { expanded = false }) {
            options.forEach { (id, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(id); expanded = false }) }
        }
    }
}

@Composable
private fun ReportStringDropdown(label: String, selected: String, options: List<Pair<String?, String>>, onSelect: (String?) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton({ expanded = true }, Modifier.fillMaxWidth()) { Text("$label: $selected") }
        DropdownMenu(expanded, { expanded = false }) {
            options.forEach { (value, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(value); expanded = false }) }
        }
    }
}

@Composable
private fun DateStepper(label: String, date: String, previous: () -> Unit, next: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedCard(modifier) {
        Column(Modifier.fillMaxWidth().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                TextButton(previous) { Text("−") }
                Text(date, fontWeight = FontWeight.Bold)
                TextButton(next) { Text("+") }
            }
        }
    }
}

@Composable
private fun ReportResultCard(title: String, subtitle: String, status: String, time: String?, method: String? = null) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusPill(status)
            }
            if (time != null || method != null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    time?.let { MetaPill("الوقت: $it") }
                    method?.let { MetaPill("التسجيل: $it") }
                }
            }
        }
    }
}

@Composable
private fun RangeResultCard(title: String, subtitle: String, present: Int, late: Int, absent: Int, rate: String) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MetaPill("حاضر: $present")
                MetaPill("متأخر: $late")
                MetaPill("غائب: $absent")
                MetaPill("النسبة: $rate")
            }
        }
    }
}

@Composable
private fun StatusPill(status: String) {
    val color = when (status) {
        "PRESENT" -> Color(0xFF187A4D)
        "LATE" -> Color(0xFF9A6700)
        "ABSENT" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.secondary
    }
    Surface(shape = MaterialTheme.shapes.large, color = color.copy(alpha = 0.12f)) {
        Text(ReportText.status(status), Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = color, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun MetaPill(text: String) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyReportState() {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("لا توجد نتائج", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("غيّر الفترة أو الفلاتر لعرض سجلات أخرى", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun resultCount(state: ReportsUiState) = when {
    state.audience == ReportAudience.STAFF && state.period == ReportPeriod.DAILY -> state.staffDaily.size
    state.audience == ReportAudience.STAFF -> state.staffRange.size
    state.period == ReportPeriod.DAILY -> state.daily.size
    else -> state.range.size
}

private fun dateRangeText(state: ReportsUiState): String = if (state.from == state.to) {
    "التاريخ: ${ReportText.date(state.from)}"
} else {
    "من ${ReportText.date(state.from)} إلى ${ReportText.date(state.to)}"
}

private fun filterSummary(state: ReportsUiState): String {
    val audienceFilter = if (state.audience == ReportAudience.STUDENTS) {
        val className = state.classes.firstOrNull { it.classId == state.classId }?.className ?: "كل الصفوف"
        val hallName = state.halls.firstOrNull { it.id == state.hallId }?.name ?: "كل القاعات"
        "$className • $hallName"
    } else {
        state.jobTitle ?: "كل الوظائف"
    }
    val status = if (state.period == ReportPeriod.DAILY) state.status?.let(ReportText::status) ?: "جميع الحالات" else null
    return listOfNotNull(audienceFilter, status).joinToString(" • ")
}

private fun periodName(value: ReportPeriod) = when (value) {
    ReportPeriod.DAILY -> "يومي"
    ReportPeriod.WEEKLY -> "أسبوعي"
    ReportPeriod.MONTHLY -> "شهري"
    ReportPeriod.CUSTOM -> "مخصص"
}

private fun reportName(state: ReportsUiState, extension: String): String {
    val period = periodName(state.period)
    val audience = if (state.audience == ReportAudience.STAFF) "الكادر" else "الطلبة"
    return "تقرير_حضور_${audience}_${period}_${state.from}${if (state.to != state.from) "_${state.to}" else ""}.$extension"
}
