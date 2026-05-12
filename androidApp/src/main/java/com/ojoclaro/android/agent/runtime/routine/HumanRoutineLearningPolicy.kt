package com.ojoclaro.android.agent.runtime.routine

import com.ojoclaro.android.memory.MemoryPolicy
import com.ojoclaro.android.memory.MemorySource
import com.ojoclaro.android.memory.MemoryType
import com.ojoclaro.android.memory.UserMemory
import com.ojoclaro.android.privacy.PrivacyGuard

/**
 * Política central de seguridad para Human Routine Learning v1.
 *
 * Reglas hard:
 *  - Toda escritura pasa por [MemoryPolicy.canStore] (o por checks equivalentes
 *    cuando el contenedor no es UserMemory).
 *  - Mensajes rápidos pasan por [PrivacyGuard.isSafeMessagePayload].
 *  - Nombres de contacto se rechazan si tienen contenido financiero/sensible
 *    o si parecen frases largas (no nombres).
 *  - Observaciones se rechazan si su [HumanRoutineObservation.labelHint]
 *    contiene contenido prohibido (banca/contraseñas/etc.) o si parece tener
 *    contenido del mensaje (longitud o patrones).
 *  - Whitelist estricta de claves de preferencia.
 */
class HumanRoutineLearningPolicy(
    val minimumOccurrencesToSuggest: Int = DEFAULT_MIN_OCCURRENCES_TO_SUGGEST,
    private val nowMillis: () -> Long = { System.currentTimeMillis() }
) {

    fun evaluatePreference(key: String, value: String): HumanRoutineSafetyDecision {
        if (key !in RoutinePreferenceKeys.ALL) {
            return HumanRoutineSafetyDecision.Reject(
                reason = "preference_key_not_whitelisted",
                spokenText = "Esa preferencia no está en mi lista. No la guardo."
            )
        }
        if (!RoutinePreferenceValues.isValid(key, value)) {
            return HumanRoutineSafetyDecision.Reject(
                reason = "preference_value_not_whitelisted",
                spokenText = "No reconozco ese valor de preferencia."
            )
        }
        // Aunque las claves/valores están whitelisted, validamos contra MemoryPolicy
        // construyendo un UserMemory equivalente.
        val memory = newPreferenceMemory(key = key, value = value)
        if (!MemoryPolicy.canStore(memory)) {
            return HumanRoutineSafetyDecision.Reject(
                reason = "memory_policy_rejected",
                spokenText = SAFETY_BLOCKED_TEXT
            )
        }
        return HumanRoutineSafetyDecision.Accept
    }

    fun evaluateContactName(name: String, isPrimary: Boolean): HumanRoutineSafetyDecision {
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            return HumanRoutineSafetyDecision.Reject(
                reason = "contact_name_blank",
                spokenText = "No reconocí un nombre claro."
            )
        }
        if (cleanName.length > MAX_CONTACT_NAME_LENGTH) {
            return HumanRoutineSafetyDecision.Reject(
                reason = "contact_name_too_long",
                spokenText = "Ese nombre es demasiado largo."
            )
        }
        if (!CONTACT_NAME_PATTERN.matches(cleanName)) {
            return HumanRoutineSafetyDecision.Reject(
                reason = "contact_name_invalid_chars",
                spokenText = "Ese nombre tiene caracteres que no puedo guardar."
            )
        }
        if (MemoryPolicy.containsProhibitedContent(cleanName)) {
            return HumanRoutineSafetyDecision.Reject(
                reason = "contact_name_prohibited_content",
                spokenText = SAFETY_BLOCKED_TEXT
            )
        }
        // Validación final: que MemoryPolicy lo acepte como TRUSTED_CONTACT.
        val memory = newContactMemory(name = cleanName, isPrimary = isPrimary)
        if (!MemoryPolicy.canStore(memory)) {
            return HumanRoutineSafetyDecision.Reject(
                reason = "memory_policy_rejected",
                spokenText = SAFETY_BLOCKED_TEXT
            )
        }
        return HumanRoutineSafetyDecision.Accept
    }

    fun evaluateQuickMessage(text: String): HumanRoutineSafetyDecision {
        val cleanText = text.trim()
        if (cleanText.isBlank()) {
            return HumanRoutineSafetyDecision.Reject(
                reason = "quick_message_blank",
                spokenText = "No reconocí un mensaje claro."
            )
        }
        if (cleanText.length > MAX_QUICK_MESSAGE_LENGTH) {
            return HumanRoutineSafetyDecision.Reject(
                reason = "quick_message_too_long",
                spokenText = "Ese mensaje es demasiado largo para guardar como rápido."
            )
        }
        if (!PrivacyGuard.isSafeMessagePayload(cleanText)) {
            return HumanRoutineSafetyDecision.Reject(
                reason = "quick_message_unsafe_payload",
                spokenText = SAFETY_BLOCKED_TEXT
            )
        }
        if (MemoryPolicy.containsProhibitedContent(cleanText)) {
            return HumanRoutineSafetyDecision.Reject(
                reason = "memory_policy_prohibited",
                spokenText = SAFETY_BLOCKED_TEXT
            )
        }
        val memory = newQuickMessageMemory(text = cleanText)
        if (!MemoryPolicy.canStore(memory)) {
            return HumanRoutineSafetyDecision.Reject(
                reason = "memory_policy_rejected",
                spokenText = SAFETY_BLOCKED_TEXT
            )
        }
        return HumanRoutineSafetyDecision.Accept
    }

    fun evaluateObservation(observation: HumanRoutineObservation): HumanRoutineSafetyDecision {
        // El labelHint NO debe contener contenido del mensaje. Heurística:
        // pasamos por MemoryPolicy.containsProhibitedContent y por
        // PrivacyGuard.containsSensitiveFinancialData. Largo ya está acotado por
        // el data class.
        if (MemoryPolicy.containsProhibitedContent(observation.labelHint)) {
            return HumanRoutineSafetyDecision.Reject(
                reason = "observation_label_prohibited",
                spokenText = SAFETY_BLOCKED_TEXT
            )
        }
        if (PrivacyGuard.containsSensitiveFinancialData(observation.labelHint)) {
            return HumanRoutineSafetyDecision.Reject(
                reason = "observation_label_financial",
                spokenText = SAFETY_BLOCKED_TEXT
            )
        }
        if (observation.kind in BANNED_OBSERVATION_KINDS) {
            return HumanRoutineSafetyDecision.Reject(
                reason = "observation_kind_banned",
                spokenText = SAFETY_BLOCKED_TEXT
            )
        }
        return HumanRoutineSafetyDecision.Accept
    }

    private fun newPreferenceMemory(key: String, value: String): UserMemory {
        val now = nowMillis()
        return UserMemory(
            id = "routine-pref-$now-$key",
            type = MemoryType.USER_PREFERENCE,
            label = key,
            value = value,
            createdAtMillis = now,
            updatedAtMillis = now,
            isSensitive = false,
            userApproved = true,
            canBeSpoken = true,
            canBeUsedForSuggestions = true,
            requiresConfirmationBeforeUse = false,
            source = MemorySource.USER_COMMAND
        )
    }

    private fun newContactMemory(name: String, isPrimary: Boolean): UserMemory {
        val now = nowMillis()
        return UserMemory(
            id = "routine-contact-$now-${name.lowercase()}",
            type = MemoryType.TRUSTED_CONTACT,
            label = name,
            value = if (isPrimary) PRIMARY_CONTACT_VALUE else FREQUENT_CONTACT_VALUE,
            createdAtMillis = now,
            updatedAtMillis = now,
            isSensitive = false,
            userApproved = true,
            canBeSpoken = true,
            canBeUsedForSuggestions = true,
            requiresConfirmationBeforeUse = false,
            source = MemorySource.USER_COMMAND
        )
    }

    private fun newQuickMessageMemory(text: String): UserMemory {
        val now = nowMillis()
        return UserMemory(
            id = "routine-quickmsg-$now",
            type = MemoryType.FREQUENT_COMMAND,
            label = QUICK_MESSAGE_LABEL,
            value = text,
            createdAtMillis = now,
            updatedAtMillis = now,
            isSensitive = false,
            userApproved = true,
            canBeSpoken = true,
            canBeUsedForSuggestions = true,
            requiresConfirmationBeforeUse = false,
            source = MemorySource.USER_COMMAND
        )
    }

    companion object {
        const val DEFAULT_MIN_OCCURRENCES_TO_SUGGEST: Int = 3
        const val MAX_CONTACT_NAME_LENGTH: Int = 40
        const val MAX_QUICK_MESSAGE_LENGTH: Int = 80
        const val PRIMARY_CONTACT_VALUE: String = "principal"
        const val FREQUENT_CONTACT_VALUE: String = "frecuente"
        const val QUICK_MESSAGE_LABEL: String = "mensaje rapido"

        const val SAFETY_BLOCKED_TEXT: String =
            "No puedo guardar eso porque puede contener información sensible."

        // Solo letras/espacios/algunos signos comunes en nombres en español.
        // Bloquea dígitos y símbolos para evitar guardar números de tarjeta etc.
        private val CONTACT_NAME_PATTERN: Regex =
            Regex("^[a-zA-ZáéíóúüñÁÉÍÓÚÜÑ' .-]+$")

        /**
         * Tipos de observación que NUNCA se trackean. Si en el futuro alguien
         * agrega "screen_text_observed" o similar, hay que extender este set.
         */
        val BANNED_OBSERVATION_KINDS: Set<String> = setOf(
            "screen_text_full",
            "ocr_text_full",
            "chat_message_content",
            "chat_history",
            "password_field",
            "verification_code",
            "card_number",
            "screen_capture"
        )
    }
}

sealed class HumanRoutineSafetyDecision {
    object Accept : HumanRoutineSafetyDecision()

    data class Reject(
        val reason: String,
        val spokenText: String
    ) : HumanRoutineSafetyDecision()
}
