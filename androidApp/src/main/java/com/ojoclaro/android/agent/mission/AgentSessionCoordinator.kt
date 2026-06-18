package com.ojoclaro.android.agent.mission

import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.delay

/**
 * Coordinador del Agent Loop (Fase 3A).
 *
 * OBJETIVO → PLANIFICAR → EJECUTAR UNA HERRAMIENTA → OBSERVAR → VERIFICAR
 * → CONTINUAR O REPLANIFICAR → FINALIZAR.
 *
 * Garantías:
 *  - una sola misión activa (AtomicBoolean);
 *  - una respuesta vieja nunca modifica una sesión nueva (operationGeneration);
 *  - presupuestos duros: pasos, replans, duración;
 *  - no-progreso: misma acción + misma pantalla dos veces seguidas corta;
 *  - cada acción se verifica contra su poscondición observada;
 *  - cancelación limpia que vuelve a IDLE.
 *
 * Kotlin puro: reloj, observación, ejecución, TTS y STT entran inyectados.
 */
class AgentSessionCoordinator(
    private val planner: AgentPlannerClient,
    private val registry: AgentToolRegistry,
    private val policyGate: AgentPolicyGate,
    private val executor: AgentToolExecutor,
    private val observe: () -> AgentObservationSnapshot,
    private val speak: (String) -> Unit,
    private val requestUserReply: suspend (question: String) -> String?,
    private val log: (String) -> Unit = {},
    private val elapsedRealtime: () -> Long = { System.nanoTime() / 1_000_000L },
    private val sleep: suspend (Long) -> Unit = { delay(it) },
    private val newSessionId: () -> String = { UUID.randomUUID().toString().take(32) }
) {

    private val active = AtomicBoolean(false)
    private val generation = AtomicLong(0L)

    @Volatile
    private var cancellationRequested: Boolean = false

    @Volatile
    var missionState: AgentMissionState = AgentMissionState.IDLE
        private set

    val isActive: Boolean get() = active.get()

    /**
     * Cancela la misión en curso: invalida la generation (cualquier resultado
     * viejo se ignora) y marca el flag que el loop chequea en cada transición.
     */
    fun cancel(reason: String) {
        if (!active.get()) return
        cancellationRequested = true
        generation.incrementAndGet()
        log("cancelRequested=true reason=${reason.take(40)}")
    }

    suspend fun runMission(goal: String): AgentMissionOutcome {
        if (!active.compareAndSet(false, true)) {
            return AgentMissionOutcome(
                status = AgentMissionStatus.FAILED,
                spokenSummary = "Ya hay una misión en curso.",
                stepsExecuted = 0,
                replans = 0,
                errorCode = "mission_already_active"
            )
        }

        cancellationRequested = false
        val gen = generation.incrementAndGet()
        val startMillis = elapsedRealtime()
        val originObservation = safeObserve()

        var session = AgentSessionState(
            sessionId = newSessionId(),
            goal = goal.trim().take(500),
            originPackage = originObservation.packageName,
            currentPackage = originObservation.packageName,
            startedAtElapsedRealtime = startMillis,
            operationGeneration = gen,
            lastObservationFingerprint = originObservation.fingerprint
        )

        transition(AgentMissionState.LISTENING_FOR_GOAL, "goal_received", session)
        var lastSignature: String? = null
        var spokeProgress = false

        try {
            var observation = originObservation
            while (true) {
                if (isStale(gen)) return cancelledOutcome(session)
                if (elapsedRealtime() - startMillis > AgentMissionBudgets.MAX_DURATION_MILLIS) {
                    return failOutcome(
                        session,
                        "La misión tardó demasiado y la corté por seguridad.",
                        "time_budget_exhausted"
                    )
                }
                if (session.stepIndex >= session.maxSteps) {
                    return failOutcome(
                        session,
                        "Se agotó el presupuesto de pasos de la misión.",
                        "step_budget_exhausted"
                    )
                }
                if (session.replanCount > AgentMissionBudgets.MAX_REPLANS) {
                    return failOutcome(
                        session,
                        "No pude completar la tarea sin repetir acciones. Te devuelvo el control.",
                        "replan_budget_exhausted"
                    )
                }

                transition(AgentMissionState.PLANNING, "request_next_tool", session)
                val decision = planner.next(plannerRequest(session, observation))
                if (isStale(gen)) return cancelledOutcome(session)

                if (!decision.ok && decision.action == null) {
                    // Backend caído / sin configurar: degradación honesta. Las
                    // rutas locales no pasan por acá, así que siguen vivas.
                    speak(OFFLINE_DEGRADATION_TEXT)
                    return AgentMissionOutcome(
                        status = AgentMissionStatus.FAILED,
                        spokenSummary = OFFLINE_DEGRADATION_TEXT,
                        stepsExecuted = session.stepIndex,
                        replans = session.replanCount,
                        errorCode = decision.errorCode ?: "planner_unavailable"
                    ).also { transition(AgentMissionState.FAILED, it.errorCode ?: "planner_unavailable", session) }
                }

                transition(AgentMissionState.VALIDATING_ACTION, "tool=${decision.action?.tool?.wire}", session)
                val validation = registry.validate(decision.action, observation)
                val validated = when (validation) {
                    is AgentToolRegistry.Validation.Rejected -> {
                        log(
                            "toolRejected=true tool=${decision.action?.tool?.wire} " +
                                "reason=${validation.reason}"
                        )
                        session = session.recordResult(
                            AgentToolResult(
                                tool = decision.action?.tool ?: AgentToolName.FAIL,
                                status = AgentToolResultStatus.BLOCKED,
                                postconditionVerified = false,
                                failureReason = validation.reason
                            ),
                            observation
                        ).withReplan()
                        observation = safeObserve()
                        continue
                    }

                    is AgentToolRegistry.Validation.Valid -> validation.action
                }

                val policy = policyGate.authorize(validated, observation)
                logStep(session, validated, policy, observation)
                if (!policy.allowed) {
                    session = session.recordResult(
                        AgentToolResult(
                            tool = validated.tool,
                            status = AgentToolResultStatus.BLOCKED,
                            postconditionVerified = false,
                            failureReason = policy.reason
                        ),
                        observation
                    ).withReplan()
                    observation = safeObserve()
                    continue
                }

                // No-progreso: misma acción con la misma pantalla que el paso
                // anterior → no la repetimos a ciegas.
                val signature = validated.signature + "|" + observation.fingerprint
                if (signature == lastSignature) {
                    return failOutcome(
                        session,
                        "No pude completar la tarea sin repetir acciones. Te devuelvo el control.",
                        "no_progress_detected"
                    )
                }
                lastSignature = signature

                // Progreso hablado: solo al arrancar (primer paso CONTINUE).
                if (!spokeProgress && decision.status == AgentMissionStatus.CONTINUE) {
                    decision.spokenProgress?.let {
                        transition(AgentMissionState.SPEAKING_PROGRESS, "mission_start", session)
                        speak(it)
                    }
                    spokeProgress = true
                }

                when (decision.status) {
                    AgentMissionStatus.COMPLETED -> {
                        transition(AgentMissionState.EXECUTING_ACTION, "finish", session)
                        executor.execute(validated, session)
                        val summary = validated.stringArg("summary").orEmpty()
                        transition(AgentMissionState.COMPLETED, "finish_spoken", session)
                        return AgentMissionOutcome(
                            status = AgentMissionStatus.COMPLETED,
                            spokenSummary = summary,
                            stepsExecuted = session.stepIndex + 1,
                            replans = session.replanCount
                        )
                    }

                    AgentMissionStatus.FAILED -> {
                        transition(AgentMissionState.EXECUTING_ACTION, "fail", session)
                        executor.execute(validated, session)
                        val message = validated.stringArg("message").orEmpty()
                        transition(AgentMissionState.FAILED, "fail_spoken", session)
                        return AgentMissionOutcome(
                            status = AgentMissionStatus.FAILED,
                            spokenSummary = message,
                            stepsExecuted = session.stepIndex + 1,
                            replans = session.replanCount,
                            errorCode = decision.errorCode
                        )
                    }

                    AgentMissionStatus.NEED_USER -> {
                        val question = validated.stringArg("question").orEmpty()
                        transition(AgentMissionState.WAITING_FOR_USER, "ask_user", session)
                        speak(question)
                        val reply = requestUserReply(question)
                        if (isStale(gen)) return cancelledOutcome(session)
                        if (reply.isNullOrBlank()) {
                            return failOutcome(
                                session,
                                "No escuché tu respuesta. Misión pausada; podés pedirla de nuevo.",
                                "user_reply_timeout"
                            )
                        }
                        if (AgentMissionPhrases.isCancelCommand(reply)) {
                            cancellationRequested = true
                            return cancelledOutcome(session)
                        }
                        session = session.recordResult(
                            AgentToolResult(
                                tool = AgentToolName.ASK_USER,
                                status = AgentToolResultStatus.SUCCESS,
                                postconditionVerified = true,
                                userReply = reply.take(240)
                            ),
                            observation
                        )
                        observation = safeObserve()
                        continue
                    }

                    AgentMissionStatus.CONTINUE -> {
                        transition(AgentMissionState.EXECUTING_ACTION, validated.tool.wire, session)
                        val rawResult = executor.execute(validated, session)
                        if (isStale(gen)) return cancelledOutcome(session)

                        transition(AgentMissionState.WAITING_FOR_OBSERVATION, validated.tool.wire, session)
                        val (verified, after) = verifyPostcondition(validated, rawResult, session)
                        observation = after
                        val result = rawResult.copy(
                            postconditionVerified = rawResult.postconditionVerified || verified
                        )
                        log(
                            "toolResult=${result.status} tool=${validated.tool.wire} " +
                                "postconditionVerified=${result.postconditionVerified}"
                        )

                        session = session.recordResult(result, observation)
                        if (result.status != AgentToolResultStatus.SUCCESS ||
                            !result.postconditionVerified
                        ) {
                            session = session.withReplan()
                        }
                    }

                    AgentMissionStatus.CANCELLED -> return cancelledOutcome(session)
                }
            }
        } finally {
            active.set(false)
            cancellationRequested = false
            if (missionState !in TERMINAL_STATES) {
                transition(AgentMissionState.IDLE, "cleanup", session)
            } else {
                missionState = AgentMissionState.IDLE
            }
        }
    }

    // --- internos ---

    private fun plannerRequest(
        session: AgentSessionState,
        observation: AgentObservationSnapshot
    ): AgentPlannerRequest = AgentPlannerRequest(
        sessionId = session.sessionId,
        goal = session.goal,
        stepIndex = session.stepIndex,
        originPackage = session.originPackage,
        observation = observation,
        completedSteps = session.completedSteps,
        lastToolResult = session.lastToolResult,
        remainingStepBudget = (session.maxSteps - session.stepIndex).coerceAtLeast(0),
        replanCount = session.replanCount
    )

    /**
     * Verifica la poscondición observada de las herramientas de navegación,
     * esperando (con poll corto) a que la ventana real cambie. Para el resto,
     * el resultado del executor ya trae la verificación local.
     */
    private suspend fun verifyPostcondition(
        action: AgentValidatedAction,
        result: AgentToolResult,
        session: AgentSessionState
    ): Pair<Boolean, AgentObservationSnapshot> {
        val expectedPackages: Set<String>? = when (action.tool) {
            AgentToolName.RETURN_TO_ORIGIN ->
                session.originPackage?.let { setOf(it) }

            AgentToolName.OPEN_APP -> when (action.stringArg("app_id")) {
                "WHATSAPP" -> setOf("com.whatsapp")
                "WHATSAPP_BUSINESS" -> setOf("com.whatsapp.w4b")
                "SETTINGS" -> setOf("com.android.settings")
                "OJO_CLARO" -> setOf("com.ojoclaro.android")
                else -> null
            }

            else -> null
        }

        if (expectedPackages == null) {
            return result.postconditionVerified to safeObserve()
        }
        if (result.status != AgentToolResultStatus.SUCCESS) {
            return false to safeObserve()
        }

        var waited = 0L
        while (waited <= AgentMissionBudgets.POSTCONDITION_MAX_WAIT_MILLIS) {
            val current = safeObserve()
            if (current.packageName in expectedPackages) {
                return true to current
            }
            sleep(AgentMissionBudgets.POSTCONDITION_POLL_MILLIS)
            waited += AgentMissionBudgets.POSTCONDITION_POLL_MILLIS
        }
        return false to safeObserve()
    }

    private fun AgentSessionState.recordResult(
        result: AgentToolResult,
        observation: AgentObservationSnapshot
    ): AgentSessionState = copy(
        stepIndex = stepIndex + 1,
        currentPackage = observation.packageName,
        completedSteps = (completedSteps + AgentCompletedStep(result.tool, result.status)).takeLast(16),
        lastObservationFingerprint = observation.fingerprint,
        lastToolResult = result
    )

    private fun AgentSessionState.withReplan(): AgentSessionState =
        copy(replanCount = replanCount + 1)

    private fun isStale(gen: Long): Boolean =
        cancellationRequested || gen != generation.get()

    private fun safeObserve(): AgentObservationSnapshot =
        runCatching { observe() }.getOrElse {
            AgentObservationSnapshot(
                packageName = null,
                screenClass = "UNKNOWN",
                privacyClass = AgentPrivacyClass.UNKNOWN,
                availableTools = listOf(
                    AgentToolName.SPEAK,
                    AgentToolName.ASK_USER,
                    AgentToolName.FINISH,
                    AgentToolName.FAIL
                ),
                canReadLocally = false
            )
        }

    /**
     * El "Misión cancelada." hablado lo emite UNA sola voz: el caller (service),
     * que también cubre la cancelación dura por job cancellation. Acá solo
     * transicionamos y devolvemos el outcome.
     */
    private fun cancelledOutcome(session: AgentSessionState): AgentMissionOutcome {
        transition(AgentMissionState.CANCELLED, "cancellation", session)
        return AgentMissionOutcome(
            status = AgentMissionStatus.CANCELLED,
            spokenSummary = CANCELLED_TEXT,
            stepsExecuted = session.stepIndex,
            replans = session.replanCount
        )
    }

    private fun failOutcome(
        session: AgentSessionState,
        spoken: String,
        errorCode: String
    ): AgentMissionOutcome {
        transition(AgentMissionState.FAILED, errorCode, session)
        speak(spoken)
        return AgentMissionOutcome(
            status = AgentMissionStatus.FAILED,
            spokenSummary = spoken,
            stepsExecuted = session.stepIndex,
            replans = session.replanCount,
            errorCode = errorCode
        )
    }

    private fun transition(
        state: AgentMissionState,
        cause: String,
        session: AgentSessionState
    ) {
        missionState = state
        log(
            "state=$state cause=${cause.take(60)} sessionIdHash=${session.sessionId.hashCode()} " +
                "stepIndex=${session.stepIndex} replanCount=${session.replanCount}"
        )
    }

    private fun logStep(
        session: AgentSessionState,
        action: AgentValidatedAction,
        policy: AgentPolicyGate.Decision,
        observation: AgentObservationSnapshot
    ) {
        log(
            "sessionIdHash=${session.sessionId.hashCode()} stepIndex=${session.stepIndex} " +
                "tool=${action.tool.wire} toolAllowed=${policy.allowed} policy=${policy.policy} " +
                "currentPackage=${observation.packageName ?: "-"} " +
                "privacyClass=${observation.privacyClass} privateContentSent=false " +
                "replanCount=${session.replanCount}"
        )
    }

    companion object {
        const val OFFLINE_DEGRADATION_TEXT: String =
            "El modo agente necesita conexión, pero todavía puedo usar las funciones locales."
        const val CANCELLED_TEXT: String = "Misión cancelada."

        private val TERMINAL_STATES = setOf(
            AgentMissionState.COMPLETED,
            AgentMissionState.FAILED,
            AgentMissionState.CANCELLED
        )
    }
}
