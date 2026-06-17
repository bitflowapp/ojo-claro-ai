package com.ojoclaro.android.ai

import com.ojoclaro.shared.model.ConfidenceLevel

/**
 * Placeholder para futura IA cloud (OpenAI, Gemini, Claude, etc.).
 *
 * IMPORTANTE:
 * Este provider NO llama APIs reales.
 * No tiene cliente HTTP.
 * No transmite imágenes, OCR, texto visible ni memoria.
 * No consume claves.
 *
 * Cuando se conecte IA real, debe pasar antes por:
 * - consentimiento explícito del usuario;
 * - PrivacyGuard;
 * - allowCloud = true;
 * - timeouts;
 * - manejo de errores;
 * - redacción de datos sensibles;
 * - confirmación antes de acciones sensibles.
 */
class FutureCloudAiProvider : AiProvider {

    override suspend fun process(task: AiTask, context: AiContext): AiResult {
        return AiResult(
            spokenText = NOT_CONFIGURED_TEXT,
            confidence = ConfidenceLevel.LOW,
            safetyNotice = SAFETY_NOTICE
        )
    }

    companion object {
        const val NOT_CONFIGURED_TEXT =
            "La IA avanzada todavía no está activada. Por ahora puedo ayudarte con lectura local y acciones básicas."

        const val SAFETY_NOTICE =
            "La IA en la nube no está conectada."
    }
}
