package com.ojoclaro.android.agent.task

import com.ojoclaro.android.agent.apps.AppCapability
import com.ojoclaro.android.agent.apps.AppCapabilityRegistry
import com.ojoclaro.android.agent.apps.AppCapabilityType
import com.ojoclaro.android.agent.apps.InstalledAppResolver
import com.ojoclaro.android.agent.apps.SafeAppLaunchPlan
import com.ojoclaro.android.agent.command.ParsedCommand
import com.ojoclaro.android.agent.core.screen.StructuredScreenSnapshot
import com.ojoclaro.android.agent.task.screen.AgentTaskScreenObservationType
import com.ojoclaro.android.agent.task.screen.AgentTaskScreenObserver
import com.ojoclaro.android.agent.task.screen.AgentTaskScreenUpdateResult

class AgentTaskOrchestrator(
    private val planner: AgentTaskPlanner = AgentTaskPlanner(),
    private val memory: AgentTaskMemory = AgentTaskMemory(),
    private val appCapabilityRegistry: AppCapabilityRegistry = AppCapabilityRegistry(),
    private val installedAppResolver: InstalledAppResolver = InstalledAppResolver.NONE,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val screenObserver: AgentTaskScreenObserver = AgentTaskScreenObserver(clock = clock)
) {

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

        if (isCancelTaskCommand(normalized) && currentPlan != null) {
            val cancelled = memory.cancelCurrentPlan("user_cancelled")
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

sealed class AgentTaskOrchestratorResult {
    data object NotHandled : AgentTaskOrchestratorResult()

    data class Handled(
        val kind: AgentTaskOrchestratorResultKind,
        val spokenText: String,
        val plan: AgentTaskPlan?,
        val launchPlan: SafeAppLaunchPlan? = null
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
    BLOCKED_BY_ACTIVE_TASK
}
