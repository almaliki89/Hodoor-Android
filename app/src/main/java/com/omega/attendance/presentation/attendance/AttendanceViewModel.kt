package com.omega.attendance.presentation.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omega.attendance.data.local.entity.*
import com.omega.attendance.data.repository.*
import com.omega.attendance.domain.usecase.RecordManualAttendanceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class AttendanceUiState(val records: List<AttendanceEntity> = emptyList(), val students: List<StudentEntity> = emptyList(), val message: String? = null)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class AttendanceViewModel @Inject constructor(
    private val attendance: AttendanceRepository,
    students: StudentRepository,
    private val manual: RecordManualAttendanceUseCase
) : ViewModel() {
    private val date = MutableStateFlow(LocalDate.now().toEpochDay()); private val message = MutableStateFlow<String?>(null)
    val state = combine(date.flatMapLatest(attendance::forDate), students.search(""), message) { rows, people, msg -> AttendanceUiState(rows, people.filter { it.status == StudentStatus.ACTIVE }, msg) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AttendanceUiState())
    fun addManual(studentId: Long, status: AttendanceStatus) = viewModelScope.launch { runCatching { manual(studentId, status) }.onSuccess { message.value = if (it) "تم التسجيل اليدوي" else "الطالب مسجل مسبقًا" }.onFailure { message.value = it.message } }
    fun changeStatus(id: Long, status: AttendanceStatus) = viewModelScope.launch { runCatching { attendance.changeStatus(id, status) }.onSuccess { message.value = "تم تعديل الحالة" }.onFailure { message.value = it.message } }
    fun delete(id: Long) = viewModelScope.launch {
        runCatching { attendance.delete(id) }
            .onSuccess { message.value = "تم حذف سجل الحضور" }
            .onFailure { message.value = it.message ?: "تعذر حذف السجل" }
    }
    fun dismissMessage() { message.value = null }
}
