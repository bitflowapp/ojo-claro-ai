package com.ojoclaro.android.agent.core.emergency

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.io.File

class EmergencyPolicyTest {

    private val policy = EmergencyPolicy()

    private val contact = EmergencyContact(
        displayName = "Sofi",
        phoneE164 = "+5491100000000",
        relation = "Hija"
    )

    @Test
    fun recognizesEmergencyPhrases() {
        listOf(
            "emergencia",
            "Necesito ayuda",
            "auxilio",
            "estoy en peligro",
            "ayudame por favor"
        ).forEach { phrase ->
            assertTrue(policy.isEmergencyPhrase(phrase), "should recognize: $phrase")
        }
    }

    @Test
    fun ignoresNormalPhrases() {
        listOf(
            "hola",
            "abrí WhatsApp",
            "leeme la pantalla",
            "qué hora es"
        ).forEach { phrase ->
            assertFalse(policy.isEmergencyPhrase(phrase))
        }
    }

    @Test
    fun planWithContactUsesDialerWithContact() {
        val plan = policy.buildPlan(emergencyContact = contact)
        assertTrue(plan.primaryAction is EmergencyPrimaryAction.OpenDialerForContact)
        val primary = plan.primaryAction as EmergencyPrimaryAction.OpenDialerForContact
        assertEquals(contact, primary.contact)
        assertEquals(0, plan.countdownSeconds)
        assertTrue(plan.spokenIntroduction.contains("confirmar"))
        assertFalse(plan.spokenIntroduction.contains("voy a actuar", ignoreCase = true))
        assertFalse(plan.spokenIntroduction.contains("segundos", ignoreCase = true))
    }

    @Test
    fun planWithoutContactOpensDialerEmpty() {
        val plan = policy.buildPlan(emergencyContact = null)
        assertEquals(EmergencyPrimaryAction.OpenDialerNoNumber, plan.primaryAction)
        assertEquals(0, plan.countdownSeconds)
        assertTrue(plan.spokenIntroduction.contains("marcador"))
    }

    @Test
    fun drillModeAlwaysAsksConfirmation() {
        val plan = policy.buildPlan(emergencyContact = contact, isDrill = true)
        assertEquals(0, plan.countdownSeconds)
        assertTrue(plan.isDrill)
        assertTrue(plan.spokenIntroduction.contains("Simulacro", ignoreCase = true))
        assertTrue(plan.spokenIntroduction.contains("confirmar"))
    }

    @Test
    fun whatsAppSecondaryHasFixedMessage() {
        val plan = policy.buildPlan(emergencyContact = contact, includeWhatsApp = true)
        val whatsApp = plan.secondaryActions
            .filterIsInstance<EmergencySecondaryAction.PrepareWhatsAppMessage>()
            .firstOrNull()
        assertTrue(whatsApp != null)
        assertTrue(whatsApp!!.message.contains("Estela"))
        assertFalse(whatsApp.message.contains("enviado"), "no debe afirmar que fue enviado")
    }

    @Test
    fun whatsAppNotIncludedWhenDisabled() {
        val plan = policy.buildPlan(emergencyContact = contact, includeWhatsApp = false)
        assertTrue(plan.secondaryActions.isEmpty())
    }

    @Test
    fun whatsAppIncludesLocationWhenAvailable() {
        val plan = policy.buildPlan(
            emergencyContact = contact,
            includeWhatsApp = true,
            location = EmergencyLocation(
                latitude = -34.6037,
                longitude = -58.3816,
                accuracyMeters = 50.0
            )
        )
        val whatsApp = plan.secondaryActions
            .filterIsInstance<EmergencySecondaryAction.PrepareWhatsAppMessage>()
            .first()
        assertTrue(whatsApp.message.contains("Ubicación", ignoreCase = true))
        assertTrue(whatsApp.message.contains("-34.6037"))
    }

    @Test
    fun spokenAfterActingNeverClaimsHelpSent() {
        val plan = policy.buildPlan(emergencyContact = contact)
        val text = policy.spokenAfterActing(plan)
        assertTrue(text.contains("Abrí el marcador"))
        assertTrue(text.contains("disparás vos", ignoreCase = true))
        assertFalse(
            text.contains("ayuda fue enviada", ignoreCase = true),
            "nunca afirmar que la ayuda fue enviada"
        )
        assertFalse(
            text.contains("llamada completada", ignoreCase = true),
            "nunca afirmar que la llamada se completó"
        )
        assertFalse(
            text.contains("enviado automáticamente", ignoreCase = true),
            "nunca afirmar envío automático"
        )
    }

    @Test
    fun spokenAfterActingMentionsMessagePreparation() {
        val plan = policy.buildPlan(emergencyContact = contact)
        val text = policy.spokenAfterActing(plan)
        assertTrue(text.contains("mensaje", ignoreCase = true))
        assertTrue(text.contains("Yo no lo envío", ignoreCase = true))
    }

    @Test
    fun safeOfferDoesNotCallOrSendByItself() {
        val text = policy.safeOfferText()

        assertTrue(text.contains("abrir Teléfono", ignoreCase = true))
        assertTrue(text.contains("abrir WhatsApp", ignoreCase = true))
        assertTrue(text.contains("confirmación", ignoreCase = true))
        assertTrue(text.contains("cancelar", ignoreCase = true))
        assertTrue(text.contains("No llamo", ignoreCase = true))
        assertTrue(text.contains("No", ignoreCase = true) && text.contains("envío", ignoreCase = true))
        assertFalse(text.contains("ayuda fue enviada", ignoreCase = true))
    }

    @Test
    fun noEmergencyPlanUsesCountdownOrAutoExecutionCopy() {
        listOf(
            policy.buildPlan(emergencyContact = contact),
            policy.buildPlan(emergencyContact = null),
            policy.buildPlan(emergencyContact = contact, isDrill = true)
        ).forEach { plan ->
            assertEquals(0, plan.countdownSeconds)
            assertFalse(plan.spokenIntroduction.contains("voy a actuar", ignoreCase = true))
            assertFalse(plan.spokenIntroduction.contains("segundos", ignoreCase = true))
        }
    }

    @Test
    fun phoneExecutorUsesDialNeverCallPermission() {
        val phoneExecutor = File("src/main/java/com/ojoclaro/android/phone/PhoneActionExecutor.kt").readText()
        val manifest = File("src/main/AndroidManifest.xml").readText()

        assertTrue(phoneExecutor.contains("Intent.ACTION_DIAL"))
        assertFalse(phoneExecutor.contains("Intent.ACTION_CALL"))
        assertFalse(manifest.contains("android.permission.CALL_PHONE"))
    }
}
