package com.example

import android.annotation.SuppressLint
import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@SuppressLint("UnsafeOptInUsageError")
@Composable
fun CameraScannerScreen(
    onQrCodeDetected: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    
    LaunchedEffect(Unit) {
        Log.d("CameraScanner", "Starting camera initialization")
        try {
            val cameraProvider = ProcessCameraProvider.getInstance(context).get()
            Log.d("CameraScanner", "Camera provider acquired")
            
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            val scanner = BarcodeScanning.getClient()
            
            imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
                @SuppressLint("UnsafeOptInUsageError")
                val mediaImage = imageProxy.image
                if (mediaImage != null) {
                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                    scanner.process(image)
                        .addOnSuccessListener { barcodes ->
                            for (barcode in barcodes) {
                                barcode.rawValue?.let { 
                                    onQrCodeDetected(it)
                                    return@let
                                }
                            }
                        }
                        .addOnFailureListener {
                            Log.e("CameraScanner", "Error scanning barcode", it)
                        }
                        .addOnCompleteListener {
                            imageProxy.close()
                        }
                } else {
                    imageProxy.close()
                }
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )
            Log.d("CameraScanner", "Camera bound to lifecycle with analyzer")
        } catch (e: Exception) {
            Log.e("CameraScanner", "Camera initialization failed", e)
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )
    }
}
