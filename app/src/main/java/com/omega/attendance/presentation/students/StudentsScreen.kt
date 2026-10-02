package com.omega.attendance.presentation.students

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.omega.attendance.data.local.dao.ClassOption
import com.omega.attendance.data.local.entity.StudentEntity
import com.omega.attendance.data.local.entity.StudentStatus
import com.omega.attendance.data.local.entity.HallEntity
import com.omega.attendance.scanner.QrCodeGenerator
import java.time.LocalDate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.print.PrintHelper
import com.omega.attendance.presentation.components.uniqueLazyItemKey

@Composable
fun StudentsScreen(readOnly: Boolean = false, vm: StudentsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState(); var add by remember { mutableStateOf(false) }; var qrStudent by remember { mutableStateOf<StudentEntity?>(null) }; var editStudent by remember { mutableStateOf<StudentEntity?>(null) }
    var pendingArchive by remember { mutableStateOf<StudentEntity?>(null) }
    var pendingDelete by remember { mutableStateOf<StudentEntity?>(null) }
    var deleteAll by remember { mutableStateOf(false) }
    var importHelp by remember { mutableStateOf(false) }
    var selectedClassId by rememberSaveable { mutableLongStateOf(0L) }
    var selectedHallId by rememberSaveable { mutableLongStateOf(0L) }
    var selecting by remember { mutableStateOf(false) }
    var selectedStudentIds by remember { mutableStateOf(emptySet<Long>()) }
    var pendingCardIds by remember { mutableStateOf<Set<Long>?>(null) }
    var pendingCardClassId by remember { mutableStateOf<Long?>(null) }
    var pendingCardHallId by remember { mutableStateOf<Long?>(null) }
    var classFilterMenu by remember { mutableStateOf(false) }
    var hallFilterMenu by remember { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { it?.let(vm::export) }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::previewImport) }
    val importTemplate = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { it?.let(vm::writeImportTemplate) }
    val cards = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let { vm.exportQrCards(it, pendingCardIds, pendingCardClassId, pendingCardHallId) }
    }
    Scaffold(floatingActionButton = { if (!readOnly) FloatingActionButton({ add = true }) { Icon(Icons.Rounded.PersonAdd, "إضافة طالب") } }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Text("الطلبة", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(vertical = 12.dp))
            OutlinedTextField(state.query, vm::setQuery, Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Rounded.Search, null) }, label = { Text("بحث بالاسم أو الرقم") }, singleLine = true)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    FilterChip(selected = state.filterClassId != null, onClick = { classFilterMenu = true }, label = { Text(state.classes.firstOrNull { it.classId == state.filterClassId }?.className ?: "كل الصفوف") })
                    DropdownMenu(classFilterMenu, { classFilterMenu = false }) {
                        DropdownMenuItem({ Text("كل الصفوف") }, { vm.setClassFilter(null); classFilterMenu = false })
                        state.classes.filter { it.academicYearId == state.activeYearId }.forEach { option -> DropdownMenuItem({ Text(option.className) }, { vm.setClassFilter(option.classId); classFilterMenu = false }) }
                    }
                }
                Box {
                    FilterChip(selected = state.filterHallId != null, onClick = { hallFilterMenu = true }, label = { Text(state.halls.firstOrNull { it.id == state.filterHallId }?.name ?: "كل القاعات") })
                    DropdownMenu(hallFilterMenu, { hallFilterMenu = false }) {
                        DropdownMenuItem({ Text("كل القاعات") }, { vm.setHallFilter(null); hallFilterMenu = false })
                        state.halls.forEach { hall -> DropdownMenuItem({ Text(hall.name) }, { vm.setHallFilter(hall.id); hallFilterMenu = false }) }
                    }
                }
            }
            if (!readOnly) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ importFile.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/pdf")) }) { Icon(Icons.Rounded.UploadFile, null); Spacer(Modifier.width(6.dp)); Text("استيراد ملف") }
                OutlinedButton({ importTemplate.launch("نموذج_استيراد_الطلبة.xlsx") }) { Text("نموذج الاستيراد") }
                OutlinedButton({ importHelp = true }) { Icon(Icons.Rounded.HelpOutline, null); Spacer(Modifier.width(6.dp)); Text("ما فائدته؟") }
                OutlinedButton({ export.launch("قائمة_الطلبة_${LocalDate.now()}.xlsx") }) { Text("تصدير") }
                OutlinedButton({
                    pendingCardIds = null
                    pendingCardClassId = null
                    pendingCardHallId = null
                    cards.launch("بطاقات_QR_كل_الطلبة_" + LocalDate.now() + ".pdf")
                }) { Text("بطاقات QR للكل") }
                OutlinedButton({
                    pendingCardIds = null
                    pendingCardClassId = state.filterClassId
                    pendingCardHallId = state.filterHallId
                    val className = state.classes.firstOrNull { it.classId == state.filterClassId }?.className.orEmpty()
                    val hallName = state.halls.firstOrNull { it.id == state.filterHallId }?.name.orEmpty()
                    cards.launch("بطاقات_QR_${listOf(className, hallName).filter(String::isNotBlank).joinToString("_")}_${LocalDate.now()}.pdf")
                }, enabled = state.filterClassId != null || state.filterHallId != null) { Text("بطاقات حسب التصفية") }
                OutlinedButton({
                    selecting = !selecting
                    if (!selecting) selectedStudentIds = emptySet()
                }) { Text(if (selecting) "إنهاء التحديد" else "تحديد طلبة") }
                OutlinedButton({ deleteAll = true }, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Rounded.DeleteSweep, null); Spacer(Modifier.width(6.dp)); Text("حذف كل الطلبة") }
            }
            if (!readOnly && selecting) Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text("المحددون: " + selectedStudentIds.size)
                Button({
                    pendingCardIds = selectedStudentIds.toSet()
                    pendingCardClassId = null
                    pendingCardHallId = null
                    cards.launch("بطاقات_QR_المحددين_" + LocalDate.now() + ".pdf")
                }, enabled = selectedStudentIds.isNotEmpty()) { Text("إنشاء بطاقات المحددين") }
            }
            if (!readOnly && state.classes.isEmpty()) AssistChip({}, { Text("أضف صفًا دراسيًا من الإعدادات أولًا") }, leadingIcon = { Icon(Icons.Rounded.Warning, null) })
            if (!readOnly) AssistChip({}, { Text("نسي الطالب بطاقة QR؟ سجّل حضوره يدويًا من قسم الحضور") }, leadingIcon = { Icon(Icons.Rounded.Badge, null) })
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 96.dp)) {
                itemsIndexed(state.students, key = { index, student -> uniqueLazyItemKey(student.id, index) }) { _, student ->
                    var more by remember { mutableStateOf(false) }
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            if (selecting && student.status == StudentStatus.ACTIVE) Checkbox(
                                checked = student.id in selectedStudentIds,
                                onCheckedChange = { checked ->
                                    selectedStudentIds = if (checked) selectedStudentIds + student.id else selectedStudentIds - student.id
                                }
                            )
                            Column(Modifier.weight(1f)) {
                                Text(student.fullName, style = MaterialTheme.typography.titleMedium)
                                Text(student.studentNumber, style = MaterialTheme.typography.bodySmall)
                                val className = state.classes.firstOrNull { it.classId == student.classId }?.className.orEmpty()
                                val hallName = state.halls.firstOrNull { it.id == student.hallId }?.name.orEmpty()
                                Text(listOf(className, hallName).filter(String::isNotBlank).joinToString(" • "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                if (student.status == StudentStatus.ARCHIVED) Text("مؤرشف", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                            }
                            IconButton({ vm.showProfile(student.id) }) { Icon(Icons.Rounded.History, "سجل الحضور") }
                            if (!readOnly) {
                                IconButton({ editStudent = student }) { Icon(Icons.Rounded.Edit, "تعديل") }
                                IconButton({ qrStudent = student }) { Icon(Icons.Rounded.QrCode, "QR") }
                                Box {
                                    IconButton({ more = true }) { Icon(Icons.Rounded.MoreVert, "المزيد") }
                                    DropdownMenu(more, { more = false }) {
                                        if (student.status == StudentStatus.ACTIVE) {
                                            DropdownMenuItem(text = { Text("أرشفة الطالب") }, onClick = { pendingArchive = student; more = false })
                                        } else {
                                            DropdownMenuItem(text = { Text("إعادة تفعيل الطالب") }, onClick = { vm.restore(student.id); more = false })
                                        }
                                        DropdownMenuItem(text = { Text("حذف نهائي") }, onClick = { pendingDelete = student; more = false })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (add) AddStudentDialog(
        options = state.classes.filter { it.academicYearId == state.activeYearId },
        halls = state.halls,
        initialClassId = selectedClassId,
        initialHallId = selectedHallId,
        dismiss = { add = false },
        save = { name, option, hall ->
            selectedClassId = option.classId
            selectedHallId = hall.id
            vm.add(name, option, hall.id)
            add = false
        }
    )
    editStudent?.let { student -> EditStudentDialog(student, state.classes, state.halls, { editStudent = null }) { name, option, phone, hallId -> vm.edit(student.id, name, option, phone, hallId); editStudent = null } }
    pendingArchive?.let { student -> AlertDialog(
        onDismissRequest = { pendingArchive = null },
        title = { Text("أرشفة الطالب؟") },
        text = { Text("سيختفي " + student.fullName + " من قائمة الطلبة النشطين، وتبقى سجلات حضوره محفوظة.") },
        confirmButton = { Button({ vm.archive(student.id); pendingArchive = null }) { Text("أرشفة") } },
        dismissButton = { TextButton({ pendingArchive = null }) { Text("إلغاء") } }
    ) }
    pendingDelete?.let { student -> AlertDialog(
        onDismissRequest = { pendingDelete = null },
        title = { Text("حذف الطالب نهائيًا؟") },
        text = { Text("سيُحذف " + student.fullName + " وجميع سجلات حضوره. لا يمكن التراجع عن هذا الإجراء.") },
        confirmButton = { Button({ vm.delete(student.id); pendingDelete = null }) { Text("حذف نهائي") } },
        dismissButton = { TextButton({ pendingDelete = null }) { Text("إلغاء") } }
    ) }
    if (deleteAll) AlertDialog(
        onDismissRequest = { deleteAll = false },
        title = { Text("حذف جميع الطلبة؟") },
        text = { Text("سيُحذف جميع الطلبة وكل سجلات حضورهم نهائيًا. لا يمكن التراجع، ويُفضّل إنشاء نسخة احتياطية أولًا.") },
        confirmButton = { Button({ vm.deleteAll(); deleteAll = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("حذف الكل نهائيًا") } },
        dismissButton = { TextButton({ deleteAll = false }) { Text("إلغاء") } }
    )
    if (importHelp) AlertDialog(
        onDismissRequest = { importHelp = false },
        title = { Text("ما الغاية من نموذج الاستيراد؟") },
        text = { Text("هو ملف Excel بسيط وجاهز لكتابة الأسماء. استخدامه اختياري؛ التطبيق يأخذ الأسماء فقط من Excel أو Word أو PDF النصي، ثم تختار الصف والقاعة داخل التطبيق.") },
        confirmButton = { TextButton({ importHelp = false }) { Text("فهمت") } }
    )
    qrStudent?.let { student -> QrDialog(student, { qrStudent = null }) }
    state.importPreview?.let { preview ->
        val needsHall = preview.valid.any { it.hall.isBlank() }
        val needsClass = preview.valid.any { it.className.isBlank() }
        var hallMenu by remember(preview) { mutableStateOf(false) }
        var classMenu by remember(preview) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = vm::cancelImport,
            title = { Text("معاينة استيراد الطلبة") },
            text = {
                Column(Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.6f).dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("إجمالي الصفوف: ${preview.total}")
                    Text("صالحة: ${preview.valid.size}", color = MaterialTheme.colorScheme.primary)
                    Text("أخطاء: ${preview.issues.size}", color = if (preview.issues.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    if (needsClass) {
                        Text("اختر الصف مرة واحدة لتطبيقه على جميع الأسماء في Word أو PDF:")
                        Box {
                            OutlinedButton({ classMenu = true }, Modifier.fillMaxWidth()) { Text(state.classes.firstOrNull { it.classId == state.importClassId }?.className ?: "اختر الصف") }
                            DropdownMenu(classMenu, { classMenu = false }) {
                                state.classes.filter { it.academicYearId == state.activeYearId }.forEach { option -> DropdownMenuItem({ Text(option.className) }, { vm.selectImportClass(option.classId); classMenu = false }) }
                            }
                        }
                    }
                    if (needsHall) {
                        Text("هذا الملف يحتوي أسماء وصفًا فقط. اختر القاعة مرة واحدة لتطبيقها على جميع الطلبة:")
                        Box {
                            OutlinedButton({ hallMenu = true }, Modifier.fillMaxWidth()) { Text(state.halls.firstOrNull { it.id == state.importHallId }?.name ?: "اختر القاعة") }
                            DropdownMenu(hallMenu, { hallMenu = false }) {
                                state.halls.forEach { hall -> DropdownMenuItem({ Text(hall.name) }, { vm.selectImportHall(hall.id); hallMenu = false }) }
                            }
                        }
                    }
                    if (preview.issues.isEmpty()) {
                        Text("ستُنشأ أرقام الطلاب ورموز STD: تلقائيًا للخانات الفارغة.")
                        preview.valid.take(10).forEach { row -> Text("${row.rowNumber}. ${row.fullName} — ${row.className}${row.hall.takeIf(String::isNotBlank)?.let { " — $it" }.orEmpty()}") }
                        if (preview.valid.size > 10) Text("… و${preview.valid.size - 10} طالبًا آخر")
                    } else {
                        Text("صحّح الملف ثم اختره مجددًا؛ لن يتم حفظ أي صف ما دامت هناك أخطاء.")
                        preview.issues.take(12).forEach { issue -> Text("الصف ${issue.row}: ${issue.message}") }
                        if (preview.issues.size > 12) Text("… و${preview.issues.size - 12} خطأ آخر")
                    }
                }
            },
            confirmButton = { Button(vm::confirmImport, enabled = preview.valid.isNotEmpty() && preview.issues.isEmpty() && (!needsClass || state.importClassId != null) && (!needsHall || state.importHallId != null)) { Text("تأكيد الاستيراد") } },
            dismissButton = { TextButton(vm::cancelImport) { Text("إلغاء") } }
        )
    }
    state.message?.let { AlertDialog(onDismissRequest = vm::dismissMessage, confirmButton = { TextButton(vm::dismissMessage) { Text("حسنًا") } }, text = { Text(it) }) }
    state.profileId?.let { id -> val student = state.students.firstOrNull { it.id == id }; if (student != null) ProfileDialog(student, state.profileHistory, vm::dismissProfile) }
    if (state.busy) Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.padding(24.dp)) }
}

@Composable private fun ProfileDialog(student: StudentEntity, history: List<com.omega.attendance.data.local.entity.AttendanceEntity>, dismiss: () -> Unit) {
    val present = history.count { it.status == com.omega.attendance.data.local.entity.AttendanceStatus.PRESENT }; val late = history.count { it.status == com.omega.attendance.data.local.entity.AttendanceStatus.LATE }
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.55f).dp
    AlertDialog(onDismissRequest = dismiss, title = { Text(student.fullName) }, text = { Column { Text("رقم الطالب: ${student.studentNumber}"); Text("حاضر: $present — متأخر: $late — السجلات: ${history.size}"); LazyColumn(Modifier.heightIn(max = maxHeight)) { itemsIndexed(history, key = { index, row -> uniqueLazyItemKey(row.id, index) }) { _, row -> Text("${java.time.LocalDate.ofEpochDay(row.attendanceDate)} — ${row.status} — ${row.checkInType}", Modifier.padding(vertical = 5.dp)) } } } }, confirmButton = { TextButton(dismiss) { Text("إغلاق") } })
}

@Composable private fun EditStudentDialog(student: StudentEntity, options: List<ClassOption>, halls: List<HallEntity>, dismiss: () -> Unit, save: (String, ClassOption, String, Long?) -> Unit) {
    var name by remember { mutableStateOf(student.fullName) }; var phone by remember { mutableStateOf(student.parentPhone.orEmpty()) }; var selected by remember { mutableStateOf(options.firstOrNull { it.classId == student.classId }) }; var menu by remember { mutableStateOf(false) }
    var hallId by remember { mutableStateOf(student.hallId) }; var hallMenu by remember { mutableStateOf(false) }
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.55f).dp
    AlertDialog(onDismissRequest = dismiss, title = { Text("تعديل الطالب") }, text = { Column(Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(name, { name = it }, label = { Text("الاسم الكامل") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(phone, { phone = it }, label = { Text("هاتف ولي الأمر") }, modifier = Modifier.fillMaxWidth())
        Box {
            OutlinedButton({ menu = true }, Modifier.fillMaxWidth()) { Text(selected?.className ?: "اختر الصف") }
            DropdownMenu(menu, { menu = false }) {
                options.forEach { option -> DropdownMenuItem({ Text(option.className) }, { selected = option; menu = false }) }
            }
        }
        Box {
            OutlinedButton({ hallMenu = true }, Modifier.fillMaxWidth()) { Text(halls.firstOrNull { it.id == hallId }?.name ?: "بدون قاعة") }
            DropdownMenu(hallMenu, { hallMenu = false }) {
                DropdownMenuItem({ Text("بدون قاعة") }, { hallId = null; hallMenu = false })
                halls.forEach { hall -> DropdownMenuItem({ Text(hall.name) }, { hallId = hall.id; hallMenu = false }) }
            }
        }
    } }, confirmButton = { Button({ selected?.let { save(name, it, phone, hallId) } }, enabled = name.isNotBlank() && selected != null) { Text("حفظ") } }, dismissButton = { TextButton(dismiss) { Text("إلغاء") } })
}

@Composable private fun AddStudentDialog(
    options: List<ClassOption>, halls: List<HallEntity>, initialClassId: Long, initialHallId: Long,
    dismiss: () -> Unit, save: (String, ClassOption, HallEntity) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selected by remember(options, initialClassId) { mutableStateOf(options.firstOrNull { it.classId == initialClassId }) }
    var selectedHall by remember(halls, initialHallId) { mutableStateOf(halls.firstOrNull { it.id == initialHallId }) }
    var menu by remember { mutableStateOf(false) }
    var hallMenu by remember { mutableStateOf(false) }
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.55f).dp
    AlertDialog(onDismissRequest = dismiss, title = { Text("إضافة طالب") }, text = { Column(Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(name, { name = it }, label = { Text("الاسم الثلاثي") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Text("رقم الطالب ورمز QR يُنشآن تلقائيًا")
        Box { OutlinedButton({ menu = true }, Modifier.fillMaxWidth()) { Text(selected?.className ?: "اختر الصف مرة واحدة") }; DropdownMenu(menu, { menu = false }) { options.forEach { option -> DropdownMenuItem({ Text(option.className) }, { selected = option; menu = false }) } } }
        Box { OutlinedButton({ hallMenu = true }, Modifier.fillMaxWidth()) { Text(selectedHall?.name ?: "اختر القاعة مرة واحدة") }; DropdownMenu(hallMenu, { hallMenu = false }) { halls.forEach { hall -> DropdownMenuItem({ Text(hall.name) }, { selectedHall = hall; hallMenu = false }) } } }
    } }, confirmButton = { Button({ val option = selected; val hall = selectedHall; if (option != null && hall != null) save(name, option, hall) }, enabled = name.isNotBlank() && selected != null && selectedHall != null) { Text("حفظ") } }, dismissButton = { TextButton(dismiss) { Text("إلغاء") } })
}

@Composable private fun QrDialog(student: StudentEntity, dismiss: () -> Unit) {
    val bitmap: Bitmap = remember(student.qrValue) { QrCodeGenerator().generate(student.qrValue, 512) }
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val qrSize = minOf((configuration.screenWidthDp - 96).dp, (configuration.screenHeightDp * 0.35f).dp, 360.dp).coerceAtLeast(96.dp)
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri -> uri?.let { context.contentResolver.openOutputStream(it)?.use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output) } } }
    AlertDialog(onDismissRequest = dismiss, title = { Text(student.fullName) }, text = { Column(Modifier.heightIn(max = (configuration.screenHeightDp * 0.55f).dp).verticalScroll(rememberScrollState())) { Image(bitmap.asImageBitmap(), "رمز الطالب", Modifier.size(qrSize)); Text(student.studentNumber); Row(Modifier.horizontalScroll(rememberScrollState())) { TextButton({ save.launch("QR_${student.studentNumber}.png") }) { Text("حفظ") }; TextButton({ PrintHelper(context).apply { scaleMode = PrintHelper.SCALE_MODE_FIT }.printBitmap("QR ${student.studentNumber}", bitmap) }) { Text("طباعة") } } } }, confirmButton = { TextButton(dismiss) { Text("إغلاق") } })
}
