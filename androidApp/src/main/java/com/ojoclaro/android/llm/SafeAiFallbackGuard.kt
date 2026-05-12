package com.ojoclaro.android.llm

import com.ojoclaro.android.agent.AgentIntent
import com.ojoclaro.android.model.AppState
import com.ojoclaro.android.privacy.PrivacyGuard

/**
 * Safe AI Fallback v1.
 *
 * Esta guarda decide si Ojo Claro puede consultar al fallback de IA cuando el
 * parser local no entiende. Es una capa defensiva, paralela a [LlmFallbackPolicy].
 *
 * Reglas (todas deben pasar):
 *  - El proxy local debe estar configurado (no consultamos sin proxy).
 *  - El texto del usuario no puede contener datos financieros/contraseñas/codigos.
 *  - La pantalla actual no puede ser sensible (bancos, password, pago, OCR completo,
 *    chat privado completo).
 *  - No puede haber una confirmacion pendiente (el usuario debe terminar el flujo
 *    primero antes de delegar a la IA).
 *
 * Si alguna falla, el caller debe degradar localmente y NUNCA decir al usuario
 * "no estoy usando la IA". Usar [SafeAiFallbackCopy.contextual] para la respuesta.
 *
 * Whitelist de intents que la IA puede proponer en v1. Es deliberadamente chica:
 * solo lectura, ayuda y aperturas seguras con confirmacion posterior.
 */
class SafeAiFallbackGuard(
    private val isProxyConfigured: () -> Boolean = { false }
) {
    fun evaluate(input: SafeAiFallbackInput): SafeAiFallbackVerdict {
        if (!isProxyConfigured()) {
            return SafeAiFallbackVerdict.Denied(reason = SafeAiFallbackReason.PROXY_NOT_CONFIGURED)
        }
        if (input.hasPendingConfirmation) {
            return SafeAiFallbackVerdict.Denied(reason = SafeAiFallbackReason.PENDING_CONFIRMATION)
        }
        if (input.screenIsSensitive) {
            return SafeAiFallbackVerdict.Denied(reason = SafeAiFallbackReason.SENSITIVE_SCREEN)
        }
        if (input.userTextLooksSensitive()) {
            return SafeAiFallbackVerdict.Denied(reason = SafeAiFallbackReason.SENSITIVE_INPUT)
        }
        if (input.fullChatVisible || input.fullOcrCaptured) {
            return SafeAiFallbackVerdict.Denied(reason = SafeAiFallbackReason.PRIVATE_CONTENT_VISIBLE)
        }
        return SafeAiFallbackVerdict.Allowed
    }

    fun filterIntent(intent: AgentIntent?): AgentIntent {
        if (intent == null) return AgentIntent.UNKNOWN
        return if (intent in WHITELIST_V1) intent else AgentIntent.UNKNOWN
    }

    companion object {
        /**
         * Whitelist inicial de Safe AI Fallback v1.
         *
         * Solo intents seguros sin envio/click/escritura automaticos. Cualquier
         * cosa fuera de esta lista se mapea a UNKNOWN y degrada localmente.
         */
        val WHITELIST_V1: Set<AgentIntent> = setOf(
            AgentIntent.HELP,
            AgentIntent.READ_VISIBLE_SCREEN,
            AgentIntent.OPEN_WHATSAPP,
            AgentIntent.REPEAT_LAST,
            AgentIntent.STOP_SPEAKING,
            AgentIntent.CANCEL,
            AgentIntent.UNKNOWN
        )
    }
}

data class SafeAiFallbackInput(
    val userText: String,
    val appState: AppState,
    val screenIsSensitive: Boolean,
    val hasPendingConfirmation: Boolean,
    val fullOcrCaptured: Boolean = false,
    val fullChatVisible: Boolean = false
) {
    fun userTextLooksSensitive(): Boolean {
        if (userText.isBlank()) return false
        val lower = userText.lowercase()
        val bankWords = listOf("banco", "transfer", "cbu", "alias", "tarjeta", "pago", "pagar", "saldo", "contraseña", "clave", "password", "pin", "codigo", "código", "otp")
        if (bankWords.any { it in lower }) return true
        return PrivacyGuard.containsSensitiveFinancialData(userText)
    }
}

sealed class SafeAiFallbackVerdict {
    data object Allowed : SafeAiFallbackVerdict()
    data class Denied(val reason: SafeAiFallbackReason) : SafeAiFallbackVerdict()

    val isAllowed: Boolean get() = this is Allowed
}

enum class SafeAiFallbackReason {
    PROXY_NOT_CONFIGURED,
    SENSITIVE_SCREEN,
    SENSITIVE_INPUT,
    PENDING_CONFIRMATION,
    PRIVATE_CONTENT_VISIBLE
}
