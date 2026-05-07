package com.ojoclaro.android.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.ojoclaro.android.camera.StableTextDetector
import com.ojoclaro.android.camera.StableTextResult
import com.ojoclaro.android.camera.TextRecognitionAnalyzer
import com.ojoclaro.android.speech.SpeechController
import com.ojoclaro.android.ui.home.HomeViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

@Composable
fun TextScanScreen(
    viewModel: HomeViewModel,
    speechController: SpeechController,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    val cameraProviderRef = remember { AtomicReference<ProcessCameraProvider?>(null) }
    val analyzerRef = remember { AtomicReference<TextRecognitionAnalyzer?>(null) }
    val detector = remember { StableTextDetector() }

    val hasCameraPermission = remember {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission) {
            viewModel.onCameraPermissionDenied()
            onClose()
        }
    }

    LaunchedEffect(Unit) {
        detector.reset(System.currentTimeMillis())
        while (isActive) {
            delay(1_000L)
            when (detector.onNoText(System.currentTimeMillis(), speechController.isSpeaking)) {
                StableTextResult.NoTextFound -> viewModel.onTextScanNoTextFound()
                is StableTextResult.TextReady,
                null -> Unit
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            analyzerRef.getAndSet(null)?.close()
            cameraProviderRef.getAndSet(null)?.let { provider ->
                ContextCompat.getMainExecutor(context).execute {
                    provider.unbindAll()
                }
            }
            cameraExecutor.shutdown()
        }
    }

    if (!hasCameraPermission) {
        PermissionMissingScreen(onClose = onClose)
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = "Camara activa para leer texto."
                },
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener(
                    {
                        val cameraProvider = cameraProviderFuture.get()
                        cameraProviderRef.set(cameraProvider)

                        val preview = Preview.Builder()
                            .build()
                            .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                        val analyzer = TextRecognitionAnalyzer(onTextDetected = { detected ->
                            val result = detector.onTextDetected(
                                text = detected,
                                nowMillis = System.currentTimeMillis(),
                                isSpeechBusy = speechController.isSpeaking
                            )

                            if (result is StableTextResult.TextReady) {
                                scope.launch {
                                    viewModel.onTextScanResult(result.text)
                                }
                            }
                        })
                        analyzerRef.set(analyzer)

                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { it.setAnalyzer(cameraExecutor, analyzer) }

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                analysis
                            )
                        } catch (_: Exception) {
                            analyzer.close()
                            analyzerRef.compareAndSet(analyzer, null)
                            viewModel.onCameraError()
                            onClose()
                        }
                    },
                    ContextCompat.getMainExecutor(ctx)
                )

                previewView
            }
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.86f))
                .navigationBarsPadding()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Leyendo texto",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    speechController.stop()
                    viewModel.onStopSpeechRequested()
                    onClose()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .semantics {
                        contentDescription = "Callar la voz y volver a la pantalla principal."
                    }
            ) {
                Text(
                    text = "Callar y volver",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun PermissionMissingScreen(onClose: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = onClose,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color.Black
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .semantics {
                    contentDescription = "Volver a la pantalla principal."
                }
        ) {
            Text(
                text = "Volver",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
