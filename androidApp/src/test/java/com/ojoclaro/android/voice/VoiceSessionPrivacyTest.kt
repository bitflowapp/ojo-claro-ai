package com.ojoclaro.android.voice

import com.ojoclaro.android.llm.LlmInputSanitizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * FASE 5 — Privacidad de VOZ con datos SINTÉTICOS (read-only).
 *
 * Tres capas de defensa, verificadas por separado:
 *  1. Sesión de voz ([VoiceListeningSession]): redacta secretos por PALABRA CLAVE
 *     (pin/clave/código…) para diagnóstico/log, y nunca los guarda como candidato
 *     parcial reutilizable (así "repetí" del input jamás los trae).
 *  2. Borde del LLM ([LlmInputSanitizer]): redacta TODO secreto sintético
 *     (incl. teléfonos/emails) antes de salir del teléfono.
 *  3. El diagnóstico de voz no transporta texto crudo (sólo metadatos).
 *
 * No se modifica ninguna lógica de privacidad. Si un secreto sobreviviera donde
 * no debe, es HALLAZGO HIGH (reportar BLOCKED), no un fix acá.
 */
class VoiceSessionPrivacyTest {

    // Secreto dictado -> valor sintético que jamás debe sobrevivir.
    private val keywordSecrets = listOf(
        "mi pin es 1234" to "1234",
        "mi código es 445566" to "445566",
        "mi clave es azul123" to "azul123"
    )

    private val allSecrets = keywordSecrets + listOf(
        "mi teléfono es 2991234567" to "2991234567",
        "mi correo es ana@prueba.com" to "ana@prueba.com"
    )

    @Test
    fun voiceSessionRedactsKeywordSecretsForLogsAndDiagnostics() {
        keywordSecrets.forEach { (phrase, secret) ->
            val session = VoiceListeningSession(sessionId = 1L, startedAt = 0L).recordFinal(phrase)
            assertTrue(session.finalWasRedacted, "debe marcar redacción: \"$phrase\"")
            assertFalse(session.finalText.contains(secret), "secreto en finalText: ${session.finalText}")
        }
    }

    @Test
    fun voiceSessionNeverKeepsASecretAsReusablePartialCandidate() {
        // "repetí" del input no puede traer el secreto: no se guarda como candidato.
        keywordSecrets.forEach { (phrase, secret) ->
            val session = VoiceListeningSession(sessionId = 2L, startedAt = 0L).recordPartial(phrase)
            assertFalse(session.bestPartialCandidate.contains(secret), "candidato con secreto: ${session.bestPartialCandidate}")
            assertTrue(session.partialWasRedacted, "partial debe marcarse redactado: \"$phrase\"")
        }
    }

    @Test
    fun sensitivePhrasesAreNotAcceptedAsSafePartials() {
        keywordSecrets.forEach { (phrase, _) ->
            assertFalse(isSafePartialCandidate(phrase), "no debe ser candidato seguro: \"$phrase\"")
        }
    }

    @Test
    fun llmSanitizerRedactsAllSyntheticSecretsBeforeLeavingPhone() {
        allSecrets.forEach { (phrase, secret) ->
            val out = LlmInputSanitizer.sanitize(phrase)
            assertFalse(out.contains(secret), "secreto al LLM: \"$phrase\" -> \"$out\"")
        }
    }

    @Test
    fun voiceDiagnosticNeverCarriesRawSecretText() {
        allSecrets.forEach { (phrase, secret) ->
            val session = VoiceListeningSession(sessionId = 3L, startedAt = 0L)
                .recordPartial(phrase)
                .recordFinal(phrase)
            val diag = session.diagnostic(VoiceHearingStatus.IDLE, VoiceSpeechEngine.PLATFORM_DEFAULT).toString()
            assertFalse(diag.contains(secret), "diagnóstico con secreto: $diag")
        }
    }

    @Test
    fun redactionMarkerIsTheNeutralPlaceholderNotTheSecret() {
        val session = VoiceListeningSession(sessionId = 4L, startedAt = 0L).recordFinal("mi pin es 1234")
        assertEquals(VoiceListeningSession.REDACTED_TEXT, session.finalText)
        assertFalse(session.finalText.contains("1234"))
    }

    @Test
    fun normalSpeechWithoutSecretsIsNotRedacted() {
        val session = VoiceListeningSession(sessionId = 5L, startedAt = 0L).recordFinal("abrí WhatsApp")
        assertFalse(session.finalWasRedacted, "no debe redactar habla normal")
        assertTrue(session.finalText.contains("abr", ignoreCase = true))
    }
}
