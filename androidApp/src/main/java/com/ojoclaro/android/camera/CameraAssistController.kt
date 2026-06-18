package com.ojoclaro.android.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.ojoclaro.android.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/**
 * V1.13 — cámara propia de Estela, SIN preview y dirigida 100% por voz.
 *
 * Diseño (mismo patrón que el capturer de Outdoor):
 *  - LifecycleOwner propio (LifecycleRegistry): vive dentro del proceso del
 *    GlobalAssistantService, que para esto promociona su foreground a
 *    microphone|camera (exención de accesibilidad ya probada en Outdoor);
 *  - ImageAnalysis (KEEP_ONLY_LATEST) + TextRecognitionAnalyzer existente
 *    (ML Kit local, throttle de callback, ImageProxy SIEMPRE cerrado);
 *  - ImageCapture bound junto al análisis: la descripción de escena toma
 *    UNA foto en memoria bajo demanda, jamás streaming;
 *  - tono audible al abrir y al capturar: nunca cámara silenciosa;
 *  - nada se persiste: los frames viven en memoria y se descartan.
 */
class CameraAssistController(
    private val context: Context
) : LifecycleOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private var provider: ProcessCameraProvider? = null
    private var analyzer: TextRecognitionAnalyzer? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var imageCapture: ImageCapture? = null
    private var paused: Boolean = false

    var onOcrText: ((String) -> Unit)? = null

    init {
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    val isRunning: Boolean
        get() = provider != null

    /** Abre la cámara y arranca el análisis OCR local. */
    suspend fun start(timeoutMillis: Long = START_TIMEOUT_MILLIS): StartResult {
        if (!hasCameraPermission()) return StartResult.NO_PERMISSION
        if (provider != null) return StartResult.ALREADY_RUNNING

        // Aviso audible: la cámara nunca se abre en silencio.
        runCatching {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
                .startTone(ToneGenerator.TONE_PROP_BEEP, 180)
        }

        val resolved = withTimeoutOrNull(timeoutMillis) {
            suspendCancellableCoroutine<ProcessCameraProvider?> { continuation ->
                val future = ProcessCameraProvider.getInstance(context)
                future.addListener(
                    { continuation.resume(runCatching { future.get() }.getOrNull()) },
                    ContextCompat.getMainExecutor(context)
                )
            }
        } ?: run {
            log("cameraStart ok=false reason=provider_timeout")
            return StartResult.UNAVAILABLE
        }
        if (resolved == null) {
            log("cameraStart ok=false reason=provider_null")
            return StartResult.UNAVAILABLE
        }

        val resolutionSelector = ResolutionSelector.Builder()
            .setResolutionStrategy(
                ResolutionStrategy(
                    Size(1280, 960),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                )
            )
            .build()

        val newAnalyzer = TextRecognitionAnalyzer(
            onTextDetected = { text -> onOcrText?.invoke(text) },
            minCallbackIntervalMillis = OCR_CALLBACK_INTERVAL_MILLIS
        )
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(resolutionSelector)
            .build()
            .also { it.setAnalyzer(analysisExecutor, newAnalyzer) }
        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setResolutionSelector(resolutionSelector)
            .setJpegQuality(80)
            .build()

        val bound = suspendCancellableCoroutine<Boolean> { continuation ->
            ContextCompat.getMainExecutor(context).execute {
                val ok = runCatching {
                    lifecycleRegistry.currentState = Lifecycle.State.RESUMED
                    resolved.unbindAll()
                    resolved.bindToLifecycle(
                        this,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        analysis,
                        capture
                    )
                }.isSuccess
                continuation.resume(ok)
            }
        }
        if (!bound) {
            runCatching { newAnalyzer.close() }
            log("cameraStart ok=false reason=bind_failed")
            return StartResult.UNAVAILABLE
        }

        provider = resolved
        analyzer = newAnalyzer
        imageAnalysis = analysis
        imageCapture = capture
        paused = false
        log("cameraStart ok=true analysisIntervalMs=$OCR_CALLBACK_INTERVAL_MILLIS")
        return StartResult.STARTED
    }

    /** Pausa el análisis sin cerrar la cámara (estado de pausa accesible). */
    fun pauseAnalysis() {
        if (paused) return
        runCatching { imageAnalysis?.clearAnalyzer() }
        paused = true
        log("cameraPause ok=true")
    }

    fun resumeAnalysis() {
        if (!paused) return
        val currentAnalyzer = analyzer ?: return
        runCatching { imageAnalysis?.setAnalyzer(analysisExecutor, currentAnalyzer) }
        paused = false
        log("cameraResume ok=true")
    }

    val isPaused: Boolean get() = paused

    /**
     * UNA foto JPEG en memoria para describir la escena (con tono previo).
     * Exige cámara abierta: el use case ya está bound junto al análisis.
     */
    suspend fun captureSceneJpeg(timeoutMillis: Long): ByteArray? {
        val capture = imageCapture ?: return null
        runCatching {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
                .startTone(ToneGenerator.TONE_PROP_BEEP2, 220)
        }
        return withTimeoutOrNull(timeoutMillis) {
            suspendCancellableCoroutine<ByteArray?> { continuation ->
                capture.takePicture(
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val bytes = runCatching {
                                val buffer = image.planes[0].buffer
                                ByteArray(buffer.remaining()).also { buffer.get(it) }
                            }.getOrNull()
                            image.close()
                            continuation.resume(bytes)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            continuation.resume(null)
                        }
                    }
                )
            }
        }
    }

    /** Cierra cámara y análisis. Reabrible con [start]. */
    fun close() {
        val hadProvider = provider != null
        runCatching { imageAnalysis?.clearAnalyzer() }
        runCatching { analyzer?.close() }
        val current = provider
        provider = null
        analyzer = null
        imageAnalysis = null
        imageCapture = null
        paused = false
        ContextCompat.getMainExecutor(context).execute {
            runCatching { current?.unbindAll() }
            runCatching { lifecycleRegistry.currentState = Lifecycle.State.CREATED }
        }
        if (hadProvider) log("cameraClose ok=true")
    }

    /** Liberación final (onDestroy del servicio dueño). */
    fun release() {
        close()
        runCatching { analysisExecutor.shutdown() }
    }

    enum class StartResult { STARTED, ALREADY_RUNNING, NO_PERMISSION, UNAVAILABLE }

    private fun log(message: String) {
        if (BuildConfig.DEBUG) Log.i(TAG, message)
    }

    private companion object {
        const val TAG = "EstelaCameraAssist"
        const val START_TIMEOUT_MILLIS = 6_000L
        const val OCR_CALLBACK_INTERVAL_MILLIS = 1_000L
    }
}
