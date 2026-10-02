package com.omega.attendance.presentation.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omega.attendance.backup.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BackupUiState(val busy: Boolean = false, val info: BackupInfo? = null, val pendingRestore: Uri? = null, val message: String? = null, val restored: Boolean = false)
@HiltViewModel
class BackupViewModel @Inject constructor(private val service: BackupService) : ViewModel() {
    private val mutable = MutableStateFlow(BackupUiState()); val state = mutable.asStateFlow()
    private var restorePassword: String? = null
    fun create(uri: Uri, password: String) = task { val info = service.create(uri, password); mutable.value = BackupUiState(info = info, message = "تم إنشاء نسخة مشفّرة والتحقق منها") }
    fun inspect(uri: Uri, password: String) = task {
        val info = service.inspect(uri, password)
        restorePassword = password
        mutable.value = BackupUiState(info = info, pendingRestore = uri)
    }
    fun confirmRestore() = task {
        val info = service.restore(requireNotNull(mutable.value.pendingRestore), requireNotNull(restorePassword))
        restorePassword = null
        mutable.value = BackupUiState(info = info, message = "تمت الاستعادة. سيُعاد تشغيل التطبيق الآن.", restored = true)
    }
    fun dismiss() { restorePassword = null; mutable.value = mutable.value.copy(message = null, pendingRestore = null) }
    private fun task(block: suspend () -> Unit) = viewModelScope.launch { mutable.value = mutable.value.copy(busy = true); runCatching { block() }.onFailure { mutable.value = mutable.value.copy(busy = false, message = it.message ?: "فشلت العملية") }; if (mutable.value.busy) mutable.value = mutable.value.copy(busy = false) }
}
