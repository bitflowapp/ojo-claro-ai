package com.ojoclaro.android.voice

import java.util.Locale

internal sealed class SpeechRecognitionLanguageCandidate {
    abstract val safeLogLabel: String
    abstract val languageTag: String?

    data class ForcedLocale(val locale: Locale) : SpeechRecognitionLanguageCandidate() {
        override val languageTag: String = locale.toLanguageTag()
        override val safeLogLabel: String = languageTag
    }

    data object DeviceDefault : SpeechRecognitionLanguageCandidate() {
        override val languageTag: String? = null
        override val safeLogLabel: String = "device-default"
    }
}

internal object SpanishSpeechRecognitionFallbacks {
    private val preferredSpanishLocales: List<Locale> = listOf(
        Locale("es", "AR"),
        Locale.forLanguageTag("es-419"),
        Locale("es", "ES"),
        Locale("es")
    )

    private val stableDefaultRecognizerSpanishLocales: List<Locale> = listOf(
        Locale("es", "AR"),
        Locale("es", "ES"),
        Locale("es")
    )

    @Suppress("UNUSED_PARAMETER")
    fun buildCandidates(
        preferredLocale: Locale = Locale("es", "AR"),
        defaultLocale: Locale,
        supportedLanguages: Collection<String>? = null
    ): List<SpeechRecognitionLanguageCandidate> {
        // Android recognizers can return null, empty, or incomplete language
        // support lists. We keep the list as diagnostic input only and never
        // use it to block Spanish recognition before trying the recognizer.
        val forcedLocales = (listOf(preferredLocale) + preferredSpanishLocales + defaultLocale)
            .filter { it.toLanguageTag().isNotBlank() }
            .distinctBy { it.toLanguageTag().lowercase(Locale.ROOT) }
            .map(SpeechRecognitionLanguageCandidate::ForcedLocale)

        return forcedLocales + SpeechRecognitionLanguageCandidate.DeviceDefault
    }

    @Suppress("UNUSED_PARAMETER")
    fun buildDefaultRecognizerCandidates(
        preferredLocale: Locale = Locale("es", "AR"),
        defaultLocale: Locale,
        supportedLanguages: Collection<String>? = null
    ): List<SpeechRecognitionLanguageCandidate> {
        val forcedLocales = (listOf(preferredLocale) + stableDefaultRecognizerSpanishLocales + defaultLocale)
            .filter { it.toLanguageTag().isNotBlank() }
            .distinctBy { it.toLanguageTag().lowercase(Locale.ROOT) }
            .map(SpeechRecognitionLanguageCandidate::ForcedLocale)

        return forcedLocales + SpeechRecognitionLanguageCandidate.DeviceDefault
    }
}

internal class SpeechLanguageFallbackPolicy(
    private val candidates: List<SpeechRecognitionLanguageCandidate>
) {
    init {
        require(candidates.isNotEmpty()) { "Speech language candidates cannot be empty." }
    }

    var currentIndex: Int = 0
        private set

    val currentCandidate: SpeechRecognitionLanguageCandidate
        get() = candidates[currentIndex]

    val allCandidates: List<SpeechRecognitionLanguageCandidate>
        get() = candidates

    fun nextCandidateAfter(errorCode: Int?): SpeechRecognitionLanguageCandidate? {
        if (!shouldTryNextLanguageCandidate(errorCode)) return null
        val nextIndex = currentIndex + 1
        if (nextIndex >= candidates.size) return null
        currentIndex = nextIndex
        return candidates[currentIndex]
    }
}

internal fun buildSpeechRecognitionLanguageCandidates(
    preferredLocale: Locale = Locale("es", "AR"),
    defaultLocale: Locale = Locale.getDefault(),
    supportedLanguages: Collection<String>? = null
): List<SpeechRecognitionLanguageCandidate> =
    SpanishSpeechRecognitionFallbacks.buildCandidates(
        preferredLocale = preferredLocale,
        defaultLocale = defaultLocale,
        supportedLanguages = supportedLanguages
    )

internal fun buildDefaultSystemSpeechRecognitionLanguageCandidates(
    preferredLocale: Locale = Locale("es", "AR"),
    defaultLocale: Locale = Locale.getDefault(),
    supportedLanguages: Collection<String>? = null
): List<SpeechRecognitionLanguageCandidate> =
    SpanishSpeechRecognitionFallbacks.buildDefaultRecognizerCandidates(
        preferredLocale = preferredLocale,
        defaultLocale = defaultLocale,
        supportedLanguages = supportedLanguages
    )

internal fun shouldTryNextLanguageCandidate(errorCode: Int?): Boolean =
    errorCode == VoiceSpeechErrorPolicy.ERROR_CODE_LANGUAGE_NOT_SUPPORTED ||
        errorCode == VoiceSpeechErrorPolicy.ERROR_CODE_LANGUAGE_UNAVAILABLE ||
        errorCode == VoiceSpeechErrorPolicy.ERROR_CODE_CANNOT_CHECK_SUPPORT ||
        errorCode == VoiceSpeechErrorPolicy.ERROR_CODE_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS
