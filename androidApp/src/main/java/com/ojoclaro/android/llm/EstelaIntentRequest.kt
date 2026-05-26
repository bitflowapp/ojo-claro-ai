package com.ojoclaro.android.llm

data class EstelaIntentRequest(
    val userText: String,
    val conversationState: String,
    val pendingAction: EstelaPendingAction? = null,
    val installedApps: List<String> = emptyList(),
    val memoryContacts: List<String> = emptyList(),
    val activeApp: String? = null,
    val permissionsGranted: Map<String, Boolean> = emptyMap()
)

data class EstelaPendingAction(
    val intent: String,
    val params: Map<String, Any?> = emptyMap()
)

object EstelaIntentConfig {
    const val SYSTEM_PROMPT_ID: String = "OJO_CLARO_INTENT_ENGINE_SYSTEM"
    const val MODEL: String = LlmAgentClientConfig.DEFAULT_MODEL
}
