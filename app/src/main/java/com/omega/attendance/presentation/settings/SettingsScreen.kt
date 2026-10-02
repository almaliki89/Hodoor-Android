package com.omega.attendance.presentation.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.omega.attendance.BuildConfig
import com.omega.attendance.data.local.entity.CalendarDayType
import com.omega.attendance.data.local.entity.AcademicYearEntity
import com.omega.attendance.data.local.entity.HallEntity
import com.omega.attendance.data.local.dao.ClassOption
import com.omega.attendance.presentation.backup.BackupSection
import com.omega.attendance.presentation.components.ResponsivePair
import com.omega.attendance.presentation.components.uniqueLazyItemKey

private enum class Category(val title: String, val hint: String, val icon: ImageVector) {
    SCHOOL("المدرسة والسنة", "اسم المدرسة والسنة الدراسية", Icons.Rounded.School),
    STRUCTURE("الصفوف والقاعات", "إضافة الصفوف والقاعات وعرضها", Icons.Rounded.MeetingRoom),
    ATTENDANCE("الحضور والماسح", "الأوقات والصوت والاهتزاز", Icons.Rounded.QrCodeScanner),
    CALENDAR("التقويم المدرسي", "العطل وأيام الامتحانات", Icons.Rounded.CalendarMonth),
    BACKUP("البيانات والنسخ", "النسخ الاحتياطي والاستعادة", Icons.Rounded.Storage),
    SECURITY("الأمان", "رمز الإدارة", Icons.Rounded.Lock),
    ABOUT("حول البرنامج", "الإصدار والتواصل", Icons.Rounded.Info)
}

private enum class DeleteKind { YEAR, CLASS, HALL, CALENDAR }
private data class DeleteTarget(val kind: DeleteKind, val id: Long, val name: String)

@Composable
fun SettingsScreen(vm: SettingsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    var selected by rememberSaveable { mutableStateOf("") }
    var schoolName by remember(state.preferences.schoolName) { mutableStateOf(state.preferences.schoolName) }
    var yearName by remember { mutableStateOf("") }
    var hallName by remember { mutableStateOf("") }
    var hallNumber by remember { mutableStateOf("") }
    var capacity by remember { mutableStateOf("") }
    var grade by remember { mutableStateOf("") }
    var section by remember { mutableStateOf("") }
    var className by remember { mutableStateOf("") }
    var classHallId by remember { mutableStateOf<Long?>(null) }
    var hallMenu by remember { mutableStateOf(false) }
    var selectedYear by remember(state.preferences.activeAcademicYearId) { mutableLongStateOf(state.preferences.activeAcademicYearId) }
    var startTime by remember(state.preferences.startMinutes) { mutableStateOf("%02d:%02d".format(state.preferences.startMinutes / 60, state.preferences.startMinutes % 60)) }
    var lateTime by remember(state.preferences.lateMinutes) { mutableStateOf("%02d:%02d".format(state.preferences.lateMinutes / 60, state.preferences.lateMinutes % 60)) }
    var cooldown by remember(state.preferences.scannerCooldownMillis) { mutableStateOf(state.preferences.scannerCooldownMillis.toString()) }
    var sound by remember(state.preferences.soundEnabled) { mutableStateOf(state.preferences.soundEnabled) }
    var vibration by remember(state.preferences.vibrationEnabled) { mutableStateOf(state.preferences.vibrationEnabled) }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var calendarDate by remember { mutableStateOf(java.time.LocalDate.now().toString()) }
    var calendarTitle by remember { mutableStateOf("") }
    var calendarType by remember { mutableStateOf(CalendarDayType.HOLIDAY) }
    var editingYear by remember { mutableStateOf<AcademicYearEntity?>(null) }
    var editingClass by remember { mutableStateOf<ClassOption?>(null) }
    var editingHall by remember { mutableStateOf<HallEntity?>(null) }
    var deleting by remember { mutableStateOf<DeleteTarget?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.years, state.preferences.activeAcademicYearId) {
        if (state.years.isNotEmpty() && state.preferences.activeAcademicYearId == 0L) vm.ensureActiveYear()
    }
    LaunchedEffect(state.halls) {
        if (classHallId != null && state.halls.none { it.id == classHallId }) classHallId = null
    }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.dismissMessage() }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val tablet = maxWidth >= 600.dp
        val category = Category.entries.firstOrNull { it.name == selected } ?: Category.SCHOOL
        if (!tablet && selected.isNotEmpty()) BackHandler { selected = "" }

        val details: @Composable (Modifier) -> Unit = { modifier ->
            val listState = remember(category) { LazyListState() }
            LazyColumn(modifier.fillMaxHeight(), state = listState, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    if (!tablet) TextButton(onClick = { selected = "" }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, null)
                        Spacer(Modifier.width(8.dp))
                        Text("كل الإعدادات")
                    }
                    Text(category.title, style = MaterialTheme.typography.headlineMedium)
                    Text(category.hint, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                when (category) {
                    Category.SCHOOL -> {
                        item {
                            SettingsCard("بيانات المدرسة") {
                                OutlinedTextField(schoolName, { schoolName = it }, label = { Text("اسم المدرسة") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                Text("السنة الدراسية النشطة", style = MaterialTheme.typography.titleMedium)
                                if (state.years.isEmpty()) Text("لا توجد سنة دراسية بعد")
                                state.years.forEach { year ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(selectedYear == year.id, { selectedYear = year.id })
                                        Text(year.name, Modifier.weight(1f))
                                        IconButton({ editingYear = year }) { Icon(Icons.Rounded.Edit, "تعديل السنة") }
                                        IconButton({ deleting = DeleteTarget(DeleteKind.YEAR, year.id, year.name) }) { Icon(Icons.Rounded.DeleteOutline, "حذف السنة") }
                                    }
                                }
                                Button({ vm.saveSchool(schoolName, selectedYear) }, Modifier.fillMaxWidth()) { Text("حفظ بيانات المدرسة") }
                            }
                        }
                        item {
                            SettingsCard("إضافة سنة دراسية", "مثال: 2026-2027") {
                                OutlinedTextField(yearName, { yearName = it }, label = { Text("السنة الدراسية") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                Button({ vm.addAcademicYear(yearName) }, enabled = yearName.matches(Regex("\\d{4}-\\d{4}"))) { Text("إضافة السنة") }
                            }
                        }
                    }
                    Category.STRUCTURE -> {
                        item {
                            SettingsCard("إضافة صف", "اختر السنة النشطة من قسم المدرسة أولًا") {
                                if (selectedYear == 0L) Text("لا توجد سنة دراسية نشطة", color = MaterialTheme.colorScheme.error)
                                OutlinedTextField(grade, { grade = it }, label = { Text("المرحلة، مثال: السادس") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                OutlinedTextField(className, { className = it }, label = { Text("اسم الصف، مثال: العلمي") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                OutlinedTextField(section, { section = it }, label = { Text("الشعبة، مثال: أ") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                Box {
                                    OutlinedButton({ hallMenu = true }, Modifier.fillMaxWidth()) {
                                        Text(state.halls.firstOrNull { it.id == classHallId }?.name ?: "القاعة: بدون تحديد")
                                    }
                                    DropdownMenu(hallMenu, { hallMenu = false }) {
                                        DropdownMenuItem(text = { Text("بدون قاعة") }, onClick = { classHallId = null; hallMenu = false })
                                        state.halls.forEach { hall ->
                                            DropdownMenuItem(text = { Text(hall.name) }, onClick = { classHallId = hall.id; hallMenu = false })
                                        }
                                    }
                                }
                                Button(
                                    { vm.addClass(selectedYear, grade, section, className, classHallId) },
                                    Modifier.fillMaxWidth(),
                                    enabled = selectedYear > 0 && grade.isNotBlank() && section.isNotBlank() && className.isNotBlank()
                                ) { Text("إضافة الصف") }
                            }
                        }
                        item {
                            SettingsCard("إضافة قاعة") {
                                OutlinedTextField(hallName, { hallName = it }, label = { Text("اسم القاعة") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                ResponsivePair(
                                    first = { m -> OutlinedTextField(hallNumber, { hallNumber = it }, label = { Text("الرقم") }, modifier = m, singleLine = true) },
                                    second = { m -> OutlinedTextField(capacity, { capacity = it.filter(Char::isDigit) }, label = { Text("السعة") }, modifier = m, singleLine = true) }
                                )
                                Button({ vm.addHall(hallName, hallNumber, capacity) }, Modifier.fillMaxWidth(), enabled = hallName.isNotBlank() && hallNumber.isNotBlank()) { Text("إضافة القاعة") }
                            }
                        }
                        if (state.classes.isNotEmpty()) {
                            item { Text("الصفوف الحالية (" + state.classes.size + ")", style = MaterialTheme.typography.titleLarge) }
                            itemsIndexed(state.classes, key = { index, row -> "class-" + uniqueLazyItemKey(row.classId, index) }) { _, row ->
                                ListItem(
                                    headlineContent = { Text(row.gradeName + " — " + row.className + " — " + row.sectionName) },
                                    supportingContent = { Text(row.hallName ?: "بدون قاعة") },
                                    trailingContent = {
                                        Row {
                                            IconButton({ editingClass = row }) { Icon(Icons.Rounded.Edit, "تعديل الصف") }
                                            IconButton({ deleting = DeleteTarget(DeleteKind.CLASS, row.classId, row.className) }) { Icon(Icons.Rounded.DeleteOutline, "حذف الصف") }
                                        }
                                    }
                                )
                            }
                        }
                        if (state.halls.isNotEmpty()) {
                            item { Text("القاعات الحالية (" + state.halls.size + ")", style = MaterialTheme.typography.titleLarge) }
                            itemsIndexed(state.halls, key = { index, hall -> "hall-" + uniqueLazyItemKey(hall.id, index) }) { _, hall ->
                                ListItem(
                                    headlineContent = { Text(hall.name) },
                                    supportingContent = { Text("الرقم " + hall.number + (hall.capacity?.let { " • السعة " + it } ?: "")) },
                                    trailingContent = {
                                        Row {
                                            IconButton({ editingHall = hall }) { Icon(Icons.Rounded.Edit, "تعديل القاعة") }
                                            IconButton({ deleting = DeleteTarget(DeleteKind.HALL, hall.id, hall.name) }) { Icon(Icons.Rounded.DeleteOutline, "حذف القاعة") }
                                        }
                                    }
                                )
                            }
                        }
                    }
                    Category.ATTENDANCE -> item {
                        SettingsCard("أوقات الحضور") {
                            ResponsivePair(
                                first = { m -> OutlinedTextField(startTime, { startTime = it.take(5) }, label = { Text("بداية الدوام HH:mm") }, modifier = m, singleLine = true) },
                                second = { m -> OutlinedTextField(lateTime, { lateTime = it.take(5) }, label = { Text("التأخر بعد HH:mm") }, modifier = m, singleLine = true) }
                            )
                            OutlinedTextField(cooldown, { cooldown = it.filter(Char::isDigit) }, label = { Text("مهلة الماسح بالمللي ثانية") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            HorizontalDivider()
                            Row(verticalAlignment = Alignment.CenterVertically) { Text("الصوت بعد المسح", Modifier.weight(1f)); Switch(sound, { sound = it }) }
                            Row(verticalAlignment = Alignment.CenterVertically) { Text("الاهتزاز بعد المسح", Modifier.weight(1f)); Switch(vibration, { vibration = it }) }
                            Button({ vm.saveAttendance(startTime, lateTime, cooldown, sound, vibration) }, Modifier.fillMaxWidth()) { Text("حفظ إعدادات الحضور") }
                        }
                    }
                    Category.CALENDAR -> item {
                        SettingsCard("تحديد يوم في التقويم", "اختر نوع اليوم ثم احفظه") {
                            OutlinedTextField(calendarDate, { calendarDate = it }, label = { Text("التاريخ YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            OutlinedTextField(calendarTitle, { calendarTitle = it }, label = { Text("وصف اختياري") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CalendarDayType.entries.forEach { type ->
                                    FilterChip(calendarType == type, { calendarType = type }, {
                                        Text(when (type) {
                                            CalendarDayType.SCHOOL_DAY -> "دوام"
                                            CalendarDayType.WEEKEND -> "عطلة أسبوعية"
                                            CalendarDayType.HOLIDAY -> "عطلة"
                                            CalendarDayType.EXAM -> "امتحان"
                                            CalendarDayType.CLOSED -> "مغلق"
                                        })
                                    })
                                }
                            }
                            Button({ vm.saveCalendarDay(calendarDate, selectedYear, calendarType, calendarTitle) }, Modifier.fillMaxWidth(), enabled = selectedYear > 0) { Text("حفظ اليوم") }
                            OutlinedButton(
                                { deleting = DeleteTarget(DeleteKind.CALENDAR, selectedYear, calendarDate) },
                                Modifier.fillMaxWidth(),
                                enabled = selectedYear > 0
                            ) { Text("حذف تخصيص هذا اليوم") }
                        }
                    }
                    Category.BACKUP -> {
                        item { BackupSection() }
                        if (BuildConfig.DEBUG) item {
                            SettingsCard("أدوات التطوير", "للاختبار فقط؛ لا تستخدمها على بيانات المدرسة الحقيقية") {
                                OutlinedButton(vm::generateLoadData, Modifier.fillMaxWidth()) { Text("إنشاء بيانات اختبار الحمل") }
                            }
                        }
                    }
                    Category.SECURITY -> item {
                        SettingsCard("رمز الإدارة", "استخدم رمزًا من 4 إلى 12 رقمًا") {
                            OutlinedTextField(newPin, { newPin = it.filter(Char::isDigit).take(12) }, label = { Text("PIN جديد") }, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(), singleLine = true)
                            OutlinedTextField(confirmPin, { confirmPin = it.filter(Char::isDigit).take(12) }, label = { Text("تأكيد PIN") }, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(), singleLine = true)
                            Button({ vm.changePin(newPin, confirmPin); newPin = ""; confirmPin = "" }, Modifier.fillMaxWidth(), enabled = newPin.length >= 4) { Text("تغيير رمز الإدارة") }
                        }
                    }
                    Category.ABOUT -> item { AboutSection() }
                }
            }
        }

        if (tablet) {
            Row(Modifier.fillMaxSize()) {
                Surface(Modifier.width(248.dp).fillMaxHeight(), tonalElevation = 2.dp) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("الإعدادات", Modifier.padding(12.dp), style = MaterialTheme.typography.headlineSmall)
                        Category.entries.forEach { item ->
                            NavigationDrawerItem(
                                label = { Text(item.title) },
                                selected = category == item,
                                onClick = { selected = item.name },
                                icon = { Icon(item.icon, null) }
                            )
                        }
                    }
                }
                details(Modifier.weight(1f))
            }
        } else if (selected.isEmpty()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Text("الإعدادات", style = MaterialTheme.typography.headlineMedium)
                    Text("اختر القسم الذي تريد تعديله", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Category.entries.forEach { item ->
                    item(key = item.name) {
                        ElevatedCard(Modifier.fillMaxWidth().clickable { selected = item.name }) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(item.icon, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                                    Text(item.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.Rounded.ChevronLeft, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        } else details(Modifier.fillMaxWidth())
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
    editingYear?.let { year -> EditYearDialog(year, { editingYear = null }) { name -> vm.updateYear(year.id, name); editingYear = null } }
    editingHall?.let { hall -> EditHallDialog(hall, { editingHall = null }) { name, number, capacity ->
        vm.updateHall(hall.id, name, number, capacity); editingHall = null
    } }
    editingClass?.let { row -> EditClassDialog(row, state.halls, { editingClass = null }) { gradeName, sectionName, name, hallId ->
        vm.updateClass(row.classId, gradeName, sectionName, name, hallId); editingClass = null
    } }
    deleting?.let { target -> AlertDialog(
        onDismissRequest = { deleting = null },
        title = { Text("تأكيد الحذف") },
        text = { Text(when (target.kind) {
            DeleteKind.YEAR -> "سيُحذف العام " + target.name + " وصفوفه وتقويمه. لن يُسمح بذلك إذا وُجد طلبة أو سجلات حضور مرتبطة به."
            DeleteKind.CLASS -> "سيُحذف الصف " + target.name + " نهائيًا. انقل طلبته أولًا إلى صف آخر."
            DeleteKind.HALL -> "سيُحذف " + target.name + "، وستصبح القاعة غير محددة للطلبة والصفوف المرتبطة بها."
            DeleteKind.CALENDAR -> "سيُحذف تخصيص يوم " + target.name + " من التقويم المدرسي."
        }) },
        confirmButton = { Button({
            when (target.kind) {
                DeleteKind.YEAR -> vm.deleteYear(target.id)
                DeleteKind.CLASS -> vm.deleteClass(target.id)
                DeleteKind.HALL -> vm.deleteHall(target.id)
                DeleteKind.CALENDAR -> vm.deleteCalendarDay(target.name, target.id)
            }
            deleting = null
        }) { Text("حذف") } },
        dismissButton = { TextButton({ deleting = null }) { Text("إلغاء") } }
    ) }
}

@Composable
private fun SettingsCard(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            subtitle?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
            content()
        }
    }
}

@Composable
private fun EditYearDialog(year: AcademicYearEntity, dismiss: () -> Unit, save: (String) -> Unit) {
    var name by remember(year.id) { mutableStateOf(year.name) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("تعديل السنة الدراسية") },
        text = { OutlinedTextField(name, { name = it }, label = { Text("اسم السنة") }, singleLine = true) },
        confirmButton = { Button({ save(name) }, enabled = name.isNotBlank()) { Text("حفظ") } },
        dismissButton = { TextButton(dismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun EditHallDialog(hall: HallEntity, dismiss: () -> Unit, save: (String, String, String) -> Unit) {
    var name by remember(hall.id) { mutableStateOf(hall.name) }
    var number by remember(hall.id) { mutableStateOf(hall.number) }
    var capacity by remember(hall.id) { mutableStateOf(hall.capacity?.toString().orEmpty()) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("تعديل القاعة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("اسم القاعة") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(number, { number = it }, label = { Text("رقم القاعة") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(capacity, { capacity = it.filter(Char::isDigit) }, label = { Text("السعة") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        },
        confirmButton = { Button({ save(name, number, capacity) }, enabled = name.isNotBlank() && number.isNotBlank()) { Text("حفظ") } },
        dismissButton = { TextButton(dismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun EditClassDialog(
    row: ClassOption,
    halls: List<HallEntity>,
    dismiss: () -> Unit,
    save: (String, String, String, Long?) -> Unit
) {
    var grade by remember(row.classId) { mutableStateOf(row.gradeName) }
    var section by remember(row.classId) { mutableStateOf(row.sectionName) }
    var name by remember(row.classId) { mutableStateOf(row.className) }
    var hallId by remember(row.classId) { mutableStateOf(row.hallId) }
    var hallMenu by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("تعديل الصف") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(grade, { grade = it }, label = { Text("المرحلة") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(name, { name = it }, label = { Text("اسم الصف") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(section, { section = it }, label = { Text("الشعبة") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Box {
                    OutlinedButton({ hallMenu = true }, Modifier.fillMaxWidth()) {
                        Text(halls.firstOrNull { it.id == hallId }?.name ?: "بدون قاعة")
                    }
                    DropdownMenu(hallMenu, { hallMenu = false }) {
                        DropdownMenuItem({ Text("بدون قاعة") }, { hallId = null; hallMenu = false })
                        halls.forEach { hall ->
                            DropdownMenuItem({ Text(hall.name) }, { hallId = hall.id; hallMenu = false })
                        }
                    }
                }
            }
        },
        confirmButton = { Button({ save(grade, section, name, hallId) }, enabled = grade.isNotBlank() && section.isNotBlank() && name.isNotBlank()) { Text("حفظ") } },
        dismissButton = { TextButton(dismiss) { Text("إلغاء") } }
    )
}
