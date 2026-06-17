package com.ojoclaro.android.model

/**
 * Estados de alto nivel de la app.
 *
 * Cada estado tiene una salida clara para el usuario:
 *  - IDLE: tocar cualquier botón.
 *  - LISTENING: tocar Callar.
 *  - SCANNING: tocar Callar y volver.
 *  - PROCESSING: tocar Callar.
 *  - SPEAKING: tocar Callar.
 *  - WAITING_CONFIRMATION: decir "confirmar" o "cancelar".
 *  - EXTERNAL_APP_HANDOFF: volver con tile, botón de accesibilidad o notificación.
 *  - PERMISSION_REQUIRED: abrir Ajustes o tocar Volver.
 *  - ERROR: tocar Callar (vuelve a IDLE).
 */
enum class AppState {
    IDLE,
    LISTENING,
    SCANNING,
    PROCESSING,
    SPEAKING,
    WAITING_WHATSAPP_ACTION,
    WAITING_WHATSAPP_CHAT_OR_MESSAGE,
    WAITING_CONTACT,
    WAITING_MESSAGE,
    WAITING_CONFIRMATION,
    EXTERNAL_APP_HANDOFF,
    GLOBAL_ASSISTANT_ACTIVE,
    GLOBAL_ASSISTANT_UNAVAILABLE,
    PERMISSION_REQUIRED,
    ERROR
}
