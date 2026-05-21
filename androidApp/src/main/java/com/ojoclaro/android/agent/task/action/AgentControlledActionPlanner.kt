package com.ojoclaro.android.agent.task.action

import com.ojoclaro.android.agent.core.screen.StructuredScreenSnapshot
import com.ojoclaro.android.agent.task.AgentTaskPlan
import com.ojoclaro.android.agent.task.AgentTaskPlanner
import com.ojoclaro.android.agent.task.AgentTaskRequiredData
import com.ojoclaro.android.agent.task.AgentTaskTicket
import com.ojoclaro.android.agent.task.AgentTaskTicketStatus
import com.ojoclaro.android.agent.task.AgentTaskType

/**
 * Paquete 6E -- Planner de propuestas de accion controlada.
 *
 * Mira la tarea activa, los tickets, y opcionalmente el snapshot de pantalla,
 * y produce una [AgentControlledActionProposal]: la proxima accion segura,
 * con su riesgo y su clasificacion.
 *
 * Esta clase es PURA. No abre apps, no escribe, no envia, no toca la
 * pantalla. Solo describe. La ejecucion real queda fuera de 6E.
 */
class AgentControlledActionPlanner(
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val idFactory: (Long, String) -> String = { now, taskId ->
        "action_${now}_$taskId"
    }
) {

    /**
     * Construye la propuesta de accion para la tarea [plan]. Nunca ejecuta
     * nada; solo describe. El llamador es responsable de verificar que haya
     * una tarea activa antes de invocar este metodo.
     */
    fun proposeNextAction(
        plan: AgentTaskPlan,
        snapshot: StructuredScreenSnapshot? = null,
        request: AgentControlledActionRequest = AgentControlledActionRequest.NEXT_STEP
    ): AgentControlledActionProposal {
        val context = selectType(plan, snapshot, request)
        return buildProposal(plan, context)
    }

    private fun selectType(
        plan: AgentTaskPlan,
        snapshot: StructuredScreenSnapshot?,
        request: AgentControlledActionRequest
    ): TypeContext {
        if (snapshot != null && isSensitiveScreen(snapshot)) {
            return TypeContext(AgentControlledActionType.BLOCKED_SENSITIVE_ACTION)
        }
        return when (plan.type) {
            AgentTaskType.REQUEST_RIDE -> selectRideType(plan, snapshot, request)
            AgentTaskType.SEND_WHATSAPP_MESSAGE,
            AgentTaskType.SEND_WHATSAPP_AUDIO -> selectWhatsAppType(plan, snapshot, request)
            else -> TypeContext(AgentControlledActionType.UNKNOWN)
        }
    }

    private fun selectRideType(
        plan: AgentTaskPlan,
        snapshot: StructuredScreenSnapshot?,
        request: AgentControlledActionRequest
    ): TypeContext {
        val rideApp = rideAppName(plan)
        if (snapshot != null && hasRideFinalConfirmation(snapshot)) {
            return TypeContext(AgentControlledActionType.FINAL_CONFIRM_RIDE, appName = rideApp)
        }
        if (request == AgentControlledActionRequest.REVIEW_PRICE ||
            (snapshot != null && hasPriceOrDriver(snapshot))
        ) {
            return TypeContext(AgentControlledActionType.REVIEW_RIDE_PRICE, appName = rideApp)
        }
        if (snapshot != null && hasPaymentInfo(snapshot)) {
            return TypeContext(AgentControlledActionType.REVIEW_PAYMENT_METHOD, appName = rideApp)
        }
        if (plan.missingData.contains(AgentTaskRequiredData.DESTINATION)) {
            return TypeContext(AgentControlledActionType.WAIT_FOR_USER_INPUT, missing = MISSING_DESTINATION)
        }
        if (!isRideAppOpened(plan)) {
            return TypeContext(AgentControlledActionType.OPEN_APP, appName = rideApp ?: APP_RIDE_GENERIC)
        }
        return TypeContext(AgentControlledActionType.WAIT_FOR_USER_INPUT, missing = MISSING_RIDE_SCREEN)
    }

    private fun selectWhatsAppType(
        plan: AgentTaskPlan,
        snapshot: StructuredScreenSnapshot?,
        request: AgentControlledActionRequest
    ): TypeContext {
        val isAudio = plan.type == AgentTaskType.SEND_WHATSAPP_AUDIO
        val contact = contactName(plan)
        val message = messageText(plan)

        if (snapshot != null && hasWhatsAppSendOrMic(snapshot)) {
            val type = if (isAudio) {
                AgentControlledActionType.FINAL_CONFIRM_SEND_AUDIO
            } else {
                AgentControlledActionType.FINAL_CONFIRM_SEND_MESSAGE
            }
            return TypeContext(type, contact = contact, message = message)
        }

        if (request == AgentControlledActionRequest.SEARCH_CHAT) {
            if (contact == null) {
                return TypeContext(AgentControlledActionType.WAIT_FOR_USER_INPUT, missing = MISSING_CONTACT)
            }
            val type = if (snapshot != null && hasWhatsAppSearchVisible(snapshot)) {
                AgentControlledActionType.FOCUS_SEARCH_FIELD
            } else {
                AgentControlledActionType.PREPARE_SEARCH_QUERY
            }
            return TypeContext(type, contact = contact)
        }

        val explicitPrepare = request == AgentControlledActionRequest.PREPARE_MESSAGE ||
            request == AgentControlledActionRequest.PREPARE_AUDIO
        if (contact == null) {
            return TypeContext(AgentControlledActionType.WAIT_FOR_USER_INPUT, missing = MISSING_CONTACT)
        }
        if (!explicitPrepare && !isWhatsAppOpened(plan)) {
            return TypeContext(AgentControlledActionType.OPEN_APP, appName = APP_WHATSAPP, contact = contact)
        }
        if (message == null) {
            return TypeContext(AgentControlledActionType.WAIT_FOR_USER_INPUT, missing = MISSING_CONTENT)
        }
        val type = if (isAudio) {
            AgentControlledActionType.PREPARE_AUDIO_SCRIPT
        } else {
            AgentControlledActionType.PREPARE_MESSAGE_TEXT
        }
        return TypeContext(type, contact = contact, message = message)
    }

    private fun buildProposal(
        plan: AgentTaskPlan,
        context: TypeContext
    ): AgentControlledActionProposal {
        val evaluation = AgentControlledActionPolicy.evaluate(context.type)
        val now = clock()
        return AgentControlledActionProposal(
            id = idFactory(now, plan.id),
            taskId = plan.id,
            ticketId = plan.currentTicket?.id,
            type = context.type,
            title = titleFor(context),
            safeDescription = safeDescriptionFor(context, evaluation),
            riskLevel = evaluation.riskLevel,
            status = evaluation.status,
            requiresConfirmation = evaluation.requiresConfirmation,
            allowedToExecuteNow = evaluation.allowedToExecuteNow,
            blockedReason = evaluation.blockedReason,
            forbiddenReason = evaluation.forbiddenReason,
            spokenText = spokenTextFor(context),
            preparedText = preparedTextFor(context),
            createdAt = now,
            updatedAt = now
        )
    }

    /**
     * Paquete 6F -- contenido preparable asociado a la propuesta. Solo se
     * llena para los tipos que preparan contenido, y siempre saneado.
     */
    private fun preparedTextFor(context: TypeContext): String? = when (context.type) {
        AgentControlledActionType.PREPARE_MESSAGE_TEXT,
        AgentControlledActionType.PREPARE_AUDIO_SCRIPT ->
            context.message?.let(::sanitizedPreparedText)
        AgentControlledActionType.PREPARE_SEARCH_QUERY,
        AgentControlledActionType.FOCUS_SEARCH_FIELD ->
            context.contact?.let(::sanitizedPreparedText)
        else -> null
    }

    private fun sanitizedPreparedText(value: String): String? {
        val clean = value.trim()
        if (clean.isBlank()) return null
        if (AgentTaskPlanner.containsSensitiveOperationalData(clean)) return null
        return AgentTaskPlanner.sanitizeOperationalText(clean)
            .trim()
            .take(MAX_QUOTED_CHARS)
            .takeIf { it.isNotBlank() }
    }

    private fun titleFor(context: TypeContext): String = when (context.type) {
        AgentControlledActionType.OPEN_APP -> "Abrir ${context.appName ?: APP_RIDE_GENERIC}"
        AgentControlledActionType.FOCUS_SEARCH_FIELD -> "Dejar lista la busqueda del chat"
        AgentControlledActionType.PREPARE_SEARCH_QUERY -> "Preparar la busqueda del chat"
        AgentControlledActionType.PREPARE_MESSAGE_TEXT -> "Preparar el contenido del mensaje"
        AgentControlledActionType.PREPARE_AUDIO_SCRIPT -> "Preparar el guion del audio"
        AgentControlledActionType.REVIEW_PAYMENT_METHOD -> "Revisar el metodo de pago"
        AgentControlledActionType.REVIEW_RIDE_PRICE -> "Revisar el precio del viaje"
        AgentControlledActionType.FINAL_CONFIRM_RIDE -> "Confirmacion final para solicitar viaje"
        AgentControlledActionType.FINAL_CONFIRM_SEND_MESSAGE -> "Confirmacion final para enviar mensaje"
        AgentControlledActionType.FINAL_CONFIRM_SEND_AUDIO -> "Confirmacion final para enviar audio"
        AgentControlledActionType.BLOCKED_SENSITIVE_ACTION -> "Accion bloqueada por pantalla sensible"
        AgentControlledActionType.WAIT_FOR_USER_INPUT -> "Falta un dato para seguir"
        AgentControlledActionType.UNKNOWN -> "Sin proxima accion segura"
    }

    private fun safeDescriptionFor(
        context: TypeContext,
        evaluation: AgentControlledActionPolicy.Evaluation
    ): String = when (context.type) {
        AgentControlledActionType.OPEN_APP ->
            "Abrir ${context.appName ?: APP_RIDE_GENERIC} sin tocar nada adentro."
        AgentControlledActionType.FOCUS_SEARCH_FIELD ->
            "Dejar marcada la busqueda del chat. No se escribe texto en la app."
        AgentControlledActionType.PREPARE_SEARCH_QUERY ->
            "Preparar el texto de busqueda en memoria. No se escribe en la app."
        AgentControlledActionType.PREPARE_MESSAGE_TEXT ->
            "Preparar el contenido del mensaje en memoria. No se escribe ni se envia."
        AgentControlledActionType.PREPARE_AUDIO_SCRIPT ->
            "Preparar el guion del audio en memoria. No se graba ni se envia."
        AgentControlledActionType.REVIEW_PAYMENT_METHOD ->
            "Orientar sobre el metodo de pago visible. No se toca ni se cambia."
        AgentControlledActionType.REVIEW_RIDE_PRICE ->
            "Orientar sobre el precio del viaje. No se acepta ni se pide el viaje."
        AgentControlledActionType.FINAL_CONFIRM_RIDE ->
            "Marcar como pendiente la confirmacion final del viaje. No se pide el viaje."
        AgentControlledActionType.FINAL_CONFIRM_SEND_MESSAGE ->
            "Marcar como pendiente la confirmacion final del mensaje. No se envia."
        AgentControlledActionType.FINAL_CONFIRM_SEND_AUDIO ->
            "Marcar como pendiente la confirmacion final del audio. No se graba ni se envia."
        AgentControlledActionType.BLOCKED_SENSITIVE_ACTION ->
            (evaluation.blockedReason ?: AgentControlledActionPolicy.REASON_SENSITIVE_SCREEN)
        AgentControlledActionType.WAIT_FOR_USER_INPUT ->
            "Falta ${context.missing ?: "un dato"} para poder seguir con la tarea."
        AgentControlledActionType.UNKNOWN ->
            AgentControlledActionPolicy.REASON_UNKNOWN_ACTION
    }

    private fun spokenTextFor(context: TypeContext): String {
        val contact = context.contact ?: "tu contacto"
        val quotedMessage = context.message?.let { quoteSafeMessage(it) }
        return when (context.type) {
            AgentControlledActionType.OPEN_APP -> when (context.appName) {
                APP_WHATSAPP ->
                    "La proxima accion segura es abrir WhatsApp. " +
                        "Puedo abrirla, pero no voy a tocar chats ni enviar nada."
                else ->
                    "La proxima accion segura es abrir ${context.appName ?: APP_RIDE_GENERIC}. " +
                        "Puedo abrirla, pero no voy a tocar nada adentro ni pedir el viaje."
            }
            AgentControlledActionType.FOCUS_SEARCH_FIELD ->
                "Veo el campo de busqueda. Puedo dejar marcada la busqueda del chat de " +
                    "$contact, pero no voy a escribir nada dentro de WhatsApp automaticamente."
            AgentControlledActionType.PREPARE_SEARCH_QUERY ->
                "La proxima accion segura es buscar el chat de $contact. Puedo preparar " +
                    "la busqueda, pero no voy a escribir ni tocar nada dentro de WhatsApp " +
                    "automaticamente."
            AgentControlledActionType.PREPARE_MESSAGE_TEXT ->
                if (quotedMessage != null) {
                    "Estoy preparando un mensaje para $contact. El contenido es: " +
                        "$quotedMessage. No voy a enviarlo sin confirmacion final."
                } else {
                    "Estoy preparando un mensaje para $contact. " +
                        "No voy a enviarlo sin confirmacion final."
                }
            AgentControlledActionType.PREPARE_AUDIO_SCRIPT ->
                if (quotedMessage != null) {
                    "Prepare el guion del audio para $contact: $quotedMessage. " +
                        "Todavia no voy a grabarlo ni a enviarlo automaticamente."
                } else {
                    "Estoy preparando el guion del audio para $contact. " +
                        "Todavia no voy a grabarlo ni a enviarlo automaticamente."
                }
            AgentControlledActionType.REVIEW_PAYMENT_METHOD ->
                "Veo informacion del metodo de pago. Esa accion es delicada. " +
                    "Solo te puedo orientar; no voy a tocar ni cambiar el metodo de pago."
            AgentControlledActionType.REVIEW_RIDE_PRICE ->
                "Veo el precio del viaje. Esa accion es delicada. Te puedo orientar para " +
                    "revisarlo, pero no voy a aceptar el precio ni pedir el viaje."
            AgentControlledActionType.FINAL_CONFIRM_RIDE ->
                "Veo una opcion para solicitar el viaje. Esa accion es critica. No voy a " +
                    "pedirlo en esta version; puedo dejarla marcada como pendiente de " +
                    "confirmacion."
            AgentControlledActionType.FINAL_CONFIRM_SEND_MESSAGE ->
                "El siguiente paso seria mandar el mensaje a $contact. Esa accion es " +
                    "critica y sigue bloqueada en esta version. No voy a mandarlo."
            AgentControlledActionType.FINAL_CONFIRM_SEND_AUDIO ->
                "El siguiente paso seria grabar y mandar el audio a $contact. Esa accion " +
                    "es critica y sigue bloqueada en esta version. No voy a grabarlo ni " +
                    "mandarlo."
            AgentControlledActionType.BLOCKED_SENSITIVE_ACTION ->
                "Esta pantalla puede tener datos sensibles. No voy a leerlos ni a preparar " +
                    "ninguna accion aca."
            AgentControlledActionType.WAIT_FOR_USER_INPUT -> when (context.missing) {
                MISSING_DESTINATION ->
                    "Para seguir necesito el destino. Decime a donde queres ir."
                MISSING_CONTACT ->
                    "Para seguir necesito el contacto. Decime a quien queres escribirle."
                MISSING_CONTENT ->
                    "Para seguir necesito el contenido. Decime que queres decir."
                MISSING_RIDE_SCREEN ->
                    "Estamos en la app de transporte. Decime que ves en pantalla o " +
                        "pedime que revise la pantalla."
                else ->
                    "Para seguir necesito un dato mas. Contame como queres avanzar."
            }
            AgentControlledActionType.UNKNOWN ->
                "Por ahora no tengo una proxima accion segura para proponer."
        }
    }

    /**
     * Cita el contenido del mensaje SOLO si es claramente no sensible. Si el
     * contenido pareciera tener datos sensibles, no se cita literalmente.
     */
    private fun quoteSafeMessage(message: String): String? {
        val clean = message.trim()
        if (clean.isBlank()) return null
        if (AgentTaskPlanner.containsSensitiveOperationalData(clean)) return null
        val safe = AgentTaskPlanner.sanitizeOperationalText(clean).trim()
        if (safe.isBlank()) return null
        return "'${safe.take(MAX_QUOTED_CHARS)}'"
    }

    // --- Lectura de la tarea -------------------------------------------------

    private fun rideAppName(plan: AgentTaskPlan): String? =
        plan.tickets
            .firstOrNull { it.requiredData.contains(AgentTaskRequiredData.RIDE_APP) }
            ?.resolvedData
            ?.get(AgentTaskRequiredData.RIDE_APP)
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    private fun isRideAppOpened(plan: AgentTaskPlan): Boolean =
        plan.tickets.any { ticket ->
            ticket.requiredData.contains(AgentTaskRequiredData.RIDE_APP_OPENED) &&
                (
                    ticket.status == AgentTaskTicketStatus.COMPLETED ||
                        ticket.resolvedData[AgentTaskRequiredData.RIDE_APP_OPENED] == "true"
                    )
        }

    private fun isWhatsAppOpened(plan: AgentTaskPlan): Boolean =
        plan.tickets.any { ticket ->
            ticket.requiredData.contains(AgentTaskRequiredData.WHATSAPP_OPENED) &&
                (
                    ticket.status == AgentTaskTicketStatus.COMPLETED ||
                        ticket.resolvedData[AgentTaskRequiredData.WHATSAPP_OPENED] == "true"
                    )
        }

    private fun contactName(plan: AgentTaskPlan): String? =
        plan.resolvedValue(AgentTaskRequiredData.CONTACT_NAME)

    private fun messageText(plan: AgentTaskPlan): String? =
        plan.resolvedValue(AgentTaskRequiredData.MESSAGE_TEXT)

    private fun AgentTaskPlan.resolvedValue(key: String): String? =
        tickets.firstOrNull { it.requiredData.contains(key) }
            ?.resolvedData
            ?.get(key)
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    // --- Lectura del snapshot ------------------------------------------------

    private fun isSensitiveScreen(snapshot: StructuredScreenSnapshot): Boolean =
        snapshot.signals.hasPasswordField ||
            snapshot.signals.hasVerificationCode ||
            snapshot.signals.isBankingApp

    private fun hasRideFinalConfirmation(snapshot: StructuredScreenSnapshot): Boolean =
        snapshot.buttons.any { label ->
            val normalized = AgentTaskPlanner.normalize(label)
            normalized == "confirmar" ||
                normalized == "solicitar" ||
                normalized == "pedir" ||
                normalized == "reservar" ||
                normalized.contains("solicitar viaje") ||
                normalized.contains("pedir viaje") ||
                normalized.contains("confirmar viaje") ||
                normalized.contains("confirm ride") ||
                normalized.contains("request ride") ||
                normalized.contains("book ride")
        }

    private fun hasPriceOrDriver(snapshot: StructuredScreenSnapshot): Boolean {
        val normalized = screenText(snapshot)
        return normalized.any {
            it.contains("precio") ||
                it.contains("conductor") ||
                it.contains("driver") ||
                it.contains("uberx") ||
                it.contains("tarifa")
        } || rawScreenText(snapshot).any { it.contains("$") }
    }

    private fun hasPaymentInfo(snapshot: StructuredScreenSnapshot): Boolean =
        screenText(snapshot).any {
            it.contains("pago") ||
                it.contains("payment") ||
                it.contains("tarjeta") ||
                it.contains("efectivo") ||
                it.contains("cash") ||
                it.contains("mercado pago")
        }

    private fun hasWhatsAppSearchVisible(snapshot: StructuredScreenSnapshot): Boolean =
        screenText(snapshot).any { it.contains("buscar") || it.contains("search") }

    private fun hasWhatsAppSendOrMic(snapshot: StructuredScreenSnapshot): Boolean =
        snapshot.buttons.any { label ->
            val normalized = AgentTaskPlanner.normalize(label)
            normalized == "enviar" ||
                normalized == "send" ||
                normalized.contains("microfono") ||
                normalized.contains("mensaje de voz") ||
                normalized.contains("voice message")
        }

    private fun rawScreenText(snapshot: StructuredScreenSnapshot): List<String> =
        snapshot.redactedTextLines +
            snapshot.buttons +
            snapshot.editableFields +
            listOfNotNull(snapshot.focusedLabel, snapshot.appLabel)

    private fun screenText(snapshot: StructuredScreenSnapshot): List<String> =
        rawScreenText(snapshot)
            .map { AgentTaskPlanner.normalize(it) }
            .filter { it.isNotBlank() }

    private data class TypeContext(
        val type: AgentControlledActionType,
        val appName: String? = null,
        val contact: String? = null,
        val message: String? = null,
        val missing: String? = null
    )

    companion object {
        private const val APP_WHATSAPP = "WhatsApp"
        private const val APP_RIDE_GENERIC = "la app de transporte"
        private const val MISSING_DESTINATION = "el destino"
        private const val MISSING_CONTACT = "el contacto"
        private const val MISSING_CONTENT = "el contenido"
        private const val MISSING_RIDE_SCREEN = "tu indicacion en la app"
        private const val MAX_QUOTED_CHARS = 120
    }
}
