package com.ojoclaro.android.agent.runtime.whatsapp

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * WhatsApp Anxiety Hardening — clasificación de comandos de orientación y
 * seguridad. Verifica que: las frases que NOMBRAN WhatsApp se reconozcan; la
 * orientación genérica ("dónde estoy"/"estoy perdido") matchee; el modo seguro
 * se active; y que NINGÚN matcher robe una confirmación de envío (sí/mandalo/
 * cancelar), porque el handler corre antes que los pendientes.
 */
class WhatsAppAnxietyPhrasesTest {

    @Test
    fun safeModeOnVariantsMatch() {
        listOf("modo seguro", "modo ansiedad", "modo calma", "hablame simple", "más corto", "decime simple")
            .forEach { assertTrue(WhatsAppAnxietyPhrases.isSafeModeOn(it), "safe on: $it") }
    }

    @Test
    fun safeModeOffVariantsMatch() {
        listOf("modo normal", "salí del modo seguro", "hablá normal", "modo largo")
            .forEach { assertTrue(WhatsAppAnxietyPhrases.isSafeModeOff(it), "safe off: $it") }
    }

    @Test
    fun explicitWhatsAppStateQueryMatches() {
        listOf(
            "qué pasa en WhatsApp",
            "qué pasa con el whatsapp",
            "qué onda WhatsApp",
            "estoy en WhatsApp",
            "en qué parte de WhatsApp estoy"
        ).forEach { assertTrue(WhatsAppAnxietyPhrases.isWhatsAppStateQuery(it), "state query: $it") }
    }

    @Test
    fun stateQueryRequiresNamingWhatsApp() {
        // Sin nombrar WhatsApp NO es consulta explícita (la genérica se rutea aparte).
        listOf("qué pasa", "dónde estoy", "qué onda", "estoy perdido")
            .forEach { assertFalse(WhatsAppAnxietyPhrases.isWhatsAppStateQuery(it), "not explicit: $it") }
    }

    @Test
    fun whatHappenedMatches() {
        listOf("qué pasó", "qué pasó recién", "qué está pasando", "en qué quedamos", "qué hiciste")
            .forEach { assertTrue(WhatsAppAnxietyPhrases.isWhatHappened(it), "what happened: $it") }
    }

    @Test
    fun whatHappenedDoesNotStealExplicitWhatsAppOrOcr() {
        // Exacto: no roba "qué pasa en whatsapp" (consulta de estado) ni
        // "qué decía el cartel" (OCR/visión).
        assertFalse(WhatsAppAnxietyPhrases.isWhatHappened("qué pasa en whatsapp"))
        assertFalse(WhatsAppAnxietyPhrases.isWhatHappened("qué decía el cartel"))
    }

    @Test
    fun genericWhereAmIMatches() {
        listOf("dónde estoy", "qué estoy viendo", "estoy perdido", "estoy perdida", "en qué pantalla estoy")
            .forEach { assertTrue(WhatsAppAnxietyPhrases.isGenericWhereAmI(it), "where am I: $it") }
    }

    @Test
    fun genericWhereAmIDoesNotMatchExplicitWhatsApp() {
        // "dónde estoy en whatsapp" la toma isWhatsAppStateQuery, no la genérica.
        assertFalse(WhatsAppAnxietyPhrases.isGenericWhereAmI("dónde estoy en whatsapp"))
    }

    @Test
    fun contextualHelpMatchesOnlyLocativeVariants() {
        listOf("qué puedo hacer acá", "qué puedo hacer ahora", "qué opciones tengo")
            .forEach { assertTrue(WhatsAppAnxietyPhrases.isContextualHelp(it), "ctx help: $it") }
        // La ayuda genérica la sigue resolviendo VoiceCommandDispatcher.
        assertFalse(WhatsAppAnxietyPhrases.isContextualHelp("ayuda"))
        assertFalse(WhatsAppAnxietyPhrases.isContextualHelp("qué puedo hacer"))
    }

    @Test
    fun anxietyMatchersNeverStealAConfirmation() {
        // Disjunción dura con sí/mandalo/cancelar/no: corriendo antes de los
        // pendientes, jamás deben consumir una respuesta de confirmación.
        listOf(
            "sí", "dale", "ok", "mandalo", "enviá", "enviar ahora", "confirmo enviar",
            "no", "cancelar", "no mandes", "me equivoqué", "pará", "repetí"
        ).forEach { confirmation ->
            assertFalse(WhatsAppAnxietyPhrases.isSafeModeOn(confirmation), "safeOn stole: $confirmation")
            assertFalse(WhatsAppAnxietyPhrases.isSafeModeOff(confirmation), "safeOff stole: $confirmation")
            assertFalse(WhatsAppAnxietyPhrases.isWhatsAppStateQuery(confirmation), "stateQ stole: $confirmation")
            assertFalse(WhatsAppAnxietyPhrases.isWhatHappened(confirmation), "whatHappened stole: $confirmation")
            assertFalse(WhatsAppAnxietyPhrases.isGenericWhereAmI(confirmation), "whereAmI stole: $confirmation")
            assertFalse(WhatsAppAnxietyPhrases.isContextualHelp(confirmation), "ctxHelp stole: $confirmation")
        }
    }
}
