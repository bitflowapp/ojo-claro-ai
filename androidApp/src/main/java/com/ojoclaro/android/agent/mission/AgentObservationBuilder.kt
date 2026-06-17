package com.ojoclaro.android.agent.mission

/**
 * Construye la observación local inmutable que ve el coordinator y, en forma
 * abstracta, el planner remoto.
 *
 * Privacidad por construcción: este builder NUNCA lee texto de pantalla.
 * Solo paquete activo + flags. Para PRIVATE_APP/SENSITIVE_APP/UNKNOWN no
 * existen public_actions; para SENSITIVE_APP la whitelist de herramientas se
 * recorta a {go_back, speak, ask_user, finish, fail}.
 */
class AgentObservationBuilder(
    private val readPackageName: () -> String?,
    private val isAccessibilityConnected: () -> Boolean,
    private val classify: (String?) -> AgentPrivacyClass = AgentPrivacyClassifier::classify
) {

    fun build(): AgentObservationSnapshot {
        val packageName = runCatching { readPackageName() }.getOrNull()
            ?.takeIf { it.isNotBlank() }
        val privacy = classify(packageName)
        val accessibilityReady = runCatching { isAccessibilityConnected() }.getOrDefault(false)

        return AgentObservationSnapshot(
            packageName = packageName,
            screenClass = screenClassFor(packageName, privacy),
            privacyClass = privacy,
            availableTools = availableToolsFor(privacy),
            publicActions = emptyList(),
            visibleItemCount = 0,
            canOpenByOrdinal = false,
            canReadLocally = accessibilityReady
        )
    }

    private fun screenClassFor(packageName: String?, privacy: AgentPrivacyClass): String {
        val normalized = packageName?.lowercase().orEmpty()
        return when {
            normalized.contains("whatsapp") -> "WHATSAPP"
            normalized == "com.android.settings" -> "SETTINGS"
            normalized == "com.ojoclaro.android" -> "OWN_APP"
            privacy == AgentPrivacyClass.SENSITIVE_APP -> "SENSITIVE"
            normalized.isBlank() -> "UNKNOWN"
            else -> "GENERIC"
        }
    }

    private fun availableToolsFor(privacy: AgentPrivacyClass): List<AgentToolName> =
        when (privacy) {
            AgentPrivacyClass.SENSITIVE_APP -> SENSITIVE_TOOLS
            else -> DEFAULT_TOOLS
        }

    companion object {
        /**
         * v1: activate_visible_action queda fuera de available_tools porque el
         * catálogo local arranca vacío. Cuando exista catálogo PUBLIC_UI real,
         * se agrega solo para esa clase.
         */
        private val DEFAULT_TOOLS: List<AgentToolName> = listOf(
            AgentToolName.CHECK_ACCESSIBILITY_STATUS,
            AgentToolName.CHECK_MICROPHONE_PERMISSION,
            AgentToolName.CHECK_BACKEND_HEALTH,
            AgentToolName.REMEMBER_ORIGIN_APP,
            AgentToolName.OPEN_APP,
            AgentToolName.RETURN_TO_ORIGIN,
            AgentToolName.READ_CURRENT_SCREEN_LOCAL,
            AgentToolName.LIST_VISIBLE_ACTIONS_LOCAL,
            AgentToolName.GO_BACK,
            AgentToolName.SCROLL_ACCESSIBILITY,
            AgentToolName.SPEAK,
            AgentToolName.ASK_USER,
            AgentToolName.FINISH,
            AgentToolName.FAIL,
            // Outdoor Guidance v1
            AgentToolName.CHECK_LOCATION_PERMISSION,
            AgentToolName.CHECK_LOCATION_SERVICES_ENABLED,
            AgentToolName.GET_CURRENT_LOCATION,
            AgentToolName.DESCRIBE_CURRENT_LOCATION,
            AgentToolName.START_OUTDOOR_GUIDANCE,
            AgentToolName.GET_ROUTE_PROGRESS,
            AgentToolName.STOP_OUTDOOR_GUIDANCE,
            AgentToolName.DESCRIBE_SCENE_ON_DEMAND
        )

        private val SENSITIVE_TOOLS: List<AgentToolName> = listOf(
            AgentToolName.GO_BACK,
            AgentToolName.SPEAK,
            AgentToolName.ASK_USER,
            AgentToolName.FINISH,
            AgentToolName.FAIL
        )
    }
}
