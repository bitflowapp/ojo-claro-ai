package com.ojoclaro.android.ui.home

import com.ojoclaro.android.agent.AgentIntent
import com.ojoclaro.android.agent.LocalIntentParser
import com.ojoclaro.android.agent.core.screen.ScreenQueryPhrases
import com.ojoclaro.android.agent.runtime.screen.RobotStatusDiagnosticPhrases
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppChatListPhrases
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppGuidedPhrases
import com.ojoclaro.android.external.CommandRouter
import com.ojoclaro.android.external.ExternalCommandType
import com.ojoclaro.android.memory.MemoryPolicy
import com.ojoclaro.android.privacy.PrivacyGuard
import com.ojoclaro.android.voice.VoiceCommandCorrection
import com.ojoclaro.android.voice.VoiceCommandCorrectionType
import com.ojoclaro.android.voice.VoiceCommandConfirmationResponse
import com.ojoclaro.android.voice.VoiceCommandDispatcher
import com.ojoclaro.android.voice.VoiceCommandTargetIntent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RobotRoutingContractTest {

    private val router = CommandRouter()
    private val localIntentParser = LocalIntentParser(router)

    @Test
    fun globalCommandsStayLocalAndWinBeforeFallback() {
        assertEquals(Route.RESET, contractRoute("resetear").route)
        assertEquals(Route.RESET, contractRoute("volver al inicio").route)
        assertEquals(Route.RESET, contractRoute("cancelar").route)
        assertEquals(Route.STOP_SPEAKING, contractRoute("callate").route)
        assertEquals(Route.STOP_SPEAKING, contractRoute("silencio").route)
        assertEquals(Route.REPEAT_LAST, contractRoute("repeti").route)
        assertEquals(Route.HELP, contractRoute("ayuda").route)
        assertEquals(Route.DIAGNOSTIC, contractRoute("diagnostico").route)
    }

    @Test
    fun globalCommandsWinWhileVoiceCorrectionIsPending() {
        assertEquals(
            PendingVoiceCorrectionGlobalAction.RESET_FLOW,
            pendingVoiceCorrectionGlobalAction("resetear")
        )
        assertEquals(
            PendingVoiceCorrectionGlobalAction.RESET_FLOW,
            pendingVoiceCorrectionGlobalAction("volver al inicio")
        )
        assertEquals(
            PendingVoiceCorrectionGlobalAction.STOP_SPEAKING,
            pendingVoiceCorrectionGlobalAction("callate")
        )
        assertEquals(
            PendingVoiceCorrectionGlobalAction.STOP_SPEAKING,
            pendingVoiceCorrectionGlobalAction("silencio")
        )
        assertEquals(
            PendingVoiceCorrectionGlobalAction.REPEAT_LAST,
            pendingVoiceCorrectionGlobalAction("repetí")
        )
        assertEquals(
            PendingVoiceCorrectionGlobalAction.HELP,
            pendingVoiceCorrectionGlobalAction("ayuda")
        )
        assertEquals(
            PendingVoiceCorrectionGlobalAction.PAUSE_ROBOT,
            pendingVoiceCorrectionGlobalAction("pausar robot")
        )
        assertEquals(
            PendingVoiceCorrectionGlobalAction.ENABLE_ROBOT,
            pendingVoiceCorrectionGlobalAction("encender robot")
        )
        assertEquals(
            PendingVoiceCorrectionGlobalAction.CANCEL,
            pendingVoiceCorrectionGlobalAction("cancelar")
        )
    }

    @Test
    fun screenAndWhatsappRoutesKeepTheirExplicitContracts() {
        assertEquals(Route.SCREEN_UNDERSTANDING, contractRoute("que hay en pantalla").route)
        assertEquals(Route.SCREEN_UNDERSTANDING, contractRoute("donde estoy").route)
        assertEquals(Route.SCREEN_UNDERSTANDING, contractRoute("que puedo hacer aca").route)
        assertEquals(Route.SCREEN_UNDERSTANDING, contractRoute("leeme lo importante").route)

        assertEquals(Route.EXTERNAL_COMMAND, contractRoute("abri WhatsApp").route)
        assertEquals(ExternalCommandType.OPEN_WHATSAPP, contractRoute("abri WhatsApp").externalType)
        assertEquals(Route.EXTERNAL_COMMAND, contractRoute("abrir wp").route)
        assertEquals(ExternalCommandType.OPEN_WHATSAPP, contractRoute("abrir wp").externalType)
        assertEquals(Route.EXTERNAL_COMMAND, contractRoute("abri el wasap").route)
        assertEquals(ExternalCommandType.OPEN_WHATSAPP, contractRoute("abri el wasap").externalType)

        assertEquals(Route.VISIBLE_CHATS, contractRoute("que chats ves").route)
        assertFalse(ScreenQueryPhrases.classify("que chats ves") != null)
        assertEquals(Route.WHATSAPP_GUIDED, contractRoute("que puedo hacer en este chat").route)
        assertEquals(Route.WHATSAPP_GUIDED, contractRoute("como mando una foto").route)
        assertEquals(Route.WHATSAPP_GUIDED, contractRoute("como mando ubicacion").route)
        assertEquals(Route.WHATSAPP_GUIDED, contractRoute("como le mando un mensaje").route)
    }

    @Test
    fun fuzzyRoutesOnlyLowRiskCommandsAndNeverSamsungNoise() {
        listOf("abrir ure Max", "abrir guasap", "abrir wasap").forEach { phrase ->
            val result = contractRoute(phrase)

            assertEquals(Route.EXTERNAL_COMMAND, result.route, phrase)
            assertEquals(ExternalCommandType.OPEN_WHATSAPP, result.externalType, phrase)
            assertEquals(VoiceCommandTargetIntent.OPEN_WHATSAPP, result.correctionTarget, phrase)
            assertTrue(result.usedFuzzyAutoCorrection, phrase)
        }

        listOf("cancion de Marco Antonio", "android", "si Aurelio").forEach { phrase ->
            val result = contractRoute(phrase)

            assertEquals(Route.NOISE_OR_FALLBACK, result.route, phrase)
            assertFalse(result.usedFuzzyAutoCorrection, phrase)
            assertFalse(result.externalType == ExternalCommandType.OPEN_WHATSAPP, phrase)
            assertFalse(result.externalType == ExternalCommandType.COMPOSE_WHATSAPP_MESSAGE, phrase)
            assertFalse(result.externalType == ExternalCommandType.NAVIGATE_TO_DESTINATION, phrase)
        }
    }

    @Test
    fun fuzzyMediumWaitsForStrictConfirmationAndCanOnlyConfirmLowRisk() {
        val correction = VoiceCommandCorrection.correct("abrir whats")

        assertEquals(VoiceCommandCorrectionType.CONFIRMATION_REQUIRED, correction.correctionType)
        assertEquals(Route.WAITING_CONFIRMATION, contractRoute("abrir whats").route)
        assertEquals(VoiceCommandTargetIntent.OPEN_WHATSAPP, correction.targetIntent)
        assertTrue(correction.canBeConfirmedSafely)
        assertEquals(
            VoiceCommandConfirmationResponse.CONFIRM,
            VoiceCommandCorrection.confirmationResponse("sí")
        )
        assertEquals(
            VoiceCommandConfirmationResponse.CANCEL,
            VoiceCommandCorrection.confirmationResponse("no")
        )
        assertEquals(
            VoiceCommandConfirmationResponse.NONE,
            VoiceCommandCorrection.confirmationResponse("sí Aurelio")
        )
    }

    @Test
    fun correctionLayerDoesNotAutoRouteDangerousActions() {
        listOf(
            "mandale mensaje a Marco",
            "enviar ubicación",
            "llamar a Marco",
            "pagar",
            "banco",
            "contraseña",
            "tarjeta"
        ).forEach { phrase ->
            val correction = VoiceCommandCorrection.correct(phrase)

            assertEquals(VoiceCommandCorrectionType.REJECTED_SENSITIVE, correction.correctionType, phrase)
            assertFalse(correction.shouldAutoExecute, phrase)
        }
    }

    @Test
    fun safeAiFallbackStartsOnlyAfterLocalRoutesDecline() {
        listOf(
            "explicame esta app",
            "ubicame en esta app",
            "ayudame a entender esto"
        ).forEach { phrase ->
            val result = contractRoute(phrase)

            assertEquals(Route.SAFE_AI_FALLBACK, result.route, phrase)
            assertFalse(result.usedFuzzyAutoCorrection, phrase)
        }
    }

    @Test
    fun sensitiveCommandsCannotUseFuzzyAutoExecution() {
        listOf(
            "mandale mensaje a Marco",
            "enviar ubicacion",
            "pagar",
            "banco",
            "clave",
            "contrasena",
            "cbu",
            "tarjeta"
        ).forEach { phrase ->
            val correction = VoiceCommandCorrection.correct(phrase)

            assertEquals(VoiceCommandCorrectionType.REJECTED_SENSITIVE, correction.correctionType, phrase)
            assertFalse(correction.shouldAutoExecute, phrase)
            assertEquals(VoiceCommandTargetIntent.NONE, correction.targetIntent, phrase)
        }
    }

    private fun contractRoute(text: String): ContractRoute {
        val correction = VoiceCommandCorrection.correct(text)
        if (correction.shouldAutoExecute) {
            val routed = contractRouteWithoutFuzzy(correction.correctedText)
            return routed.copy(
                usedFuzzyAutoCorrection = true,
                correctionTarget = correction.targetIntent
            )
        }
        if (correction.correctionType == VoiceCommandCorrectionType.CONFIRMATION_REQUIRED) {
            return ContractRoute(Route.WAITING_CONFIRMATION, correctionTarget = correction.targetIntent)
        }
        if (VoiceCommandCorrection.isKnownRecognizerNoise(text)) {
            return ContractRoute(Route.NOISE_OR_FALLBACK)
        }
        return contractRouteWithoutFuzzy(text)
    }

    private fun contractRouteWithoutFuzzy(text: String): ContractRoute =
        when {
            robotSessionCommand(text) != RobotSessionCommand.NONE -> ContractRoute(Route.ROBOT_SESSION)
            VoiceCommandDispatcher.isStopCommand(text) -> ContractRoute(Route.STOP_SPEAKING)
            isResetFlowCommand(text) -> ContractRoute(Route.RESET)
            controlCommandKeyForTest(text) in setOf("cancelar", "cancela") -> ContractRoute(Route.RESET)
            isRepeatLastResponseCommand(text) -> ContractRoute(Route.REPEAT_LAST)
            VoiceCommandDispatcher.isHelpCommand(text) -> ContractRoute(Route.HELP)
            RobotStatusDiagnosticPhrases.isDiagnosticCommand(text) -> ContractRoute(Route.DIAGNOSTIC)
            ScreenQueryPhrases.classify(text) != null -> ContractRoute(Route.SCREEN_UNDERSTANDING)
            WhatsAppGuidedPhrases.classify(text) != null -> ContractRoute(Route.WHATSAPP_GUIDED)
            WhatsAppChatListPhrases.isChatListCommand(text) -> ContractRoute(Route.VISIBLE_CHATS)
            shouldHandleExternalCommand(text, hasPendingConsent = false, router = router) -> {
                ContractRoute(Route.EXTERNAL_COMMAND, externalType = router.parse(text).type)
            }
            PrivacyGuard.containsSensitiveFinancialData(text) ||
                MemoryPolicy.containsProhibitedContent(text) -> ContractRoute(Route.BLOCKED_SENSITIVE)
            localIntentParser.parse(text).intent != AgentIntent.UNKNOWN -> ContractRoute(Route.LOCAL_AGENT)
            else -> ContractRoute(Route.SAFE_AI_FALLBACK)
        }

    private data class ContractRoute(
        val route: Route,
        val externalType: ExternalCommandType? = null,
        val usedFuzzyAutoCorrection: Boolean = false,
        val correctionTarget: VoiceCommandTargetIntent = VoiceCommandTargetIntent.NONE
    )

    private enum class Route {
        ROBOT_SESSION,
        STOP_SPEAKING,
        RESET,
        REPEAT_LAST,
        HELP,
        DIAGNOSTIC,
        SCREEN_UNDERSTANDING,
        WHATSAPP_GUIDED,
        VISIBLE_CHATS,
        EXTERNAL_COMMAND,
        LOCAL_AGENT,
        SAFE_AI_FALLBACK,
        WAITING_CONFIRMATION,
        BLOCKED_SENSITIVE,
        NOISE_OR_FALLBACK
    }

    private fun controlCommandKeyForTest(text: String): String =
        text.lowercase()
            .replace("á", "a")
            .replace("é", "e")
            .replace("í", "i")
            .replace("ó", "o")
            .replace("ú", "u")
            .replace(Regex("\\s+"), " ")
            .trim()
}
