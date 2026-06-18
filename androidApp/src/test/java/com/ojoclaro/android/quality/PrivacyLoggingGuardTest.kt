package com.ojoclaro.android.quality

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Barrera anti-regresión de privacidad de logs. Ningún log de PRODUCCIÓN puede
 * interpolar texto crudo sensible (STT/pantalla/OCR/WhatsApp/contactos/teléfono):
 * solo metadatos (len/count/categorías) o helpers de [com.ojoclaro.android.logging.SafeLog].
 *
 * No busca perfección: corta los patrones obvios para evitar regresiones. Si una
 * línea legítima necesita una de estas variables, debe acotarla en la MISMA línea
 * con .length/.size/len=/count/redact/shortHash/presencia.
 */
class PrivacyLoggingGuardTest {

    private val logCall = Regex(
        "(Log\\.[diwev]\\(|println\\(|logBackground\\(|logChatNav\\(|logAgentCore\\(|Timber\\.[diwev]\\()"
    )

    // Variables de alto riesgo: texto crudo del usuario / pantalla / OCR / WhatsApp.
    private val forbiddenRaw = listOf(
        "userText", "normalizedText", "rawText", "rawRecognizedText", "recognizedText",
        "transcript", "ocrText", "visibleText", "screenText", "messageText",
        "messageBody", "contactName", "chatName", "phoneNumber"
    )

    private val safeMarkers = listOf(
        ".length", "?.length", ".size", "?.size", "len=", "Len=", "count", "Count",
        "redact", "shortHash", "Present", "!= null", "== null", "isBlank", "isEmpty"
    )

    @Test
    fun productionLogsDoNotInterpolateRawSensitiveText() {
        val root = locateRepoRoot()
        val offenders = mutableListOf<String>()
        File(root, "androidApp/src/main").walkTopDown()
            .filter { it.isFile && it.extension == "kt" && !it.invariantSeparatorsPath.contains("/build/") }
            .forEach { file ->
                file.readLines().forEachIndexed { index, line ->
                    if (!logCall.containsMatchIn(line)) return@forEachIndexed
                    val hasRaw = forbiddenRaw.any { v ->
                        line.contains("\$$v") || line.contains("\${$v")
                    }
                    if (!hasRaw) return@forEachIndexed
                    val bounded = safeMarkers.any { line.contains(it) }
                    if (!bounded) {
                        offenders += "${file.relativeTo(root).invariantSeparatorsPath}:${index + 1}"
                    }
                }
            }
        assertTrue(
            offenders.isEmpty(),
            "Raw sensitive text interpolated in production logs (use SafeLog/metadata):\n" +
                offenders.joinToString("\n")
        )
    }

    /** El leak real del audit: STT no debe truncar y loguear texto crudo. */
    @Test
    fun sttEngineNeverLogsRawRecognizedText() {
        val root = locateRepoRoot()
        val stt = File(root, "androidApp/src/main/java/com/ojoclaro/android/voice/AndroidSpeechInputEngine.kt")
        assertTrue(stt.exists(), "STT engine source must exist")
        val text = stt.readText()
        // safeSpeechCandidateForLog debe emitir longitud, jamás .take() del texto.
        val body = text.substringAfter("fun safeSpeechCandidateForLog").substringBefore("private data class RecognitionTiming")
        assertTrue(body.contains("length"), "candidate log helper must emit length")
        assertTrue(!body.contains(".take("), "candidate log helper must NOT truncate raw recognized text")
    }

    @Test
    fun debugCommandActivityLogsOnlyCommandMetadata() {
        val root = locateRepoRoot()
        val source = File(
            root,
            "androidApp/src/debug/java/com/ojoclaro/android/debug/DebugCommandActivity.kt"
        ).readText()

        assertFalse(
            source.lines().any { it.contains("log(") && it.contains("${'$'}command") },
            "DebugCommandActivity must never pass the raw command to Logcat"
        )
        assertTrue(source.contains("SafeLog.textLen(command)"), "debug command log must include only length")
        assertTrue(source.contains("SafeLog.shortHash(command)"), "debug command log must use coarse hash")
    }

    /** Errores técnicos: ni message/localizedMessage de excepción ni stacktrace. */
    @Test
    fun productionLogsDoNotDumpExceptionMessageOrStacktrace() {
        val root = locateRepoRoot()
        val throwableMessage = Regex(
            "\\b(error|err|e|t|ex|exc|exception|throwable|cause|it)\\.(message|localizedMessage)\\b"
        )
        val offenders = mutableListOf<String>()
        File(root, "androidApp/src/main").walkTopDown()
            .filter { it.isFile && it.extension == "kt" && !it.invariantSeparatorsPath.contains("/build/") }
            .forEach { file ->
                file.readLines().forEachIndexed { index, line ->
                    if (!logCall.containsMatchIn(line)) return@forEachIndexed
                    val dumpsError = throwableMessage.containsMatchIn(line) ||
                        line.contains("localizedMessage") ||
                        line.contains("printStackTrace")
                    if (dumpsError) {
                        offenders += "${file.relativeTo(root).invariantSeparatorsPath}:${index + 1}"
                    }
                }
            }
        assertTrue(
            offenders.isEmpty(),
            "Exception message/stacktrace in production logs (use SafeLog.error, which logs only errorKind):\n" +
                offenders.joinToString("\n")
        )
    }

    private fun locateRepoRoot(): File {
        var current = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (true) {
            if (File(current, "settings.gradle.kts").exists()) return current
            current = current.parentFile ?: break
        }
        return File(".").absoluteFile
    }
}
