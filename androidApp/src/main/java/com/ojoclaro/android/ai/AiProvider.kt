package com.ojoclaro.android.ai

/**
 * Contrato para cualquier proveedor de inteligencia del agente.
 *
 * Implementaciones esperadas:
 * - LocalRuleBasedAiProvider: reglas locales, sin internet.
 * - FutureCloudAiProvider: placeholder seguro.
 * - OpenAiProvider futuro: IA multimodal real, solo con consentimiento explícito.
 *
 * Este provider no debe:
 * - ejecutar acciones Android;
 * - guardar datos;
 * - enviar información sensible sin allowCloud + consentimiento;
 * - hablar directamente por TTS.
 */
interface AiProvider {
    suspend fun process(task: AiTask, context: AiContext): AiResult
}
