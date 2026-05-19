package com.ojoclaro.android.voice

import com.ojoclaro.android.agent.core.runtime.BridgeDispatchKind
import com.ojoclaro.android.agent.core.runtime.BridgeDispatchOutcome
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentBridgeVoiceMapperTest {

    @Test
    fun pendingMapsToConfirmationRequired() {
        val outcome = handled(
            kind = BridgeDispatchKind.PENDING,
            speakText = "Esta acción requiere confirmación. Decime confirmar o cancelar.",
            pendingPrompt = "Vas a abrir WhatsApp con María. ¿Confirmás?"
        )

        val feedback = AgentBridgeVoiceMapper.toSpokenFeedback(outcome)

        assertNotNull(feedback)
        assertEquals("agent.confirmation.required", feedback.semanticKey)
        assertEquals(SpokenFeedbackCategory.CONFIRMATION_REQUIRED, feedback.category)
        assertEquals(SpokenFeedbackPriority.HIGH, feedback.priority)
        assertEquals(
            "Esta acción requiere confirmación. Decime confirmar o cancelar.",
            feedback.text
        )
        assertFalse(feedback.force)
    }

    @Test
    fun confirmedTextDoesNotClaimExecution() {
        val outcome = handled(
            kind = BridgeDispatchKind.CONFIRMED,
            speakText = "Confirmado. La acción quedó autorizada."
        )

        val feedback = AgentBridgeVoiceMapper.toSpokenFeedback(outcome)

        assertNotNull(feedback)
        assertEquals("agent.confirmation.confirmed", feedback.semanticKey)
        assertEquals(SpokenFeedbackCategory.CONFIRMED, feedback.category)

        listOf(
            "enviado",
            "mandé",
            "mande",
            "ejecutado",
            "listo, enviado",
            "ya lo mandé",
            "ya lo mande",
            "mensaje enviado"
        ).forEach { forbidden ->
            assertFalse(
                feedback.text.lowercase().contains(forbidden),
                "Confirmed feedback should not contain '$forbidden'"
            )
        }
    }

    @Test
    fun cancelledMapsToCancelledFeedback() {
        val outcome = handled(
            kind = BridgeDispatchKind.CANCELLED,
            speakText = "Cancelado."
        )

        val feedback = AgentBridgeVoiceMapper.toSpokenFeedback(outcome)

        assertNotNull(feedback)
        assertEquals("agent.confirmation.cancelled", feedback.semanticKey)
        assertEquals(SpokenFeedbackCategory.CANCELLED, feedback.category)
        assertEquals("Cancelado.", feedback.text)
    }

    @Test
    fun rejectedIsSafetyHighPriorityAndForced() {
        val outcome = handled(
            kind = BridgeDispatchKind.REJECTED,
            speakText = "No puedo hacer eso por seguridad."
        )

        val feedback = AgentBridgeVoiceMapper.toSpokenFeedback(outcome)

        assertNotNull(feedback)
        assertEquals("agent.action.rejected", feedback.semanticKey)
        assertEquals(SpokenFeedbackCategory.REJECTED, feedback.category)
        assertEquals(SpokenFeedbackPriority.HIGH, feedback.priority)
        assertTrue(feedback.force)
        assertEquals("No puedo hacer eso por seguridad.", feedback.text)
    }

    @Test
    fun needsSlotAsksForMissingData() {
        val outcome = handled(
            kind = BridgeDispatchKind.NEEDS_SLOT,
            speakText = "¿A qué contacto?",
            pendingPrompt = "¿A qué contacto?"
        )

        val feedback = AgentBridgeVoiceMapper.toSpokenFeedback(outcome)

        assertNotNull(feedback)
        assertEquals("agent.needs.slot", feedback.semanticKey)
        assertEquals(SpokenFeedbackCategory.NEEDS_SLOT, feedback.category)
        assertEquals(SpokenFeedbackPriority.HIGH, feedback.priority)
        assertEquals("¿A qué contacto?", feedback.text)
    }

    @Test
    fun noPendingMapsToInfoNoPendingKey() {
        val outcome = handled(
            kind = BridgeDispatchKind.NO_PENDING,
            speakText = "No hay ninguna acción pendiente para confirmar."
        )

        val feedback = AgentBridgeVoiceMapper.toSpokenFeedback(outcome)

        assertNotNull(feedback)
        assertEquals("agent.confirmation.no_pending", feedback.semanticKey)
        assertEquals(SpokenFeedbackCategory.INFO, feedback.category)
        assertTrue(feedback.text.contains("pendiente", ignoreCase = true))
    }

    @Test
    fun expiredIsForcedAndUsesSafeDefault() {
        val outcome = handled(
            kind = BridgeDispatchKind.EXPIRED,
            speakText = ""
        )

        val feedback = AgentBridgeVoiceMapper.toSpokenFeedback(outcome)

        assertNotNull(feedback)
        assertEquals("agent.confirmation.expired", feedback.semanticKey)
        assertEquals(SpokenFeedbackPriority.HIGH, feedback.priority)
        assertTrue(feedback.force)
        assertTrue(feedback.text.contains("vencio", ignoreCase = true) ||
            feedback.text.contains("venció", ignoreCase = true))
    }

    @Test
    fun readyUsesGenericInfoCategory() {
        val outcome = handled(
            kind = BridgeDispatchKind.READY,
            speakText = "Voy a abrir WhatsApp."
        )

        val feedback = AgentBridgeVoiceMapper.toSpokenFeedback(outcome)

        assertNotNull(feedback)
        assertEquals("agent.action.ready", feedback.semanticKey)
        assertEquals(SpokenFeedbackCategory.INFO, feedback.category)
        assertEquals("Voy a abrir WhatsApp.", feedback.text)
    }

    @Test
    fun fallbackToLegacyDoesNotProduceFeedback() {
        val outcome = BridgeDispatchOutcome.FallbackToLegacy(reason = "typed_confirmation_disabled")

        val feedback = AgentBridgeVoiceMapper.toSpokenFeedback(outcome)

        assertNull(feedback)
    }

    @Test
    fun mapperHasNoAndroidApiOrUnsafeActions() {
        val source = File(
            "src/main/java/com/ojoclaro/android/voice/AgentBridgeVoiceMapper.kt"
        ).readText()

        listOf(
            "import android.",
            "Context",
            "TextToSpeech",
            "performClick(",
            "dispatchGesture(",
            "performGlobalAction(",
            "startActivity(",
            "SmsManager",
            "ACTION_CALL"
        ).forEach { forbidden ->
            assertFalse(source.contains(forbidden), forbidden)
        }
    }

    @Test
    fun mapperSourceDoesNotClaimExecution() {
        val source = File(
            "src/main/java/com/ojoclaro/android/voice/AgentBridgeVoiceMapper.kt"
        ).readText().lowercase()

        listOf(
            "mensaje enviado",
            "ya lo mande",
            "ya lo mandé",
            "listo, enviado",
            "acción ejecutada",
            "accion ejecutada"
        ).forEach { forbidden ->
            assertFalse(source.contains(forbidden), forbidden)
        }
    }

    private fun handled(
        kind: BridgeDispatchKind,
        speakText: String,
        pendingPrompt: String? = null,
        hasPending: Boolean = kind == BridgeDispatchKind.PENDING
    ): BridgeDispatchOutcome.Handled = BridgeDispatchOutcome.Handled(
        speakText = speakText,
        pendingPrompt = pendingPrompt,
        hasPending = hasPending,
        kind = kind
    )
}
