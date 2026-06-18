package com.ojoclaro.android.agent.runtime.whatsapp

import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppReplyConfirmationResolver.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * WhatsApp Anxiety Hardening — contrato de la máquina de DOBLE confirmación.
 * Cubre los casos I.1–I.4, I.6 e I.9 del sprint: cancelar desde cualquier paso,
 * el "sí" a secas que jamás envía en el paso 2, el "mandalo" bloqueado en
 * dry-run, "repetí" que no toca nada y "no mandes" que cancela.
 */
class WhatsAppReplyConfirmationResolverTest {

    private fun resolve(step: Int, text: String, dryRun: Boolean = true): Outcome =
        WhatsAppReplyConfirmationResolver.resolve(step, text, dryRun)

    private val sendOutcomes = setOf(Outcome.BLOCKED_DRY_RUN, Outcome.SEND_REAL_NOT_ENABLED)

    @Test
    fun cancelFromStep1Cancels() {
        listOf("cancelar", "no", "no mandes", "me equivoqué", "pará", "dejá", "mejor no")
            .forEach { assertEquals(Outcome.CANCELLED, resolve(1, it), "step1 cancel: $it") }
    }

    @Test
    fun cancelFromStep2Cancels() {
        listOf("cancelar", "no", "no mandes", "no lo mandes", "me equivoqué", "pará")
            .forEach { assertEquals(Outcome.CANCELLED, resolve(2, it), "step2 cancel: $it") }
    }

    @Test
    fun bareAffirmativeAtStep2NeverSends() {
        listOf("sí", "si", "dale", "ok", "bueno", "ajá", "mmm", "puede ser", "capaz")
            .forEach { phrase ->
                val outcome = resolve(2, phrase)
                assertTrue(outcome !in sendOutcomes, "weak '$phrase' jamás debe enviar (fue $outcome)")
                assertEquals(
                    Outcome.WEAK_CONFIRMATION_BLOCKED,
                    outcome,
                    "weak '$phrase' debe registrarse como confirmación ambigua bloqueada"
                )
            }
    }

    @Test
    fun strongConfirmAtStep2IsBlockedInDryRun() {
        listOf("mandalo", "enviar ahora", "sí, mandalo", "confirmo enviar", "envialo ahora")
            .forEach {
                assertEquals(Outcome.BLOCKED_DRY_RUN, resolve(2, it, dryRun = true), "dry-run strong: $it")
            }
    }

    @Test
    fun strongConfirmAtStep2WouldSendOnlyWithDryRunOff() {
        // Aun con dry-run apagado, el envío real NO está habilitado en este modo:
        // el contrato del sprint nunca toca enviar.
        assertEquals(Outcome.SEND_REAL_NOT_ENABLED, resolve(2, "mandalo", dryRun = false))
        assertEquals(Outcome.SEND_REAL_NOT_ENABLED, resolve(2, "confirmo enviar", dryRun = false))
    }

    @Test
    fun step1AdvancesOnCalmAffirmative() {
        listOf("sí", "dale", "correcto", "está bien", "seguí", "preparar")
            .forEach { assertEquals(Outcome.ADVANCED_TO_SEND, resolve(1, it), "advance: $it") }
    }

    @Test
    fun repeatNeverTouchesWhatsAppInEitherStep() {
        listOf("repetí", "repetilo", "no entendí", "qué dijiste", "de nuevo").forEach {
            assertEquals(Outcome.REPEAT_PROMPT, resolve(1, it), "repeat step1: $it")
            assertEquals(Outcome.REPEAT_PROMPT, resolve(2, it), "repeat step2: $it")
        }
    }

    @Test
    fun unrecognizedInputRepromptsWithoutSending() {
        assertEquals(Outcome.REPROMPT_STEP_1, resolve(1, "qué hora es"))
        val step2 = resolve(2, "qué hora es")
        assertTrue(step2 !in sendOutcomes, "reprompt nunca envía")
        assertEquals(Outcome.REPROMPT_STEP_2, step2)
    }

    @Test
    fun cancelWinsOverAnyConfirmation() {
        // "no, mandalo" contiene una confirmación fuerte, pero el "no" gana.
        assertEquals(Outcome.CANCELLED, resolve(2, "no"))
    }

    @Test
    fun stopLikeCancelPhrasesCancelAtStep2() {
        // Microfix WA-5: estas frases (que también son STOP global sin pending)
        // deben CANCELAR el envío pendiente por la ruta WA-5 segura.
        listOf("pará no mandes", "no mandes", "pará", "basta", "cancelar", "me equivoqué", "no, me equivoqué")
            .forEach { assertEquals(Outcome.CANCELLED, resolve(2, it), "stop-like cancel: $it") }
    }
}
