package com.omega.attendance.presentation.scanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.omega.attendance.scanner.CameraQrAnalyzer
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

@Composable
fun ScannerScreen(vm: ScannerViewModel = hiltViewModel()) {
    val context = LocalContext.current; val state by vm.state.collectAsState()
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var permission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permission = it }
    DisposableEffect(Unit) { val window = (context as ComponentActivity).window; window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) } }
    LaunchedEffect(Unit) { while (true) { now = LocalDateTime.now(); kotlinx.coroutines.delay(1_000) } }
    LaunchedEffect(state.eventId) { if (state.eventId > 0) feedback(context, state.feedback, state.sound, state.vibration) }
    if (!permission) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Rounded.NoPhotography, null, Modifier.size(72.dp)); Text("يلزم السماح باستخدام الكاميرا لمسح البطاقات", style = MaterialTheme.typography.titleLarge); Button({ launcher.launch(Manifest.permission.CAMERA) }, Modifier.padding(16.dp)) { Text("السماح بالكاميرا") }
        }
    } else BoxWithConstraints(Modifier.fillMaxSize()) {
        val compactLandscape = maxHeight < 420.dp && maxWidth > maxHeight
        val frameSize = if (compactLandscape) {
            minOf(maxHeight - 100.dp, maxWidth * 0.35f, 180.dp).coerceAtLeast(80.dp)
        } else {
            minOf(maxWidth - 40.dp, maxHeight - 220.dp, 320.dp).coerceAtLeast(96.dp)
        }
        CameraPreview(vm::scan)
        if (compactLandscape) {
            Row(
                Modifier.align(Alignment.Center).fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScanFrame(frameSize)
                FeedbackCard(state.feedback, Modifier.weight(1f), compact = true)
            }
            Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Color.Black.copy(alpha = .6f)).padding(8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(now.format(DateTimeFormatter.ofPattern("HH:mm:ss")), color = Color.White, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(16.dp))
                Text(now.format(DateTimeFormatter.ofPattern("yyyy/MM/dd")), color = Color.White)
            }
        } else {
            ScanFrame(frameSize, Modifier.align(Alignment.Center).offset(y = (-20).dp))
            Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Color.Black.copy(alpha = .6f)).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(now.format(DateTimeFormatter.ofPattern("HH:mm:ss")), color = Color.White, style = MaterialTheme.typography.headlineMedium); Text(now.format(DateTimeFormatter.ofPattern("yyyy/MM/dd")), color = Color.White) }
            FeedbackCard(state.feedback, Modifier.align(Alignment.BottomCenter).padding(20.dp).fillMaxWidth(), compact = maxHeight < 500.dp)
        }
    }
}

@Composable
private fun ScanFrame(size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).background(Color.Transparent).padding(4.dp)) {
        Surface(Modifier.fillMaxSize(), shape = RoundedCornerShape(24.dp), color = Color.Transparent, border = androidx.compose.foundation.BorderStroke(3.dp, Color.White)) {}
    }
}

@Composable private fun CameraPreview(onQr: (String) -> Unit) {
    val context = LocalContext.current; val owner = context as ComponentActivity
    val providerFuture = remember { ProcessCameraProvider.getInstance(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember { BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()) }
    DisposableEffect(Unit) { onDispose { if (providerFuture.isDone) runCatching { providerFuture.get().unbindAll() }; scanner.close(); executor.shutdown() } }
    AndroidView(factory = { ctx ->
        val view = PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
        providerFuture.addListener({
            val provider = providerFuture.get(); val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
            val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build().also { it.setAnalyzer(executor, CameraQrAnalyzer(scanner, onQr)) }
            runCatching { provider.unbindAll(); provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis) }
        }, ContextCompat.getMainExecutor(ctx)); view
    }, modifier = Modifier.fillMaxSize())
}

@Composable private fun FeedbackCard(value: ScanFeedback, modifier: Modifier, compact: Boolean = false) {
    val color = when (value) { is ScanFeedback.Success -> if (value.late) Color(0xFF9A6700) else Color(0xFF087F5B); ScanFeedback.Duplicate -> Color(0xFFB36B00); is ScanFeedback.Unknown, is ScanFeedback.Error -> MaterialTheme.colorScheme.error; else -> Color(0xFF263238) }
    Surface(modifier, color = color.copy(alpha = .94f), contentColor = Color.White, shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(if (compact) 10.dp else 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        when (value) {
            ScanFeedback.Ready -> { Icon(Icons.Rounded.QrCodeScanner, null, Modifier.size(if (compact) 32.dp else 40.dp)); Text("جاهز للمسح") }
            ScanFeedback.Processing -> { CircularProgressIndicator(color = Color.White); Spacer(Modifier.height(8.dp)); Text("جارٍ التحقق...") }
            is ScanFeedback.Success -> {
                Icon(Icons.Rounded.CheckCircle, "تم التسجيل", Modifier.size(if (compact) 44.dp else 76.dp))
                Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
                Text(if (value.late) "تم تسجيل التأخر" else "تم تسجيل الحضور", style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall)
            }
            ScanFeedback.Duplicate -> {
                Icon(Icons.Rounded.Info, "مسجل مسبقًا", Modifier.size(if (compact) 40.dp else 64.dp))
                Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
                Text("تم تسجيل الحضور مسبقًا", style = MaterialTheme.typography.titleLarge)
            }
            ScanFeedback.Unknown -> { Icon(Icons.Rounded.Error, null, Modifier.size(56.dp)); Text("بطاقة غير معروفة"); Text("لم يتم العثور على طالب أو موظف مرتبط بهذا الرمز") }
            is ScanFeedback.Error -> Text(value.message)
        }
    } }
}
private fun feedback(context: Context, value: ScanFeedback, sound: Boolean, vibration: Boolean) {
    val success = value is ScanFeedback.Success
    if (sound) ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70).apply { startTone(if (success) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_NACK, 120); release() }
    if (vibration) { val vibrator = context.getSystemService(Vibrator::class.java); if (Build.VERSION.SDK_INT >= 26) vibrator?.vibrate(VibrationEffect.createOneShot(if (success) 70 else 150, VibrationEffect.DEFAULT_AMPLITUDE)) else @Suppress("DEPRECATION") vibrator?.vibrate(70) }
}
