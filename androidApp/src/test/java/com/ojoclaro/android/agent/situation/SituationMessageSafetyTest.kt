package com.ojoclaro.android.agent.situation

import com.ojoclaro.android.agent.core.AgentRiskLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SituationMessageSafetyTest {

    private fun pending(
        intentName: String = SituationIntent.WRITE_MESSAGE.name,
        target: String = "",
        payload: Map<String, String> = mapOf(
            "contact" to "Sofi",
            "message" to "llego tarde"
        )
    ): PendingAction = PendingAction(
        label = "preparar un mensaje",
        intentName = intentName,
        riskLevel = AgentRiskLevel.MEDIUM,
        confirmationPrompt = "¿Querés que prepare el mensaje?",
        expiresAt = 999_999L,
        target = target,
        payload = payload
    )

    @Test
    fun write_message_con_contact_y_message_valido_es_seguro() {
        assertTrue(SituationMessageSafety.isSafeWriteMessagePendingAction(pending()))
    }

    @Test
    fun sin_contact_devuelve_false() {
        val action = pending(payload = mapOf("message" to "llego tarde"))
        assertFalse(SituationMessageSafety.isSafeWriteMessagePendingAction(action))
    }

    @Test
    fun sin_message_devuelve_false() {
        val action = pending(payload = mapOf("contact" to "Sofi"))
        assertFalse(SituationMessageSafety.isSafeWriteMessagePendingAction(action))
    }

    @Test
    fun message_con_contrasena_devuelve_false() {
        val action = pending(payload = mapOf("contact" to "Sofi", "message" to "mi contraseña es 1234"))
        assertFalse(SituationMessageSafety.isSafeWriteMessagePendingAction(action))
    }

    @Test
    fun message_con_token_devuelve_false() {
        val action = pending(payload = mapOf("contact" to "Sofi", "message" to "el token es abc"))
        assertFalse(SituationMessageSafety.isSafeWriteMessagePendingAction(action))
    }

    @Test
    fun message_con_banco_devuelve_false() {
        val action = pending(payload = mapOf("contact" to "Sofi", "message" to "estoy en el banco"))
        assertFalse(SituationMessageSafety.isSafeWriteMessagePendingAction(action))
    }

    @Test
    fun message_largo_devuelve_false() {
        val isSafe = runCatching {
            val action = pending(payload = mapOf("contact" to "Sofi", "message" to "x".repeat(241)))
            SituationMessageSafety.isSafeWriteMessagePendingAction(action)
        }.getOrDefault(false)
        assertFalse(isSafe)
    }

    @Test
    fun contact_desde_target_funciona() {
        val action = pending(
            target = "Sofi",
            payload = mapOf("message" to "llego tarde")
        )
        assertEquals("Sofi", SituationMessageSafety.contactFrom(action))
        assertTrue(SituationMessageSafety.isSafeWriteMessagePendingAction(action))
    }

    @Test
    fun contact_desde_payload_tiene_prioridad() {
        val action = pending(
            target = "Sofi",
            payload = mapOf("contact" to "Ana", "message" to "llego tarde")
        )
        assertEquals("Ana", SituationMessageSafety.contactFrom(action))
        assertTrue(SituationMessageSafety.isSafeWriteMessagePendingAction(action))
    }

    @Test
    fun intent_que_no_es_write_message_devuelve_false() {
        val action = pending(intentName = SituationIntent.OPEN_APP.name)
        assertFalse(SituationMessageSafety.isSafeWriteMessagePendingAction(action))
    }
}
