package com.ojoclaro.android.voice

import android.speech.SpeechRecognizer
import com.ojoclaro.android.agent.runtime.conversation.ConversationalRepair
import com.ojoclaro.android.memory.MemoryPolicy
import com.ojoclaro.android.privacy.PrivacyGuard

enum class SpeechErrorCategory {
    NO_MATCH,
    SPEECH_TIMEOUT,
    RECOGNIZER_BUSY,
    NETWORK,
    CLIENT,
    INSUFFICIENT_PERMISSIONS,
    TOO_MANY_REQUESTS,
    SERVICE_DISCONNECTED,
    LANGUAGE_UNAVAILABLE,
    SERVICE_UNAVAILABLE,
    UNKNOWN
}

enum class VoiceSpeechEngine(
    val safeLabel: String
) {
    ON_DEVICE("on_device"),
    PLATFORM_DEFAULT("platform_default"),
    UNAVAILABLE("unavailable")
}

enum class VoiceHearingStatus(
    val publicLabel: String
) {
    IDLE("sin resultado"),
    LISTENING("escuchando"),
    NO_RESULT("sin resultado"),
    USING_PARTIAL("usando parcial"),
    ERROR_TIMEOUT("error timeout"),
    RECOGNIZER_BUSY("recognizer busy"),
    ERROR("error")
}

data class VoiceListeningDiagnostic(
    val sessionId: Long,
    val hearingStatus: VoiceHearingStatus,
    val errorCategory: SpeechErrorCategory? = null,
    val hasPartial: Boolean = false,
    val usedPartial: Boolean = false,
    val speechEngine: VoiceSpeechEngine = VoiceSpeechEngine.PLATFORM_DEFAULT
)

data class VoiceListeningSession(
    val sessionId: Long,
    val startedAt: Long,
    val lastPartialTextRedacted: String = "",
    val bestPartialCandidate: String = "",
    val finalText: String = "",
    val errorCode: Int? = null,
    val errorCategory: SpeechErrorCategory = SpeechErrorCategory.UNKNOWN,
    val retryCount: Int = 0,
    val consecutiveNoMatch: Int = 0,
    val consecutiveTimeouts: Int = 0,
    val wasSpeakingWhenStarted: Boolean = false,
    val wasRobotEnabled: Boolean = true,
    val shouldAutoRestart: Boolean = true,
    val shouldSubmitFinal: Boolean = false,
    val partialWasRedacted: Boolean = false,
    val finalWasRedacted: Boolean = false,
    val usedPartial: Boolean = false
) {
    val hasPartialCandidate: Boolean
        get() = bestPartialCandidate.isNotBlank()

    fun recordPartial(rawText: String): VoiceListeningSession {
        val cleanText = normalizeSpeechText(rawText)
        if (cleanText.isBlank()) return this

        val sensitive = isSensitiveVoiceText(cleanText)
        val candidate = if (!sensitive && isSafePartialCandidate(cleanText)) {
            chooseBetterPartialCandidate(bestPartialCandidate, cleanText)
        } else {
            bestPartialCandidate
        }

        return copy(
            lastPartialTextRedacted = if (sensitive) REDACTED_TEXT else cleanText,
            bestPartialCandidate = candidate,
            partialWasRedacted = partialWasRedacted || sensitive
        )
    }

    fun recordFinal(rawText: String): VoiceListeningSession {
        val cleanText = normalizeSpeechText(rawText)
        if (cleanText.isBlank()) {
            return copy(finalText = "", shouldSubmitFinal = false)
        }
        val sensitive = isSensitiveVoiceText(cleanText)
        return copy(
            finalText = if (sensitive) REDACTED_TEXT else cleanText,
            finalWasRedacted = sensitive,
            shouldSubmitFinal = true
        )
    }

    fun recordError(
        errorCode: Int?,
        retryCount: Int,
        shouldAutoRestart: Boolean
    ): VoiceListeningSession {
        val category = VoiceSpeechErrorPolicy.categoryFor(errorCode)
        return copy(
            errorCode = errorCode,
            errorCategory = category,
            retryCount = retryCount,
            consecutiveNoMatch = if (category == SpeechErrorCategory.NO_MATCH) {
                consecutiveNoMatch + 1
            } else {
                consecutiveNoMatch
            },
            consecutiveTimeouts = if (category == SpeechErrorCategory.SPEECH_TIMEOUT) {
                consecutiveTimeouts + 1
            } else {
                consecutiveTimeouts
            },
            shouldAutoRestart = shouldAutoRestart
        )
    }

    fun textForSubmission(rawFinalText: String): VoiceSubmissionDecision {
        val cleanFinal = normalizeSpeechText(rawFinalText)
        val safePartial = bestPartialCandidate.takeIf(::isSafePartialCandidate).orEmpty()

        if (cleanFinal.isBlank()) {
            return if (safePartial.isNotBlank()) {
                VoiceSubmissionDecision(text = safePartial, usedPartial = true)
            } else {
                VoiceSubmissionDecision()
            }
        }

        if (safePartial.isNotBlank() && shouldPreferPartial(safePartial, cleanFinal)) {
            return VoiceSubmissionDecision(text = safePartial, usedPartial = true)
        }

        return VoiceSubmissionDecision(text = cleanFinal, usedPartial = false)
    }

    fun partialForError(): VoiceSubmissionDecision =
        bestPartialCandidate
            .takeIf(::isSafePartialCandidate)
            ?.let { VoiceSubmissionDecision(text = it, usedPartial = true) }
            ?: VoiceSubmissionDecision()

    fun markUsedPartial(): VoiceListeningSession =
        copy(usedPartial = true, shouldSubmitFinal = true)

    fun diagnostic(
        hearingStatus: VoiceHearingStatus,
        speechEngine: VoiceSpeechEngine
    ): VoiceListeningDiagnostic =
        VoiceListeningDiagnostic(
            sessionId = sessionId,
            hearingStatus = hearingStatus,
            errorCategory = errorCategory.takeUnless { it == SpeechErrorCategory.UNKNOWN && errorCode == null },
            hasPartial = hasPartialCandidate || lastPartialTextRedacted.isNotBlank(),
            usedPartial = usedPartial,
            speechEngine = speechEngine
        )

    data class VoiceSubmissionDecision(
        val text: String = "",
        val usedPartial: Boolean = false
    )

    companion object {
        const val REDACTED_TEXT: String = "[contenido sensible omitido]"
    }
}

object VoiceSpeechErrorPolicy {
    // SpeechRecognizer error codes added in Android 12 (API 31) and later.
    // Declared as private numeric constants so the module keeps compiling on
    // setups whose android.jar might not stub these constants and so the unit
    // tests can reference them without depending on platform-specific symbols.
    internal const val ERROR_CODE_TOO_MANY_REQUESTS: Int = 10
    internal const val ERROR_CODE_SERVER_DISCONNECTED: Int = 11
    internal const val ERROR_CODE_LANGUAGE_NOT_SUPPORTED: Int = 12
    internal const val ERROR_CODE_LANGUAGE_UNAVAILABLE: Int = 13
    internal const val ERROR_CODE_CANNOT_CHECK_SUPPORT: Int = 14
    internal const val ERROR_CODE_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS: Int = 15

    fun categoryFor(errorCode: Int?): SpeechErrorCategory =
        when (errorCode) {
            SpeechRecognizer.ERROR_NO_MATCH -> SpeechErrorCategory.NO_MATCH
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SpeechErrorCategory.SPEECH_TIMEOUT
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> SpeechErrorCategory.RECOGNIZER_BUSY
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            SpeechRecognizer.ERROR_SERVER -> SpeechErrorCategory.NETWORK
            SpeechRecognizer.ERROR_CLIENT,
            SpeechRecognizer.ERROR_AUDIO -> SpeechErrorCategory.CLIENT
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechErrorCategory.INSUFFICIENT_PERMISSIONS
            ERROR_CODE_TOO_MANY_REQUESTS -> SpeechErrorCategory.TOO_MANY_REQUESTS
            ERROR_CODE_SERVER_DISCONNECTED -> SpeechErrorCategory.SERVICE_DISCONNECTED
            ERROR_CODE_LANGUAGE_NOT_SUPPORTED,
            ERROR_CODE_LANGUAGE_UNAVAILABLE -> SpeechErrorCategory.LANGUAGE_UNAVAILABLE
            ERROR_CODE_CANNOT_CHECK_SUPPORT,
            ERROR_CODE_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS -> SpeechErrorCategory.SERVICE_UNAVAILABLE
            else -> SpeechErrorCategory.UNKNOWN
        }

    fun humanMessageFor(category: SpeechErrorCategory): String =
        when (category) {
            SpeechErrorCategory.NO_MATCH -> ConversationalRepair.NOISE
            SpeechErrorCategory.SPEECH_TIMEOUT ->
                "No escuché bien. Probá acercarte un poco o hablar después del tono."
            SpeechErrorCategory.RECOGNIZER_BUSY ->
                "El micrófono se ocupó un momento. Probá de nuevo."
            SpeechErrorCategory.NETWORK ->
                "El servicio de voz no respondió. Sigo con comandos locales."
            SpeechErrorCategory.CLIENT ->
                "Reinicio el micrófono y vuelvo a escuchar."
            SpeechErrorCategory.INSUFFICIENT_PERMISSIONS ->
                VoiceCommandController.MICROPHONE_PERMISSION_MESSAGE
            SpeechErrorCategory.TOO_MANY_REQUESTS ->
                "El reconocimiento de voz está ocupado. Esperá un momento y probá otra vez."
            SpeechErrorCategory.SERVICE_DISCONNECTED ->
                "El servicio de voz no respondió. Probá otra vez."
            SpeechErrorCategory.LANGUAGE_UNAVAILABLE ->
                "El reconocimiento de voz en español no está disponible en este dispositivo."
            SpeechErrorCategory.SERVICE_UNAVAILABLE ->
                "El servicio de voz no está disponible. Probá otra vez."
            SpeechErrorCategory.UNKNOWN ->
                "No pude escuchar bien. Probá otra vez."
        }

    fun hearingStatusFor(category: SpeechErrorCategory): VoiceHearingStatus =
        when (category) {
            SpeechErrorCategory.NO_MATCH -> VoiceHearingStatus.NO_RESULT
            SpeechErrorCategory.SPEECH_TIMEOUT -> VoiceHearingStatus.ERROR_TIMEOUT
            SpeechErrorCategory.RECOGNIZER_BUSY,
            SpeechErrorCategory.TOO_MANY_REQUESTS -> VoiceHearingStatus.RECOGNIZER_BUSY
            SpeechErrorCategory.NETWORK,
            SpeechErrorCategory.CLIENT,
            SpeechErrorCategory.INSUFFICIENT_PERMISSIONS,
            SpeechErrorCategory.SERVICE_DISCONNECTED,
            SpeechErrorCategory.LANGUAGE_UNAVAILABLE,
            SpeechErrorCategory.SERVICE_UNAVAILABLE,
            SpeechErrorCategory.UNKNOWN -> VoiceHearingStatus.ERROR
        }

    fun shouldResetRecognizer(category: SpeechErrorCategory): Boolean =
        category == SpeechErrorCategory.RECOGNIZER_BUSY ||
            category == SpeechErrorCategory.CLIENT ||
            category == SpeechErrorCategory.SERVICE_DISCONNECTED

    fun shouldAutoRestart(category: SpeechErrorCategory): Boolean =
        category != SpeechErrorCategory.INSUFFICIENT_PERMISSIONS
}

internal fun isSafePartialCandidate(text: String): Boolean {
    val cleanText = normalizeSpeechText(text)
    if (cleanText.length !in MIN_PARTIAL_CHARS..MAX_SESSION_TEXT_CHARS) return false
    if (isSensitiveVoiceText(cleanText)) return false
    if (VoiceCommandCorrection.isKnownRecognizerNoise(cleanText)) return false
    if (dangerousPartialCommandRegex.containsMatchIn(MemoryPolicy.normalize(cleanText))) return false

    val correction = VoiceCommandCorrection.correct(cleanText)
    if (correction.correctionType == VoiceCommandCorrectionType.REJECTED_SENSITIVE) return false
    return correction.targetIntent != VoiceCommandTargetIntent.NONE &&
        correction.targetIntent.isLowRiskExecutable
}

private fun normalizeSpeechText(rawText: String): String =
    rawText
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(MAX_SESSION_TEXT_CHARS)

private fun isSensitiveVoiceText(text: String): Boolean =
    MemoryPolicy.containsProhibitedContent(text) ||
        PrivacyGuard.containsSensitiveFinancialData(text) ||
        voiceSensitiveRegex.containsMatchIn(MemoryPolicy.normalize(text))

private fun chooseBetterPartialCandidate(current: String, next: String): String =
    when {
        current.isBlank() -> next
        partialScore(next) > partialScore(current) -> next
        partialScore(next) == partialScore(current) && next.length > current.length -> next
        else -> current
    }

private fun partialScore(text: String): Int {
    val correction = VoiceCommandCorrection.correct(text)
    return when {
        isClearGlobalCommand(text) -> 100
        correction.shouldAutoExecute -> 90
        correction.canBeConfirmedSafely -> 80
        else -> 0
    }
}

private fun shouldPreferPartial(partialText: String, finalText: String): Boolean {
    if (!isSafePartialCandidate(partialText)) return false
    if (isClearGlobalCommand(finalText)) return false
    if (finalText.isBlank() || VoiceCommandCorrection.isKnownRecognizerNoise(finalText)) return true

    val partialCorrection = VoiceCommandCorrection.correct(partialText)
    val finalCorrection = VoiceCommandCorrection.correct(finalText)
    if (partialCorrection.targetIntent == VoiceCommandTargetIntent.NONE) return false
    if (partialCorrection.targetIntent != finalCorrection.targetIntent) return false

    val partialNormalized = VoicePhraseNormalizer.normalizeForParser(partialText).lowercase()
    val canonicalNormalized = VoicePhraseNormalizer
        .normalizeForParser(partialCorrection.correctedText)
        .lowercase()
    return partialNormalized == canonicalNormalized &&
        finalCorrection.originalText != finalCorrection.correctedText
}

private fun isClearGlobalCommand(text: String): Boolean =
    VoiceCommandCorrection.correct(text).targetIntent in setOf(
        VoiceCommandTargetIntent.RESET_FLOW,
        VoiceCommandTargetIntent.STOP_SPEAKING,
        VoiceCommandTargetIntent.REPEAT_LAST,
        VoiceCommandTargetIntent.PAUSE_ROBOT,
        VoiceCommandTargetIntent.ENABLE_ROBOT
    )

private const val MIN_PARTIAL_CHARS = 3
private const val MAX_SESSION_TEXT_CHARS = 120

private val dangerousPartialCommandRegex = Regex(
    "\\b(?:" +
        "mandar|manda|mandale|enviar|envia|enviale|mensaje|ubicacion|foto|" +
        "llamar|llama|llamada|pagar|pago|transferir|banco|clave|contrasena|" +
        "password|otp|codigo|pin|tarjeta|cbu|cvu" +
        ")\\b"
)

private val voiceSensitiveRegex = Regex(
    "\\b(?:" +
        "banco|bancaria|bancario|pago|pagar|transferencia|clave|contrasena|" +
        "password|otp|codigo|pin|tarjeta|cbu|cvu|mercado pago" +
        ")\\b"
)
