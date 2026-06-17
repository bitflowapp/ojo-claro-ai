package com.ojoclaro.android.agent.estela

import com.ojoclaro.android.agent.runtime.whatsapp.VisibleScreenCommand
import com.ojoclaro.android.agent.runtime.whatsapp.VisibleScreenCommandParser
import com.ojoclaro.android.external.CommandRouter
import com.ojoclaro.android.external.ExternalCommandType

class EstelaSimplePlanner(
    private val commandRouter: CommandRouter = CommandRouter()
) {

    fun plan(rawText: String, context: EstelaAgentContext): EstelaPlanningResult {
        val normalized = EstelaSafetyPolicy.normalize(rawText)

        if (normalized in CONFIRM_PHRASES) {
            return EstelaPlanningResult(
                intent = EstelaIntent.Confirm,
                plan = controlPlan(
                    rawText = rawText,
                    intentName = "confirm",
                    spokenSummary = "Confirmado.",
                    step = EstelaPlanStep.Complete("confirmed")
                )
            )
        }

        if (normalized in CANCEL_PHRASES) {
            return EstelaPlanningResult(
                intent = EstelaIntent.Cancel,
                plan = controlPlan(
                    rawText = rawText,
                    intentName = "cancel",
                    spokenSummary = "Cancelado.",
                    step = EstelaPlanStep.CancelPendingAction
                )
            )
        }

        if (isHelpCommand(normalized)) {
            val spoken = "Podés pedirme leer la pantalla, abrir WhatsApp, abrir un chat visible o preparar un mensaje."
            return EstelaPlanningResult(
                intent = EstelaIntent.Help,
                plan = EstelaPlan(
                    id = idFor("help", rawText),
                    userGoal = "Pedir ayuda",
                    steps = listOf(
                        EstelaPlanStep.Speak(spoken),
                        EstelaPlanStep.Complete("help_shown")
                    ),
                    riskLevel = EstelaRiskLevel.LOW,
                    requiresConfirmation = false,
                    spokenSummary = spoken,
                    visibleSummary = spoken
                )
            )
        }

        val routed = commandRouter.parse(rawText)
        if (routed.type == ExternalCommandType.OPEN_WHATSAPP) {
            return openWhatsAppPlan(rawText)
        }

        if (routed.type == ExternalCommandType.READ_VISIBLE_SCREEN || isReadScreenCommand(normalized)) {
            return readScreenPlan(rawText)
        }

        if (routed.type == ExternalCommandType.COMPOSE_WHATSAPP_MESSAGE) {
            val target = routed.contactName.orEmpty()
            val message = routed.messageText.orEmpty()
            if (target.isNotBlank() && message.isNotBlank()) {
                return composeMessagePlan(rawText, target, message)
            }
        }

        val visibleCommand = VisibleScreenCommandParser.parse(rawText)
        if (visibleCommand is VisibleScreenCommand.OpenVisibleChat) {
            return openVisibleChatPlan(rawText, visibleCommand.targetName, context)
        }

        parseDialContact(normalized)?.let { contact ->
            return dialContactPlan(rawText, contact)
        }

        return EstelaPlanningResult(
            intent = EstelaIntent.Unknown(rawText),
            plan = null
        )
    }

    private fun openWhatsAppPlan(rawText: String): EstelaPlanningResult {
        val spoken = "Entendí: querés abrir WhatsApp."
        return EstelaPlanningResult(
            intent = EstelaIntent.OpenApp("WhatsApp"),
            plan = EstelaPlan(
                id = idFor("open-whatsapp", rawText),
                userGoal = "Abrir WhatsApp",
                steps = listOf(
                    EstelaPlanStep.Speak(spoken),
                    EstelaPlanStep.OpenExternalApp("WhatsApp"),
                    EstelaPlanStep.UpdateState(EstelaLiveState.Completed),
                    EstelaPlanStep.Complete("WhatsApp abierto")
                ),
                riskLevel = EstelaRiskLevel.LOW,
                requiresConfirmation = false,
                spokenSummary = spoken,
                visibleSummary = "WhatsApp abierto. Puedo ayudarte con lo que ves en pantalla."
            )
        )
    }

    private fun readScreenPlan(rawText: String): EstelaPlanningResult {
        val spoken = "Voy a leer la pantalla."
        return EstelaPlanningResult(
            intent = EstelaIntent.ReadScreen,
            plan = EstelaPlan(
                id = idFor("read-screen", rawText),
                userGoal = "Leer la pantalla visible",
                steps = listOf(
                    EstelaPlanStep.Speak(spoken),
                    EstelaPlanStep.ReadVisibleScreen,
                    EstelaPlanStep.Complete("screen_read_requested")
                ),
                riskLevel = EstelaRiskLevel.LOW,
                requiresConfirmation = false,
                spokenSummary = spoken,
                visibleSummary = spoken
            )
        )
    }

    private fun openVisibleChatPlan(
        rawText: String,
        targetName: String,
        context: EstelaAgentContext
    ): EstelaPlanningResult {
        val spoken = "Veo $targetName. Necesito tu confirmación para abrir el chat."
        val goalPrefix = if (context.currentExternalApp.equals("WhatsApp", ignoreCase = true)) {
            "Abrir chat visible en WhatsApp"
        } else {
            "Abrir chat visible"
        }
        return EstelaPlanningResult(
            intent = EstelaIntent.OpenVisibleChat(targetName),
            plan = EstelaPlan(
                id = idFor("open-visible-chat", rawText),
                userGoal = "$goalPrefix: $targetName",
                steps = listOf(
                    EstelaPlanStep.Speak(spoken),
                    EstelaPlanStep.ReadVisibleScreen,
                    EstelaPlanStep.FindVisibleTarget(targetName),
                    EstelaPlanStep.RequestConfirmation(
                        summary = spoken,
                        confirmationToken = idFor("confirm-visible-chat", rawText)
                    )
                ),
                riskLevel = EstelaRiskLevel.MEDIUM,
                requiresConfirmation = true,
                spokenSummary = spoken,
                visibleSummary = spoken
            )
        )
    }

    private fun composeMessagePlan(
        rawText: String,
        target: String,
        message: String
    ): EstelaPlanningResult {
        val spoken = "Puedo preparar el mensaje para $target. Necesito tu confirmación."
        return EstelaPlanningResult(
            intent = EstelaIntent.ComposeMessage(target = target, message = message),
            plan = EstelaPlan(
                id = idFor("compose-message", rawText),
                userGoal = "Preparar mensaje para $target",
                steps = listOf(
                    EstelaPlanStep.Speak(spoken),
                    EstelaPlanStep.PrepareMessage(target = target, message = message),
                    EstelaPlanStep.RequestConfirmation(
                        summary = spoken,
                        confirmationToken = idFor("confirm-message", rawText)
                    )
                ),
                riskLevel = EstelaRiskLevel.MEDIUM,
                requiresConfirmation = true,
                spokenSummary = spoken,
                visibleSummary = spoken
            )
        )
    }

    private fun dialContactPlan(rawText: String, contact: String): EstelaPlanningResult {
        val spoken = "Puedo abrir el marcador para $contact. No voy a llamar automáticamente."
        return EstelaPlanningResult(
            intent = EstelaIntent.DialContact(contact),
            plan = EstelaPlan(
                id = idFor("dial-contact", rawText),
                userGoal = "Preparar llamada a $contact",
                steps = listOf(
                    EstelaPlanStep.Speak(spoken),
                    EstelaPlanStep.OpenDialer(contact),
                    EstelaPlanStep.RequestConfirmation(
                        summary = spoken,
                        confirmationToken = idFor("confirm-dial", rawText)
                    )
                ),
                riskLevel = EstelaRiskLevel.MEDIUM,
                requiresConfirmation = true,
                spokenSummary = spoken,
                visibleSummary = spoken
            )
        )
    }

    private fun controlPlan(
        rawText: String,
        intentName: String,
        spokenSummary: String,
        step: EstelaPlanStep
    ): EstelaPlan = EstelaPlan(
        id = idFor(intentName, rawText),
        userGoal = spokenSummary,
        steps = listOf(
            EstelaPlanStep.Speak(spokenSummary),
            step
        ),
        riskLevel = EstelaRiskLevel.LOW,
        requiresConfirmation = false,
        spokenSummary = spokenSummary,
        visibleSummary = spokenSummary
    )

    private fun isReadScreenCommand(normalized: String): Boolean =
        normalized in READ_SCREEN_PHRASES ||
            normalized.contains("lee la pantalla") ||
            normalized.contains("leer la pantalla") ||
            normalized.contains("leeme la pantalla")

    private fun isHelpCommand(normalized: String): Boolean =
        normalized in HELP_PHRASES ||
            normalized.startsWith("ayuda ") ||
            normalized.endsWith(" ayuda")

    private fun parseDialContact(normalized: String): String? {
        for (regex in DIAL_CONTACT_REGEXES) {
            val match = regex.matchEntire(normalized) ?: continue
            val contact = match.groupValues[1]
                .replace(Regex("\\s+"), " ")
                .trim()
            if (contact.length >= 2) return contact
        }
        return null
    }

    companion object {
        private val CONFIRM_PHRASES = setOf(
            "confirmar",
            "confirmo",
            "aceptar",
            "confirmar mensaje",
            "confirmar el mensaje",
            "confirmar llamada",
            "confirmar la llamada"
        )

        private val CANCEL_PHRASES = setOf(
            "cancelar",
            "cancela",
            "anular",
            "no"
        )

        private val HELP_PHRASES = setOf(
            "ayuda",
            "ayudame",
            "que podes hacer",
            "que puedo pedirte",
            "estela ayuda"
        )

        private val READ_SCREEN_PHRASES = setOf(
            "lee la pantalla",
            "lee pantalla",
            "leer pantalla",
            "leer la pantalla",
            "leeme la pantalla",
            "que dice la pantalla"
        )

        private val DIAL_CONTACT_REGEXES = listOf(
            Regex("^(?:llama|llamar)\\s+a\\s+(.+)$"),
            Regex("^(?:quiero\\s+llamar|prepara\\s+llamada)\\s+a\\s+(.+)$")
        )

        private fun idFor(prefix: String, rawText: String): String {
            val hash = rawText.hashCode().toLong().let { if (it < 0) -it else it }
            return "estela-$prefix-$hash"
        }
    }
}
