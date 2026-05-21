package com.ojoclaro.android.agent.task.followup

import com.ojoclaro.android.agent.apps.AppCapabilityRegistry
import com.ojoclaro.android.agent.core.screen.StructuredScreenSnapshot
import com.ojoclaro.android.agent.task.AgentTaskPlan
import com.ojoclaro.android.agent.task.AgentTaskPlanner
import com.ojoclaro.android.agent.task.AgentTaskRequiredData
import com.ojoclaro.android.agent.task.AgentTaskType
import java.util.Locale

data class AgentTaskFollowUpPolicyResult(
    val shouldObserve: Boolean,
    val trigger: AgentTaskFollowUpTrigger,
    val importance: AgentTaskFollowUpImportance = AgentTaskFollowUpImportance.LOW,
    val semanticKey: String? = null,
    val reasonKey: String? = null
)

class AgentTaskFollowUpPolicy {

    fun evaluate(event: AgentTaskFollowUpEvent): AgentTaskFollowUpPolicyResult {
        val plan = event.currentPlan
            ?: return noObserve(AgentTaskFollowUpTrigger.NO_ACTIVE_TASK)
        val current = event.currentSnapshot
            ?: return noObserve(AgentTaskFollowUpTrigger.NO_SNAPSHOT)
        if (current.isEmpty) return noObserve(AgentTaskFollowUpTrigger.NO_SNAPSHOT)

        val previous = event.previousSnapshot
        val currentSensitiveReason = sensitiveReason(current)
        val previousSensitiveReason = previous?.let(::sensitiveReason)
        if (currentSensitiveReason != null &&
            (previousSensitiveReason != currentSensitiveReason || packageChanged(previous, current))
        ) {
            return observe(
                trigger = AgentTaskFollowUpTrigger.SENSITIVE_SCREEN,
                importance = AgentTaskFollowUpImportance.CRITICAL,
                semanticKey = "task.followup.sensitive",
                reasonKey = currentSensitiveReason
            )
        }

        val previousFingerprint = previous?.let { fingerprintFor(plan, it) }
        val currentFingerprint = fingerprintFor(plan, current)
        val newCue = firstNewCue(previousFingerprint, currentFingerprint)
        if (newCue != null) {
            return observe(
                trigger = AgentTaskFollowUpTrigger.TASK_SCREEN_CUE_CHANGED,
                importance = importanceForCue(newCue),
                semanticKey = semanticKeyForCue(newCue, current.packageName),
                reasonKey = newCue
            )
        }

        if (packageChanged(previous, current)) {
            return observe(
                trigger = AgentTaskFollowUpTrigger.PACKAGE_CHANGED,
                importance = AgentTaskFollowUpImportance.NORMAL,
                semanticKey = semanticKeyForPackage(current.packageName),
                reasonKey = current.packageName?.takeIf { it.isNotBlank() }
            )
        }

        return noObserve(AgentTaskFollowUpTrigger.NO_RELEVANT_CHANGE)
    }

    fun sensitiveReason(snapshot: StructuredScreenSnapshot): String? {
        val packageName = snapshot.packageName.orEmpty()
        return when {
            snapshot.signals.hasPasswordField -> "password"
            snapshot.signals.hasVerificationCode -> "verification_code"
            snapshot.signals.isBankingApp || isBankingOrPaymentPackage(packageName) ->
                "banking_or_payment"
            snapshot.signals.hasPaymentOrTransferSignals &&
                !isRidePackage(packageName) &&
                !isWhatsAppPackage(packageName) -> "banking_or_payment"
            else -> null
        }
    }

    private fun noObserve(trigger: AgentTaskFollowUpTrigger): AgentTaskFollowUpPolicyResult =
        AgentTaskFollowUpPolicyResult(
            shouldObserve = false,
            trigger = trigger
        )

    private fun observe(
        trigger: AgentTaskFollowUpTrigger,
        importance: AgentTaskFollowUpImportance,
        semanticKey: String,
        reasonKey: String?
    ): AgentTaskFollowUpPolicyResult =
        AgentTaskFollowUpPolicyResult(
            shouldObserve = true,
            trigger = trigger,
            importance = importance,
            semanticKey = semanticKey,
            reasonKey = reasonKey
        )

    private fun packageChanged(
        previous: StructuredScreenSnapshot?,
        current: StructuredScreenSnapshot
    ): Boolean {
        if (previous == null) return false
        val previousPackage = previous.packageName?.takeIf { it.isNotBlank() }
        val currentPackage = current.packageName?.takeIf { it.isNotBlank() }
        return currentPackage != null && previousPackage != currentPackage
    }

    private fun fingerprintFor(
        plan: AgentTaskPlan,
        snapshot: StructuredScreenSnapshot
    ): TaskFollowUpFingerprint {
        val cues = when (plan.type) {
            AgentTaskType.REQUEST_RIDE -> rideCues(snapshot)
            AgentTaskType.SEND_WHATSAPP_MESSAGE,
            AgentTaskType.SEND_WHATSAPP_AUDIO -> whatsAppCues(plan, snapshot)
            else -> emptySet()
        }
        return TaskFollowUpFingerprint(cues = cues)
    }

    private fun firstNewCue(
        previous: TaskFollowUpFingerprint?,
        current: TaskFollowUpFingerprint
    ): String? {
        val previousCues = previous?.cues.orEmpty()
        return CUE_PRIORITY.firstOrNull { cue ->
            cue in current.cues && cue !in previousCues
        }
    }

    private fun rideCues(snapshot: StructuredScreenSnapshot): Set<String> {
        val cues = LinkedHashSet<String>()
        if (isRidePackage(snapshot.packageName.orEmpty())) cues += CUE_RIDE_APP
        if (hasDestinationField(snapshot)) cues += CUE_RIDE_DESTINATION
        if (hasPaymentVisible(snapshot)) cues += CUE_RIDE_PAYMENT
        if (hasPriceOrDriverVisible(snapshot)) cues += CUE_RIDE_PRICE_DRIVER
        if (hasRideFinalConfirmation(snapshot)) cues += CUE_RIDE_FINAL
        return cues
    }

    private fun whatsAppCues(
        plan: AgentTaskPlan,
        snapshot: StructuredScreenSnapshot
    ): Set<String> {
        val cues = LinkedHashSet<String>()
        if (isWhatsAppPackage(snapshot.packageName.orEmpty())) cues += CUE_WHATSAPP_OPEN
        if (hasWhatsAppSearchVisible(snapshot)) cues += CUE_WHATSAPP_SEARCH
        val contact = expectedContact(plan)
        if (contact != null && screenContains(snapshot, contact)) {
            cues += CUE_WHATSAPP_CHAT_CANDIDATE
        }
        if (hasWhatsAppMessageBox(snapshot)) cues += CUE_WHATSAPP_MESSAGE_BOX
        if (hasWhatsAppSendOrMicVisible(snapshot)) cues += CUE_WHATSAPP_SEND_OR_MIC
        return cues
    }

    private fun importanceForCue(cue: String): AgentTaskFollowUpImportance =
        when (cue) {
            CUE_RIDE_PAYMENT,
            CUE_RIDE_PRICE_DRIVER,
            CUE_RIDE_FINAL,
            CUE_WHATSAPP_CHAT_CANDIDATE,
            CUE_WHATSAPP_SEND_OR_MIC -> AgentTaskFollowUpImportance.HIGH
            else -> AgentTaskFollowUpImportance.NORMAL
        }

    private fun semanticKeyForCue(cue: String, packageName: String?): String =
        "task.followup.cue.$cue.${packageName.orEmpty().lowercase(Locale.ROOT)}"

    private fun semanticKeyForPackage(packageName: String?): String =
        "task.followup.package.${packageName.orEmpty().lowercase(Locale.ROOT)}"

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

    private fun isRidePackage(packageName: String): Boolean =
        packageName.equals(AppCapabilityRegistry.UBER_PACKAGE, ignoreCase = true) ||
            packageName.equals(AppCapabilityRegistry.CABIFY_PACKAGE, ignoreCase = true) ||
            packageName.equals(AppCapabilityRegistry.DIDI_PACKAGE, ignoreCase = true)

    private fun isWhatsAppPackage(packageName: String): Boolean =
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

    private data class TaskFollowUpFingerprint(
        val cues: Set<String>
    )

    companion object {
        const val CUE_RIDE_APP: String = "ride_app"
        const val CUE_RIDE_DESTINATION: String = "ride_destination"
        const val CUE_RIDE_PAYMENT: String = "ride_payment"
        const val CUE_RIDE_PRICE_DRIVER: String = "ride_price_driver"
        const val CUE_RIDE_FINAL: String = "ride_final_confirmation"
        const val CUE_WHATSAPP_OPEN: String = "whatsapp_open"
        const val CUE_WHATSAPP_SEARCH: String = "whatsapp_search"
        const val CUE_WHATSAPP_CHAT_CANDIDATE: String = "whatsapp_chat_candidate"
        const val CUE_WHATSAPP_MESSAGE_BOX: String = "whatsapp_message_box"
        const val CUE_WHATSAPP_SEND_OR_MIC: String = "whatsapp_send_or_mic"

        private val CUE_PRIORITY: List<String> = listOf(
            CUE_RIDE_FINAL,
            CUE_RIDE_PAYMENT,
            CUE_RIDE_PRICE_DRIVER,
            CUE_RIDE_DESTINATION,
            CUE_WHATSAPP_SEND_OR_MIC,
            CUE_WHATSAPP_MESSAGE_BOX,
            CUE_WHATSAPP_CHAT_CANDIDATE,
            CUE_WHATSAPP_SEARCH,
            CUE_WHATSAPP_OPEN,
            CUE_RIDE_APP
        )
    }
}
