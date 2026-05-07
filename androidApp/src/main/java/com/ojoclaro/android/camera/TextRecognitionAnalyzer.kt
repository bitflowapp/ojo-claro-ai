package com.ojoclaro.android.camera

import android.annotation.SuppressLint
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class TextRecognitionAnalyzer(
    private val onTextDetected: (String) -> Unit,
    private val minCallbackIntervalMillis: Long = 700L
) : ImageAnalysis.Analyzer, AutoCloseable {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val processing = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)

    private var lastCallbackKey = ""
    private var lastCallbackAtMillis = 0L

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        if (closed.get()) {
            imageProxy.close()
            return
        }

        if (!processing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            finishFrame(imageProxy)
            return
        }

        val image = try {
            InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )
        } catch (_: IllegalArgumentException) {
            finishFrame(imageProxy)
            return
        }

        try {
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    if (closed.get()) return@addOnSuccessListener

                    val text = normalizeForCallback(result.text)
                    if (text.isNotBlank() && shouldEmit(text)) {
                        onTextDetected(text)
                    }
                }
                .addOnFailureListener {
                    // Error de frame: no crashear. El siguiente frame puede funcionar.
                }
                .addOnCompleteListener {
                    finishFrame(imageProxy)
                }
        } catch (_: Exception) {
            finishFrame(imageProxy)
        }
    }

    override fun close() {
        closed.set(true)
        recognizer.close()
    }

    private fun finishFrame(imageProxy: ImageProxy) {
        processing.set(false)
        imageProxy.close()
    }

    @Synchronized
    private fun shouldEmit(text: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        val key = normalizeKey(text)

        if (key == lastCallbackKey && now - lastCallbackAtMillis < minCallbackIntervalMillis) {
            return false
        }

        lastCallbackKey = key
        lastCallbackAtMillis = now
        return true
    }

    private fun normalizeForCallback(text: String): String {
        val normalized = text
            .lineSequence()
            .map { line ->
                line
                    .replace(WHITESPACE_REGEX, " ")
                    .trim()
            }
            .filter { it.isNotBlank() }
            .joinToString(separator = "\n")
            .trim()

        if (normalized.length <= MAX_CALLBACK_TEXT_CHARS) {
            return normalized
        }

        return normalized
            .take(MAX_CALLBACK_TEXT_CHARS)
            .trimEnd()
            .trimEnd('.', ',', ';', ':')
            .plus("…")
    }

    private fun normalizeKey(text: String): String {
        return text
            .lowercase(Locale.ROOT)
            .replace(WHITESPACE_REGEX, " ")
            .trim()
    }

    private companion object {
        private val WHITESPACE_REGEX = Regex("\\s+")
        private const val MAX_CALLBACK_TEXT_CHARS = 1_200
    }
}
