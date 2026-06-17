package com.ojoclaro.android.agent.situation

/**
 * Estados del Situation Brain / Cerebro de Situación.
 *
 * Fase 1: solo modelo. Esta enum NO está cableada todavía al flujo real de
 * producción. Es la futura "única fuente de verdad" que reemplazará a las
 * máquinas de estado paralelas (AppState / RobotSessionState / AgentState).
 *
 * Cada estado describe "qué está haciendo el asistente ahora mismo":
 *  - IDLE: en reposo, sin tarea activa.
 *  - LISTENING: micrófono abierto, esperando voz del usuario.
 *  - UNDERSTANDING: hay texto reconocido, se está clasificando la intención.
 *  - READING_SCREEN: leyendo / resumiendo la pantalla bajo pedido.
 *  - PLANNING: construyendo un plan (slot-filling o multi-paso).
 *  - WAITING_CONFIRMATION: hay una acción lista que requiere confirmación humana.
 *  - EXECUTING_GUIDED_ACTION: ejecutando un paso guiado o un handoff externo.
 *  - SPEAKING: reproduciendo una respuesta por TTS.
 *  - CANCELLED: el usuario canceló todo ("callate", "cancelá"). Estado de salida.
 *  - ERROR_RECOVERY: un handler falló de forma recuperable; se guía al usuario.
 */
enum class SituationState {
    IDLE,
    LISTENING,
    UNDERSTANDING,
    READING_SCREEN,
    PLANNING,
    WAITING_CONFIRMATION,
    EXECUTING_GUIDED_ACTION,
    SPEAKING,
    CANCELLED,
    ERROR_RECOVERY
}
