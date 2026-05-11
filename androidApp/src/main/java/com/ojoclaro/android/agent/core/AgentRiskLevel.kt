package com.ojoclaro.android.agent.core

/**
 * Nivel de riesgo de un AgentTool, plan o paso.
 *
 * Reglas:
 *  - NONE: acciones internas inocuas (REPEAT_LAST, ayuda).
 *  - LOW: abre app externa, prepara texto sin enviar.
 *  - MEDIUM: prepara mensaje con contenido del usuario; abre mapas/teléfono.
 *  - HIGH: lee pantalla; toca memoria; podría tocar datos personales.
 *  - BLOCKED: nunca se ejecuta. Bancos, pagos, contraseñas, salud sensible.
 */
enum class AgentRiskLevel {
    NONE,
    LOW,
    MEDIUM,
    HIGH,
    BLOCKED
}
