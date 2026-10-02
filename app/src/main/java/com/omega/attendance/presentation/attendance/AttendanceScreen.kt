package com.omega.attendance.presentation.attendance

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.omega.attendance.data.local.entity.*
import com.omega.attendance.presentation.components.uniqueLazyItemKey

@Composable
fun AttendanceScreen(readOnly: Boolean = false, vm: AttendanceViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState(); var manual by remember { mutableStateOf(false) }; var edit by remember { mutableStateOf<AttendanceEntity?>(null) }; var deleting by remember { mutableStateOf<AttendanceEntity?>(null) }
    Scaffold(floatingActionButton = { if (!readOnly) ExtendedFloatingActionButton(text = { Text("تسجيل يدوي") }, icon = {}, onClick = { manual = true }) }) { padding -> Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
        Text("حضور اليوم", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(vertical = 16.dp)); Text("المسجلون: ${state.records.size}")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)) { itemsIndexed(state.records, key = { index, row -> uniqueLazyItemKey(row.id, index) }) { _, row -> ElevatedCard(Modifier.fillMaxWidth()) { Row(Modifier.padding(12.dp)) { Column(Modifier.weight(1f)) { Text(row.studentNameSnapshot, style = MaterialTheme.typography.titleMedium); Text("${row.studentNumberSnapshot} — ${label(row.status)} — ${time(row.attendanceTime)}"); Text(if (row.checkInType == CheckInType.MANUAL) "تسجيل يدوي" else "بطاقة QR", style = MaterialTheme.typography.bodySmall) }; if (!readOnly) { IconButton({ edit = row }) { Icon(Icons.Rounded.Edit, "تعديل") }; IconButton({ deleting = row }) { Icon(Icons.Rounded.DeleteOutline, "حذف السجل") } } } } } }
    } }
    if (manual) SelectionDialog("تسجيل حضور يدوي", state.students.map { it.id to "${it.fullName} — ${it.studentNumber}" }, { manual = false }) { id -> vm.addManual(id, AttendanceStatus.PRESENT); manual = false }
    edit?.let { row -> StatusDialog({ edit = null }) { vm.changeStatus(row.id, it); edit = null } }
    deleting?.let { row -> AlertDialog(
        onDismissRequest = { deleting = null },
        title = { Text("حذف سجل الحضور؟") },
        text = { Text("سيُحذف سجل " + row.studentNameSnapshot + " لهذا اليوم نهائيًا.") },
        confirmButton = { Button({ vm.delete(row.id); deleting = null }) { Text("حذف نهائي") } },
        dismissButton = { TextButton({ deleting = null }) { Text("إلغاء") } }
    ) }
    state.message?.let { AlertDialog(onDismissRequest = vm::dismissMessage, text = { Text(it) }, confirmButton = { TextButton(vm::dismissMessage) { Text("حسنًا") } }) }
}

@Composable private fun SelectionDialog(title: String, values: List<Pair<Long, String>>, dismiss: () -> Unit, select: (Long) -> Unit) {
    var query by remember { mutableStateOf("") }; val filtered = remember(values, query) { values.filter { it.second.contains(query.trim(), ignoreCase = true) }.take(100) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { Column { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("ابحث عن الطالب بالاسم أو الرقم") }, singleLine = true); LazyColumn(Modifier.heightIn(max = 360.dp)) { items(filtered) { (id, name) -> TextButton({ select(id) }, Modifier.fillMaxWidth()) { Text(name, Modifier.fillMaxWidth()) } } } } }, confirmButton = {}, dismissButton = { TextButton(dismiss) { Text("إلغاء") } })
}
@Composable private fun StatusDialog(dismiss: () -> Unit, select: (AttendanceStatus) -> Unit) { AlertDialog(onDismissRequest = dismiss, title = { Text("تعديل الحالة") }, text = { Column { AttendanceStatus.entries.forEach { TextButton({ select(it) }, Modifier.fillMaxWidth()) { Text(label(it)) } } } }, confirmButton = {}, dismissButton = { TextButton(dismiss) { Text("إلغاء") } }) }
private fun label(status: AttendanceStatus) = when (status) { AttendanceStatus.PRESENT -> "حاضر"; AttendanceStatus.LATE -> "متأخر"; AttendanceStatus.ABSENT -> "غائب"; AttendanceStatus.EXCUSED -> "مجاز" }
private fun time(seconds: Long) = "%02d:%02d".format(seconds / 3600, (seconds % 3600) / 60)
