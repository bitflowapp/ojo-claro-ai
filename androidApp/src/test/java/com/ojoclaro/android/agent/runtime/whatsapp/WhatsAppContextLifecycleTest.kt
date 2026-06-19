package com.ojoclaro.android.agent.runtime.whatsapp

import com.ojoclaro.android.voice.VoiceCommandDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * M3 — Lifecycle del contexto operativo de WhatsApp.
 *
 * Cierra el blocker NEEDS HUMAN de la remediación red team: el contexto operativo
 * (`WhatsAppConversationContext`) sólo se limpiaba con "olvidá el contexto", así que
 * tras una cancelación, irse al Home, perder el foreground o el teardown del servicio
 * podía sobrevivir y aflorar en el recuerdo hablado ("¿de qué estábamos hablando?").
 *
 * El núcleo verificable es [WhatsAppContextLifecyclePolicy] (puro) + el adaptador
 * [WhatsAppContextLifecycle] (aplica sobre el contexto real). Acá probamos:
 *  - cada evento → su decisión,
 *  - el adaptador limpia/preserva el contexto real correctamente,
 *  - los 18 escenarios de la misión expresados como contratos,
 *  - que las frases reales de cancelación enrutan al evento de cancelación.
 *
 * El consumidor real del contexto es `spokenRecall()`: si quedó limpio, el recuerdo
 * dice "No tengo contexto reciente de WhatsApp."
 */
class WhatsAppContextLifecycleTest {

    @BeforeTest
    fun setUp() = WhatsAppConversationContext.clear()

    @AfterTest
    fun tearDown() = WhatsAppConversationContext.clear()

    private fun noteChat(label: String, phoneEnding: String? = null, now: Long = 1_000L) {
        WhatsAppConversationContext.noteDestination(label, phoneEnding, now)
    }

    // ---------------------------------------------------------------------
    // Política pura: un evento → una decisión.
    // ---------------------------------------------------------------------

    @Test
    fun policy_sameChatTurn_preserves() {
        assertEquals(
            WhatsAppContextDecision.PRESERVE,
            WhatsAppContextLifecyclePolicy.decide(WhatsAppContextEvent.SameChatTurn)
        )
    }

    @Test
    fun policy_conceptualQuestion_preserves() {
        assertEquals(
            WhatsAppContextDecision.PRESERVE,
            WhatsAppContextLifecyclePolicy.decide(WhatsAppContextEvent.ConceptualQuestion)
        )
    }

    @Test
    fun policy_explicitCancel_clearsOperational() {
        assertEquals(
            WhatsAppContextDecision.CLEAR_OPERATIONAL_CONTEXT,
            WhatsAppContextLifecyclePolicy.decide(WhatsAppContextEvent.ExplicitCancel)
        )
    }

    @Test
    fun policy_leftForeground_clearsOperational() {
        assertEquals(
            WhatsAppContextDecision.CLEAR_OPERATIONAL_CONTEXT,
            WhatsAppContextLifecyclePolicy.decide(WhatsAppContextEvent.LeftWhatsAppForeground)
        )
    }

    @Test
    fun policy_chatChanged_clearsOperational() {
        assertEquals(
            WhatsAppContextDecision.CLEAR_OPERATIONAL_CONTEXT,
            WhatsAppContextLifecyclePolicy.decide(WhatsAppContextEvent.ChatChanged)
        )
    }

    @Test
    fun policy_serviceTeardown_clearsAll() {
        assertEquals(
            WhatsAppContextDecision.CLEAR_ALL,
            WhatsAppContextLifecyclePolicy.decide(WhatsAppContextEvent.ServiceTeardown)
        )
    }

    @Test
    fun policy_explicitForget_clearsAll() {
        assertEquals(
            WhatsAppContextDecision.CLEAR_ALL,
            WhatsAppContextLifecyclePolicy.decide(WhatsAppContextEvent.ExplicitForget)
        )
    }

    @Test
    fun policy_returnedForegroundWithFreshSnapshot_preserves() {
        assertEquals(
            WhatsAppContextDecision.PRESERVE,
            WhatsAppContextLifecyclePolicy.decide(
                WhatsAppContextEvent.ReturnedToWhatsAppForeground(hasFreshSnapshot = true)
            )
        )
    }

    @Test
    fun policy_returnedForegroundWithoutFreshSnapshot_clearsOperational() {
        assertEquals(
            WhatsAppContextDecision.CLEAR_OPERATIONAL_CONTEXT,
            WhatsAppContextLifecyclePolicy.decide(
                WhatsAppContextEvent.ReturnedToWhatsAppForeground(hasFreshSnapshot = false)
            )
        )
    }

    @Test
    fun policy_lateCallbackSameEpoch_preserves() {
        assertEquals(
            WhatsAppContextDecision.PRESERVE,
            WhatsAppContextLifecyclePolicy.decide(
                WhatsAppContextEvent.LateCallback(callbackEpoch = 7L, currentEpoch = 7L)
            )
        )
    }

    @Test
    fun policy_lateCallbackDifferentEpoch_ignored() {
        assertEquals(
            WhatsAppContextDecision.IGNORE_STALE_EVENT,
            WhatsAppContextLifecyclePolicy.decide(
                WhatsAppContextEvent.LateCallback(callbackEpoch = 7L, currentEpoch = 8L)
            )
        )
    }

    // ---------------------------------------------------------------------
    // Adaptador: aplica la decisión sobre el contexto REAL.
    // ---------------------------------------------------------------------

    @Test
    fun adapter_explicitCancel_clearsRealContext() {
        noteChat("Ana Prueba", "1234")
        assertNotNull(WhatsAppConversationContext.current())
        val decision = WhatsAppContextLifecycle.handle(WhatsAppContextEvent.ExplicitCancel)
        assertEquals(WhatsAppContextDecision.CLEAR_OPERATIONAL_CONTEXT, decision)
        assertNull(WhatsAppConversationContext.current())
    }

    @Test
    fun adapter_sameChatTurn_preservesRealContext() {
        noteChat("Ana Prueba", "1234")
        val decision = WhatsAppContextLifecycle.handle(WhatsAppContextEvent.SameChatTurn)
        assertEquals(WhatsAppContextDecision.PRESERVE, decision)
        assertEquals("Ana Prueba", WhatsAppConversationContext.current()?.chatLabelRedacted)
    }

    @Test
    fun adapter_serviceTeardown_clearsRealContext() {
        noteChat("José Demo")
        WhatsAppContextLifecycle.handle(WhatsAppContextEvent.ServiceTeardown)
        assertNull(WhatsAppConversationContext.current())
    }

    @Test
    fun adapter_leftForeground_clearsRealContext() {
        noteChat("Luz Test")
        WhatsAppContextLifecycle.handle(WhatsAppContextEvent.LeftWhatsAppForeground)
        assertNull(WhatsAppConversationContext.current())
    }

    @Test
    fun adapter_chatChanged_clearsRealContext() {
        noteChat("Ana Prueba")
        WhatsAppContextLifecycle.handle(WhatsAppContextEvent.ChatChanged)
        assertNull(WhatsAppConversationContext.current())
    }

    @Test
    fun adapter_conceptualQuestion_doesNotCreateContext() {
        // Sin contexto previo, una pregunta conceptual NO debe inventar uno.
        assertNull(WhatsAppConversationContext.current())
        WhatsAppContextLifecycle.handle(WhatsAppContextEvent.ConceptualQuestion)
        assertNull(WhatsAppConversationContext.current())
    }

    @Test
    fun adapter_staleLateCallback_neitherClearsNorRestores() {
        noteChat("Ana Prueba")
        val decision = WhatsAppContextLifecycle.handle(
            WhatsAppContextEvent.LateCallback(callbackEpoch = 1L, currentEpoch = 2L)
        )
        assertEquals(WhatsAppContextDecision.IGNORE_STALE_EVENT, decision)
        // IGNORE no toca el contexto: lo que había sigue como estaba (no lo borra
        // ni lo "restaura"); el borrado lo hace el evento real (cancel/home/etc).
        assertEquals("Ana Prueba", WhatsAppConversationContext.current()?.chatLabelRedacted)
    }

    // ---------------------------------------------------------------------
    // Frases reales de cancelación → evento de cancelación (puente con GAS).
    // ---------------------------------------------------------------------

    @Test
    fun realCancelPhrasesRouteToCancelEvent() {
        val cancelPhrases = listOf(
            "cancelá", "abortá", "no mandes nada", "frená todo", "pará todo",
            "me arrepentí", "dejalo", "olvidalo"
        )
        for (phrase in cancelPhrases) {
            assertTrue(
                VoiceCommandDispatcher.isBareCancelCommand(phrase),
                "«$phrase» debería ser cancelación → ExplicitCancel"
            )
            // y la cancelación limpia el contexto operativo:
            noteChat("Contacto A")
            WhatsAppContextLifecycle.handle(WhatsAppContextEvent.ExplicitCancel)
            assertNull(
                WhatsAppConversationContext.current(),
                "tras «$phrase» (cancel) el contexto debe quedar limpio"
            )
        }
    }

    @Test
    fun stopOnlyPhrasesAreNotCancellations() {
        // "callar"/"silencio" cortan el TTS pero NO son cancelación: el usuario
        // sigue en el chat, así que el recuerdo debe sobrevivir.
        for (phrase in listOf("callar", "silencio", "callate")) {
            assertFalse(
                VoiceCommandDispatcher.isBareCancelCommand(phrase),
                "«$phrase» es STOP de voz, no cancelación de contexto"
            )
        }
    }

    // ---------------------------------------------------------------------
    // Los 18 escenarios de la misión como contratos (los con epoch/tardío van
    // en WhatsAppContextLateEventTest).
    // ---------------------------------------------------------------------

    /** S1: chat A → "cancelá" → el recuerdo no menciona a A. */
    @Test
    fun s1_chatThenCancel_recallHasNoChat() {
        noteChat("Ana Prueba", "1234")
        WhatsAppContextLifecycle.handle(WhatsAppContextEvent.ExplicitCancel)
        val recall = WhatsAppConversationContext.spokenRecall()
        assertFalse(recall.contains("Ana Prueba"))
        assertTrue(recall.contains("No tengo contexto"))
    }

    /** S2: chat A con borrador → "no mandes nada" → contexto limpio. */
    @Test
    fun s2_draftThenNoMandesNada_cleared() {
        noteChat("Contacto A")
        WhatsAppConversationContext.notePendingAction("send_message", 1_000L)
        WhatsAppConversationContext.noteDraftLen(20, 1_000L)
        assertTrue(VoiceCommandDispatcher.isBareCancelCommand("no mandes nada"))
        WhatsAppContextLifecycle.handle(WhatsAppContextEvent.ExplicitCancel)
        assertNull(WhatsAppConversationContext.current())
    }

    /** S3: chat A → Home → recuerdo sin contexto. */
    @Test
    fun s3_chatThenHome_recallNoContext() {
        noteChat("José Demo")
        WhatsAppContextLifecycle.handle(WhatsAppContextEvent.LeftWhatsAppForeground)
        assertTrue(WhatsAppConversationContext.spokenRecall().contains("No tengo contexto"))
    }

    /** S4: pierde foreground → vuelve SIN snapshot fresco → no se reutiliza A. */
    @Test
    fun s4_loseForegroundThenReturnNoSnapshot_notReused() {
        noteChat("Contacto A")
        WhatsAppContextLifecycle.handle(WhatsAppContextEvent.LeftWhatsAppForeground)
        WhatsAppContextLifecycle.handle(
            WhatsAppContextEvent.ReturnedToWhatsAppForeground(hasFreshSnapshot = false)
        )
        assertNull(WhatsAppConversationContext.current())
    }

    /** S5: chat A → chat B → el recuerdo refleja B, no A. */
    @Test
    fun s5_chatAtoB_recallReflectsB() {
        noteChat("Contacto A")
        WhatsAppContextLifecycle.handle(WhatsAppContextEvent.ChatChanged)
        noteChat("Contacto B")
        val recall = WhatsAppConversationContext.spokenRecall()
        assertTrue(recall.contains("Contacto B"))
        assertFalse(recall.contains("Contacto A"))
    }

    /** S8/S9/S10: "pará todo"/"abortá"/"frená todo" limpian (vía cancel). */
    @Test
    fun s8to10_panicPhrasesClear() {
        for (phrase in listOf("pará todo", "abortá", "frená todo", "detené eso")) {
            noteChat("Contacto A")
            assertTrue(
                VoiceCommandDispatcher.isBareCancelCommand(phrase) ||
                    VoiceCommandDispatcher.isStopCommand(phrase),
                "«$phrase» debe ser cancel o stop reconocido"
            )
            WhatsAppContextLifecycle.handle(WhatsAppContextEvent.ExplicitCancel)
            assertNull(WhatsAppConversationContext.current())
        }
    }

    /** S11: pregunta conceptual sobre contexto previo válido → preserva, no rompe. */
    @Test
    fun s11_conceptualQuestionWithContext_preserves() {
        noteChat("Ana Prueba")
        WhatsAppContextLifecycle.handle(WhatsAppContextEvent.ConceptualQuestion)
        assertEquals("Ana Prueba", WhatsAppConversationContext.current()?.chatLabelRedacted)
    }

    /** S12: contexto con datos → cancel → nada persiste. */
    @Test
    fun s12_sensitiveThenCancel_nothingPersists() {
        noteChat("Contacto A", "9988")
        WhatsAppConversationContext.noteAssistantSummary("Le dije algo a Contacto A", 1_000L)
        WhatsAppContextLifecycle.handle(WhatsAppContextEvent.ExplicitCancel)
        assertNull(WhatsAppConversationContext.current())
        assertTrue(WhatsAppConversationContext.spokenRecall().contains("No tengo contexto"))
    }

    /** S13: lectura válida → "repetí" (mismo turno) → preserva. */
    @Test
    fun s13_validReadThenRepeat_preserves() {
        noteChat("José Demo")
        WhatsAppConversationContext.noteMessageCount(3, 1_000L)
        WhatsAppContextLifecycle.handle(WhatsAppContextEvent.SameChatTurn)
        assertEquals("José Demo", WhatsAppConversationContext.current()?.chatLabelRedacted)
        assertEquals(3, WhatsAppConversationContext.current()?.lastMessageCount)
    }

    /** S14: A se invalidó → B visible → el snapshot confirma B. */
    @Test
    fun s14_chatAinvalidThenBvisible_snapshotB() {
        noteChat("Contacto A")
        WhatsAppContextLifecycle.handle(WhatsAppContextEvent.LeftWhatsAppForeground)
        assertNull(WhatsAppConversationContext.current())
        // vuelve con snapshot fresco de B:
        noteChat("Contacto B", "4321")
        WhatsAppContextLifecycle.handle(
            WhatsAppContextEvent.ReturnedToWhatsAppForeground(hasFreshSnapshot = true)
        )
        assertEquals("Contacto B", WhatsAppConversationContext.current()?.chatLabelRedacted)
    }

    /** S17: doble cancel es idempotente y seguro. */
    @Test
    fun s17_doubleCancel_idempotent() {
        noteChat("Contacto A")
        WhatsAppContextLifecycle.handle(WhatsAppContextEvent.ExplicitCancel)
        WhatsAppContextLifecycle.handle(WhatsAppContextEvent.ExplicitCancel)
        assertNull(WhatsAppConversationContext.current())
    }

    /** S18: A → B → Home → sólo el último estado, y Home lo limpia. */
    @Test
    fun s18_rapidAtoBtoHome_onlyCurrentThenCleared() {
        noteChat("Contacto A")
        WhatsAppContextLifecycle.handle(WhatsAppContextEvent.ChatChanged)
        noteChat("Contacto B")
        assertEquals("Contacto B", WhatsAppConversationContext.current()?.chatLabelRedacted)
        WhatsAppContextLifecycle.handle(WhatsAppContextEvent.LeftWhatsAppForeground)
        assertNull(WhatsAppConversationContext.current())
    }

    /** Continuidad sana: dentro del mismo chat, varios turnos PRESERVAN el contexto. */
    @Test
    fun sameChatMultipleTurns_preserveContinuity() {
        noteChat("Ana Prueba", "1234")
        repeat(3) { WhatsAppContextLifecycle.handle(WhatsAppContextEvent.SameChatTurn) }
        val recall = WhatsAppConversationContext.spokenRecall()
        assertTrue(recall.contains("Ana Prueba"))
        assertTrue(recall.contains("1234"))
    }
}
