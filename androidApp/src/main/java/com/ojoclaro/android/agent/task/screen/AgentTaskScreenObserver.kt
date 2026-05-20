package com.ojoclaro.android.agent.task.screen

import com.ojoclaro.android.agent.apps.AppCapabilityRegistry
import com.ojoclaro.android.agent.core.screen.StructuredScreenSnapshot
import com.ojoclaro.android.agent.task.AgentTaskPlan
import com.ojoclaro.android.agent.task.AgentTaskPlanner
import com.ojoclaro.android.agent.task.AgentTaskRequiredData
import com.ojoclaro.android.agent.task.AgentTaskState
import com.ojoclaro.android.agent.task.AgentTaskTicket
import com.ojoclaro.android.agent.task.AgentTaskTicketStatus
import com.ojoclaro.android.agent.task.AgentTaskType

enum class AgentTaskScreenObservationType {
    NO_ACTIVE_PLAN,
    NO_SNAPSHOT,
    APP_MATCHED_TASK,
    RIDE_APP_OPENED,
    RIDE_DESTINATION_FIELD_VISIBLE,
    RIDE_PAYMENT_VISIBLE,
    RIDE_PRICE_OR_DRIVER_VISIBLE,
    RIDE_FINAL_CONFIRMATION_VISIBLE,
    WHATSAPP_OPENED,
    WHATSAPP_SEARCH_VISIBLE,
    WHATSAPP_CHAT_CANDIDATE_VISIBLE,
    WHATSAPP_MESSAGE_BOX_VISIBLE,
    WHATSAPP_SEND_BUTTON_VISIBLE,
    SENSITIVE_SCREEN_BLOCKED,
    UNKNOWN_TASK_SCREEN,
    TASK_SCREEN_UNCHANGED
}

enum class AgentTaskScreenIntent {
    REVIEW_CURRENT_TASK,
    CONTINUE_CURRENT_TASK,
    STATUS_ONLY
}

data class AgentTaskScreenObservation(
    val type: AgentTaskScreenObservationType,
    val packageName: String? = null,
    val appLabel: String? = null
)

data class AgentTaskScreenUpdate(
    val ticketId: String,
    val ticketTitle: String,
    val oldStatus: AgentTaskTicketStatus,
    val newStatus: AgentTaskTicketStatus,
    val reason: String
)

data class AgentTaskScreenUpdateResult(
    val observation: AgentTaskScreenObservation,
    val updatedPlan: AgentTaskPlan?,
    val updates: List<AgentTaskScreenUpdate> = emptyList(),
    val spokenText: String? = null,
    val safeStatusText: String = spokenText.orEmpty(),
    val waitingForUser: Boolean = false,
    val requiresConfirmation: Boolean = false,
    val blocked: Boolean = false,
    val riskWarning: String? = null,
    val changed: Boolean = updates.isNotEmpty()
)

class AgentTaskScreenObserver(
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    fun observe(
        currentPlan: AgentTaskPlan?,
        snapshot: StructuredScreenSnapshot?
    ): AgentTaskScreenUpdateResult {
        if (currentPlan == null) {
            return AgentTaskScreenUpdateResult(
                observation = AgentTaskScreenObservation(AgentTaskScreenObservationType.NO_ACTIVE_PLAN),
                updatedPlan = null,
                spokenText = "No hay una tarea activa."
            )
        }
        if (snapshot == null || snapshot.isEmpty) {
            return AgentTaskScreenUpdateResult(
                observation = AgentTaskScreenObservation(AgentTaskScreenObservationType.NO_SNAPSHOT),
                updatedPlan = currentPlan,
                spokenText = "Todavia no tengo una lectura de pantalla disponible.",
                safeStatusText = currentPlan.operationalStatusSummary()
            )
        }
        if (isSensitiveBlocked(snapshot)) {
            return AgentTaskScreenUpdateResult(
                observation = observation(
                    type = AgentTaskScreenObservationType.SENSITIVE_SCREEN_BLOCKED,
                    snapshot = snapshot
                ),
                updatedPlan = currentPlan,
                spokenText = "Esta pantalla puede contener datos sensibles. No voy a leerlos.",
                safeStatusText = currentPlan.operationalStatusSummary(),
                blocked = true,
                riskWarning = "sensitive_screen"
            )
        }

        return when (currentPlan.type) {
            AgentTaskType.REQUEST_RIDE -> observeRide(currentPlan, snapshot)
            AgentTaskType.SEND_WHATSAPP_MESSAGE,
            AgentTaskType.SEND_WHATSAPP_AUDIO -> observeWhatsApp(currentPlan, snapshot)
            else -> AgentTaskScreenUpdateResult(
                observation = observation(
                    type = AgentTaskScreenObservationType.UNKNOWN_TASK_SCREEN,
                    snapshot = snapshot
                ),
                updatedPlan = currentPlan,
                spokenText = currentPlan.operationalStatusSummary(),
                safeStatusText = currentPlan.operationalStatusSummary()
            )
        }
    }

    private fun observeRide(
        plan: AgentTaskPlan,
        snapshot: StructuredScreenSnapshot
    ): AgentTaskScreenUpdateResult {
        var tickets = plan.tickets
        val updates = mutableListOf<AgentTaskScreenUpdate>()
        var type = AgentTaskScreenObservationType.TASK_SCREEN_UNCHANGED
        var spokenText: String? = null

        val rideAppName = rideAppName(snapshot)
        if (rideAppName != null) {
            val packageName = snapshot.packageName.orEmpty()
            val appData = mapOf(
                AgentTaskRequiredData.RIDE_APP to rideAppName,
                AgentTaskRequiredData.RIDE_APP_PACKAGE to packageName,
                AgentTaskRequiredData.RIDE_APP_OPENED to "true"
            )
            tickets = updateTicket(
                tickets = tickets,
                updates = updates,
                selector = { it.title == "Buscar app de transporte" },
                newStatus = AgentTaskTicketStatus.COMPLETED,
                resolvedData = appData,
                reason = "ride_app_visible"
            )
            tickets = updateTicket(
                tickets = tickets,
                updates = updates,
                selector = {
                    it.title.startsWith("Abrir ") ||
                        (
                            it.requiredData.contains(AgentTaskRequiredData.RIDE_APP_OPENED) &&
                                it.title != "Buscar app de transporte"
                            )
                },
                newStatus = AgentTaskTicketStatus.COMPLETED,
                resolvedData = mapOf(AgentTaskRequiredData.RIDE_APP_OPENED to "true"),
                reason = "ride_app_visible"
            )
            if (updates.any { it.reason == "ride_app_visible" }) {
                type = AgentTaskScreenObservationType.RIDE_APP_OPENED
                spokenText = "Ya estamos en $rideAppName."
            } else {
                type = AgentTaskScreenObservationType.APP_MATCHED_TASK
            }
        }

        if (hasDestinationField(snapshot)) {
            tickets = updateTicket(
                tickets = tickets,
                updates = updates,
                selector = { it.requiredData.contains(AgentTaskRequiredData.DESTINATION) },
                newStatus = AgentTaskTicketStatus.WAITING_FOR_USER,
                reason = "ride_destination_field_visible"
            )
            type = AgentTaskScreenObservationType.RIDE_DESTINATION_FIELD_VISIBLE
            spokenText = "Veo un campo para destino. Decime a donde queres ir si todavia no lo confirmaste."
        }

        if (hasPaymentVisible(snapshot)) {
            tickets = updateTicket(
                tickets = tickets,
                updates = updates,
                selector = { it.requiredData.contains(AgentTaskRequiredData.PAYMENT_METHOD) },
                newStatus = AgentTaskTicketStatus.REQUIRES_CONFIRMATION,
                reason = "ride_payment_visible"
            )
            type = AgentTaskScreenObservationType.RIDE_PAYMENT_VISIBLE
            spokenText = "Veo informacion de pago. No voy a tocar ni confirmar nada."
        }

        if (hasPriceOrDriverVisible(snapshot)) {
            tickets = updateTicket(
                tickets = tickets,
                updates = updates,
                selector = { it.requiredData.contains(AgentTaskRequiredData.PRICE_AND_DRIVER) },
                newStatus = AgentTaskTicketStatus.REQUIRES_CONFIRMATION,
                reason = "ride_price_or_driver_visible"
            )
            type = AgentTaskScreenObservationType.RIDE_PRICE_OR_DRIVER_VISIBLE
            spokenText = "Veo informacion del viaje. Revisa precio y conductor antes de confirmar."
        }

        if (hasRideFinalConfirmation(snapshot)) {
            tickets = updateTicket(
                tickets = tickets,
                updates = updates,
                selector = { it.requiredData.contains(AgentTaskRequiredData.FINAL_RIDE_CONFIRMATION) },
                newStatus = AgentTaskTicketStatus.REQUIRES_CONFIRMATION,
                reason = "ride_final_confirmation_visible"
            )
            type = AgentTaskScreenObservationType.RIDE_FINAL_CONFIRMATION_VISIBLE
            spokenText = "Veo una opcion para solicitar el viaje. No voy a pedirlo sin tu confirmacion final."
        }

        return resultFor(
            originalPlan = plan,
            tickets = tickets,
            updates = updates,
            type = type,
            snapshot = snapshot,
            spokenText = spokenText
        )
    }

    private fun observeWhatsApp(
        plan: AgentTaskPlan,
        snapshot: StructuredScreenSnapshot
    ): AgentTaskScreenUpdateResult {
        var tickets = plan.tickets
        val updates = mutableListOf<AgentTaskScreenUpdate>()
        var type = AgentTaskScreenObservationType.TASK_SCREEN_UNCHANGED
        var spokenText: String? = null
        val isAudio = plan.type == AgentTaskType.SEND_WHATSAPP_AUDIO

        if (isWhatsAppPackage(snapshot.packageName)) {
            tickets = updateTicket(
                tickets = tickets,
                updates = updates,
                selector = { it.requiredData.contains(AgentTaskRequiredData.WHATSAPP_OPENED) },
                newStatus = AgentTaskTicketStatus.COMPLETED,
                resolvedData = mapOf(AgentTaskRequiredData.WHATSAPP_OPENED to "true"),
                reason = "whatsapp_visible"
            )
            if (updates.any { it.reason == "whatsapp_visible" }) {
                type = AgentTaskScreenObservationType.WHATSAPP_OPENED
                spokenText = "Ya estamos en WhatsApp."
            }
        }

        if (hasWhatsAppSearchVisible(snapshot)) {
            tickets = updateTicket(
                tickets = tickets,
                updates = updates,
                selector = { it.requiredData.contains(AgentTaskRequiredData.CONTACT_NAME) },
                newStatus = AgentTaskTicketStatus.ACTIVE,
                reason = "whatsapp_search_visible"
            )
            type = AgentTaskScreenObservationType.WHATSAPP_SEARCH_VISIBLE
            spokenText = "Veo busqueda de WhatsApp. Falta buscar el chat correcto."
        }

        val expectedContact = expectedContact(plan)
        if (expectedContact != null && screenContains(snapshot, expectedContact)) {
            tickets = updateTicket(
                tickets = tickets,
                updates = updates,
                selector = { it.requiredData.contains(AgentTaskRequiredData.WHATSAPP_CHAT_CONFIRMED) },
                newStatus = AgentTaskTicketStatus.REQUIRES_CONFIRMATION,
                reason = "whatsapp_contact_candidate_visible"
            )
            type = AgentTaskScreenObservationType.WHATSAPP_CHAT_CANDIDATE_VISIBLE
            spokenText = "Parece que estamos cerca del chat de $expectedContact. Confirma si es el chat correcto."
        }

        if (hasWhatsAppMessageBox(snapshot)) {
            val contentStatus = if (messageText(plan).isNullOrBlank()) {
                AgentTaskTicketStatus.WAITING_FOR_USER
            } else {
                AgentTaskTicketStatus.ACTIVE
            }
            tickets = updateTicket(
                tickets = tickets,
                updates = updates,
                selector = { it.requiredData.contains(AgentTaskRequiredData.MESSAGE_TEXT) },
                newStatus = contentStatus,
                reason = "whatsapp_message_box_visible"
            )
            type = AgentTaskScreenObservationType.WHATSAPP_MESSAGE_BOX_VISIBLE
            spokenText = if (isAudio) {
                "Veo el campo para escribir. El audio queda como tarea pendiente. Todavia no voy a grabarlo ni enviarlo automaticamente."
            } else {
                "Veo el campo para escribir. Puedo ayudarte a preparar el mensaje, pero no voy a enviarlo sin confirmacion."
            }
        }

        if (hasWhatsAppSendOrMicVisible(snapshot)) {
            tickets = updateTicket(
                tickets = tickets,
                updates = updates,
                selector = { it.requiredData.contains(AgentTaskRequiredData.FINAL_MESSAGE_CONFIRMATION) },
                newStatus = AgentTaskTicketStatus.REQUIRES_CONFIRMATION,
                reason = "whatsapp_send_or_mic_visible"
            )
            type = AgentTaskScreenObservationType.WHATSAPP_SEND_BUTTON_VISIBLE
            spokenText = if (isAudio) {
                "Veo la opcion de enviar o grabar. No voy a grabar ni enviar nada sin tu confirmacion final."
            } else {
                "Veo la opcion de enviar. No voy a enviar nada sin tu confirmacion final."
            }
        }

        return resultFor(
            originalPlan = plan,
            tickets = tickets,
            updates = updates,
            type = type,
            snapshot = snapshot,
            spokenText = spokenText
        )
    }

    private fun resultFor(
        originalPlan: AgentTaskPlan,
        tickets: List<AgentTaskTicket>,
        updates: List<AgentTaskScreenUpdate>,
        type: AgentTaskScreenObservationType,
        snapshot: StructuredScreenSnapshot,
        spokenText: String?
    ): AgentTaskScreenUpdateResult {
        val changed = updates.isNotEmpty()
        val updatedPlan = if (changed) {
            originalPlan.copy(
                tickets = tickets,
                status = statusFor(tickets),
                updatedAt = clock()
            )
        } else {
            originalPlan
        }
        val safeText = spokenText ?: updatedPlan.operationalStatusSummary()
        return AgentTaskScreenUpdateResult(
            observation = observation(
                type = if (changed) type else AgentTaskScreenObservationType.TASK_SCREEN_UNCHANGED,
                snapshot = snapshot
            ),
            updatedPlan = updatedPlan,
            updates = updates,
            spokenText = safeText,
            safeStatusText = updatedPlan.operationalStatusSummary(),
            waitingForUser = updatedPlan.isWaitingForUser,
            requiresConfirmation = updatedPlan.status == AgentTaskState.REQUIRES_CONFIRMATION ||
                updatedPlan.tickets.any { it.status == AgentTaskTicketStatus.REQUIRES_CONFIRMATION },
            changed = changed
        )
    }

    private fun updateTicket(
        tickets: List<AgentTaskTicket>,
        updates: MutableList<AgentTaskScreenUpdate>,
        selector: (AgentTaskTicket) -> Boolean,
        newStatus: AgentTaskTicketStatus,
        resolvedData: Map<String, String> = emptyMap(),
        reason: String
    ): List<AgentTaskTicket> {
        val index = tickets.indexOfFirst(selector)
        if (index < 0) return tickets
        val ticket = tickets[index]
        if (ticket.status == AgentTaskTicketStatus.COMPLETED &&
            newStatus != AgentTaskTicketStatus.COMPLETED
        ) {
            return tickets
        }
        val mergedRequired = ticket.requiredData + resolvedData.keys
        val mergedResolved = ticket.resolvedData + resolvedData
        val completedAt = when (newStatus) {
            AgentTaskTicketStatus.COMPLETED -> ticket.completedAt ?: clock()
            else -> null
        }
        if (ticket.status == newStatus &&
            ticket.resolvedData == mergedResolved &&
            ticket.requiredData == mergedRequired &&
            ticket.completedAt == completedAt
        ) {
            return tickets
        }
        val updated = ticket.copy(
            status = newStatus,
            requiredData = mergedRequired,
            resolvedData = mergedResolved,
            completedAt = completedAt
        )
        updates += AgentTaskScreenUpdate(
            ticketId = ticket.id,
            ticketTitle = ticket.title,
            oldStatus = ticket.status,
            newStatus = newStatus,
            reason = reason
        )
        return tickets.toMutableList().also { it[index] = updated }
    }

    private fun statusFor(tickets: List<AgentTaskTicket>): AgentTaskState =
        when {
            tickets.all { it.status == AgentTaskTicketStatus.COMPLETED } -> AgentTaskState.COMPLETED
            tickets.any { it.status == AgentTaskTicketStatus.REQUIRES_CONFIRMATION } ->
                AgentTaskState.REQUIRES_CONFIRMATION
            tickets.any { it.status == AgentTaskTicketStatus.WAITING_FOR_USER } ->
                AgentTaskState.WAITING_FOR_USER
            tickets.any { it.status == AgentTaskTicketStatus.WAITING_FOR_SCREEN } ->
                AgentTaskState.WAITING_FOR_SCREEN
            tickets.any { it.status == AgentTaskTicketStatus.ACTIVE } -> AgentTaskState.ACTIVE
            else -> AgentTaskState.ACTIVE
        }

    private fun observation(
        type: AgentTaskScreenObservationType,
        snapshot: StructuredScreenSnapshot
    ): AgentTaskScreenObservation = AgentTaskScreenObservation(
        type = type,
        packageName = snapshot.packageName,
        appLabel = snapshot.appLabel
    )

    private fun isSensitiveBlocked(snapshot: StructuredScreenSnapshot): Boolean {
        val packageName = snapshot.packageName.orEmpty()
        return snapshot.signals.hasPasswordField ||
            snapshot.signals.hasVerificationCode ||
            snapshot.signals.isBankingApp ||
            isBankingOrPaymentPackage(packageName) ||
            (snapshot.signals.hasPaymentOrTransferSignals &&
                !isRidePackage(packageName) &&
                !isWhatsAppPackage(packageName))
    }

    private fun rideAppName(snapshot: StructuredScreenSnapshot): String? {
        val packageName = snapshot.packageName.orEmpty()
        return when {
            packageName.equals(AppCapabilityRegistry.UBER_PACKAGE, ignoreCase = true) -> "Uber"
            packageName.equals(AppCapabilityRegistry.CABIFY_PACKAGE, ignoreCase = true) -> "Cabify"
            packageName.equals(AppCapabilityRegistry.DIDI_PACKAGE, ignoreCase = true) -> "DiDi"
            else -> null
        }
    }

    private fun isRidePackage(packageName: String?): Boolean =
        packageName.equals(AppCapabilityRegistry.UBER_PACKAGE, ignoreCase = true) ||
            packageName.equals(AppCapabilityRegistry.CABIFY_PACKAGE, ignoreCase = true) ||
            packageName.equals(AppCapabilityRegistry.DIDI_PACKAGE, ignoreCase = true)

    private fun isWhatsAppPackage(packageName: String?): Boolean =
        packageName.equals(AppCapabilityRegistry.WHATSAPP_PACKAGE, ignoreCase = true) ||
            packageName.equals(AppCapabilityRegistry.WHATSAPP_BUSINESS_PACKAGE, ignoreCase = true)

    private fun isBankingOrPaymentPackage(packageName: String): Boolean =
        packageName.equals(AppCapabilityRegistry.MERCADO_PAGO_PACKAGE, ignoreCase = true) ||
            packageName.equals(AppCapabilityRegistry.BANCO_GALICIA_PACKAGE, ignoreCase = true) ||
            packageName.equals(AppCapabilityRegistry.SANTANDER_AR_PACKAGE, ignoreCase = true) ||
            packageName.equals(AppCapabilityRegistry.BBVA_AR_PACKAGE, ignoreCase = true) ||
            packageName.equals(AppCapabilityRegistry.ICBC_AR_PACKAGE, ignoreCase = true) ||
            packageName.equals(AppCapabilityRegistry.BANCO_NACION_PACKAGE, ignoreCase = true) ||
            packageName.equals(AppCapabilityRegistry.BANCO_PROVINCIA_PACKAGE, ignoreCase = true)

    private fun hasDestinationField(snapshot: StructuredScreenSnapshot): Boolean =
        anyScreenText(snapshot).any { normalized ->
            normalized.contains("destino") ||
                normalized.contains("a donde") ||
                normalized.contains("adonde") ||
                normalized.contains("where to") ||
                normalized.contains("direccion") ||
                normalized.contains("ingresar destino") ||
                normalized.contains("elegir destino")
        }

    private fun hasPaymentVisible(snapshot: StructuredScreenSnapshot): Boolean =
        anyScreenText(snapshot).any { normalized ->
            normalized.contains("pago") ||
                normalized.contains("payment") ||
                normalized.contains("tarjeta") ||
                normalized.contains("efectivo") ||
                normalized.contains("cash") ||
                normalized.contains("visa") ||
                normalized.contains("mastercard") ||
                normalized.contains("mercado pago")
        }

    private fun hasPriceOrDriverVisible(snapshot: StructuredScreenSnapshot): Boolean {
        val raw = rawScreenText(snapshot)
        return anyScreenText(snapshot).any { normalized ->
                normalized.contains("precio") ||
                normalized.contains("conductor") ||
                normalized.contains("driver") ||
                normalized.contains("uberx") ||
                normalized.contains("llegada") ||
                normalized.contains("min")
        } || raw.any { it.contains("$") || it.contains("ARS", ignoreCase = true) }
    }

    private fun hasRideFinalConfirmation(snapshot: StructuredScreenSnapshot): Boolean =
        snapshot.buttons.any { label ->
            val normalized = AgentTaskPlanner.normalize(label)
            normalized == "confirmar" ||
                normalized == "solicitar" ||
                normalized == "pedir" ||
                normalized == "reservar" ||
                normalized == "aceptar viaje" ||
                normalized == "continuar viaje" ||
                normalized.contains("solicitar viaje") ||
                normalized.contains("pedir viaje") ||
                normalized.contains("confirm ride") ||
                normalized.contains("request ride") ||
                normalized.contains("book ride")
        }

    private fun hasWhatsAppSearchVisible(snapshot: StructuredScreenSnapshot): Boolean =
        anyScreenText(snapshot).any { normalized ->
            normalized == "buscar" ||
                normalized.contains("buscar") ||
                normalized == "search" ||
                normalized.contains("search")
        }

    private fun hasWhatsAppMessageBox(snapshot: StructuredScreenSnapshot): Boolean =
        snapshot.editableFields.any { label ->
            val normalized = AgentTaskPlanner.normalize(label)
            normalized.contains("mensaje") ||
                normalized.contains("message") ||
                normalized.contains("escribe") ||
                normalized.contains("type a message") ||
                normalized.contains("texto")
        } ||
            snapshot.focusedLabel?.let { label ->
                val normalized = AgentTaskPlanner.normalize(label)
                normalized.contains("mensaje") ||
                    normalized.contains("message") ||
                    normalized.contains("escribe")
            } == true

    private fun hasWhatsAppSendOrMicVisible(snapshot: StructuredScreenSnapshot): Boolean =
        snapshot.buttons.any { label ->
            val normalized = AgentTaskPlanner.normalize(label)
            normalized == "enviar" ||
                normalized == "send" ||
                normalized.contains("microfono") ||
                normalized.contains("mic") ||
                normalized.contains("mensaje de voz") ||
                normalized.contains("voice message")
        }

    private fun expectedContact(plan: AgentTaskPlan): String? =
        plan.tickets
            .firstOrNull { it.requiredData.contains(AgentTaskRequiredData.CONTACT_NAME) }
            ?.resolvedData
            ?.get(AgentTaskRequiredData.CONTACT_NAME)
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    private fun messageText(plan: AgentTaskPlan): String? =
        plan.tickets
            .firstOrNull { it.requiredData.contains(AgentTaskRequiredData.MESSAGE_TEXT) }
            ?.resolvedData
            ?.get(AgentTaskRequiredData.MESSAGE_TEXT)
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    private fun screenContains(snapshot: StructuredScreenSnapshot, value: String): Boolean {
        val expected = AgentTaskPlanner.normalize(value)
        if (expected.isBlank()) return false
        return anyScreenText(snapshot).any { it.contains(expected) }
    }

    private fun rawScreenText(snapshot: StructuredScreenSnapshot): List<String> =
        snapshot.redactedTextLines +
            snapshot.buttons +
            snapshot.editableFields +
            listOfNotNull(snapshot.focusedLabel, snapshot.appLabel)

    private fun anyScreenText(snapshot: StructuredScreenSnapshot): List<String> =
        rawScreenText(snapshot)
            .map { AgentTaskPlanner.normalize(it) }
            .filter { it.isNotBlank() }
}
