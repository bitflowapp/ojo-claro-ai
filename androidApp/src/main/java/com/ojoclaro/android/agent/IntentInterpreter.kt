package com.ojoclaro.android.agent

/**
 * Contrato común para cualquier interpretador de intenciones.
 *
 * Hoy hay una única implementación: [LocalIntentParser], 100% determinista,
 * basada en regex y conjuntos de frases.
 *
 * En una fase futura se podrá agregar:
 *  - `AiIntentInterpreter`: solo usado cuando la confianza local es baja.
 *    Filtra entrada con [com.ojoclaro.android.privacy.PrivacyGuard] antes de
 *    salir a cloud y nunca ejecuta acciones por sí mismo. Cualquier intención
 *    propuesta debe pasar por [SafetyPolicy.gate] antes de ser aceptada.
 *
 * Reglas duras del contrato:
 *  - Una implementación NO puede ejecutar acciones, ni hablar, ni persistir.
 *  - Solo devuelve un [ParsedAgentIntent] que incluye intent, slots,
 *    confianza, slots faltantes y si requiere confirmación.
 *  - Es responsabilidad del orquestador y del manager de conversación
 *    decidir qué hacer con ese resultado.
 */
fun interface IntentInterpreter {
    fun parse(rawText: String): ParsedAgentIntent
}
