package com.ojoclaro.android.safety

import com.ojoclaro.android.agent.intelligence.ActiveApp
import com.ojoclaro.android.agent.intelligence.ActiveAppResolver
import com.ojoclaro.android.agent.intelligence.Confidence
import com.ojoclaro.android.agent.intelligence.ConfirmationVerdict
import com.ojoclaro.android.agent.intelligence.MessagingTaskPlanner
import com.ojoclaro.android.agent.intelligence.RawScreen
import com.ojoclaro.android.agent.intelligence.ReasonerNode
import com.ojoclaro.android.agent.intelligence.ResolutionSource
import com.ojoclaro.android.agent.intelligence.ResolvedContact
import com.ojoclaro.android.agent.intelligence.RiskyControl
import com.ojoclaro.android.agent.intelligence.ScreenModel
import com.ojoclaro.android.agent.intelligence.ScreenType
import com.ojoclaro.android.agent.intelligence.TargetApp
import com.ojoclaro.android.agent.intelligence.UberCopilotNarrator
import com.ojoclaro.android.agent.intelligence.UberCopilotPhrases
import com.ojoclaro.android.agent.intelligence.UberIntent
import com.ojoclaro.android.agent.intelligence.UberRiskyControl
import com.ojoclaro.android.agent.intelligence.UberScreenReasoner
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppVoiceSendPhrases
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Contrato de seguridad CRUZADO (puro). Regresión: si alguien debilita una de
 * estas invariantes, este test falla. Complementa (no reemplaza) a
 * MessagingTaskPlannerTest / UberCopilotTest / ScreenIntelligenceTest.
 *
 * Reglas duras verificadas:
 *  - "sí"/"dale"/"ok"/etc. NUNCA confirman un envío ni un viaje.
 *  - el envío real exige confirmación FUERTE + chat verificado + sin pagos.
 *  - el botón final de Uber se marca riesgoso; "confirmo pedir Uber ahora" no toca.
 *  - SystemUI/lockscreen no se confunde con una app real.
 */
class SafetyRegressionTest {

    // ---------- WhatsApp / Instagram: envío seguro ----------

    private val marco = ResolvedContact("Marco Luna", TargetApp.WHATSAPP, ResolutionSource.EXACT_VISIBLE, 0)
    private fun conv(title: String?, risky: List<RiskyControl> = emptyList()) =
        ScreenModel(ActiveApp.WHATSAPP, ScreenType.CONVERSATION, title, emptyList(), 2, true, true, risky, false, Confidence.HIGH, "")

    @Test fun extendedWeakAffirmationsAllRejected() {
        for (w in listOf("si", "sí", "dale", "ok", "okey", "oka", "sip", "obvio", "claro")) {
            assertEquals(
                ConfirmationVerdict.WEAK_REJECTED, MessagingTaskPlanner.classifyConfirmation(w),
                "'$w' debería ser WEAK_REJECTED"
            )
        }
    }

    @Test fun weakAffirmationsNeverSend() {
        for (w in listOf("si", "dale", "ok", "okey", "claro")) {
            assertFalse(
                MessagingTaskPlanner.canSend(w, marco, conv("Marco Luna"), chatVerified = true, preparedTextMatchesField = true),
                "'$w' jamás debe enviar"
            )
        }
    }

    @Test fun strongConfirmSendsOnlyToVerifiedMatchingChat() {
        assertTrue(MessagingTaskPlanner.canSend("mandalo", marco, conv("Marco Luna"), true, true))
        // contacto equivocado
        assertFalse(MessagingTaskPlanner.canSend("mandalo", marco, conv("Sofía"), true, true))
        // tarjeta a la vista
        assertFalse(MessagingTaskPlanner.canSend("mandalo", marco, conv("Marco Luna", listOf(RiskyControl.CARD)), true, true))
        // pago a la vista
        assertFalse(MessagingTaskPlanner.canSend("mandalo", marco, conv("Marco Luna", listOf(RiskyControl.PAYMENT)), true, true))
        // chat no verificado
        assertFalse(MessagingTaskPlanner.canSend("mandalo", marco, conv("Marco Luna"), chatVerified = false, preparedTextMatchesField = true))
    }

    @Test fun whatsAppBareWeakNeverConfirmsSendCancelAlwaysWins() {
        assertFalse(WhatsAppVoiceSendPhrases.isConfirmSend("si"))
        assertFalse(WhatsAppVoiceSendPhrases.isConfirmSend("dale"))
        assertFalse(WhatsAppVoiceSendPhrases.isConfirmSend("ok"))
        assertTrue(WhatsAppVoiceSendPhrases.isConfirmSend("mandalo"))
        assertTrue(WhatsAppVoiceSendPhrases.isCancelSend("no"))
        assertTrue(WhatsAppVoiceSendPhrases.isCancelSend("cancelar"))
    }

    @Test fun sensitivePayloadsBlockedBeforeSend() {
        assertTrue(WhatsAppVoiceSendPhrases.looksSensitive("mi clave es secreta"))
        assertTrue(WhatsAppVoiceSendPhrases.looksSensitive("el cvv 123"))
        assertTrue(WhatsAppVoiceSendPhrases.looksSensitive("tarjeta 1234 5678 9012 3456"))
        assertFalse(WhatsAppVoiceSendPhrases.looksSensitive("ya salgo, llego en diez minutos"))
    }

    // ---------- Uber: copiloto guiado, sin pedir ----------

    private fun uberScreen(vararg nodes: ReasonerNode) =
        UberScreenReasoner.reason(RawScreen("com.ubercab", nodes.toList()))

    @Test fun uberWeakAndCasualNeverConfirm() {
        for (w in listOf("sí", "dale", "ok", "confirmo", "pedilo", "pedi el uber")) {
            assertNull(UberCopilotPhrases.parse(w), "'$w' no debe ser intención de Uber")
        }
    }

    @Test fun uberStrongConfirmRecognizedButNeverTaps() {
        assertEquals(UberIntent.ConfirmRequest, UberCopilotPhrases.parse("confirmo pedir Uber ahora"))
        val msg = UberCopilotNarrator.confirmRequestBlocked()
        assertTrue(msg.contains("no tengo habilitado", ignoreCase = true))
        assertTrue(msg.contains("toques vos", ignoreCase = true))
    }

    @Test fun uberFinalButtonsAreFlaggedRisky() {
        // "Solicita un viaje" / "Solicitar viaje" / "Request ride" → REQUEST_RIDE.
        assertTrue(uberScreen(ReasonerNode(text = "Solicita un viaje", isClickable = true))
            .riskyControls.contains(UberRiskyControl.REQUEST_RIDE))
        assertTrue(uberScreen(ReasonerNode(text = "Solicitar viaje", isClickable = true))
            .riskyControls.contains(UberRiskyControl.REQUEST_RIDE))
        assertTrue(uberScreen(ReasonerNode(text = "Request ride", isClickable = true))
            .riskyControls.contains(UberRiskyControl.REQUEST_RIDE))
        // "Confirmar Uber" / "Confirm ride" → CONFIRM_RIDE (también riesgoso).
        assertTrue(uberScreen(ReasonerNode(text = "Confirmar Uber", isClickable = true))
            .riskyControls.contains(UberRiskyControl.CONFIRM_RIDE))
        assertTrue(uberScreen(ReasonerNode(text = "Confirm ride", isClickable = true))
            .riskyControls.contains(UberRiskyControl.CONFIRM_RIDE))
    }

    @Test fun uberDoesNotConfusePriceWithEtaDistanceRating() {
        val m = uberScreen(
            ReasonerNode(text = "UberX"), ReasonerNode(text = "5 min"),
            ReasonerNode(text = "3 km"), ReasonerNode(text = "4.8 estrellas")
        )
        assertNull(m.priceText)
    }

    // ---------- Screen Intelligence: no confundir SystemUI ----------

    @Test fun systemUiNeverConfusedWithRealApp() {
        // Paquete concreto (systemui) → OTHER, no se fuerza una app.
        val r = ActiveAppResolver.resolve(
            rawPackage = "com.android.systemui",
            externalIsWhatsApp = true,         // estado viejo
            inInstagramByMarkers = true,       // estado viejo
            nodes = emptyList(),
        )
        assertEquals(ActiveApp.OTHER, r.app)
    }

    @Test fun foregroundAppPackagesResolveCorrectly() {
        assertEquals(
            ActiveApp.INSTAGRAM,
            ActiveAppResolver.resolve("com.instagram.android", false, false, emptyList()).app
        )
        assertEquals(
            ActiveApp.WHATSAPP,
            ActiveAppResolver.resolve("com.whatsapp", false, false, emptyList()).app
        )
    }
}
