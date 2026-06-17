package com.ojoclaro.android.voice

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VoiceFeedbackControllerTest {

    private var now: Long = 1_000L
    private val spoken = mutableListOf<String>()
    private val controller = VoiceFeedbackController(
        speakNow = { spoken += it },
        clock = { now }
    )

    @Test
    fun firstMessageIsAllowed() {
        val decision = controller.emit(feedback("screen.read", "Leo la pantalla."))

        assertSpeak(decision, "Leo la pantalla.", "fresh")
        assertEquals(listOf("Leo la pantalla."), spoken)
    }

    @Test
    fun identicalMessageInsideDedupWindowIsSuppressed() {
        controller.emit(feedback("screen.read", "Leo la pantalla."))
        now += 500L

        val decision = controller.emit(feedback("screen.read", "Leo la pantalla."))

        assertSuppress(decision, "duplicate_text_within_window")
        assertEquals(listOf("Leo la pantalla."), spoken)
    }

    @Test
    fun identicalMessageOutsideDedupWindowIsAllowed() {
        controller.emit(feedback("screen.read", "Leo la pantalla."))
        now += SpokenFeedback.DEFAULT_DEDUP_WINDOW_MS + 1L

        val decision = controller.emit(feedback("screen.read", "Leo la pantalla."))

        assertSpeak(decision, "Leo la pantalla.", "fresh")
        assertEquals(listOf("Leo la pantalla.", "Leo la pantalla."), spoken)
    }

    @Test
    fun forceAllowsRepeatedMessage() {
        controller.emit(feedback("repeat", "Repito lo ultimo."))
        now += 100L

        val decision = controller.emit(
            feedback(
                semanticKey = "repeat",
                text = "Repito lo ultimo.",
                force = true
            )
        )

        assertSpeak(decision, "Repito lo ultimo.", "forced")
        assertEquals(listOf("Repito lo ultimo.", "Repito lo ultimo."), spoken)
    }

    @Test
    fun criticalAllowsRepeatedMessage() {
        controller.emit(feedback("safety.warning", "Esta pantalla parece sensible."))
        now += 100L

        val decision = controller.emit(
            feedback(
                semanticKey = "safety.warning",
                text = "Esta pantalla parece sensible.",
                category = SpokenFeedbackCategory.SAFETY_WARNING,
                priority = SpokenFeedbackPriority.CRITICAL
            )
        )

        assertSpeak(decision, "Esta pantalla parece sensible.", "critical")
        assertEquals(
            listOf("Esta pantalla parece sensible.", "Esta pantalla parece sensible."),
            spoken
        )
    }

    @Test
    fun semanticKeyDeduplicatesEvenWhenTextChangesSlightly() {
        controller.emit(feedback("open.whatsapp", "Voy a abrir WhatsApp."))
        now += 500L

        val decision = controller.emit(feedback("open.whatsapp", "Abro WhatsApp."))

        assertSuppress(decision, "duplicate_semantic_key_within_window")
        assertEquals(listOf("Voy a abrir WhatsApp."), spoken)
    }

    @Test
    fun alternatesRotateToAvoidRepetition() {
        val request = feedback(
            semanticKey = "listening",
            text = "Te escucho.",
            alternates = listOf("Dale, te escucho.", "Decime.")
        )

        assertSpeak(controller.emit(request), "Te escucho.", "fresh")
        now += 500L
        assertSpeak(controller.emit(request), "Dale, te escucho.", "alternate_rotated")
        now += 500L
        assertSpeak(controller.emit(request), "Decime.", "alternate_rotated")

        assertEquals(listOf("Te escucho.", "Dale, te escucho.", "Decime."), spoken)
    }

    @Test
    fun confirmationRequiredDoesNotRepeatLikeParrot() {
        val request = feedback(
            semanticKey = "confirm.pending",
            text = "Esta accion requiere confirmacion.",
            category = SpokenFeedbackCategory.CONFIRMATION_REQUIRED,
            priority = SpokenFeedbackPriority.HIGH
        )

        controller.emit(request)
        now += 250L

        val decision = controller.emit(request)

        assertSuppress(decision, "duplicate_text_within_window")
        assertEquals(listOf("Esta accion requiere confirmacion."), spoken)
    }

    @Test
    fun rejectedSafetyWarningCanForceSpeech() {
        controller.emit(
            feedback(
                semanticKey = "safety.reject",
                text = "No puedo hacer eso por seguridad.",
                category = SpokenFeedbackCategory.REJECTED
            )
        )
        now += 100L

        val decision = controller.emit(
            feedback(
                semanticKey = "safety.reject",
                text = "No puedo hacer eso por seguridad.",
                category = SpokenFeedbackCategory.SAFETY_WARNING,
                priority = SpokenFeedbackPriority.CRITICAL,
                force = true
            )
        )

        assertSpeak(decision, "No puedo hacer eso por seguridad.", "forced")
        assertEquals(
            listOf("No puedo hacer eso por seguridad.", "No puedo hacer eso por seguridad."),
            spoken
        )
    }

    @Test
    fun cancelledSpeaksOnceAndThenSuppressesQuickRepeat() {
        val request = feedback(
            semanticKey = "action.cancelled",
            text = "Cancelado.",
            category = SpokenFeedbackCategory.CANCELLED
        )

        assertSpeak(controller.emit(request), "Cancelado.", "fresh")
        now += 100L
        assertSuppress(controller.emit(request), "duplicate_text_within_window")

        assertEquals(listOf("Cancelado."), spoken)
    }

    @Test
    fun controllerDoesNotDependOnAndroidApis() {
        val sources = voiceFeedbackSources()

        listOf(
            "import android.",
            "android.content.Context",
            "Context",
            "TextToSpeech",
            "SpeechController",
            "performClick(",
            "dispatchGesture(",
            "performGlobalAction(",
            "startActivity(",
            "SmsManager",
            "ACTION_CALL"
        ).forEach { forbidden ->
            assertFalse(sources.contains(forbidden), forbidden)
        }
    }

    @Test
    fun primitivesDoNotReferenceRuntimeEntryPoints() {
        val sources = voiceFeedbackSources()

        listOf(
            "HomeViewModel",
            "MainActivity",
            "OjoClaroAccessibilityService",
            "AssistantOrchestrator",
            "AgentRuntimeBridge"
        ).forEach { forbidden ->
            assertFalse(sources.contains(forbidden), forbidden)
        }

        val entryPoints = listOf(
            "src/main/java/com/ojoclaro/android/ui/home/HomeViewModel.kt",
            "src/main/java/com/ojoclaro/android/MainActivity.kt",
            "src/main/java/com/ojoclaro/android/accessibility/OjoClaroAccessibilityService.kt"
        ).joinToString(separator = "\n") { File(it).readText() }

        assertFalse(entryPoints.contains("VoiceFeedbackController"))
        assertFalse(entryPoints.contains("SpokenFeedback"))
    }

    @Test
    fun genericConfirmedFeedbackDoesNotClaimExecution() {
        val sources = voiceFeedbackSources().lowercase()

        listOf(
            "mensaje enviado",
            "enviado automaticamente",
            "accion realizada",
            "pago realizado",
            "transferencia realizada",
            "compra realizada"
        ).forEach { forbidden ->
            assertFalse(sources.contains(forbidden), forbidden)
        }

        val decision = controller.emit(
            feedback(
                semanticKey = "action.confirmed",
                text = "Confirmado. La accion quedo autorizada.",
                category = SpokenFeedbackCategory.CONFIRMED
            )
        )

        assertSpeak(decision, "Confirmado. La accion quedo autorizada.", "fresh")
        assertFalse(spoken.single().contains("enviado", ignoreCase = true))
    }

    private fun feedback(
        semanticKey: String,
        text: String,
        category: SpokenFeedbackCategory = SpokenFeedbackCategory.INFO,
        priority: SpokenFeedbackPriority = SpokenFeedbackPriority.NORMAL,
        force: Boolean = false,
        dedupWindowMs: Long = SpokenFeedback.DEFAULT_DEDUP_WINDOW_MS,
        alternates: List<String> = emptyList()
    ): SpokenFeedback = SpokenFeedback(
        semanticKey = semanticKey,
        text = text,
        category = category,
        priority = priority,
        force = force,
        dedupWindowMs = dedupWindowMs,
        alternates = alternates,
        createdAtMillis = now
    )

    private fun assertSpeak(
        decision: VoiceFeedbackDecision,
        text: String,
        reason: String
    ) {
        val speak = decision as? VoiceFeedbackDecision.Speak
        assertTrue(speak != null, "Expected Speak, got $decision")
        assertEquals(text, speak.text)
        assertEquals(reason, speak.reason)
    }

    private fun assertSuppress(
        decision: VoiceFeedbackDecision,
        reason: String
    ) {
        val suppress = decision as? VoiceFeedbackDecision.Suppress
        assertTrue(suppress != null, "Expected Suppress, got $decision")
        assertEquals(reason, suppress.reason)
    }

    private fun voiceFeedbackSources(): String =
        listOf(
            "src/main/java/com/ojoclaro/android/voice/SpokenFeedback.kt",
            "src/main/java/com/ojoclaro/android/voice/VoiceFeedbackController.kt"
        ).joinToString(separator = "\n") { File(it).readText() }
}
