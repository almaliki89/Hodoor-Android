package com.omega.attendance.presentation.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omega.attendance.security.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AuthState { data object Loading : AuthState; data object Setup : AuthState; data object Locked : AuthState; data class Unlocked(val role: UserRole) : AuthState }
@HiltViewModel
class SecurityViewModel @Inject constructor(private val auth: AuthRepository) : ViewModel() {
    private val mutable = MutableStateFlow<AuthState>(AuthState.Loading); val state = mutable.asStateFlow(); private val message = MutableStateFlow<String?>(null); val error = message.asStateFlow()
    private var backgroundAt: Long? = null
    private var timeoutMinutes: Int = 5
    init { viewModelScope.launch { auth.config.collect { config -> timeoutMinutes = config.autoLockMinutes; if (mutable.value == AuthState.Loading) mutable.value = if (config.hasAdminPin) AuthState.Locked else AuthState.Setup } } }
    fun setup(pin: String, confirm: String) = viewModelScope.launch { if (pin != confirm || pin.length < 4) { message.value = "يجب أن يتطابق الرقمان وأن يتكونا من 4 أرقام على الأقل"; return@launch }; runCatching { auth.setAdminPin(pin) }.onSuccess { mutable.value = AuthState.Unlocked(UserRole.ADMIN) }.onFailure { message.value = "تعذر حفظ رمز الإدارة" } }
    fun login(pin: String) = viewModelScope.launch { if (auth.verify(pin)) mutable.value = AuthState.Unlocked(UserRole.ADMIN) else message.value = "رمز PIN غير صحيح" }
    fun operator() { mutable.value = AuthState.Unlocked(UserRole.OPERATOR) }
    fun lock() { if (mutable.value is AuthState.Unlocked) mutable.value = AuthState.Locked }
    fun background() { if (mutable.value is AuthState.Unlocked) backgroundAt = System.currentTimeMillis() }
    fun foreground() { val since = backgroundAt ?: return; backgroundAt = null; if (System.currentTimeMillis() - since >= timeoutMinutes * 60_000L) lock() }
}
