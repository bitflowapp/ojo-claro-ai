package com.ojoclaro.android.global

import com.ojoclaro.android.external.ExternalActionEvent
import com.ojoclaro.android.voice.VoicePhraseNormalizer

enum class ExternalAppName(val spokenName: String) {
    WHATSAPP("WhatsApp"),
    MAPS("Maps"),
    PHONE("Telefono"),
    UNKNOWN("app externa");

    companion object {
        fun fromHandoffName(value: String): ExternalAppName {
            val normalized = VoicePhraseNormalizer.normalizeForParser(value)
                .lowercase()
                .trim()
            return when {
                "whatsapp" in normalized -> WHATSAPP
                "maps" in normalized || "mapas" in normalized -> MAPS
                "telefono" in normalized || "teléfono" in normalized -> PHONE
                else -> UNKNOWN
            }
        }
    }
}

enum class GlobalAssistantAction {
    START,
    LISTEN,
    SILENCE,
    STOP,
    EXPIRE
}

object GlobalAssistantMode {
    const val TTL_MILLIS: Long = 60_000L
    const val CHANNEL_ID = "ojo_claro_global_assistant"
    const val NOTIFICATION_ID = 20260507

    const val ACTION_START = "com.ojoclaro.android.global.ACTION_START"
    const val ACTION_LISTEN = "com.ojoclaro.android.global.ACTION_LISTEN"
    const val ACTION_SILENCE = "com.ojoclaro.android.global.ACTION_SILENCE"
    const val ACTION_STOP = "com.ojoclaro.android.global.ACTION_STOP"

    const val EXTRA_EXTERNAL_APP_NAME = "external_app_name"
    const val EXTRA_REASON = "reason"
    const val EXTRA_RETURN_HINT = "return_hint"
    const val EXTRA_EXPECT_WHATSAPP_ACTION = "expect_whatsapp_action"
    const val EXTRA_START_LISTENING_DELAY_MS = "start_listening_delay_ms"

    const val WHATSAPP_CONTINUATION_TEXT =
        "Abro WhatsApp. Puedo seguir por unos segundos. Decime el chat o el mensaje."
    const val BACKGROUND_MIC_FALLBACK =
        "Para seguir, toca Escuchar o volve a Ojo Claro."
    const val EXPIRED_TEXT =
        "Modo Ojo Claro pausado."

    fun shouldExpectWhatsAppAction(handoff: ExternalActionEvent.ExternalAppHandoff): Boolean =
        handoff.delegate == ExternalActionEvent.OpenWhatsApp &&
            handoff.spokenText.contains("chat", ignoreCase = true) &&
            handoff.spokenText.contains("mensaje", ignoreCase = true)

    fun isStrictConfirmation(text: String): Boolean {
        val normalized = VoicePhraseNormalizer.normalizeForParser(text)
            .lowercase()
            .trim()
        return normalized in setOf("confirmar", "confirmo", "aceptar")
    }
}
