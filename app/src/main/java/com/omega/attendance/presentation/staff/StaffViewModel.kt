package com.omega.attendance.presentation.staff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omega.attendance.data.local.entity.StaffEntity
import com.omega.attendance.data.local.entity.StaffAttendanceEntity
import com.omega.attendance.data.repository.StaffRepository
import com.omega.attendance.data.importer.RosterDocumentImporter
import com.omega.attendance.data.importer.StaffImportRow
import com.omega.attendance.data.importer.isStaffQrReady
import com.omega.attendance.data.settings.SettingsRepository
import com.omega.attendance.export.pdf.QrCardsPdfExporter
import android.net.Uri
import com.omega.attendance.domain.usecase.RecordManualStaffAttendanceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.time.LocalDate

data class StaffImportPreview(val rows: List<StaffImportRow>, val nonStandardTitles: Set<String>)
data class StaffUiState(val query: String = "", val members: List<StaffEntity> = emptyList(), val today: List<StaffAttendanceEntity> = emptyList(), val busy: Boolean = false, val message: String? = null, val importPreview: StaffImportPreview? = null)

@OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class StaffViewModel @Inject constructor(private val repository: StaffRepository, private val manual: RecordManualStaffAttendanceUseCase, private val importer: RosterDocumentImporter, private val settings: SettingsRepository, private val qrCards: QrCardsPdfExporter) : ViewModel() {
    private val query = MutableStateFlow("")
    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val importPreview = MutableStateFlow<StaffImportPreview?>(null)
    private val members = query.debounce(250).flatMapLatest(repository::search)
    private val notices = combine(message, importPreview) { text, preview -> text to preview }
    val state = combine(query, members, repository.attendanceForDate(LocalDate.now().toEpochDay()), busy, notices) { q, rows, today, b, notice -> StaffUiState(q, rows, today, b, notice.first, notice.second) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StaffUiState())

    fun setQuery(value: String) { query.value = value }
    fun add(name: String, jobTitle: String, phone: String, notes: String, specialty: String) = launchTask("تمت إضافة الموظف") { repository.add(name, jobTitle, phone, notes, specialty) }
    fun edit(id: Long, name: String, jobTitle: String, phone: String, notes: String, specialty: String) = launchTask("تم تحديث بيانات الموظف") { repository.edit(id, name, jobTitle, phone, notes, specialty) }
    fun archive(id: Long, archived: Boolean) = launchTask(if (archived) "تمت أرشفة الموظف" else "تمت إعادة تفعيل الموظف") { repository.archive(id, archived) }
    fun delete(id: Long) = launchTask("تم حذف الموظف") { repository.delete(id) }
    fun deleteAll() = launchTask("تم حذف جميع أفراد الكادر وسجلات حضورهم نهائيًا") { repository.deleteAll() }
    fun addManual(id: Long) = launchTask("تم تسجيل حضور الموظف يدويًا") { if (!manual(id)) error("تم تسجيل حضور الموظف مسبقًا") }
    fun exportQrCards(uri: Uri) = launchTask("تم إنشاء بطاقات QR للكادر بصيغة PDF") {
        val members = repository.allActive()
        require(members.isNotEmpty() && members.all { isStaffQrReady(it.jobTitle) }) { "حدد المسمى الوظيفي لجميع أفراد الكادر قبل طباعة البطاقات" }
        qrCards.exportStaff(uri, members, settings.preferences.first().schoolName)
    }
    fun previewImport(uri: Uri) = viewModelScope.launch {
        busy.value = true
        runCatching { importer.readStaff(uri) }
            .onSuccess { rows ->
                importPreview.value = StaffImportPreview(rows, emptySet())
            }
            .onFailure { message.value = it.message ?: "تعذر قراءة ملف الاستيراد" }
        busy.value = false
    }
    fun confirmImport() {
        val rows = importPreview.value?.rows ?: return
        viewModelScope.launch {
            busy.value = true
            runCatching { repository.import(rows) }
                .onSuccess { result ->
                    importPreview.value = null
                    message.value = "اكتمل استيراد الأسماء: تمت إضافة ${result.added}. اختر المسمى الوظيفي أمام كل اسم، ثم اطبع QR."
                }
                .onFailure { message.value = it.message ?: "تعذر استيراد الكادر" }
            busy.value = false
        }
    }
    fun cancelImport() { importPreview.value = null }
    fun dismissMessage() { message.value = null }
    private fun launchTask(success: String, block: suspend () -> Unit) = viewModelScope.launch {
        busy.value = true
        runCatching { block() }.onSuccess { message.value = success }.onFailure { message.value = it.message ?: "تعذر إكمال العملية" }
        busy.value = false
    }
}
