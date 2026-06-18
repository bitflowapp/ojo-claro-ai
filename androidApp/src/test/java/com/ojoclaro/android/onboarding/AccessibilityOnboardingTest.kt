package com.ojoclaro.android.onboarding

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccessibilityOnboardingTest {

    @Test
    fun activateRequestsMatch() {
        listOf(
            "activar Estela", "activá accesibilidad", "ayudame a activar Estela",
            "no puedo activar accesibilidad", "cómo activo Estela", "habilitar Estela"
        ).forEach { assertTrue(AccessibilityOnboardingPhrases.isActivateRequest(it), "activate: $it") }
    }

    @Test
    fun returnConfirmationsMatch() {
        listOf("ya activé Estela", "ya está activado", "ya quedó activada", "ya volví")
            .forEach { assertTrue(AccessibilityOnboardingPhrases.isReturnConfirmation(it), "return: $it") }
    }

    @Test
    fun capabilityQuestionsMatch() {
        assertTrue(AccessibilityOnboardingPhrases.isWhatCanIDoNow("qué puedo hacer ahora"))
        assertTrue(AccessibilityOnboardingPhrases.isWhatCanIDoNow("qué podés hacer en WhatsApp"))
        assertTrue(AccessibilityOnboardingPhrases.isWhatIsMissing("qué falta"))
        assertTrue(AccessibilityOnboardingPhrases.isWhatIsMissing("qué me falta para usar WhatsApp"))
        assertTrue(AccessibilityOnboardingPhrases.isWhyNotWorking("por qué no funciona"))
        assertTrue(AccessibilityOnboardingPhrases.isWhyNotWorking("por qué no funciona WhatsApp"))
        assertTrue(AccessibilityOnboardingPhrases.isWhatsAppHelp("ayuda de WhatsApp"))
        assertTrue(AccessibilityOnboardingPhrases.isStatusRequest("estado de Estela"))
    }

    @Test
    fun conceptualHowWhatsAppWorksIsNotOnboardingOrDiagnosticHelp() {
        val phrase = "cómo funciona WhatsApp"
        assertFalse(AccessibilityOnboardingPhrases.isWhatCanIDoNow(phrase))
        assertFalse(AccessibilityOnboardingPhrases.isWhatIsMissing(phrase))
        assertFalse(AccessibilityOnboardingPhrases.isWhyNotWorking(phrase))
        assertFalse(AccessibilityOnboardingPhrases.isWhatsAppHelp(phrase))
    }

    @Test
    fun onboardingMatchersDoNotStealConfirmations() {
        listOf("sí", "dale", "mandalo", "cancelar", "no mandes", "repetí").forEach {
            assertFalse(AccessibilityOnboardingPhrases.isActivateRequest(it))
            assertFalse(AccessibilityOnboardingPhrases.isReturnConfirmation(it))
            assertFalse(AccessibilityOnboardingPhrases.isWhatCanIDoNow(it))
        }
    }

    @Test
    fun returnCheckDistinguishesBoundFromEnabledButNotBound() {
        assertTrue(
            AccessibilityOnboardingNarrator.returnCheck(serviceListed = true, bound = true)
                .contains("ya puede leer", ignoreCase = true)
        )
        val listedNotBound = AccessibilityOnboardingNarrator.returnCheck(serviceListed = true, bound = false)
        assertTrue(listedNotBound.contains("todavía no vinculó", ignoreCase = true))
        assertTrue(listedNotBound.contains("reiniciar", ignoreCase = true))
        assertTrue(
            AccessibilityOnboardingNarrator.returnCheck(serviceListed = false, bound = false)
                .contains("falta activar", ignoreCase = true)
        )
    }

    @Test
    fun guidanceIsBlindFriendlyAndHonest() {
        val g = AccessibilityOnboardingNarrator.guidance()
        assertTrue(g.contains("Ajustes", ignoreCase = true))
        assertTrue(g.contains("TalkBack", ignoreCase = true))
        assertTrue(g.contains("no puedo activarlo solo", ignoreCase = true))
    }
}
