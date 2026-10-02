package com.omega.attendance.presentation.backup

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import com.omega.attendance.presentation.components.ResponsivePair
import java.time.LocalDate
import kotlin.system.exitProcess

private enum class BackupAction { CREATE, RESTORE }

@Composable
fun BackupSection(vm: BackupViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState(); val context = LocalContext.current
    var action by remember { mutableStateOf<BackupAction?>(null) }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var pendingCreatePassword by remember { mutableStateOf<String?>(null) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val chosen = pendingCreatePassword
        if (uri != null && chosen != null) vm.create(uri, chosen)
        pendingCreatePassword = null
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingRestoreUri = uri
            password = ""
            confirmPassword = ""
            action = BackupAction.RESTORE
        }
    }
    ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("النسخ الاحتياطي والاستعادة", style = MaterialTheme.typography.titleLarge)
        ResponsivePair(
            first = { modifier -> Button({ password = ""; confirmPassword = ""; action = BackupAction.CREATE }, modifier) { Text("نسخ احتياطي مشفّر") } },
            second = { modifier -> OutlinedButton({ restore.launch(arrayOf("*/*")) }, modifier) { Text("استعادة") } }
        )
        Text("اختر كلمة مرور قوية واحتفظ بها؛ لا يمكن فتح النسخة المشفّرة من دونها.", style = MaterialTheme.typography.bodySmall)
        state.info?.let { Text("${it.createdAt}\nالإصدار ${it.appVersion} — قاعدة البيانات ${it.databaseBytes / 1024} KB") }
    } }
    action?.let { mode ->
        AlertDialog(
            onDismissRequest = { action = null; password = ""; confirmPassword = ""; pendingRestoreUri = null },
            title = { Text(if (mode == BackupAction.CREATE) "كلمة مرور النسخة الاحتياطية" else "فتح النسخة الاحتياطية") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (mode == BackupAction.CREATE) "أدخل كلمة مرور لا تقل عن 12 حرفًا. ستحتاج إليها عند الاستعادة." else "أدخل كلمة مرور النسخة. للنسخ القديمة غير المشفّرة اختر كلمة مرور جديدة لحماية نسخة الأمان.")
                    OutlinedTextField(password, { password = it }, label = { Text("كلمة المرور") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                    if (mode == BackupAction.CREATE) OutlinedTextField(confirmPassword, { confirmPassword = it }, label = { Text("تأكيد كلمة المرور") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                }
            },
            confirmButton = {
                Button({
                    if (mode == BackupAction.CREATE) {
                        pendingCreatePassword = password
                        create.launch("SchoolAttendance_Backup_" + LocalDate.now() + ".omega")
                    } else {
                        pendingRestoreUri?.let { vm.inspect(it, password) }
                        pendingRestoreUri = null
                    }
                    password = ""
                    confirmPassword = ""
                    action = null
                }, enabled = password.length >= 12 && (mode != BackupAction.CREATE || password == confirmPassword)) { Text("متابعة") }
            },
            dismissButton = { TextButton({ action = null; password = ""; confirmPassword = ""; pendingRestoreUri = null }) { Text("إلغاء") } }
        )
    }
    state.pendingRestore?.let { AlertDialog(onDismissRequest = vm::dismiss, title = { Text("تأكيد الاستعادة") }, text = { Text((if (state.info?.encrypted == false) "هذه نسخة قديمة غير مشفّرة. " else "") + "سيتم إنشاء نسخة أمان مشفّرة ثم استبدال البيانات الحالية بالنسخة المحددة.") }, confirmButton = { Button(vm::confirmRestore) { Text("استعادة") } }, dismissButton = { TextButton(vm::dismiss) { Text("إلغاء") } }) }
    state.message?.let { AlertDialog(onDismissRequest = vm::dismiss, text = { Text(it) }, confirmButton = { TextButton({ if (state.restored) { val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)!!.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(intent); (context as? Activity)?.finishAffinity(); exitProcess(0) } else vm.dismiss() }) { Text("حسنًا") } }) }
    if (state.busy) CircularProgressIndicator()
}
