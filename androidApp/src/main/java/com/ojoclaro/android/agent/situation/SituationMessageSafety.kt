package com.ojoclaro.android.agent.situation

import com.ojoclaro.android.memory.MemoryPolicy
import com.ojoclaro.android.privacy.PrivacyGuard

/**
 * Guardia local para WRITE_MESSAGE del Situation Brain.
 *
 * Pura: no usa APIs Android, red ni persistencia. PrivacyGuard sigue siendo la
 * fuente general de privacidad; esta capa agrega restricciones cortas de Fase 9.
 */
object SituationMessageSafety {

    fun isSafeWriteMessagePendingAction(action: PendingAction): Boolean {
        if (situationIntentFromPendingAction(action) != SituationIntent.WRITE_MESSAGE) return false

        val contact = contactFrom(action)
        val message = messageFrom(action)
        if (!isPlausibleContact(contact)) return false
        if (message.isBlank()) return false
        if (message.length > MAX_MESSAGE_CHARS) return false
        if (!PrivacyGuard.isSafeMessagePayload(message)) return false
        if (containsBlockedMessageToken(message)) return false
        return true
    }

    fun contactFrom(action: PendingAction): String =
        action.payload["contact"]
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: action.target.trim()

    fun messageFrom(action: PendingAction): String =
        action.payload["message"]?.trim().orEmpty()

    private fun isPlausibleContact(contact: String): Boolean {
        if (contact.isBlank()) return false
        if (contact.length > MAX_CONTACT_CHARS) return false
        if (contact.any { it.isISOControl() }) return false
        if (contact.none { it.isLetterOrDigit() }) return false
        if (weirdContactRegex.containsMatchIn(contact)) return false
        return true
    }

    private fun containsBlockedMessageToken(message: String): Boolean {
        val normalized = MemoryPolicy.normalize(message)
        return blockedMessageTokenRegex.containsMatchIn(normalized)
    }

    private const val MAX_MESSAGE_CHARS = 240
    private const val MAX_CONTACT_CHARS = 80

    private val blockedMessageTokenRegex =
        Regex("\\b(?:contrasena|clave|token|codigo|tarjeta|banco|transferencia|transferi|cvu|cbu|dni)\\b")

    private val weirdContactRegex = Regex("[\\r\\n\\t<>@{}\\[\\]|\\\\/:;=]")
}
