package com.ojoclaro.android.agent.estela

import com.ojoclaro.android.privacy.PrivacyGuard
import com.ojoclaro.android.voice.VoicePhraseNormalizer
import java.text.Normalizer
import java.util.Locale

class EstelaSafetyPolicy {

    fun evaluate(
        rawText: String,
        intent: EstelaIntent,
        plan: EstelaPlan?
    ): EstelaSafetyDecision {
        evaluateRawText(rawText).takeUnless { it.allowed }?.let { return it }

        return when (intent) {
            is EstelaIntent.OpenApp,
            EstelaIntent.ReadScreen,
            EstelaIntent.Help,
            EstelaIntent.Confirm,
            EstelaIntent.Cancel,
            is EstelaIntent.SearchYouTube,
            is EstelaIntent.OpenSpotify,
            EstelaIntent.DescribeEnvironment,
            EstelaIntent.ReadCameraText ->
                EstelaSafetyDecision(
                    allowed = true,
                    requiresConfirmation = plan?.requiresConfirmation == true
                )

            is EstelaIntent.OpenVisibleChat,
            is EstelaIntent.DialContact ->
                EstelaSafetyDecision(
                    allowed = true,
                    requiresConfirmation = true
                )

            is EstelaIntent.ComposeMessage -> {
                if (!PrivacyGuard.isSafeMessagePayload(intent.message)) {
                    EstelaSafetyDecision(
                        allowed = false,
                        requiresConfirmation = false,
                        blockedReason = "sensitive_message_payload",
                        safeAlternative = "No puedo preparar ese mensaje porque parece contener datos sensibles."
                    )
                } else {
                    EstelaSafetyDecision(
                        allowed = true,
                        requiresConfirmation = true
                    )
                }
            }

            is EstelaIntent.Unknown ->
                EstelaSafetyDecision(
                    allowed = true,
                    requiresConfirmation = false
                )
        }
    }

    fun evaluateRawText(rawText: String): EstelaSafetyDecision {
        val text = normalize(rawText)
        if (text.isBlank()) {
            return EstelaSafetyDecision(allowed = true, requiresConfirmation = false)
        }

        val isBlocked = BLOCKED_PATTERNS.any { it.containsMatchIn(text) }
        return if (isBlocked) {
            EstelaSafetyDecision(
                allowed = false,
                requiresConfirmation = false,
                blockedReason = "direct_sensitive_action",
                safeAlternative = BLOCKED_DIRECT_ACTION_TEXT
            )
        } else {
            EstelaSafetyDecision(allowed = true, requiresConfirmation = false)
        }
    }

    companion object {
        const val BLOCKED_DIRECT_ACTION_TEXT =
            "Todavía no ejecuto compras, pagos, envíos o llamadas directas. " +
                "Puedo ayudarte a preparar la acción y pedirte confirmación."

        private val diacriticRegex = Regex("\\p{Mn}+")

        private val BLOCKED_PATTERNS = listOf(
            Regex("\\b(?:compra|comprar|comprame|comprá|compralo|comprala|comprar)\\b"),
            Regex("\\b(?:paga|pagar|pagame|pagá|pagalo|pagala|transferi|transferir)\\b"),
            Regex("\\b(?:borra|borrar|elimina|eliminar|archiva|archivar)\\b"),
            Regex("\\b(?:manda|mandar|envia|enviar|envialo|mandalo)\\s+(?:el\\s+)?mensaje\\b"),
            Regex("\\b(?:llama|llamar)\\s+ahora\\b"),
            Regex("\\b(?:hace|hacer)\\s+la\\s+llamada\\b")
        )

        fun normalize(text: String): String {
            val lowered = VoicePhraseNormalizer.normalizeForParser(text)
                .lowercase(Locale("es", "AR"))
            return Normalizer.normalize(lowered, Normalizer.Form.NFD)
                .replace(diacriticRegex, "")
                .replace(Regex("\\s+"), " ")
                .trim()
                .trim('.', ',', ';', ':', '!', '?', '¿', '¡')
        }
    }
}
