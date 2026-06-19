package com.ojoclaro.android.agent.runtime.whatsapp

import com.ojoclaro.android.agent.core.screen.ScreenContextProvider
import com.ojoclaro.android.agent.core.screen.ScreenElement
import com.ojoclaro.android.agent.core.screen.ScreenElementRole
import com.ojoclaro.android.agent.core.screen.ScreenSnapshot
import com.ojoclaro.android.llm.LlmInputSanitizer
import com.ojoclaro.android.voice.VoiceHearingStatus
import com.ojoclaro.android.voice.VoiceListeningSession
import com.ojoclaro.android.voice.VoiceSpeechEngine
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * FASE 6 (privacidad en pantalla/draft) + FASE 7 (copy) del flujo WhatsApp guiado.
 *
 * Datos SINTÉTICOS únicamente. Verifica las capas de defensa:
 *  - borde LLM ([LlmInputSanitizer]): redacta PIN/código/clave/teléfono;
 *  - sesión de voz ([VoiceListeningSession]): redacta secretos por palabra clave;
 *  - contexto WhatsApp ([WhatsAppConversationContext]): número→últimos 4, label
 *    sin corridas largas, recall sin secretos y reafirma "no envío";
 *  - guía ([WhatsAppGuidedWorkflowUseCase]): plantillas fijas, jamás contenido
 *    del chat.
 * Y audita que el copy hablado del flujo NO exponga jerga técnica.
 */
class WhatsAppGuidedFlowPrivacyTest {

    @BeforeTest fun setUp() = WhatsAppConversationContext.clear()
    @AfterTest fun tearDown() = WhatsAppConversationContext.clear()

    private val keywordSecrets = listOf(
        "mi pin es 1234" to "1234",
        "mi código es 445566" to "445566",
        "mi clave es azul123" to "azul123"
    )
    private val allSecrets = keywordSecrets + listOf("mi número es 2991234567" to "2991234567")

    // ---------------- FASE 6 — privacidad ----------------

    @Test fun secretsAreRedactedBeforeLlm() {
        allSecrets.forEach { (phrase, secret) ->
            val out = LlmInputSanitizer.sanitize(phrase)
            assertFalse(out.contains(secret), "secreto al LLM: \"$phrase\" -> \"$out\"")
        }
    }

    @Test fun voiceSessionRedactsKeywordSecretsForLogs() {
        keywordSecrets.forEach { (phrase, secret) ->
            val s = VoiceListeningSession(1L, 0L).recordFinal(phrase)
            assertTrue(s.finalWasRedacted, "redacta: \"$phrase\"")
            assertFalse(s.finalText.contains(secret))
        }
    }

    @Test fun conversationContextRedactsNumbersInChatLabelAndPhone() {
        WhatsAppConversationContext.noteDestination("Ana 2991234567", "2991234567", 1L)
        val snap = WhatsAppConversationContext.current()!!
        assertFalse(snap.chatLabelRedacted!!.contains("2991234567"), "label con número crudo")
        assertEquals("4567", snap.phoneEnding, "solo últimos 4")
    }

    @Test fun spokenRecallNeverContainsSyntheticSecrets() {
        WhatsAppConversationContext.noteDestination("Ana 2991234567", "2991234567", 1L)
        WhatsAppConversationContext.noteDraftLen(10, 1L)
        val recall = WhatsAppConversationContext.spokenRecall()
        listOf("2991234567", "1234", "445566", "azul123").forEach {
            assertFalse(recall.contains(it), "recall con secreto: $recall")
        }
    }

    @Test fun secretNotReusedInAnotherChatAfterSwitch() {
        WhatsAppConversationContext.noteDestination("Ana 2991234567", "2991234567", 1L)
        WhatsAppConversationContext.clear()
        WhatsAppConversationContext.noteDestination("Luz Test", null, 2L)
        val snap = WhatsAppConversationContext.current()!!
        assertFalse((snap.chatLabelRedacted ?: "").contains("2991234567"))
        assertEquals(null, snap.phoneEnding, "no hereda el teléfono del chat anterior")
    }

    @Test fun guidanceNeverLeaksOnScreenChatContentWithSyntheticSecret() {
        val snap = ScreenSnapshot(
            packageName = "com.whatsapp",
            text = "Ana Prueba: mi pin es 1234, no lo pierdas",
            elements = listOf(
                ScreenElement("Ana Prueba: mi pin es 1234", ScreenElementRole.TEXT, isInteractive = false),
                ScreenElement("Mensaje", ScreenElementRole.EDIT_TEXT, isInteractive = true),
                ScreenElement("Enviar", ScreenElementRole.BUTTON, isInteractive = true)
            ),
            capturedAtMillis = 0L
        )
        val useCase = WhatsAppGuidedWorkflowUseCase(
            provider = ScreenContextProvider { snap },
            isAccessibilityReady = { true }
        )
        listOf("qué puedo hacer en este chat", "cómo le mando un mensaje", "estoy en WhatsApp").forEach { phrase ->
            val r = useCase.handle(phrase)
            val text = (r as? WhatsAppGuidedResponse.Guidance)?.spokenText ?: ""
            assertFalse(text.contains("1234"), "filtró el PIN para '$phrase': $text")
            assertFalse(text.contains("mi pin", ignoreCase = true), "quoteó el chat para '$phrase'")
        }
    }

    // ---------------- FASE 7 — copy hablado sin jerga ----------------

    private val jargon = listOf(
        Regex("NO_MATCH"), Regex("\\bblocked\\b", RegexOption.IGNORE_CASE),
        Regex("\\bintent\\b", RegexOption.IGNORE_CASE), Regex("\\bhandler\\b", RegexOption.IGNORE_CASE),
        Regex("not[_ ]?found", RegexOption.IGNORE_CASE), Regex("\\bruntime\\b", RegexOption.IGNORE_CASE),
        Regex("\\bfallback\\b", RegexOption.IGNORE_CASE), Regex("critical guard", RegexOption.IGNORE_CASE),
        Regex("AccessibilityNodeInfo"), Regex("com\\.whatsapp"),
        // Nombres de enum técnicos que no deben hablarse.
        Regex("WhatsAppGuidedCommand"), Regex("SafeLlmRoute"), Regex("WhatsAppScreenState")
    )

    private fun assertHuman(text: String, where: String) {
        assertTrue(text.isNotBlank(), "$where: copy vacío")
        jargon.forEach { rx -> assertFalse(rx.containsMatchIn(text), "$where: jerga /${rx.pattern}/ en \"$text\"") }
    }

    private fun chatOpenSnapshot() = ScreenSnapshot(
        packageName = "com.whatsapp",
        text = "x",
        elements = listOf(
            ScreenElement("Mensaje", ScreenElementRole.EDIT_TEXT, isInteractive = true),
            ScreenElement("Cámara", ScreenElementRole.BUTTON, isInteractive = true),
            ScreenElement("Adjuntar", ScreenElementRole.BUTTON, isInteractive = true),
            ScreenElement("Enviar", ScreenElementRole.BUTTON, isInteractive = true)
        ),
        capturedAtMillis = 0L
    )

    @Test fun guidedUseCaseSpokenCopyHasNoTechnicalJargon() {
        val useCase = WhatsAppGuidedWorkflowUseCase(
            provider = ScreenContextProvider { chatOpenSnapshot() },
            isAccessibilityReady = { true }
        )
        listOf(
            "estoy en WhatsApp", "qué puedo hacer en este chat",
            "cómo mando una foto", "cómo mando ubicación", "cómo le mando un mensaje"
        ).forEach { phrase ->
            val text = (useCase.handle(phrase) as WhatsAppGuidedResponse.Guidance).spokenText
            assertHuman(text, "guidance($phrase)")
        }
        assertHuman(WhatsAppGuidedWorkflowUseCase.NEEDS_ACCESSIBILITY_TEXT, "needs-accessibility")
    }

    @Test fun safetyRefusalsAreCalmAndJargonFree() {
        WhatsAppMediaCallRefusalPhrases.Kind.values().forEach { k ->
            val r = WhatsAppMediaCallRefusalPhrases.refusal(k)
            assertHuman(r, "refusal($k)")
            assertTrue(r.contains("seguridad", ignoreCase = true), "enmarca en seguridad: $r")
        }
    }

    @Test fun contextRecallCopyIsHumanAndReassuring() {
        WhatsAppConversationContext.noteDestination("Ana Prueba", null, 1L)
        WhatsAppConversationContext.notePendingAction("send_message", 1L)
        val recall = WhatsAppConversationContext.spokenRecall()
        assertHuman(recall, "recall")
        assertTrue(recall.contains("No voy a enviar nada", ignoreCase = true), "reafirma que no envía")
    }

    @Test fun emptyContextRecallIsHuman() {
        WhatsAppConversationContext.clear()
        assertHuman(WhatsAppConversationContext.spokenRecall(), "empty-recall")
    }
}
