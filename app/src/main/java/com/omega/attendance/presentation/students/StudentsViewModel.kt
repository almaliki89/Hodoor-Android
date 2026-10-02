package com.omega.attendance.presentation.students

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omega.attendance.data.local.dao.ClassOption
import com.omega.attendance.data.local.entity.StudentEntity
import com.omega.attendance.data.local.entity.HallEntity
import com.omega.attendance.data.repository.*
import com.omega.attendance.data.settings.SettingsRepository
import com.omega.attendance.domain.model.*
import com.omega.attendance.export.excel.XlsxCodec
import com.omega.attendance.data.importer.RosterDocumentImporter
import com.omega.attendance.export.pdf.QrCardsPdfExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class StudentsUiState(
    val query: String = "", val students: List<StudentEntity> = emptyList(),
    val classes: List<ClassOption> = emptyList(), val halls: List<HallEntity> = emptyList(),
    val activeYearId: Long = 0, val busy: Boolean = false,
    val message: String? = null,
    val profileId: Long? = null, val profileHistory: List<com.omega.attendance.data.local.entity.AttendanceEntity> = emptyList(),
    val importPreview: ImportPreview? = null, val importHallId: Long? = null, val importClassId: Long? = null,
    val filterClassId: Long? = null, val filterHallId: Long? = null
)

@OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class StudentsViewModel @Inject constructor(
    private val repository: StudentRepository,
    private val structure: SchoolStructureRepository,
    private val settings: SettingsRepository,
    private val xlsx: XlsxCodec,
    private val documentImporter: RosterDocumentImporter,
    private val qrCards: QrCardsPdfExporter,
    attendance: AttendanceRepository
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val profileId = MutableStateFlow<Long?>(null)
    private val importPreview = MutableStateFlow<ImportPreview?>(null)
    private val importHallId = MutableStateFlow<Long?>(null)
    private val importClassId = MutableStateFlow<Long?>(null)
    private val filterClassId = MutableStateFlow<Long?>(null)
    private val filterHallId = MutableStateFlow<Long?>(null)
    private var rawImportPreview: ImportPreview? = null
    private var pendingImportRows: List<NewStudent> = emptyList()
    private val profileHistory = profileId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else attendance.forStudent(id) }
    private val searchRequest = combine(query.debounce(250), filterClassId, filterHallId) { text, classId, hallId -> Triple(text, classId, hallId) }
    private val results = searchRequest.flatMapLatest { request -> repository.search(request.first, request.second, request.third) }
    val state = combine(query, results, structure.classOptions(), structure.halls(), settings.preferences, busy, message, profileId, profileHistory, importPreview, importHallId, importClassId, filterClassId, filterHallId) { values ->
        @Suppress("UNCHECKED_CAST")
        StudentsUiState(
            query = values[0] as String,
            students = values[1] as List<StudentEntity>,
            classes = values[2] as List<ClassOption>,
            halls = values[3] as List<HallEntity>,
            activeYearId = (values[4] as com.omega.attendance.data.settings.AppPreferences).activeAcademicYearId,
            busy = values[5] as Boolean,
            message = values[6] as String?,
            profileId = values[7] as Long?,
            profileHistory = values[8] as List<com.omega.attendance.data.local.entity.AttendanceEntity>,
            importPreview = values[9] as ImportPreview?,
            importHallId = values[10] as Long?,
            importClassId = values[11] as Long?,
            filterClassId = values[12] as Long?,
            filterHallId = values[13] as Long?
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StudentsUiState())

    init {
        viewModelScope.launch {
            runCatching {
                val preferences = settings.preferences.first()
                val yearId = structure.ensureDefaults(preferences.activeAcademicYearId)
                if (preferences.activeAcademicYearId == 0L) settings.updateSchool(preferences.schoolName, yearId)
            }.onFailure { message.value = "تعذر إعداد الصفوف والقاعات تلقائيًا" }
        }
    }

    fun setQuery(value: String) { query.value = value }
    fun setClassFilter(value: Long?) { filterClassId.value = value }
    fun setHallFilter(value: Long?) { filterHallId.value = value }
    fun add(name: String, option: ClassOption, hallId: Long) = launchTask("تمت إضافة الطالب وإنشاء رقمه وQR تلقائيًا") {
        repository.add(NewStudent("", name, option.gradeId, option.classId, option.sectionId, hallId, option.academicYearId))
    }
    fun archive(id: Long) = launchTask("تمت أرشفة الطالب") { repository.archive(id) }
    fun restore(id: Long) = launchTask("تمت إعادة تفعيل الطالب") { repository.restore(id) }
    fun delete(id: Long) = launchTask("تم حذف الطالب وسجلات حضوره نهائيًا") { repository.deletePermanently(id) }
    fun deleteAll() = launchTask("تم حذف جميع الطلبة وسجلات حضورهم نهائيًا") { repository.deleteAllPermanently() }
    fun edit(id: Long, name: String, option: ClassOption, phone: String, hallId: Long?) = launchTask("تم تحديث بيانات الطالب") { repository.edit(id, name, phone, option, hallId) }
    fun export(uri: Uri) = launchTask("تم تصدير ملف Excel بنجاح") {
        withContext(Dispatchers.IO) {
            xlsx.writeStudents(uri, repository.all(), state.value.classes, state.value.halls, settings.preferences.first().schoolName)
        }
    }
    fun writeImportTemplate(uri: Uri) = launchTask("تم إنشاء نموذج استيراد الطلبة") { withContext(Dispatchers.IO) { xlsx.writeTemplate(uri) } }
    fun previewImport(uri: Uri) = viewModelScope.launch {
        busy.value = true
        runCatching {
            val existing = repository.all()
            val parsed = documentImporter.readStudents(uri, existing.map { it.fullName }.toSet())
            rawImportPreview = parsed
            importHallId.value = null
            importClassId.value = null
            prepareImport(parsed, null, null)
        }.onSuccess { importPreview.value = it }.onFailure { message.value = it.message ?: "تعذر قراءة ملف الاستيراد" }
        busy.value = false
    }
    fun selectImportHall(id: Long) {
        importHallId.value = id
        rawImportPreview?.let { importPreview.value = prepareImport(it, id, importClassId.value) }
    }
    fun selectImportClass(id: Long) {
        importClassId.value = id
        rawImportPreview?.let { importPreview.value = prepareImport(it, importHallId.value, id) }
    }
    fun confirmImport() {
        val rows = pendingImportRows
        if (rows.isEmpty() || rows.size != importPreview.value?.valid?.size || importPreview.value?.issues?.isNotEmpty() != false) return
        launchTask(null) {
            repository.addAll(rows)
            importPreview.value = null
            rawImportPreview = null
            importHallId.value = null
            importClassId.value = null
            pendingImportRows = emptyList()
            message.value = "تم استيراد ${rows.size} طالبًا وإنشاء أرقامهم ورموز QR تلقائيًا"
        }
    }
    fun cancelImport() { importPreview.value = null; importHallId.value = null; importClassId.value = null; rawImportPreview = null; pendingImportRows = emptyList() }
    fun exportQrCards(uri: Uri, selectedIds: Set<Long>?, classId: Long? = null, hallId: Long? = null) = launchTask("تم إنشاء بطاقات QR بصيغة PDF") {
        withContext(Dispatchers.IO) {
            val students = repository.all().filter {
                it.status == com.omega.attendance.data.local.entity.StudentStatus.ACTIVE &&
                    (selectedIds == null || it.id in selectedIds)
                    && (classId == null || it.classId == classId)
                    && (hallId == null || it.hallId == hallId)
            }
            qrCards.export(uri, students, state.value.classes, state.value.halls, settings.preferences.first().schoolName)
        }
    }
    fun dismissMessage() { message.value = null }
    fun showProfile(id: Long) { profileId.value = id }
    fun dismissProfile() { profileId.value = null }
    private fun launchTask(success: String?, block: suspend () -> Unit) = viewModelScope.launch {
        busy.value = true
        runCatching { block() }.onSuccess { if (success != null) message.value = success }.onFailure { message.value = it.message ?: "تعذر إكمال العملية" }
        busy.value = false
    }
    private fun prepareImport(parsed: ImportPreview, hallOverrideId: Long?, classOverrideId: Long?): ImportPreview {
        val options = state.value.classes.filter { it.academicYearId == state.value.activeYearId }
        val halls = state.value.halls
        val overrideHall = halls.firstOrNull { it.id == hallOverrideId }
        val resolved = mutableListOf<NewStudent>()
        val valid = mutableListOf<ImportRow>()
        val issues = parsed.issues.toMutableList()
        parsed.valid.forEach { row ->
            val option = if (row.className.isBlank()) options.firstOrNull { it.classId == classOverrideId } else options.firstOrNull { it.className.importKey() == row.className.importKey() }
            val hall = if (row.hall.isBlank()) overrideHall else halls.firstOrNull { it.name.importKey() == row.hall.importKey() || it.number.importKey() == row.hall.importKey() || "القاعة ${it.number}".importKey() == row.hall.importKey() }
            when {
                row.className.isBlank() && classOverrideId == null -> valid += row
                option == null -> issues += ImportIssue(row.rowNumber, "الصف غير موجود في إعدادات التطبيق")
                row.hall.isNotBlank() && hall == null -> issues += ImportIssue(row.rowNumber, "القاعة غير موجودة في إعدادات التطبيق")
                else -> {
                    valid += row
                    if (hall != null) resolved += NewStudent(row.studentNumber, row.fullName, option.gradeId, option.classId, option.sectionId, hall.id, option.academicYearId, row.parentPhone)
                }
            }
        }
        pendingImportRows = resolved
        return ImportPreview(parsed.total, valid, issues.sortedBy { it.row })
    }
    private fun String.importKey(): String = replace(Regex("\\s+"), " ").trim().replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
}
