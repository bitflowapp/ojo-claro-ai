package com.ojoclaro.android.agent

import com.ojoclaro.android.agent.core.screen.NextStepQueryPhrases
import com.ojoclaro.android.agent.core.screen.ScreenQueryPhrases
import com.ojoclaro.android.agent.core.screen.ScreenSummaryMode
import com.ojoclaro.android.agent.payments.PaymentGuidePhrases
import com.ojoclaro.android.agent.runtime.whatsapp.VisibleScreenCommandParser
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppCallPhrases
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppSmartComposeParser
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppVoiceSendPhrases
import com.ojoclaro.android.outdoor.OutdoorPhrases
import com.ojoclaro.android.outdoor.RideMonitorPhrases
import com.ojoclaro.android.outdoor.removeSpanishAccents
import com.ojoclaro.android.voice.VoicePhraseNormalizer

/**
 * V1.11 — clasificador NOMBRADO de intención de tarea.
 *
 * No reemplaza el routing de GlobalAssistantService (que sigue siendo la
 * verdad ejecutable): delega en los MISMOS matchers deterministas y existe
 * para tres cosas:
 *  1. logging/QA: cada frase reconocida puede etiquetarse con un intent;
 *  2. tests de contrato: la tabla frase→intent→política es verificable;
 *  3. despachar las intenciones NUEVAS (videollamada, audio, monitoreo,
 *     guía de pagos) sin tocar las rutas que ya funcionan.
 *
 * Política de seguridad por intent en [policyFor]: la matriz de la misión.
 */
object EstelaTaskIntentRouter {

    enum class TaskIntent {
        READ_SCREEN,
        EXPLAIN_SCREEN,
        OPEN_APP,
        OPEN_WHATSAPP_CHAT,
        SEND_WHATSAPP_TEXT_PENDING_CONFIRMATION,
        CONFIRM_SEND_WHATSAPP_TEXT,
        START_WHATSAPP_VIDEO_CALL_PENDING_CONFIRMATION,
        START_WHATSAPP_AUDIO_FLOW,
        REQUEST_RIDE_PENDING_CONFIRMATION,
        MONITOR_RIDE_STATUS,
        GUIDE_PAYMENT_LINKING,
        GUIDE_CARD_REGISTRATION,
        SENSITIVE_PAYMENT_BLOCK,
        UNKNOWN
    }

    enum class SafetyClass {
        /** Leer/explicar pantalla, abrir apps, monitorear: sin confirmación extra. */
        SAFE,

        /** Enviar mensajes, iniciar llamadas, tocar botones importantes. */
        CONFIRM_REQUIRED,

        /** Pagos/tarjetas/viaje final: SOLO guía hablada, jamás automatizar. */
        SENSITIVE_GUIDED,

        /** Ingresar datos financieros o confirmar dinero: ni con confirmación. */
        FORBIDDEN_AUTOMATIC
    }

    fun classify(rawText: String): TaskIntent {
        val text = fold(rawText)
        if (text.isBlank()) return TaskIntent.UNKNOWN

        // 1. Lo sensible primero: jamás puede caer en otra rama por error.
        when (PaymentGuidePhrases.classify(text)) {
            PaymentGuidePhrases.Kind.SENSITIVE_BLOCK -> return TaskIntent.SENSITIVE_PAYMENT_BLOCK
            PaymentGuidePhrases.Kind.REGISTER_CARD -> return TaskIntent.GUIDE_CARD_REGISTRATION
            PaymentGuidePhrases.Kind.LINK_PAYMENT -> return TaskIntent.GUIDE_PAYMENT_LINKING
            null -> Unit
        }

        // 2. Audio ANTES que texto: "mandale un audio a Marco" no es compose.
        if (WhatsAppVoiceSendPhrases.isSendAudioRequest(text) || isAudioFlowActivation(text)) {
            return TaskIntent.START_WHATSAPP_AUDIO_FLOW
        }

        if (WhatsAppCallPhrases.parseVideoCall(text) != null) {
            return TaskIntent.START_WHATSAPP_VIDEO_CALL_PENDING_CONFIRMATION
        }

        if (RideMonitorPhrases.isStartCommand(text)) return TaskIntent.MONITOR_RIDE_STATUS

        if (WhatsAppSmartComposeParser.parseCompose(text) != null ||
            WhatsAppVoiceSendPhrases.isSendDraftCommand(text)
        ) {
            return TaskIntent.SEND_WHATSAPP_TEXT_PENDING_CONFIRMATION
        }
        if (WhatsAppVoiceSendPhrases.isConfirmSend(text)) {
            return TaskIntent.CONFIRM_SEND_WHATSAPP_TEXT
        }

        when (val outdoor = OutdoorPhrases.parse(text)) {
            is OutdoorPhrases.Command.TransportQuery ->
                return TaskIntent.REQUEST_RIDE_PENDING_CONFIRMATION
            is OutdoorPhrases.Command.OpenMaps -> return TaskIntent.OPEN_APP
            else -> if (outdoor != null) return TaskIntent.UNKNOWN
        }

        if (VisibleScreenCommandParser.parse(text) != null) {
            return TaskIntent.OPEN_WHATSAPP_CHAT
        }

        when (ScreenQueryPhrases.classify(text)) {
            ScreenSummaryMode.WHAT_CAN_I_DO -> return TaskIntent.EXPLAIN_SCREEN
            null -> Unit
            else -> return TaskIntent.READ_SCREEN
        }
        if (NextStepQueryPhrases.classify(text) != null) return TaskIntent.EXPLAIN_SCREEN

        if (isOpenAppCommand(text)) return TaskIntent.OPEN_APP

        return TaskIntent.UNKNOWN
    }

    /** Matriz de seguridad de la misión: qué exige cada intención. */
    fun policyFor(intent: TaskIntent): SafetyClass = when (intent) {
        TaskIntent.READ_SCREEN,
        TaskIntent.EXPLAIN_SCREEN,
        TaskIntent.OPEN_APP,
        TaskIntent.OPEN_WHATSAPP_CHAT,
        TaskIntent.MONITOR_RIDE_STATUS,
        TaskIntent.START_WHATSAPP_AUDIO_FLOW,
        TaskIntent.UNKNOWN -> SafetyClass.SAFE

        TaskIntent.SEND_WHATSAPP_TEXT_PENDING_CONFIRMATION,
        TaskIntent.CONFIRM_SEND_WHATSAPP_TEXT,
        TaskIntent.START_WHATSAPP_VIDEO_CALL_PENDING_CONFIRMATION,
        TaskIntent.REQUEST_RIDE_PENDING_CONFIRMATION -> SafetyClass.CONFIRM_REQUIRED

        TaskIntent.GUIDE_PAYMENT_LINKING,
        TaskIntent.GUIDE_CARD_REGISTRATION -> SafetyClass.SENSITIVE_GUIDED

        TaskIntent.SENSITIVE_PAYMENT_BLOCK -> SafetyClass.FORBIDDEN_AUTOMATIC
    }

    /** "activame el audio en el chat de Marco" → flujo guiado de nota de voz. */
    fun isAudioFlowActivation(rawText: String): Boolean {
        val text = fold(rawText)
        if ("audio" !in text) return false
        return (text.contains("activa") || text.contains("activame") ||
            text.contains("prepara") || text.contains("preparame")) &&
            (text.contains("chat") || text.contains("whatsapp"))
    }

    private fun isOpenAppCommand(text: String): Boolean {
        val tokens = text.split(' ')
        return tokens.size in 2..5 &&
            tokens.first() in setOf("abri", "abrir", "abre", "abrime")
    }

    private fun fold(rawText: String): String =
        VoicePhraseNormalizer.normalizeForParser(rawText)
            .lowercase()
            .removeSpanishAccents()
            .replace(Regex("[¿?¡!.,;:]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
