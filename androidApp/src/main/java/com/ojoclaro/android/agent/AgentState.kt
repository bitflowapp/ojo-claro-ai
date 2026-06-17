package com.ojoclaro.android.agent

import com.ojoclaro.android.model.AppState

enum class AgentState {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING,
    WAITING_CONFIRMATION,
    WAITING_CONTACT,
    WAITING_MESSAGE,
    WAITING_PHONE_NUMBER,
    WAITING_DESTINATION,
    WAITING_LOCATION_ALIAS,
    WAITING_WHATSAPP_ACTION,
    WAITING_WHATSAPP_CHAT_OR_MESSAGE,
    WAITING_TIME,
    WAITING_FREQUENCY,
    WAITING_PERMISSION,
    SAFE_MODE,
    ERROR_RECOVERABLE,
    STOPPED_BY_USER
}

fun AppState.toAgentState(): AgentState = when (this) {
    AppState.IDLE -> AgentState.IDLE
    AppState.LISTENING -> AgentState.LISTENING
    AppState.SCANNING,
    AppState.PROCESSING,
    AppState.EXTERNAL_APP_HANDOFF,
    AppState.GLOBAL_ASSISTANT_ACTIVE,
    AppState.GLOBAL_ASSISTANT_UNAVAILABLE -> AgentState.PROCESSING
    AppState.SPEAKING -> AgentState.SPEAKING
    AppState.WAITING_WHATSAPP_ACTION -> AgentState.WAITING_WHATSAPP_ACTION
    AppState.WAITING_WHATSAPP_CHAT_OR_MESSAGE -> AgentState.WAITING_WHATSAPP_CHAT_OR_MESSAGE
    AppState.WAITING_CONTACT -> AgentState.WAITING_CONTACT
    AppState.WAITING_MESSAGE -> AgentState.WAITING_MESSAGE
    AppState.WAITING_CONFIRMATION -> AgentState.WAITING_CONFIRMATION
    AppState.PERMISSION_REQUIRED -> AgentState.WAITING_PERMISSION
    AppState.ERROR -> AgentState.ERROR_RECOVERABLE
}

fun AgentState.toAppState(): AppState = when (this) {
    AgentState.IDLE -> AppState.IDLE
    AgentState.LISTENING -> AppState.LISTENING
    AgentState.PROCESSING -> AppState.PROCESSING
    AgentState.SPEAKING -> AppState.SPEAKING
    // Slot-fill (WAITING_CONTACT/MESSAGE/etc.) son estados conversacionales,
    // NO errores. La UI debe pintarlos como "esperando" igual que WAITING_CONFIRMATION
    // para no confundir a un usuario ciego con un mensaje de error inexistente.
    AgentState.WAITING_CONFIRMATION -> AppState.WAITING_CONFIRMATION
    AgentState.WAITING_WHATSAPP_ACTION -> AppState.WAITING_WHATSAPP_ACTION
    AgentState.WAITING_WHATSAPP_CHAT_OR_MESSAGE -> AppState.WAITING_WHATSAPP_CHAT_OR_MESSAGE
    AgentState.WAITING_CONTACT -> AppState.WAITING_CONTACT
    AgentState.WAITING_MESSAGE -> AppState.WAITING_MESSAGE
    AgentState.WAITING_PHONE_NUMBER,
    AgentState.WAITING_DESTINATION,
    AgentState.WAITING_LOCATION_ALIAS,
    AgentState.WAITING_TIME,
    AgentState.WAITING_FREQUENCY -> AppState.WAITING_CONFIRMATION
    AgentState.WAITING_PERMISSION -> AppState.PERMISSION_REQUIRED
    AgentState.SAFE_MODE,
    AgentState.ERROR_RECOVERABLE -> AppState.ERROR
    AgentState.STOPPED_BY_USER -> AppState.IDLE
}
