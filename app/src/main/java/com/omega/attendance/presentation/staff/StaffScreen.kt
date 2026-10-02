package com.omega.attendance.presentation.staff

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.print.PrintHelper
import com.omega.attendance.data.local.entity.StaffEntity
import com.omega.attendance.scanner.QrCodeGenerator
import com.omega.attendance.domain.model.StaffJobTitles
import com.omega.attendance.data.importer.isStaffQrReady
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StaffScreen(vm: StaffViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    var editing by remember { mutableStateOf<StaffEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<StaffEntity?>(null) }
    var assigningTitle by remember { mutableStateOf<StaffEntity?>(null) }
    var deleteAll by remember { mutableStateOf(false) }
    var importHelp by remember { mutableStateOf(false) }
    var qrMember by remember { mutableStateOf<StaffEntity?>(null) }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::previewImport) }
    val exportCards = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri -> uri?.let(vm::exportQrCards) }
    Scaffold(floatingActionButton = { FloatingActionButton({ adding = true }) { Icon(Icons.Rounded.PersonAdd, "إضافة موظف") } }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Text("الكادر المدرسي", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(vertical = 12.dp))
            OutlinedButton({ importFile.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/pdf")) }, Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.UploadFile, null)
                Spacer(Modifier.width(8.dp))
                Text("استيراد الكادر من Excel أو Word أو PDF")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton({ importHelp = true }) { Icon(Icons.Rounded.HelpOutline, null); Spacer(Modifier.width(6.dp)); Text("طريقة تجهيز الملف") }
                TextButton({ deleteAll = true }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Rounded.DeleteSweep, null); Spacer(Modifier.width(6.dp)); Text("حذف كل الكادر") }
            }
            OutlinedButton({ exportCards.launch("بطاقات_QR_كل_الكادر_${LocalDate.now()}.pdf") }, Modifier.fillMaxWidth(), enabled = state.members.isNotEmpty() && state.members.all { isStaffQrReady(it.jobTitle) }) {
                Icon(Icons.Rounded.PictureAsPdf, null)
                Spacer(Modifier.width(8.dp))
                Text("تصدير بطاقات QR للكل")
            }
            OutlinedTextField(state.query, vm::setQuery, Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Rounded.Search, null) }, label = { Text("بحث بالاسم أو المسمى الوظيفي") }, singleLine = true)
            if (state.members.isEmpty() && !state.busy) Text("لا يوجد موظفون. اضغط + لإضافة أول موظف.", Modifier.padding(16.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)) {
                items(state.members, key = { it.id }) { member ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(member.fullName, style = MaterialTheme.typography.titleMedium)
                            Text(member.jobTitle.ifBlank { "لم يُحدد المسمى الوظيفي" }, style = MaterialTheme.typography.bodyMedium, color = if (member.jobTitle.isBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                            member.specialty?.let { Text("الاختصاص: $it", style = MaterialTheme.typography.bodyMedium) }
                            if (member.phone != null) Text(member.phone, style = MaterialTheme.typography.bodySmall)
                            if (member.status == "ARCHIVED") Text("مؤرشف", color = MaterialTheme.colorScheme.error)
                            val today = state.today.firstOrNull { it.staffId == member.id }
                            Text(if (today == null) "لم يُسجل حضوره اليوم" else "حاضر اليوم — ${if (today.checkInType.name == "MANUAL") "يدوي" else "QR"}", color = if (today == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
                            FlowRow {
                                TextButton({ editing = member }) { Text("تعديل") }
                                TextButton({ assigningTitle = member }) { Text(if (member.jobTitle.isBlank()) "اختيار المسمى الوظيفي" else "تغيير المسمى الوظيفي") }
                                TextButton({ qrMember = member }, enabled = isStaffQrReady(member.jobTitle)) { Text("QR") }
                                if (member.status == "ACTIVE" && today == null) TextButton({ vm.addManual(member.id) }) { Text("حضور يدوي") }
                                TextButton({ vm.archive(member.id, member.status == "ACTIVE") }) { Text(if (member.status == "ACTIVE") "أرشفة" else "إعادة تفعيل") }
                                TextButton({ deleting = member }) { Text("حذف") }
                            }
                        }
                    }
                }
            }
        }
    }
    if (adding) StaffEditor(null, { adding = false }) { name, title, phone, notes, specialty -> vm.add(name, title, phone, notes, specialty); adding = false }
    editing?.let { member -> StaffEditor(member, { editing = null }) { name, title, phone, notes, specialty -> vm.edit(member.id, name, title, phone, notes, specialty); editing = null } }
    deleting?.let { member -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("حذف الموظف نهائيًا؟") }, text = { Text("سيُحذف ${member.fullName} وجميع سجلات حضوره. لا يمكن التراجع عن هذا الإجراء.") }, confirmButton = { Button({ vm.delete(member.id); deleting = null }) { Text("حذف") } }, dismissButton = { TextButton({ deleting = null }) { Text("إلغاء") } }) }
    assigningTitle?.let { member -> AlertDialog(
        onDismissRequest = { assigningTitle = null },
        title = { Text("المسمى الوظيفي — ${member.fullName}") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { StaffJobTitles.values.forEach { title -> OutlinedButton({ vm.edit(member.id, member.fullName, title, member.phone.orEmpty(), member.notes.orEmpty(), member.specialty.orEmpty()); assigningTitle = null }, Modifier.fillMaxWidth()) { Text(title) } } } },
        confirmButton = { TextButton({ assigningTitle = null }) { Text("إلغاء") } }
    ) }
    if (deleteAll) AlertDialog(onDismissRequest = { deleteAll = false }, title = { Text("حذف جميع أفراد الكادر؟") }, text = { Text("سيُحذف جميع أفراد الكادر وكل سجلات حضورهم نهائيًا. لا يمكن التراجع، ويُفضّل إنشاء نسخة احتياطية أولًا.") }, confirmButton = { Button({ vm.deleteAll(); deleteAll = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("حذف الكل نهائيًا") } }, dismissButton = { TextButton({ deleteAll = false }) { Text("إلغاء") } })
    if (importHelp) AlertDialog(onDismissRequest = { importHelp = false }, title = { Text("طريقة تجهيز ملف الكادر") }, text = { Text("يكفي أن يحتوي الملف على عمود للأسماء. التطبيق يأخذ الأسماء فقط من Excel أو Word أو PDF النصي، ثم تختار المسمى الوظيفي داخل التطبيق. ملفات PDF الممسوحة كصور تحتاج تحويلًا إلى Word أو Excel أولًا.") }, confirmButton = { TextButton({ importHelp = false }) { Text("فهمت") } })
    qrMember?.let { StaffQrDialog(it) { qrMember = null } }
    state.importPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = vm::cancelImport,
            title = { Text("معاينة استيراد الكادر") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("تم العثور على ${preview.rows.size} اسمًا. ستُحفظ الأسماء أولًا، وبعدها اختر المسمى الوظيفي أمام كل اسم في قائمة الكادر.")
                    preview.rows.forEach { row ->
                        Text("${row.rowNumber}. ${row.fullName}")
                    }
                }
            },
            confirmButton = { Button(vm::confirmImport, enabled = preview.rows.isNotEmpty()) { Text("استيراد الأسماء") } },
            dismissButton = { TextButton(vm::cancelImport) { Text("إلغاء") } }
        )
    }
    state.message?.let { AlertDialog(onDismissRequest = vm::dismissMessage, confirmButton = { TextButton(vm::dismissMessage) { Text("حسنًا") } }, text = { Text(it) }) }
    if (state.busy) CircularProgressIndicator(Modifier.padding(24.dp))
}

@Composable
private fun StaffQrDialog(member: StaffEntity, dismiss: () -> Unit) {
    val bitmap: Bitmap = remember(member.qrValue) { QrCodeGenerator().generate(member.qrValue, 512) }
    val context = LocalContext.current
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri -> uri?.let { context.contentResolver.openOutputStream(it)?.use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output) } } }
    AlertDialog(onDismissRequest = dismiss, title = { Text("بطاقة الكادر — ${member.fullName}") }, text = { Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) { Image(bitmap.asImageBitmap(), "رمز QR للموظف", Modifier.size(280.dp)); Text(member.jobTitle); Row { TextButton({ save.launch("QR_كادر_${member.id}.png") }) { Text("حفظ") }; TextButton({ PrintHelper(context).apply { scaleMode = PrintHelper.SCALE_MODE_FIT }.printBitmap("Staff QR ${member.id}", bitmap) }) { Text("طباعة") } } } }, confirmButton = { TextButton(dismiss) { Text("إغلاق") } })
}

@Composable
private fun StaffEditor(member: StaffEntity?, dismiss: () -> Unit, save: (String, String, String, String, String) -> Unit) {
    var name by remember(member) { mutableStateOf(member?.fullName.orEmpty()) }
    var title by remember(member) { mutableStateOf(member?.jobTitle.orEmpty()) }
    var phone by remember(member) { mutableStateOf(member?.phone.orEmpty()) }
    var notes by remember(member) { mutableStateOf(member?.notes.orEmpty()) }
    var specialty by remember(member) { mutableStateOf(member?.specialty.orEmpty()) }
    var menu by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(if (member == null) "إضافة موظف" else "تعديل موظف") }, text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("الاسم الكامل") }, singleLine = true)
            OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("المسمى الوظيفي") }, singleLine = true)
            Box { TextButton({ menu = true }) { Text("اختيار المسمى الوظيفي") }; DropdownMenu(menu, { menu = false }) { StaffJobTitles.values.forEach { value -> DropdownMenuItem({ Text(value) }, { title = value; menu = false }) } } }
            OutlinedTextField(specialty, { specialty = it }, Modifier.fillMaxWidth(), label = { Text("الاختصاص (اختياري)") }, singleLine = true)
            OutlinedTextField(phone, { phone = it }, Modifier.fillMaxWidth(), label = { Text("رقم الهاتف (اختياري)") }, singleLine = true)
            OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("ملاحظات (اختياري)") })
        }
    }, confirmButton = { Button({ save(name, title, phone, notes, specialty) }, enabled = name.isNotBlank() && title.isNotBlank()) { Text("حفظ") } }, dismissButton = { TextButton(dismiss) { Text("إلغاء") } })
}
