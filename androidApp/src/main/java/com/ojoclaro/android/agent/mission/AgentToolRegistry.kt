package com.ojoclaro.android.agent.mission

/**
 * Registro local tipado de herramientas.
 *
 * Valida nombre + argumentos + precondiciones de la acción que propuso el
 * planner ANTES del policy gate y de la ejecución. Rechaza action_id vencidos
 * o de otra pantalla. No ejecuta nada.
 */
class AgentToolRegistry(
    private val catalog: AgentActionCatalog = AgentActionCatalog()
) {

    sealed class Validation {
        data class Valid(val action: AgentValidatedAction) : Validation()
        data class Rejected(val reason: String) : Validation()
    }

    fun validate(
        action: AgentPlannedAction?,
        observation: AgentObservationSnapshot
    ): Validation {
        if (action == null) return Validation.Rejected("missing_action")
        val args = action.arguments

        return when (action.tool) {
            AgentToolName.CHECK_ACCESSIBILITY_STATUS,
            AgentToolName.CHECK_MICROPHONE_PERMISSION,
            AgentToolName.RETURN_TO_ORIGIN,
            AgentToolName.GO_BACK,
            AgentToolName.CHECK_LOCATION_PERMISSION,
            AgentToolName.CHECK_LOCATION_SERVICES_ENABLED,
            AgentToolName.GET_CURRENT_LOCATION,
            AgentToolName.DESCRIBE_CURRENT_LOCATION,
            AgentToolName.GET_ROUTE_PROGRESS,
            AgentToolName.STOP_OUTDOOR_GUIDANCE,
            AgentToolName.DESCRIBE_SCENE_ON_DEMAND -> valid(action.tool)

            AgentToolName.START_OUTDOOR_GUIDANCE -> {
                val destination = sanitizedText(args["destination"])
                if (destination.length < 3) {
                    Validation.Rejected("destination_missing")
                } else {
                    valid(action.tool, mapOf("destination" to destination.take(200)))
                }
            }

            AgentToolName.CHECK_BACKEND_HEALTH -> {
                val timeout = (args["timeout_ms"] as? Number)?.toInt() ?: 5_000
                valid(action.tool, mapOf("timeout_ms" to timeout.coerceIn(500, 10_000)))
            }

            AgentToolName.REMEMBER_ORIGIN_APP -> {
                val requested = (args["package_name"] as? String)?.trim().orEmpty()
                val observed = observation.packageName.orEmpty()
                when {
                    requested.isBlank() -> Validation.Rejected("package_name_missing")
                    // Solo paquetes realmente observados: nada inventado por el modelo.
                    requested != observed ->
                        Validation.Rejected("package_not_observed")
                    else -> valid(action.tool, mapOf("package_name" to requested))
                }
            }

            AgentToolName.OPEN_APP -> {
                val appId = (args["app_id"] as? String)?.trim()?.uppercase().orEmpty()
                if (appId in OPEN_APP_WHITELIST) {
                    valid(action.tool, mapOf("app_id" to appId))
                } else {
                    Validation.Rejected("app_id_not_whitelisted")
                }
            }

            AgentToolName.READ_CURRENT_SCREEN_LOCAL -> {
                val mode = (args["mode"] as? String)?.trim()?.uppercase().orEmpty()
                if (mode in READ_MODES) {
                    valid(action.tool, mapOf("mode" to mode))
                } else {
                    Validation.Rejected("read_mode_invalid")
                }
            }

            AgentToolName.LIST_VISIBLE_ACTIONS_LOCAL -> {
                val category = (args["category"] as? String)?.trim()?.uppercase().orEmpty()
                if (category in LIST_CATEGORIES) {
                    valid(action.tool, mapOf("category" to category))
                } else {
                    Validation.Rejected("category_invalid")
                }
            }

            AgentToolName.ACTIVATE_VISIBLE_ACTION -> {
                val actionId = (args["action_id"] as? String)?.trim().orEmpty()
                when {
                    actionId.isBlank() -> Validation.Rejected("action_id_missing")
                    // El ID debe existir en el catálogo emitido para la pantalla
                    // ACTUAL (fingerprint). Cambió la ventana → ID vencido.
                    !catalog.isValidFor(actionId, observation.fingerprint) ->
                        Validation.Rejected("action_id_expired_or_unknown")
                    else -> valid(action.tool, mapOf("action_id" to actionId))
                }
            }

            AgentToolName.SCROLL_ACCESSIBILITY -> {
                val direction = (args["direction"] as? String)?.trim()?.uppercase().orEmpty()
                if (direction in SCROLL_DIRECTIONS) {
                    valid(action.tool, mapOf("direction" to direction))
                } else {
                    Validation.Rejected("direction_invalid")
                }
            }

            AgentToolName.SPEAK -> requireText(action.tool, args, "message")

            AgentToolName.ASK_USER -> {
                val question = sanitizedText(args["question"])
                val reason = (args["reason"] as? String)?.trim()?.uppercase().orEmpty()
                when {
                    question.isBlank() -> Validation.Rejected("question_missing")
                    reason !in ASK_REASONS -> Validation.Rejected("reason_invalid")
                    else -> valid(
                        action.tool,
                        mapOf("question" to question, "reason" to reason)
                    )
                }
            }

            AgentToolName.FINISH -> requireText(action.tool, args, "summary")

            AgentToolName.FAIL -> {
                val message = sanitizedText(args["message"])
                val recoverable = args["recoverable"] as? Boolean
                when {
                    message.isBlank() -> Validation.Rejected("message_missing")
                    recoverable == null -> Validation.Rejected("recoverable_missing")
                    else -> valid(
                        action.tool,
                        mapOf("message" to message, "recoverable" to recoverable)
                    )
                }
            }
        }
    }

    private fun requireText(
        tool: AgentToolName,
        args: Map<String, Any?>,
        key: String
    ): Validation {
        val text = sanitizedText(args[key])
        return if (text.isBlank()) {
            Validation.Rejected("${key}_missing")
        } else {
            valid(tool, mapOf(key to text))
        }
    }

    private fun sanitizedText(raw: Any?): String =
        (raw as? String).orEmpty()
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(AgentMissionBudgets.MAX_SPOKEN_CHARS)

    private fun valid(
        tool: AgentToolName,
        args: Map<String, Any?> = emptyMap()
    ): Validation = Validation.Valid(AgentValidatedAction(tool, args))

    companion object {
        val OPEN_APP_WHITELIST = setOf("WHATSAPP", "WHATSAPP_BUSINESS", "SETTINGS", "OJO_CLARO")
        val READ_MODES = setOf("SUMMARY", "VISIBLE_ACTIONS", "VISIBLE_MESSAGES")
        val LIST_CATEGORIES = setOf("GENERAL", "SETTINGS")
        val SCROLL_DIRECTIONS = setOf("FORWARD", "BACKWARD")
        val ASK_REASONS = setOf("AMBIGUOUS", "CONFIRMATION_REQUIRED", "MANUAL_ACTION_REQUIRED")
    }
}

/**
 * Catálogo en memoria de acciones públicas con action_id efímero.
 *
 * v1 arranca vacío: nadie emite IDs, así que activate_visible_action siempre
 * se rechaza. Cuando se implemente el catálogo PUBLIC_UI real, los IDs se
 * emiten ligados al fingerprint de la observación y vencen al cambiar la
 * ventana o por TTL.
 */
class AgentActionCatalog(
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS
) {
    private data class Entry(
        val actionId: String,
        val observationFingerprint: String,
        val issuedAtMillis: Long
    )

    private val entries = LinkedHashMap<String, Entry>()

    @Synchronized
    fun publish(actionId: String, observationFingerprint: String) {
        entries[actionId] = Entry(actionId, observationFingerprint, nowMillis())
    }

    @Synchronized
    fun invalidateAll() {
        entries.clear()
    }

    @Synchronized
    fun isValidFor(actionId: String, observationFingerprint: String): Boolean {
        val entry = entries[actionId] ?: return false
        if (entry.observationFingerprint != observationFingerprint) return false
        return nowMillis() - entry.issuedAtMillis <= ttlMillis
    }

    private companion object {
        const val DEFAULT_TTL_MILLIS: Long = 15_000L
    }
}
