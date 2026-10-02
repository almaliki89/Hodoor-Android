package com.omega.attendance.presentation.security

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.*
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.omega.attendance.presentation.navigation.OmegaApp
import com.omega.attendance.R

@Composable
fun SecurityGate(vm: SecurityViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState(); val error by vm.error.collectAsState(); val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) { val observer = LifecycleEventObserver { _, event -> when (event) { Lifecycle.Event.ON_STOP -> vm.background(); Lifecycle.Event.ON_START -> vm.foreground(); else -> Unit } }; owner.lifecycle.addObserver(observer); onDispose { owner.lifecycle.removeObserver(observer) } }
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        when (val value = state) {
            AuthState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            AuthState.Setup -> PinPanel("إعداد رمز الإدارة", "حفظ وبدء الاستخدام", error, true, vm::setup, {})
            AuthState.Locked -> PinPanel("التطبيق مقفل", "دخول الإدارة", error, false, { pin, _ -> vm.login(pin) }, vm::operator)
            is AuthState.Unlocked -> OmegaApp(value.role)
        }
    }
}

@Composable private fun PinPanel(title: String, action: String, error: String?, confirmation: Boolean, submit: (String, String) -> Unit, operator: () -> Unit) {
    var pin by remember { mutableStateOf("") }; var confirm by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(R.drawable.omega_logo),
            contentDescription = "شعار حضور",
            modifier = Modifier.size(88.dp),
            contentScale = ContentScale.Fit
        )
        Text(title, style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(12) }, label = { Text("رمز PIN") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.padding(top = 16.dp).widthIn(max = 420.dp).fillMaxWidth())
        if (confirmation) OutlinedTextField(confirm, { confirm = it.filter(Char::isDigit).take(12) }, label = { Text("تأكيد الرمز") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button({ submit(pin, confirm) }, enabled = pin.length >= 4, modifier = Modifier.padding(top = 12.dp)) { Text(action) }
        if (!confirmation) TextButton(operator) { Text("الدخول كمشغّل") }
    }
}
