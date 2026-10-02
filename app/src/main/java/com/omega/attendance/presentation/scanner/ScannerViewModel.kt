package com.omega.attendance.presentation.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omega.attendance.data.local.entity.*
import com.omega.attendance.data.settings.SettingsRepository
import com.omega.attendance.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ScanFeedback {
    data object Ready : ScanFeedback
    data object Processing : ScanFeedback
    data class Success(val late: Boolean) : ScanFeedback
    data object Duplicate : ScanFeedback
    data object Unknown : ScanFeedback
    data class Error(val message: String) : ScanFeedback
}

data class ScannerUiState(val feedback: ScanFeedback = ScanFeedback.Ready, val eventId: Long = 0, val sound: Boolean = true, val vibration: Boolean = true)

@HiltViewModel
class ScannerViewModel @Inject constructor(private val register: RegisterAttendanceUseCase, private val settings: SettingsRepository) : ViewModel() {
    private val feedback = MutableStateFlow<ScanFeedback>(ScanFeedback.Ready); private val eventId = MutableStateFlow(0L); private var job: Job? = null
    val state = combine(feedback, eventId, settings.preferences) { f, id, p -> ScannerUiState(f, id, p.soundEnabled, p.vibrationEnabled) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScannerUiState())
    fun scan(token: String) {
        if (feedback.value == ScanFeedback.Processing) return
        job?.cancel(); job = viewModelScope.launch {
            feedback.value = ScanFeedback.Processing
            feedback.value = when (val result = register(token)) {
                is RegistrationResult.Success -> ScanFeedback.Success(result.status == AttendanceStatus.LATE)
                RegistrationResult.Duplicate -> ScanFeedback.Duplicate
                RegistrationResult.Unknown -> ScanFeedback.Unknown
                is RegistrationResult.Failure -> ScanFeedback.Error(result.message)
            }
            eventId.value++
            delay(settings.preferences.first().scannerCooldownMillis); feedback.value = ScanFeedback.Ready
        }
    }
}
