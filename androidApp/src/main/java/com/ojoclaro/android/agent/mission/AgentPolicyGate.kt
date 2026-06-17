package com.ojoclaro.android.agent.mission

/**
 * Política de seguridad del Agent Core v1.
 *
 * GPT propone; este gate autoriza o rechaza ANTES de ejecutar. Defensa en
 * profundidad: aunque el backend ya valida la whitelist, Android decide solo.
 *
 * Clasificación:
 *  - SAFE_READ: consultas de estado y lecturas locales. Siempre permitidas
 *    (la lectura local respeta sus propios filtros de pantalla sensible).
 *  - SAFE_NAVIGATION: abrir app whitelisteada, volver, back, scroll.
 *  - USER_CONFIRMATION: cambios de estado visibles. En v1 NO se ejecutan
 *    automáticamente: el gate los devuelve como no permitidos con causa.
 *  - BLOCKED: nunca en v1.
 */
class AgentPolicyGate {

    data class Decision(
        val allowed: Boolean,
        val policy: AgentActionPolicy,
        val reason: String
    )

    fun classify(tool: AgentToolName): AgentActionPolicy = when (tool) {
        AgentToolName.CHECK_ACCESSIBILITY_STATUS,
        AgentToolName.CHECK_MICROPHONE_PERMISSION,
        AgentToolName.CHECK_BACKEND_HEALTH,
        AgentToolName.REMEMBER_ORIGIN_APP,
        AgentToolName.READ_CURRENT_SCREEN_LOCAL,
        AgentToolName.LIST_VISIBLE_ACTIONS_LOCAL,
        AgentToolName.SPEAK,
        AgentToolName.ASK_USER,
        AgentToolName.FINISH,
        AgentToolName.FAIL -> AgentActionPolicy.SAFE_READ

        // Outdoor: consultas y descripciones son lectura segura. La captura de
        // escena llega acá solo si el USUARIO la pidió (la misión es su pedido).
        AgentToolName.CHECK_LOCATION_PERMISSION,
        AgentToolName.CHECK_LOCATION_SERVICES_ENABLED,
        AgentToolName.GET_CURRENT_LOCATION,
        AgentToolName.DESCRIBE_CURRENT_LOCATION,
        AgentToolName.GET_ROUTE_PROGRESS,
        AgentToolName.DESCRIBE_SCENE_ON_DEMAND -> AgentActionPolicy.SAFE_READ

        AgentToolName.OPEN_APP,
        AgentToolName.RETURN_TO_ORIGIN,
        AgentToolName.GO_BACK,
        AgentToolName.SCROLL_ACCESSIBILITY,
        AgentToolName.START_OUTDOOR_GUIDANCE,
        AgentToolName.STOP_OUTDOOR_GUIDANCE -> AgentActionPolicy.SAFE_NAVIGATION

        // Activar un control público cambia estado visible: en v1 requiere
        // confirmación y por lo tanto no se auto-ejecuta.
        AgentToolName.ACTIVATE_VISIBLE_ACTION -> AgentActionPolicy.USER_CONFIRMATION
    }

    fun authorize(
        action: AgentValidatedAction,
        observation: AgentObservationSnapshot
    ): Decision {
        val policy = classify(action.tool)

        // Pantalla sensible: solo el subconjunto seguro, sin navegación que
        // pueda interactuar con la app sensible (salvo go_back para salir).
        if (observation.privacyClass == AgentPrivacyClass.SENSITIVE_APP &&
            action.tool !in SENSITIVE_ALLOWED
        ) {
            return Decision(
                allowed = false,
                policy = AgentActionPolicy.BLOCKED,
                reason = "sensitive_screen_restricts_tool"
            )
        }

        return when (policy) {
            AgentActionPolicy.SAFE_READ,
            AgentActionPolicy.SAFE_NAVIGATION ->
                Decision(allowed = true, policy = policy, reason = "allowed")

            AgentActionPolicy.USER_CONFIRMATION ->
                Decision(
                    allowed = false,
                    policy = policy,
                    reason = "needs_user_confirmation_v1"
                )

            AgentActionPolicy.BLOCKED ->
                Decision(allowed = false, policy = policy, reason = "blocked_v1")
        }
    }

    private companion object {
        val SENSITIVE_ALLOWED = setOf(
            AgentToolName.GO_BACK,
            AgentToolName.SPEAK,
            AgentToolName.ASK_USER,
            AgentToolName.FINISH,
            AgentToolName.FAIL
        )
    }
}
