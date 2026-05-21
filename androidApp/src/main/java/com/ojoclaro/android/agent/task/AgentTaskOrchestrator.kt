package com.ojoclaro.android.agent.task

import com.ojoclaro.android.agent.apps.AppCapability
import com.ojoclaro.android.agent.apps.AppCapabilityRegistry
import com.ojoclaro.android.agent.apps.AppCapabilityType
import com.ojoclaro.android.agent.apps.InstalledAppResolver
import com.ojoclaro.android.agent.apps.SafeAppLaunchPlan
import com.ojoclaro.android.agent.apps.SafeAppLaunchResult
import com.ojoclaro.android.agent.apps.SafeAppLauncher
import com.ojoclaro.android.agent.command.ParsedCommand
import com.ojoclaro.android.agent.core.screen.StructuredScreenSnapshot
import com.ojoclaro.android.agent.task.action.AgentControlledActionMemory
import com.ojoclaro.android.agent.task.action.AgentControlledActionPlanner
import com.ojoclaro.android.agent.task.action.AgentControlledActionProposal
import com.ojoclaro.android.agent.task.action.AgentControlledActionRequest
import com.ojoclaro.android.agent.task.action.AgentControlledActionResult
import com.ojoclaro.android.agent.task.action.AgentControlledActionResultKind
import com.ojoclaro.android.agent.task.action.AgentControlledActionRisk
import com.ojoclaro.android.agent.task.action.AgentControlledActionType
import com.ojoclaro.android.agent.task.execution.AgentExecutionAuditEntry
import com.ojoclaro.android.agent.task.execution.AgentSafeExecutionGate
import com.ojoclaro.android.agent.task.execution.AgentSafeExecutionRequest
import com.ojoclaro.android.agent.task.execution.AgentSafeExecutionResult
import com.ojoclaro.android.agent.task.execution.AgentSafeExecutionStatus
import com.ojoclaro.android.agent.task.screen.AgentTaskScreenObservationType
import com.ojoclaro.android.agent.task.screen.AgentTaskScreenObserver
import com.ojoclaro.android.agent.task.screen.AgentTaskScreenUpdateResult

class AgentTaskOrchestrator(
    private val planner: AgentTaskPlanner = AgentTaskPlanner(),
    private val memory: AgentTaskMemory = AgentTaskMemory(),
    private val appCapabilityRegistry: AppCapabilityRegistry = AppCapabilityRegistry(),
    private val installedAppResolver: InstalledAppResolver = InstalledAppResolver.NONE,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val screenObserver: AgentTaskScreenObserver = AgentTaskScreenObserver(clock = clock),
    private val actionPlanner: AgentControlledActionPlanner = AgentControlledActionPlanner(clock = clock),
    private val actionMemory: AgentControlledActionMemory = AgentControlledActionMemory(clock = clock),
    private val safeExecutionGate: AgentSafeExecutionGate = AgentSafeExecutionGate(),
    /**
     * Paquete 6F -- launcher de apertura segura opcional. Con null (default de
     * produccion hoy), la apertura de apps sigue el handoff externo legacy. Si
     * se inyecta, OPEN_APP se ejecuta directo vvia SafeAppLauncher (tests y
     * wiring futuro). Nunca toca botones internos de la app.
     */
    private val safeAppLauncher: SafeAppLauncher? = null
) {

    private val executionAudits = ArrayDeque<AgentExecutionAuditEntry>()

    fun handle(
        rawUserCommand: String,
        parsedCommand: ParsedCommand? = null,
        currentScreenSnapshot: StructuredScreenSnapshot? = null,
        knownApps: List<AgentTaskKnownApp> = emptyList(),
        userPreferences: Map<String, String> = emptyMap(),
        hasPendingBridgeConfirmation: Boolean = false
    ): AgentTaskOrchestratorResult {
        val normalized = AgentTaskPlanner.normalize(rawUserCommand)
        val currentPlan = memory.currentPlan()

        if (isCancelActionProposalCommand(normalized)) {
            return handledFromActionResult(cancelCurrentActionProposal())
        }

        if (isCancelTaskCommand(normalized) && currentPlan != null) {
            val cancelled = memory.cancelCurrentPlan("user_cancelled")
            actionMemory.clearProposal()
            return AgentTaskOrchestratorResult.Handled(
                kind = AgentTaskOrchestratorResultKind.CANCELLED,
                spokenText = "Tarea ${cancelled?.title ?: currentPlan.title} cancelada.",
                plan = null
            )
        }

        if (isCancelTaskCommand(normalized)) {
            return AgentTaskOrchestratorResult.Handled(
                kind = AgentTaskOrchestratorResultKind.NO_ACTIVE_TASK,
                spokenText = "No hay una tarea activa para cancelar.",
                plan = null
            )
        }

        if (isStatusQuery(normalized)) {
            return if (currentPlan != null) {
                AgentTaskOrchestratorResult.Handled(
                    kind = AgentTaskOrchestratorResultKind.STATUS,
                    spokenText = currentPlan.operationalStatusSummary(),
                    plan = currentPlan
                )
            } else {
                AgentTaskOrchestratorResult.Handled(
                    kind = AgentTaskOrchestratorResultKind.NO_ACTIVE_TASK,
                    spokenText = "No hay una tarea activa.",
                    plan = null
                )
            }
        }

        if (isTaskScreenReviewCommand(normalized)) {
            return screenUpdateToHandled(
                result = observeScreenForCurrentTask(currentScreenSnapshot)
            )
        }

        if (isRideAppQuery(normalized)) {
            return handleRideAppQuery(currentPlan)
        }

        parseSafeExecutionCommand(normalized)?.let { execCommand ->
            val proposal = actionMemory.currentActionProposal()
            // Comandos ambiguos ("confirmo", "dale") se ceden al flujo de
            // confirmacion legacy/bridge si no hay propuesta o si hay una
            // confirmacion externa pendiente. Asi no secuestramos un
            // "confirmo" que pertenece al bridge.
            if (execCommand.deferToConfirmationFlow &&
                (proposal == null || hasPendingBridgeConfirmation)
            ) {
                return AgentTaskOrchestratorResult.NotHandled
            }
            return handledFromExecution(
                executeSafeAction(
                    userCommand = normalized,
                    currentPlan = currentPlan,
                    proposal = proposal,
                    hasPendingBridgeConfirmation = hasPendingBridgeConfirmation,
                    userConfirmedStrongly = execCommand.deferToConfirmationFlow
                )
            )
        }

        parseActionProposalRequest(normalized)?.let { request ->
            return handledFromActionResult(
                proposeControlledAction(
                    request = request,
                    currentPlan = currentPlan,
                    snapshot = currentScreenSnapshot,
                    hasPendingBridgeConfirmation = hasPendingBridgeConfirmation
                )
            )
        }

        val candidatePlan = planner.plan(
            rawUserCommand = rawUserCommand,
            parsedCommand = parsedCommand,
            currentScreenSnapshot = currentScreenSnapshot,
            knownApps = knownApps,
            userPreferences = userPreferences
        )

        if (candidatePlan.type.isWhatsAppTaskType()) {
            return handleWhatsAppTaskPlan(
                candidatePlan = candidatePlan,
                normalizedCommand = normalized,
                currentPlan = currentPlan,
                hasPendingBridgeConfirmation = hasPendingBridgeConfirmation
            )
        }

        parseAppLaunchCommand(normalized)?.let { command ->
            return handleAppLaunchCommand(
                command = command,
                currentPlan = currentPlan,
                hasPendingBridgeConfirmation = hasPendingBridgeConfirmation
            )
        }

        if (candidatePlan.type != AgentTaskType.REQUEST_RIDE) {
            return AgentTaskOrchestratorResult.NotHandled
        }

        if (hasPendingBridgeConfirmation) {
            return blockedByPendingConfirmation()
        }

        if (currentPlan != null) {
            return blockedByActiveTask(currentPlan)
        }

        val enrichedPlan = enrichRidePlanWithInstalledApps(candidatePlan)
        val started = memory.startPlan(enrichedPlan)
        return AgentTaskOrchestratorResult.Handled(
            kind = AgentTaskOrchestratorResultKind.PLAN_STARTED,
            spokenText = creationSpeech(started),
            plan = started
        )
    }

    fun currentPlan(): AgentTaskPlan? = memory.currentPlan()

    /** Paquete 6E -- propuesta de accion controlada activa, si la hay. */
    fun currentActionProposal(): AgentControlledActionProposal? =
        actionMemory.currentActionProposal()

    fun observeScreenForCurrentTask(
        snapshot: StructuredScreenSnapshot?
    ): AgentTaskScreenUpdateResult {
        val result = screenObserver.observe(
            currentPlan = memory.currentPlan(),
            snapshot = snapshot
        )
        val storedPlan = result.updatedPlan
            ?.takeIf { result.changed }
            ?.let { memory.replaceCurrentPlan(it) }
        return if (storedPlan != null) {
            result.copy(updatedPlan = storedPlan)
        } else {
            result
        }
    }

    fun onSafeAppLaunchResult(
        packageName: String,
        launched: Boolean
    ): AgentTaskPlan? {
        val current = memory.currentPlan() ?: return null
        val now = clock()
        val updatedTickets = current.tickets.map { ticket ->
            when {
                ticket.appPackageHint.equals(packageName, ignoreCase = true) &&
                    ticket.requiredData.contains(AgentTaskRequiredData.RIDE_APP_OPENED) -> {
                    if (launched) {
                        ticket.copy(
                            status = AgentTaskTicketStatus.COMPLETED,
                            resolvedData = ticket.resolvedData +
                                (AgentTaskRequiredData.RIDE_APP_OPENED to "true"),
                            completedAt = now
                        )
                    } else {
                        ticket.copy(status = AgentTaskTicketStatus.FAILED)
                    }
                }
                ticket.requiredData.contains(AgentTaskRequiredData.WHATSAPP_OPENED) &&
                    isWhatsAppPackageName(packageName) -> {
                    if (launched) {
                        ticket.copy(
                            status = AgentTaskTicketStatus.COMPLETED,
                            resolvedData = ticket.resolvedData +
                                (AgentTaskRequiredData.WHATSAPP_OPENED to "true"),
                            completedAt = now
                        )
                    } else {
                        ticket.copy(status = AgentTaskTicketStatus.FAILED)
                    }
                }
                else -> ticket
            }
        }
        val nextStatus = when {
            updatedTickets.any { it.status == AgentTaskTicketStatus.FAILED } ->
                AgentTaskState.FAILED
            current.status == AgentTaskState.WAITING_FOR_USER ->
                AgentTaskState.WAITING_FOR_USER
            else -> AgentTaskState.ACTIVE
        }
        return memory.replaceCurrentPlan(
            current.copy(
                tickets = activateNextPendingTicket(updatedTickets),
                status = nextStatus
            )
        )
    }

    fun reset() {
        memory.reset()
        actionMemory.reset()
        executionAudits.clear()
    }

    /**
     * Paquete 6E -- mira la tarea activa, los tickets y el snapshot, y propone
     * la proxima accion segura. NUNCA ejecuta nada: solo describe, clasifica el
     * riesgo y guarda la propuesta en memoria de tarea.
     *
     * Si hay una confirmacion pendiente del bridge, no se crea una propuesta
     * critica nueva: se devuelve un resultado bloqueado y la propuesta previa
     * (si la habia) se mantiene intacta.
     */
    private fun proposeControlledAction(
        request: AgentControlledActionRequest,
        currentPlan: AgentTaskPlan?,
        snapshot: StructuredScreenSnapshot?,
        hasPendingBridgeConfirmation: Boolean
    ): AgentControlledActionResult {
        if (currentPlan == null) {
            return AgentControlledActionResult(
                kind = AgentControlledActionResultKind.NO_ACTIVE_TASK,
                spokenText = "No hay una tarea activa.",
                proposal = null
            )
        }
        val proposal = actionPlanner.proposeNextAction(
            plan = currentPlan,
            snapshot = snapshot,
            request = request
        )
        if (hasPendingBridgeConfirmation &&
            proposal.riskLevel == AgentControlledActionRisk.CRITICAL
        ) {
            return AgentControlledActionResult(
                kind = AgentControlledActionResultKind.BLOCKED_BY_PENDING_CONFIRMATION,
                spokenText = "Hay una accion pendiente que requiere confirmacion. " +
                    "Deci confirmar o cancelar antes de proponer una accion critica nueva.",
                proposal = null
            )
        }
        val saved = actionMemory.saveProposal(proposal)
        return AgentControlledActionResult(
            kind = AgentControlledActionResultKind.PROPOSAL_CREATED,
            spokenText = saved.spokenText,
            proposal = saved
        )
    }

    private fun cancelCurrentActionProposal(): AgentControlledActionResult {
        val cancelled = actionMemory.cancelProposal("user_cancelled_action")
        return if (cancelled != null) {
            AgentControlledActionResult(
                kind = AgentControlledActionResultKind.PROPOSAL_CANCELLED,
                spokenText = "Cancele la propuesta de accion. No quedo nada preparado.",
                proposal = cancelled
            )
        } else {
            AgentControlledActionResult(
                kind = AgentControlledActionResultKind.NO_PROPOSAL_TO_CANCEL,
                spokenText = "No hay una propuesta de accion activa para cancelar.",
                proposal = null
            )
        }
    }

    private fun handledFromActionResult(
        result: AgentControlledActionResult
    ): AgentTaskOrchestratorResult.Handled {
        val kind = when (result.kind) {
            AgentControlledActionResultKind.PROPOSAL_CREATED ->
                AgentTaskOrchestratorResultKind.ACTION_PROPOSAL
            AgentControlledActionResultKind.PROPOSAL_CANCELLED,
            AgentControlledActionResultKind.NO_PROPOSAL_TO_CANCEL ->
                AgentTaskOrchestratorResultKind.ACTION_PROPOSAL_CANCELLED
            AgentControlledActionResultKind.NO_ACTIVE_TASK ->
                AgentTaskOrchestratorResultKind.NO_ACTIVE_TASK
            AgentControlledActionResultKind.BLOCKED_BY_PENDING_CONFIRMATION ->
                AgentTaskOrchestratorResultKind.BLOCKED_BY_PENDING_CONFIRMATION
        }
        return AgentTaskOrchestratorResult.Handled(
            kind = kind,
            spokenText = result.spokenText,
            plan = memory.currentPlan(),
            actionProposal = result.proposal
        )
    }

    /** Paquete 6F -- auditoria reciente de ejecuciones seguras. */
    fun recentExecutionAudits(): List<AgentExecutionAuditEntry> = executionAudits.toList()

    /**
     * Paquete 6F -- Safe Execution Gate. Toma la propuesta de accion actual,
     * la pasa por la puerta de ejecucion segura y ejecuta SOLO la parte segura
     * y permitida: abrir una app soportada o preparar contenido en memoria.
     * Nunca envia, nunca paga, nunca pide viaje, nunca toca botones internos.
     */
    private fun executeSafeAction(
        userCommand: String,
        currentPlan: AgentTaskPlan?,
        proposal: AgentControlledActionProposal?,
        hasPendingBridgeConfirmation: Boolean,
        userConfirmedStrongly: Boolean
    ): AgentSafeExecutionResult {
        val request = AgentSafeExecutionRequest(
            plan = currentPlan,
            proposal = proposal,
            userCommand = userCommand,
            hasPendingExternalConfirmation = hasPendingBridgeConfirmation,
            userConfirmedStrongly = userConfirmedStrongly
        )
        val decision = safeExecutionGate.decide(request)
        if (decision.status != AgentSafeExecutionStatus.ALLOW_SAFE_EXECUTION) {
            return safeExecutionResult(
                status = decision.status,
                spokenText = decision.spokenText,
                executed = false,
                proposal = proposal,
                detail = "blocked:${decision.reason}"
            )
        }
        return when (decision.executableType) {
            AgentControlledActionType.OPEN_APP ->
                executeOpenApp(currentPlan, proposal)
            AgentControlledActionType.PREPARE_MESSAGE_TEXT,
            AgentControlledActionType.PREPARE_AUDIO_SCRIPT,
            AgentControlledActionType.PREPARE_SEARCH_QUERY ->
                executePrepare(proposal, decision.executableType)
            else -> safeExecutionResult(
                status = AgentSafeExecutionStatus.FAILED_SAFE,
                spokenText = "No tengo una accion segura para ejecutar ahora.",
                executed = false,
                proposal = proposal,
                detail = "unexpected_executable_type:${decision.executableType}"
            )
        }
    }

    private fun executeOpenApp(
        currentPlan: AgentTaskPlan?,
        proposal: AgentControlledActionProposal?
    ): AgentSafeExecutionResult {
        val capability = capabilityForOpenApp(currentPlan)
            ?: return safeExecutionResult(
                status = AgentSafeExecutionStatus.FAILED_SAFE,
                spokenText = "No encontre la app para abrir de forma segura.",
                executed = false,
                proposal = proposal,
                detail = "open_app_no_capability"
            )
        val launcher = safeAppLauncher
        if (launcher != null) {
            val launchResult = launcher.launch(capability, userConfirmed = true)
            val launched = launchResult is SafeAppLaunchResult.Launched
            if (launched) {
                onSafeAppLaunchResult(capability.packageName, launched = true)
            }
            return safeExecutionResult(
                status = if (launched) {
                    AgentSafeExecutionStatus.ALLOW_SAFE_EXECUTION
                } else {
                    AgentSafeExecutionStatus.FAILED_SAFE
                },
                spokenText = launchResult.spokenText,
                executed = launched,
                proposal = proposal,
                detail = "open_app_direct:${capability.packageName}:launched=$launched",
                launchedDirectly = launched
            )
        }
        // Fallback legacy: handoff externo. La apertura real la hace la UI.
        return safeExecutionResult(
            status = AgentSafeExecutionStatus.ALLOW_SAFE_EXECUTION,
            spokenText = openAppHandoffSpeech(capability),
            executed = true,
            proposal = proposal,
            detail = "open_app_handoff:${capability.packageName}",
            launchPlan = SafeAppLaunchPlan(capability = capability, userConfirmed = true)
        )
    }

    private fun executePrepare(
        proposal: AgentControlledActionProposal?,
        type: AgentControlledActionType
    ): AgentSafeExecutionResult {
        val prepared = proposal?.preparedText
        // Refrescamos la propuesta en memoria para marcar que quedo preparada.
        proposal?.let { actionMemory.saveProposal(it) }
        return safeExecutionResult(
            status = AgentSafeExecutionStatus.ALLOW_SAFE_EXECUTION,
            spokenText = preparedSpeech(type, prepared),
            executed = true,
            proposal = proposal,
            detail = "prepare:${type.name}:has_content=${prepared != null}",
            preparedText = prepared
        )
    }

    private fun capabilityForOpenApp(plan: AgentTaskPlan?): AppCapability? =
        when (plan?.type) {
            AgentTaskType.REQUEST_RIDE -> {
                val hint = plan.tickets
                    .firstOrNull {
                        it.requiredData.contains(AgentTaskRequiredData.RIDE_APP_PACKAGE)
                    }
                    ?.resolvedData
                    ?.get(AgentTaskRequiredData.RIDE_APP_PACKAGE)
                appCapabilityRegistry.firstInstalledRideApp(
                    resolver = installedAppResolver,
                    preferredHint = hint
                )
            }
            AgentTaskType.SEND_WHATSAPP_MESSAGE,
            AgentTaskType.SEND_WHATSAPP_AUDIO -> firstInstalledWhatsAppCapability()
            else -> null
        }

    private fun openAppHandoffSpeech(capability: AppCapability): String =
        when (capability.type) {
            AppCapabilityType.RIDE_HAILING ->
                "Voy a abrir ${capability.appName}. No voy a solicitar el viaje sin tu confirmacion final."
            AppCapabilityType.MESSAGING ->
                "Voy a abrir ${capability.appName}. No voy a tocar chats ni enviar nada."
            else ->
                "Voy a abrir ${capability.appName}. No voy a completar ninguna accion automaticamente."
        }

    private fun preparedSpeech(
        type: AgentControlledActionType,
        prepared: String?
    ): String = when (type) {
        AgentControlledActionType.PREPARE_MESSAGE_TEXT ->
            if (prepared != null) {
                "Deje preparado el mensaje: '$prepared'. No voy a enviarlo sin tu confirmacion final."
            } else {
                "Deje preparado el mensaje. No voy a enviarlo sin tu confirmacion final."
            }
        AgentControlledActionType.PREPARE_AUDIO_SCRIPT ->
            if (prepared != null) {
                "Deje preparado el guion del audio: '$prepared'. No voy a grabarlo ni enviarlo automaticamente."
            } else {
                "Deje preparado el guion del audio. No voy a grabarlo ni enviarlo automaticamente."
            }
        AgentControlledActionType.PREPARE_SEARCH_QUERY ->
            if (prepared != null) {
                "Prepare la busqueda de $prepared. Todavia no voy a escribir ni tocar nada en WhatsApp automaticamente."
            } else {
                "Prepare la busqueda del chat. Todavia no voy a escribir ni tocar nada en WhatsApp automaticamente."
            }
        else -> "Deje la accion preparada. No ejecute ninguna accion sensible."
    }

    private fun safeExecutionResult(
        status: AgentSafeExecutionStatus,
        spokenText: String,
        executed: Boolean,
        proposal: AgentControlledActionProposal?,
        detail: String,
        preparedText: String? = null,
        launchPlan: SafeAppLaunchPlan? = null,
        launchedDirectly: Boolean = false
    ): AgentSafeExecutionResult {
        val audit = AgentExecutionAuditEntry(
            taskId = memory.currentPlan()?.id,
            proposalId = proposal?.id,
            actionType = proposal?.type,
            status = status,
            executed = executed,
            detail = detail,
            timestampMillis = clock()
        )
        recordAudit(audit)
        return AgentSafeExecutionResult(
            status = status,
            spokenText = spokenText,
            executed = executed,
            preparedText = preparedText,
            launchPlan = launchPlan,
            launchedDirectly = launchedDirectly,
            proposal = proposal,
            audit = audit
        )
    }

    private fun recordAudit(entry: AgentExecutionAuditEntry) {
        executionAudits.addLast(entry)
        while (executionAudits.size > MAX_AUDIT_ENTRIES) {
            executionAudits.removeFirst()
        }
    }

    private fun handledFromExecution(
        result: AgentSafeExecutionResult
    ): AgentTaskOrchestratorResult.Handled {
        val kind = if (result.status == AgentSafeExecutionStatus.ALLOW_SAFE_EXECUTION) {
            AgentTaskOrchestratorResultKind.ACTION_EXECUTED
        } else {
            AgentTaskOrchestratorResultKind.ACTION_EXECUTION_BLOCKED
        }
        return AgentTaskOrchestratorResult.Handled(
            kind = kind,
            spokenText = result.spokenText,
            plan = memory.currentPlan(),
            launchPlan = result.launchPlan,
            actionProposal = result.proposal
        )
    }

    private fun handleRideAppQuery(
        currentPlan: AgentTaskPlan?
    ): AgentTaskOrchestratorResult.Handled {
        val installedRideApps = appCapabilityRegistry.installedCapabilitiesForType(
            type = AppCapabilityType.RIDE_HAILING,
            resolver = installedAppResolver
        )
        if (installedRideApps.isEmpty()) {
            val updated = currentPlan
                ?.takeIf { it.type == AgentTaskType.REQUEST_RIDE }
                ?.let { memory.replaceCurrentPlan(markRideSearchBlocked(it)) }
            return AgentTaskOrchestratorResult.Handled(
                kind = AgentTaskOrchestratorResultKind.NO_RIDE_APPS,
                spokenText = "No encontre una app de transporte instalada. Podes instalar Uber o Cabify, o abrir una opcion manual.",
                plan = updated ?: currentPlan
            )
        }

        val appNames = installedRideApps.joinToString(" o ") { it.appName }
        val updated = currentPlan
            ?.takeIf { it.type == AgentTaskType.REQUEST_RIDE }
            ?.let { memory.replaceCurrentPlan(markRideAppFound(it, installedRideApps.first())) }
        return AgentTaskOrchestratorResult.Handled(
            kind = AgentTaskOrchestratorResultKind.RIDE_APPS_AVAILABLE,
            spokenText = "Encontre $appNames para pedir viaje. Decime abrir ${installedRideApps.first().appName} si queres que la abra.",
            plan = updated ?: currentPlan
        )
    }

    private fun handleAppLaunchCommand(
        command: AppLaunchCommand,
        currentPlan: AgentTaskPlan?,
        hasPendingBridgeConfirmation: Boolean
    ): AgentTaskOrchestratorResult {
        if (hasPendingBridgeConfirmation) return blockedByPendingConfirmation()

        val capability = capabilityForLaunchCommand(command, currentPlan)
            ?: return if (command.isGenericRideAppCommand) {
                AgentTaskOrchestratorResult.Handled(
                    kind = AgentTaskOrchestratorResultKind.NO_RIDE_APPS,
                    spokenText = "No se que app abrir todavia. Decime que apps tengo para pedir taxi.",
                    plan = currentPlan
                )
            } else {
                AgentTaskOrchestratorResult.NotHandled
            }

        if (currentPlan != null && !capability.isCompatibleWith(currentPlan)) {
            return blockedByActiveTask(currentPlan)
        }

        if (!installedAppResolver.isPackageInstalled(capability.packageName)) {
            val updated = currentPlan
                ?.takeIf { it.type == AgentTaskType.REQUEST_RIDE && capability.type == AppCapabilityType.RIDE_HAILING }
                ?.let { memory.replaceCurrentPlan(markRideSearchBlocked(it)) }
            return AgentTaskOrchestratorResult.Handled(
                kind = AgentTaskOrchestratorResultKind.APP_NOT_INSTALLED,
                spokenText = "No encontre ${capability.appName} instalada.",
                plan = updated ?: currentPlan
            )
        }

        if (!capability.canOpenSafely || capability.requiresConfirmationToOpen) {
            return AgentTaskOrchestratorResult.Handled(
                kind = AgentTaskOrchestratorResultKind.APP_REQUIRES_CONFIRMATION,
                spokenText = "${capability.appName} requiere confirmacion antes de abrir. No la abri.",
                plan = currentPlan
            )
        }

        val launchPlan = SafeAppLaunchPlan(capability = capability, userConfirmed = true)
        val updatedPlan = currentPlan
            ?.takeIf { it.type == AgentTaskType.REQUEST_RIDE && capability.type == AppCapabilityType.RIDE_HAILING }
            ?.let { memory.replaceCurrentPlan(markRideAppLaunchRequested(it, capability)) }

        return AgentTaskOrchestratorResult.Handled(
            kind = AgentTaskOrchestratorResultKind.APP_LAUNCH_READY,
            spokenText = launchSpeech(capability, updatedPlan),
            plan = updatedPlan ?: currentPlan,
            launchPlan = launchPlan
        )
    }

    private fun capabilityForLaunchCommand(
        command: AppLaunchCommand,
        currentPlan: AgentTaskPlan?
    ): AppCapability? {
        command.appName?.let { appName ->
            return appCapabilityRegistry.findByAppName(appName)
        }
        val selectedPackage = currentPlan
            ?.tickets
            ?.firstOrNull {
                it.requiredData.contains(AgentTaskRequiredData.RIDE_APP) &&
                    it.resolvedData[AgentTaskRequiredData.RIDE_APP_PACKAGE].isNullOrBlank().not()
            }
            ?.resolvedData
            ?.get(AgentTaskRequiredData.RIDE_APP_PACKAGE)
        selectedPackage?.let { packageName ->
            appCapabilityRegistry.findByPackageName(packageName)?.let { return it }
        }
        return appCapabilityRegistry.firstInstalledRideApp(installedAppResolver)
    }

    private fun enrichRidePlanWithInstalledApps(plan: AgentTaskPlan): AgentTaskPlan {
        val preferredHint = plan.tickets.firstOrNull { it.title == RIDE_SEARCH_TICKET_TITLE }
            ?.appPackageHint
        val rideApp = appCapabilityRegistry.firstInstalledRideApp(
            resolver = installedAppResolver,
            preferredHint = preferredHint
        )
        return when {
            rideApp != null -> markRideAppFound(plan, rideApp)
            appCapabilityRegistry.capabilitiesForType(AppCapabilityType.RIDE_HAILING)
                .any { installedAppResolver.isPackageInstalled(it.packageName) } -> plan
            else -> markRideSearchBlocked(plan)
        }
    }

    private fun markRideAppFound(
        plan: AgentTaskPlan,
        capability: AppCapability
    ): AgentTaskPlan {
        val now = clock()
        val updatedTickets = plan.tickets.map { ticket ->
            if (ticket.title == RIDE_SEARCH_TICKET_TITLE) {
                ticket.copy(
                    status = AgentTaskTicketStatus.COMPLETED,
                    requiredData = setOf(
                        AgentTaskRequiredData.RIDE_APP,
                        AgentTaskRequiredData.RIDE_APP_PACKAGE
                    ),
                    resolvedData = mapOf(
                        AgentTaskRequiredData.RIDE_APP to capability.appName,
                        AgentTaskRequiredData.RIDE_APP_PACKAGE to capability.packageName
                    ),
                    appPackageHint = capability.packageName,
                    completedAt = now
                )
            } else {
                ticket
            }
        }
        return plan.copy(
            tickets = ensureOpenRideAppTicket(
                tickets = updatedTickets,
                planId = plan.id,
                capability = capability,
                status = AgentTaskTicketStatus.WAITING_FOR_USER
            ),
            status = plan.status
        )
    }

    private fun markRideAppLaunchRequested(
        plan: AgentTaskPlan,
        capability: AppCapability
    ): AgentTaskPlan {
        val withFound = markRideAppFound(plan, capability)
        return withFound.copy(
            tickets = ensureOpenRideAppTicket(
                tickets = withFound.tickets,
                planId = withFound.id,
                capability = capability,
                status = AgentTaskTicketStatus.ACTIVE
            )
        )
    }

    private fun markRideSearchBlocked(plan: AgentTaskPlan): AgentTaskPlan =
        plan.copy(
            status = AgentTaskState.WAITING_FOR_USER,
            tickets = plan.tickets.map { ticket ->
                if (ticket.title == RIDE_SEARCH_TICKET_TITLE) {
                    ticket.copy(
                        status = AgentTaskTicketStatus.BLOCKED,
                        safeForAutomation = false
                    )
                } else {
                    ticket
                }
            }
        )

    private fun ensureOpenRideAppTicket(
        tickets: List<AgentTaskTicket>,
        planId: String,
        capability: AppCapability,
        status: AgentTaskTicketStatus
    ): List<AgentTaskTicket> {
        val openTitle = "Abrir ${capability.appName}"
        val existing = tickets.firstOrNull {
            it.requiredData.contains(AgentTaskRequiredData.RIDE_APP_OPENED)
        }
        val openTicket = AgentTaskTicket(
            id = "$planId-ticket-open-${AppCapabilityRegistry.normalizeAppName(capability.appName)}",
            title = openTitle,
            description = "Abrir ${capability.appName} sin tocar botones internos ni pedir el viaje.",
            status = status,
            requiredData = setOf(AgentTaskRequiredData.RIDE_APP_OPENED),
            riskLevel = AgentTaskRiskLevel.LOW,
            confirmationRequired = false,
            appPackageHint = capability.packageName,
            safeForAutomation = true
        )
        return if (existing == null) {
            val searchIndex = tickets.indexOfFirst { it.title == RIDE_SEARCH_TICKET_TITLE }
            if (searchIndex < 0) {
                listOf(openTicket) + tickets
            } else {
                tickets.take(searchIndex + 1) + openTicket + tickets.drop(searchIndex + 1)
            }
        } else {
            tickets.map { ticket ->
                if (ticket.id == existing.id) {
                    openTicket.copy(
                        id = ticket.id,
                        completedAt = null
                    )
                } else {
                    ticket
                }
            }
        }
    }

    private fun activateNextPendingTicket(tickets: List<AgentTaskTicket>): List<AgentTaskTicket> {
        if (tickets.any { it.status == AgentTaskTicketStatus.ACTIVE }) return tickets
        val next = tickets.firstOrNull { it.status == AgentTaskTicketStatus.PENDING } ?: return tickets
        return tickets.map { ticket ->
            if (ticket.id == next.id) ticket.copy(status = AgentTaskTicketStatus.ACTIVE) else ticket
        }
    }

    private fun creationSpeech(plan: AgentTaskPlan): String {
        val rideApp = selectedRideApp(plan)
        val appPart = if (rideApp != null) {
            " Encontre $rideApp. Decime abrir $rideApp si queres que la abra."
        } else if (plan.tickets.any { it.title == RIDE_SEARCH_TICKET_TITLE && it.status == AgentTaskTicketStatus.BLOCKED }) {
            " No encontre una app de transporte instalada. Podes instalar Uber o Cabify, o abrir una opcion manual."
        } else {
            ""
        }
        val destinationPart = if (plan.missingData.contains(AgentTaskRequiredData.DESTINATION)) {
            " A donde queres ir?"
        } else {
            ""
        }
        return "${plan.safeSummaryForSpeech}$appPart$destinationPart"
    }

    private fun selectedRideApp(plan: AgentTaskPlan): String? =
        plan.tickets
            .firstOrNull { it.requiredData.contains(AgentTaskRequiredData.RIDE_APP) }
            ?.resolvedData
            ?.get(AgentTaskRequiredData.RIDE_APP)

    private fun launchSpeech(
        capability: AppCapability,
        plan: AgentTaskPlan?
    ): String =
        if (plan?.type == AgentTaskType.REQUEST_RIDE &&
            capability.type == AppCapabilityType.RIDE_HAILING
        ) {
            "Abri ${capability.appName}. Ahora puedo orientarte con la pantalla, pero no voy a solicitar el viaje sin confirmacion final."
        } else {
            "Abri ${capability.appName}. No complete ninguna accion automaticamente."
        }

    private fun handleWhatsAppTaskPlan(
        candidatePlan: AgentTaskPlan,
        normalizedCommand: String,
        currentPlan: AgentTaskPlan?,
        hasPendingBridgeConfirmation: Boolean
    ): AgentTaskOrchestratorResult {
        if (hasPendingBridgeConfirmation) return blockedByPendingConfirmation()

        if (currentPlan != null && !currentPlan.type.isWhatsAppTaskType()) {
            return blockedByActiveTask(currentPlan)
        }

        if (currentPlan != null) {
            val updated = memory.replaceCurrentPlan(
                mergeWhatsAppTaskData(currentPlan, candidatePlan)
            )
            return AgentTaskOrchestratorResult.Handled(
                kind = AgentTaskOrchestratorResultKind.TASK_UPDATED,
                spokenText = updated.operationalStatusSummary(),
                plan = updated,
                launchPlan = whatsappLaunchPlanIfExplicit(normalizedCommand)
            )
        }

        val started = memory.startPlan(candidatePlan)
        return AgentTaskOrchestratorResult.Handled(
            kind = AgentTaskOrchestratorResultKind.PLAN_STARTED,
            spokenText = whatsAppCreationSpeech(started, normalizedCommand),
            plan = started,
            launchPlan = whatsappLaunchPlanIfExplicit(normalizedCommand)
        )
    }

    private fun mergeWhatsAppTaskData(
        currentPlan: AgentTaskPlan,
        candidatePlan: AgentTaskPlan
    ): AgentTaskPlan {
        val contact = candidatePlan.contactName() ?: currentPlan.contactName()
        val message = candidatePlan.messageText() ?: currentPlan.messageText()
        val wantsAudio = candidatePlan.type == AgentTaskType.SEND_WHATSAPP_AUDIO ||
            currentPlan.type == AgentTaskType.SEND_WHATSAPP_AUDIO
        val mergedType = if (wantsAudio) {
            AgentTaskType.SEND_WHATSAPP_AUDIO
        } else {
            AgentTaskType.SEND_WHATSAPP_MESSAGE
        }
        val mergedTitle = when {
            wantsAudio && contact != null -> "Mandar audio a $contact"
            wantsAudio -> "Preparar audio de WhatsApp"
            contact != null -> "Mandar mensaje a $contact"
            else -> "Preparar mensaje de WhatsApp"
        }
        val tickets = currentPlan.tickets.map { ticket ->
            when {
                ticket.requiredData.contains(AgentTaskRequiredData.CONTACT_NAME) && contact != null ->
                    ticket.copy(
                        status = nextPendingStatus(ticket.status),
                        resolvedData = ticket.resolvedData +
                            (AgentTaskRequiredData.CONTACT_NAME to contact),
                        completedAt = null
                    )
                ticket.requiredData.contains(AgentTaskRequiredData.MESSAGE_TEXT) && message != null ->
                    ticket.copy(
                        title = if (wantsAudio) {
                            "Preparar contenido del audio"
                        } else {
                            "Preparar contenido del mensaje"
                        },
                        description = if (wantsAudio) {
                            "Preparar el contenido del audio sin grabarlo ni enviarlo."
                        } else {
                            "Preparar el contenido del mensaje sin escribirlo ni enviarlo automaticamente."
                        },
                        status = nextPendingStatus(ticket.status),
                        resolvedData = ticket.resolvedData +
                            mapOf(
                                AgentTaskRequiredData.MESSAGE_TEXT to message,
                                AgentTaskRequiredData.WANTS_AUDIO to wantsAudio.toString()
                            ),
                        completedAt = null
                    )
                else -> ticket
            }
        }
        return currentPlan.copy(
            type = mergedType,
            title = mergedTitle,
            tickets = tickets,
            status = statusForTickets(tickets),
            updatedAt = clock(),
            safeSummaryForSpeech = if (wantsAudio) {
                "Voy a preparar una tarea para un audio por WhatsApp. No voy a grabarlo ni enviarlo sin tu confirmacion final."
            } else {
                "Voy a preparar una tarea para un mensaje por WhatsApp. No voy a enviarlo sin tu confirmacion final."
            }
        )
    }

    private fun nextPendingStatus(status: AgentTaskTicketStatus): AgentTaskTicketStatus =
        when (status) {
            AgentTaskTicketStatus.WAITING_FOR_USER,
            AgentTaskTicketStatus.ACTIVE -> AgentTaskTicketStatus.PENDING
            else -> status
        }

    private fun statusForTickets(tickets: List<AgentTaskTicket>): AgentTaskState =
        when {
            tickets.all { it.status == AgentTaskTicketStatus.COMPLETED } -> AgentTaskState.COMPLETED
            tickets.any { it.status == AgentTaskTicketStatus.REQUIRES_CONFIRMATION } ->
                AgentTaskState.REQUIRES_CONFIRMATION
            tickets.any { it.status == AgentTaskTicketStatus.WAITING_FOR_USER } ->
                AgentTaskState.WAITING_FOR_USER
            tickets.any { it.status == AgentTaskTicketStatus.WAITING_FOR_SCREEN } ->
                AgentTaskState.WAITING_FOR_SCREEN
            else -> AgentTaskState.ACTIVE
        }

    private fun whatsAppCreationSpeech(
        plan: AgentTaskPlan,
        normalizedCommand: String
    ): String {
        val missingText = listOfNotNull(
            if (plan.missingData.contains(AgentTaskRequiredData.CONTACT_NAME)) {
                "Falta saber a que contacto queres escribir."
            } else {
                null
            },
            if (plan.missingData.contains(AgentTaskRequiredData.MESSAGE_TEXT)) {
                "Falta saber que queres decir."
            } else {
                null
            }
        ).joinToString(" ")
        val openText = if (isExplicitWhatsAppOpenCommand(normalizedCommand)) {
            " Voy a abrir WhatsApp, pero no voy a tocar chats ni enviar nada."
        } else {
            " Decime abrir WhatsApp cuando quieras empezar."
        }
        return "${plan.safeSummaryForSpeech} $missingText$openText".trim()
    }

    private fun whatsappLaunchPlanIfExplicit(normalizedCommand: String): SafeAppLaunchPlan? {
        if (!isExplicitWhatsAppOpenCommand(normalizedCommand)) return null
        val capability = firstInstalledWhatsAppCapability() ?: return null
        if (!capability.canOpenSafely || capability.requiresConfirmationToOpen) return null
        return SafeAppLaunchPlan(capability = capability, userConfirmed = true)
    }

    private fun firstInstalledWhatsAppCapability(): AppCapability? =
        listOfNotNull(
            appCapabilityRegistry.findByPackageName(AppCapabilityRegistry.WHATSAPP_PACKAGE),
            appCapabilityRegistry.findByPackageName(AppCapabilityRegistry.WHATSAPP_BUSINESS_PACKAGE)
        ).firstOrNull { installedAppResolver.isPackageInstalled(it.packageName) }

    private fun screenUpdateToHandled(
        result: AgentTaskScreenUpdateResult
    ): AgentTaskOrchestratorResult.Handled {
        val kind = when (result.observation.type) {
            AgentTaskScreenObservationType.NO_ACTIVE_PLAN ->
                AgentTaskOrchestratorResultKind.NO_ACTIVE_TASK
            AgentTaskScreenObservationType.NO_SNAPSHOT ->
                AgentTaskOrchestratorResultKind.NO_SCREEN_SNAPSHOT
            else -> AgentTaskOrchestratorResultKind.TASK_SCREEN_OBSERVED
        }
        return AgentTaskOrchestratorResult.Handled(
            kind = kind,
            spokenText = result.spokenText ?: result.safeStatusText,
            plan = result.updatedPlan
        )
    }

    private fun blockedByPendingConfirmation(): AgentTaskOrchestratorResult.Handled =
        AgentTaskOrchestratorResult.Handled(
            kind = AgentTaskOrchestratorResultKind.BLOCKED_BY_PENDING_CONFIRMATION,
            spokenText = "Hay una accion pendiente que requiere confirmacion. Deci confirmar o cancelar antes de iniciar otra tarea.",
            plan = null
        )

    private fun blockedByActiveTask(currentPlan: AgentTaskPlan): AgentTaskOrchestratorResult.Handled =
        AgentTaskOrchestratorResult.Handled(
            kind = AgentTaskOrchestratorResultKind.BLOCKED_BY_ACTIVE_TASK,
            spokenText = "Ya hay una tarea activa. Podes cancelarla o pedirme que la reemplace.",
            plan = currentPlan
        )

    companion object {
        private const val RIDE_SEARCH_TICKET_TITLE = "Buscar app de transporte"
        private const val MAX_AUDIT_ENTRIES = 20

        fun isCancelTaskCommand(normalized: String): Boolean =
            normalized == "cancelar tarea" ||
                normalized == "cancela tarea" ||
                normalized == "cancelame la tarea" ||
                normalized == "cancela eso" ||
                normalized == "cancelar eso" ||
                normalized == "olvidalo"

        fun isStatusQuery(normalized: String): Boolean =
            normalized == "que estas haciendo" ||
                normalized == "en que paso estas" ||
                normalized == "en que paso estamos" ||
                normalized == "que falta" ||
                normalized == "que falta hacer" ||
                normalized == "estado de la tarea"

        fun isTaskScreenReviewCommand(normalized: String): Boolean =
            normalized == "revisa la tarea" ||
                normalized == "revisar la tarea" ||
                normalized == "actualiza la tarea" ||
                normalized == "actualizar la tarea" ||
                normalized == "segui con la tarea" ||
                normalized == "seguir con la tarea" ||
                normalized == "revisa la pantalla" ||
                normalized == "revisa la pantalla para la tarea" ||
                normalized == "revisar la pantalla para la tarea"

        /**
         * Paquete 6E -- detecta comandos que piden una propuesta de accion
         * controlada. Devuelve null si el comando no es de esta familia.
         */
        fun parseActionProposalRequest(normalized: String): AgentControlledActionRequest? =
            when (normalized) {
                "cual es el proximo paso",
                "cual es el siguiente paso",
                "que vas a hacer ahora",
                "que vas a hacer",
                "prepara el siguiente paso",
                "preparar el siguiente paso",
                "hace lo siguiente",
                "haz lo siguiente",
                "segui" -> AgentControlledActionRequest.NEXT_STEP
                "busca el chat",
                "buscar el chat",
                "busca el chat de",
                "busca la conversacion" -> AgentControlledActionRequest.SEARCH_CHAT
                "prepara el mensaje",
                "preparar el mensaje" -> AgentControlledActionRequest.PREPARE_MESSAGE
                "prepara el audio",
                "preparar el audio" -> AgentControlledActionRequest.PREPARE_AUDIO
                "prepara el taxi",
                "preparar el taxi",
                "prepara el viaje",
                "preparar el viaje" -> AgentControlledActionRequest.PREPARE_RIDE
                "revisa el precio",
                "revisar el precio",
                "revisa la tarifa",
                "revisar la tarifa" -> AgentControlledActionRequest.REVIEW_PRICE
                else -> null
            }

        /**
         * Paquete 6F -- detecta comandos de ejecucion segura. Devuelve null si
         * el comando no es de esta familia. [SafeExecutionCommand.deferToConfirmationFlow]
         * marca los comandos ambiguos ("confirmo", "dale") que deben cederse al
         * flujo de confirmacion legacy/bridge cuando corresponde.
         */
        fun parseSafeExecutionCommand(normalized: String): SafeExecutionCommand? =
            when (normalized) {
                "ejecuta la accion segura",
                "ejecutar la accion segura",
                "ejecuta la accion",
                "ejecutar la accion",
                "hacelo",
                "hazlo",
                "avanza",
                "avanzar",
                "preparalo",
                "prepararlo",
                "dejalo listo" -> SafeExecutionCommand(deferToConfirmationFlow = false)
                "confirmo",
                "dale" -> SafeExecutionCommand(deferToConfirmationFlow = true)
                else -> null
            }

        fun isCancelActionProposalCommand(normalized: String): Boolean =
            normalized == "cancela la accion" ||
                normalized == "cancelar la accion" ||
                normalized == "cancela la propuesta" ||
                normalized == "cancelar la propuesta" ||
                normalized == "cancela la propuesta de accion" ||
                normalized == "cancelar la propuesta de accion"

        fun isRideAppQuery(normalized: String): Boolean =
            normalized == "que apps tengo para pedir taxi" ||
                normalized == "que apps tengo para pedir un taxi" ||
                normalized == "que app tengo para pedir taxi" ||
                normalized == "que app de transporte tengo"

        fun parseAppLaunchCommand(normalized: String): AppLaunchCommand? {
            val appName = when {
                normalized.contains("uber") -> "Uber"
                normalized.contains("cabify") -> "Cabify"
                normalized.contains("didi") -> "DiDi"
                normalized.contains("mercado pago") -> "Mercado Pago"
                normalized.contains("whatsapp") -> "WhatsApp"
                normalized.contains("telegram") -> "Telegram"
                normalized.contains("maps") || normalized.contains("mapas") -> "Google Maps"
                normalized.contains("ajustes") || normalized.contains("settings") -> "Android Settings"
                else -> null
            }
            val isOpen = normalized.startsWith("abri ") ||
                normalized.startsWith("abrir ") ||
                normalized.startsWith("abreme ") ||
                normalized.startsWith("anda a ") ||
                normalized.startsWith("anda al ") ||
                normalized.startsWith("usa ") ||
                normalized.startsWith("usar ") ||
                normalized.startsWith("segui con ")
            val isGenericRideApp = normalized == "abri la app" ||
                normalized == "abrir la app" ||
                normalized == "usa la app" ||
                normalized == "segui con la app"
            if (!isOpen && !isGenericRideApp) return null
            if (appName == null && !isGenericRideApp) return null
            return AppLaunchCommand(
                appName = appName,
                isGenericRideAppCommand = isGenericRideApp
            )
        }
    }
}

private fun AgentTaskType.isWhatsAppTaskType(): Boolean =
    this == AgentTaskType.SEND_WHATSAPP_MESSAGE ||
        this == AgentTaskType.SEND_WHATSAPP_AUDIO

private fun AppCapability.isCompatibleWith(plan: AgentTaskPlan): Boolean =
    when (plan.type) {
        AgentTaskType.REQUEST_RIDE -> type == AppCapabilityType.RIDE_HAILING
        AgentTaskType.SEND_WHATSAPP_MESSAGE,
        AgentTaskType.SEND_WHATSAPP_AUDIO -> type == AppCapabilityType.MESSAGING &&
            (
                packageName.equals(AppCapabilityRegistry.WHATSAPP_PACKAGE, ignoreCase = true) ||
                    packageName.equals(AppCapabilityRegistry.WHATSAPP_BUSINESS_PACKAGE, ignoreCase = true)
                )
        else -> false
    }

private fun AgentTaskPlan.contactName(): String? =
    tickets.firstOrNull { it.requiredData.contains(AgentTaskRequiredData.CONTACT_NAME) }
        ?.resolvedData
        ?.get(AgentTaskRequiredData.CONTACT_NAME)
        ?.trim()
        ?.takeIf { it.isNotBlank() }

private fun AgentTaskPlan.messageText(): String? =
    tickets.firstOrNull { it.requiredData.contains(AgentTaskRequiredData.MESSAGE_TEXT) }
        ?.resolvedData
        ?.get(AgentTaskRequiredData.MESSAGE_TEXT)
        ?.trim()
        ?.takeIf { it.isNotBlank() }

private fun isExplicitWhatsAppOpenCommand(normalized: String): Boolean =
    normalized == "anda a whatsapp" ||
        normalized == "anda al whatsapp" ||
        normalized == "abri whatsapp" ||
        normalized == "abrir whatsapp" ||
        normalized == "abreme whatsapp"

private fun isWhatsAppPackageName(packageName: String): Boolean =
    packageName.equals(AppCapabilityRegistry.WHATSAPP_PACKAGE, ignoreCase = true) ||
        packageName.equals(AppCapabilityRegistry.WHATSAPP_BUSINESS_PACKAGE, ignoreCase = true)

data class AppLaunchCommand(
    val appName: String?,
    val isGenericRideAppCommand: Boolean
)

/**
 * Paquete 6F -- comando de ejecucion segura ya clasificado.
 * [deferToConfirmationFlow] marca los comandos ambiguos ("confirmo", "dale")
 * que deben cederse al flujo de confirmacion legacy/bridge cuando no hay una
 * propuesta propia o hay una confirmacion externa pendiente.
 */
data class SafeExecutionCommand(
    val deferToConfirmationFlow: Boolean
)

sealed class AgentTaskOrchestratorResult {
    data object NotHandled : AgentTaskOrchestratorResult()

    data class Handled(
        val kind: AgentTaskOrchestratorResultKind,
        val spokenText: String,
        val plan: AgentTaskPlan?,
        val launchPlan: SafeAppLaunchPlan? = null,
        val actionProposal: AgentControlledActionProposal? = null
    ) : AgentTaskOrchestratorResult() {
        val activeTaskTitle: String
            get() = plan?.title.orEmpty()

        val activeTaskStep: String
            get() = plan?.activeStepForUi().orEmpty()

        val activeTaskSummary: String
            get() = plan?.operationalStatusSummary().orEmpty()

        val waitingForUser: Boolean
            get() = plan?.isWaitingForUser == true

        val pendingDebugLabel: String
            get() = when {
                plan == null -> ""
                waitingForUser -> "TASK_${plan.type.name}_WAITING_USER"
                else -> "TASK_${plan.type.name}"
            }
    }
}

enum class AgentTaskOrchestratorResultKind {
    PLAN_STARTED,
    TASK_UPDATED,
    TASK_SCREEN_OBSERVED,
    STATUS,
    CANCELLED,
    NO_ACTIVE_TASK,
    NO_SCREEN_SNAPSHOT,
    RIDE_APPS_AVAILABLE,
    NO_RIDE_APPS,
    APP_LAUNCH_READY,
    APP_NOT_INSTALLED,
    APP_REQUIRES_CONFIRMATION,
    BLOCKED_BY_PENDING_CONFIRMATION,
    BLOCKED_BY_ACTIVE_TASK,
    ACTION_PROPOSAL,
    ACTION_PROPOSAL_CANCELLED,
    ACTION_EXECUTED,
    ACTION_EXECUTION_BLOCKED
}
