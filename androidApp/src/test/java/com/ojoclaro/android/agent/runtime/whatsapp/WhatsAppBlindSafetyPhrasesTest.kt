package com.ojoclaro.android.agent.runtime.whatsapp

import com.ojoclaro.android.agent.runtime.conversation.ConversationGate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Blind Safety — tests PUROS de las frases/clasificadores de los fixes HIGH.
 * Sin Android, sin números reales (solo sintéticos donde haga falta).
 */
class WhatsAppBlindSafetyPhrasesTest {

    // #3 — Cancelación universal: estas frases cancelan TANTO el envío V1.2
    // (isCancelSend) COMO el reply WA-5 (isCancel). Antes "pará no mandes" caía
    // al STOP global mudo por la cobertura más pobre de isCancelSend.
    @Test
    fun universalCancelPhrasesCancelOnBothPaths() {
        listOf("pará no mandes", "no mandes", "cancelar", "me arrepentí", "borrá el borrador")
            .forEach { phrase ->
                assertTrue(
                    WhatsAppVoiceSendPhrases.isCancelSend(phrase),
                    "isCancelSend should accept cancel phrase: $phrase"
                )
                assertTrue(
                    WhatsAppReplyPhrases.isCancel(phrase),
                    "isCancel should accept cancel phrase: $phrase"
                )
            }
    }

    // #4 — "mandá una foto por WhatsApp" es acción PROHIBIDA (SEND_PHOTO),
    // jamás cámara. El narrador de rechazo nunca queda vacío.
    @Test
    fun sendPhotoIsForbiddenNotCamera() {
        val match = WhatsAppForbiddenCommandParser.parse("mandá una foto por WhatsApp")
        assertEquals(WhatsAppActionType.SEND_PHOTO, match?.action)
        val refusal = WhatsAppForbiddenActionNarrator.refusal(WhatsAppActionType.SEND_PHOTO)
        assertTrue(refusal.isNotBlank(), "refusal must not be empty")
        assertTrue(refusal.contains("No toqué nada"), "refusal must reassure nothing was touched")
    }

    // #5 — verbos MUTANTES de WhatsApp son críticos (no van a LLM); verbos de
    // LECTURA NO son críticos (siguen su ruta local de lectura).
    @Test
    fun criticalGuardFlagsMutatingVerbsNotReads() {
        listOf(
            "respondele que ya voy", "borrá esa conversación", "reenviá esto",
            "pagale a juan", "mandale una foto", "reportá a esa persona"
        ).forEach { assertTrue(WhatsAppCriticalGuard.isCritical(it), "should be critical: $it") }

        listOf(
            "leeme los chats", "qué chats tengo", "mostrame la pantalla", "leé los mensajes"
        ).forEach { assertFalse(WhatsAppCriticalGuard.isCritical(it), "read verb must NOT be critical: $it") }
    }

    // #5 — defensa en profundidad: las frases críticas NO son "conversación",
    // así que jamás se envían a /conversation aunque esquiven el guard.
    @Test
    fun criticalPhrasesAreNotConversational() {
        listOf(
            "respondele que ya voy", "borrá esa conversación", "reenviá esto",
            "pagale a juan", "reportá a esa persona", "mandale una foto"
        ).forEach { assertFalse(ConversationGate.isConversational(it), "must NOT be conversational: $it") }
    }

    // #7 — flags peligrosos default FALSE (no real send / no videollamada real).
    @Test
    fun dangerousFlagsDefaultFalse() {
        assertFalse(WhatsAppFeatureFlags.DISABLED.realSendEnabled)
        assertFalse(WhatsAppFeatureFlags.DISABLED.videoCallEnabled)
        assertFalse(WhatsAppFeatureFlags().realSendEnabled)
        assertFalse(WhatsAppFeatureFlags().videoCallEnabled)
    }

    // #1 (HIGH fix del review) — el match de label es por TOKEN de palabra
    // completa: jamás verifica un chat por colisión de substring
    // ("ana"⊄"susana", "leo"⊄"leonardo", "marco"⊄"marcos").
    @Test
    fun labelMatcherRejectsSubstringCollisionsButAcceptsRealMatches() {
        // colisiones peligrosas → NO verifican (evita borrador en chat equivocado)
        assertEquals(false, WhatsAppLabelMatcher.matches("Ana", "Susana"))
        assertEquals(false, WhatsAppLabelMatcher.matches("Leo", "Leonardo"))
        assertEquals(false, WhatsAppLabelMatcher.matches("Marco", "Marcos Pérez"))
        // FAIL-CLOSED (#1): un SUBconjunto en cualquier dirección NO verifica.
        // "Ana García" esperado vs cabecera "Ana" (otro contacto) = MISMATCH; y
        // "Ana" esperado vs "Ana García" tampoco alcanza sin últimos-4.
        assertEquals(false, WhatsAppLabelMatcher.matches("Ana García", "Ana"))
        assertEquals(false, WhatsAppLabelMatcher.matches("Ana", "Ana García"))
        // coincidencias reales = igualdad de tokens significativos (acentos/caso aparte)
        assertEquals(true, WhatsAppLabelMatcher.matches("Ana", "Ana"))
        assertEquals(true, WhatsAppLabelMatcher.matches("Ana García", "Ana García"))
        assertEquals(true, WhatsAppLabelMatcher.matches("josé", "Jose"))
        // título numérico/vacío → null (decide la señal de últimos-4 / fail-closed)
        assertEquals(null, WhatsAppLabelMatcher.matches("Ana", "+54 9 11 5550-0000"))
        assertEquals(null, WhatsAppLabelMatcher.matches("Ana", ""))
        assertEquals(null, WhatsAppLabelMatcher.matches("", "Ana"))
    }

    // #1 (HIGH) — fail-closed end-to-end: sin últimos-4, un nombre de cabecera que
    // es subconjunto del esperado NO verifica (no escribe borrador en chat ajeno).
    @Test
    fun verifierFailsClosedOnSubsetNameWithoutPhoneEnding() {
        val subset = WhatsAppDestinationVerifier.verify(
            expectedEnding = null, inChat = true, hasEntryField = true, timedOut = false,
            visibleEnding = null,
            labelMatches = WhatsAppLabelMatcher.matches("Ana García", "Ana")
        )
        assertEquals(false, subset.isVerified)
        // La señal fuerte de últimos-4 sigue verificando (el fix no la rompe).
        val strong = WhatsAppDestinationVerifier.verify(
            expectedEnding = "4242", inChat = true, hasEntryField = true, timedOut = false,
            visibleEnding = "4242",
            labelMatches = WhatsAppLabelMatcher.matches("Ana García", "Ana")
        )
        assertEquals(true, strong.isVerified)
        // Y un nombre EXACTO sin números también verifica (caso legítimo común).
        val exact = WhatsAppDestinationVerifier.verify(
            expectedEnding = null, inChat = true, hasEntryField = true, timedOut = false,
            visibleEnding = null,
            labelMatches = WhatsAppLabelMatcher.matches("Ana García", "Ana García")
        )
        assertEquals(true, exact.isVerified)
    }
}
