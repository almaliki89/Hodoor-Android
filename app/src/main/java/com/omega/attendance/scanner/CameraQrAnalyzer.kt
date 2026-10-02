package com.omega.attendance.scanner

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean

class CameraQrAnalyzer(private val scanner: BarcodeScanner, private val onQr: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val processing = AtomicBoolean(false)
    private var latchedToken: String? = null
    private var emptyFrames = 0
    @androidx.camera.core.ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null || !processing.compareAndSet(false, true)) { imageProxy.close(); return }
        scanner.process(InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees))
            .addOnSuccessListener { codes ->
                val token = codes.firstNotNullOfOrNull { it.rawValue }
                if (token == null) { emptyFrames++; if (emptyFrames >= 3) latchedToken = null }
                else { emptyFrames = 0; if (token != latchedToken) { latchedToken = token; onQr(token) } }
            }
            .addOnCompleteListener { processing.set(false); imageProxy.close() }
    }
}
