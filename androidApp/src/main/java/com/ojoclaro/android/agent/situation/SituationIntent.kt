package com.ojoclaro.android.agent.situation

/**
 * Intención de alto nivel del Situation Brain.
 *
 * Es una capa por encima del AgentIntent existente: el IntentClassifier (Fase 2)
 * mapeará cada AgentIntent del LocalIntentParser a uno de estos grupos para que
 * el Brain razone sobre "qué quiere lograr el usuario", no sobre el texto crudo.
 *
 *  - READ_SCREEN: leer el texto visible de la pantalla.
 *  - SUMMARIZE_SCREEN: resumir la pantalla en vez de leerla completa.
 *  - EXPLAIN_WHAT_I_SEE: explicar dónde está parado el usuario / qué app es.
 *  - OPEN_APP: abrir una app externa (WhatsApp, Maps, etc.).
 *  - WRITE_MESSAGE: preparar un mensaje (nunca enviarlo solo).
 *  - CALL_CONTACT: abrir el marcador con un contacto (nunca llamar solo).
 *  - GUIDE_USER: guiar paso a paso un flujo (ej. WhatsApp guiado).
 *  - HELP_ME_WORK: activar el Modo Compañero.
 *  - MANAGE_MEMORY: recordar / listar / olvidar memoria, contactos o lugares.
 *  - CONTROL: comandos de control (confirmar, cancelar, repetir, callar).
 *  - EMERGENCY_STOP: corte duro inmediato de todo.
 *  - UNKNOWN: no se entendió; va a fallback.
 *  - UNSAFE_REQUEST: rechazado por SafetyPolicy / RiskDetector.
 */
enum class SituationIntent {
    READ_SCREEN,
    SUMMARIZE_SCREEN,
    EXPLAIN_WHAT_I_SEE,
    OPEN_APP,
    WRITE_MESSAGE,
    CALL_CONTACT,
    GUIDE_USER,
    HELP_ME_WORK,
    MANAGE_MEMORY,
    CONTROL,
    EMERGENCY_STOP,
    UNKNOWN,
    UNSAFE_REQUEST
}
