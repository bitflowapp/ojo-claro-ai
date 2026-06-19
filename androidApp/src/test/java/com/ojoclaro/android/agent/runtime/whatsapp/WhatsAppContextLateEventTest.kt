package com.ojoclaro.android.agent.runtime.whatsapp

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * M3 — Callbacks tardíos y orden de eventos.
 *
 * El riesgo fino: tras cancelar / irse / teardown, un callback asíncrono viejo
 * (una lectura o un borrador que terminó de resolverse) NO debe "revivir" el
 * contexto operativo que ya se limpió. El mecanismo es una generación de
 * invalidación (`epoch`) que sube en cada `clear()`: un callback captura la
 * generación al empezar y, si cuando vuelve la generación cambió, es obsoleto.
 *
 * Como el `WhatsAppConversationContext` es un singleton que acumula estado entre
 * tests, NO asumimos epoch absoluto: medimos contra una línea base por test.
 */
class WhatsAppContextLateEventTest {

    @BeforeTest
    fun setUp() = WhatsAppConversationContext.clear()

    @AfterTest
    fun tearDown() = WhatsAppConversationContext.clear()

    private fun note(label: String) =
        WhatsAppConversationContext.noteDestination(label, null, 1_000L)

    // ---------------------------------------------------------------------
    // Mecánica del epoch.
    // ---------------------------------------------------------------------

    @Test
    fun clearBumpsEpoch() {
        val before = WhatsAppConversationContext.epoch()
        WhatsAppConversationContext.clear()
        assertEquals(before + 1L, WhatsAppConversationContext.epoch())
    }

    @Test
    fun multipleClearsBumpMonotonically() {
        val before = WhatsAppConversationContext.epoch()
        WhatsAppConversationContext.clear()
        WhatsAppConversationContext.clear()
        WhatsAppConversationContext.clear()
        assertEquals(before + 3L, WhatsAppConversationContext.epoch())
    }

    @Test
    fun noteDoesNotBumpEpoch() {
        // Escribir contexto dentro de una sesión viva NO es una invalidación.
        val before = WhatsAppConversationContext.epoch()
        note("Ana Prueba")
        WhatsAppConversationContext.notePendingAction("send_message", 1_000L)
        WhatsAppConversationContext.noteDraftLen(10, 1_000L)
        assertEquals(before, WhatsAppConversationContext.epoch())
    }

    @Test
    fun cancelViaAdapterBumpsEpoch() {
        note("Ana Prueba")
        val before = WhatsAppConversationContext.epoch()
        WhatsAppContextLifecycle.handle(WhatsAppContextEvent.ExplicitCancel)
        assertNotEquals(before, WhatsAppConversationContext.epoch())
    }

    // ---------------------------------------------------------------------
    // Callback tardío: obsoleto tras una invalidación; vivo si no la hubo.
    // ---------------------------------------------------------------------

    /** S6/S7: cancel → callback tardío (lectura/borrador) → NO restaura. */
    @Test
    fun lateCallbackAfterCancel_isStaleAndIgnored() {
        note("Contacto A")
        val captured = WhatsAppConversationContext.epoch()
        WhatsAppContextLifecycle.handle(WhatsAppContextEvent.ExplicitCancel)
        assertNull(WhatsAppConversationContext.current())

        val decision = WhatsAppContextLifecycle.handle(
            WhatsAppContextEvent.LateCallback(captured, WhatsAppConversationContext.epoch())
        )
        assertEquals(WhatsAppContextDecision.IGNORE_STALE_EVENT, decision)
        // Sigue limpio: el callback tardío no revivió a A.
        assertNull(WhatsAppConversationContext.current())
    }

    @Test
    fun lateCallbackWithoutInvalidation_isFresh() {
        note("Contacto A")
        val captured = WhatsAppConversationContext.epoch()
        val decision = WhatsAppContextLifecycle.handle(
            WhatsAppContextEvent.LateCallback(captured, WhatsAppConversationContext.epoch())
        )
        assertEquals(WhatsAppContextDecision.PRESERVE, decision)
        assertEquals("Contacto A", WhatsAppConversationContext.current()?.chatLabelRedacted)
    }

    /** S16: teardown → callback tardío → no revive. */
    @Test
    fun teardownThenLateCallback_noRevive() {
        note("José Demo")
        val captured = WhatsAppConversationContext.epoch()
        WhatsAppContextLifecycle.handle(WhatsAppContextEvent.ServiceTeardown)
        val decision = WhatsAppContextLifecycle.handle(
            WhatsAppContextEvent.LateCallback(captured, WhatsAppConversationContext.epoch())
        )
        assertEquals(WhatsAppContextDecision.IGNORE_STALE_EVENT, decision)
        assertNull(WhatsAppConversationContext.current())
    }

    /** S6 variante: cambio de chat A→B y luego llega una lectura tardía de A. */
    @Test
    fun lateReadCallbackFromOldChat_doesNotClobberNewChat() {
        note("Contacto A")
        val capturedForA = WhatsAppConversationContext.epoch()
        WhatsAppContextLifecycle.handle(WhatsAppContextEvent.ChatChanged) // invalida A
        note("Contacto B")

        val decision = WhatsAppContextLifecycle.handle(
            WhatsAppContextEvent.LateCallback(capturedForA, WhatsAppConversationContext.epoch())
        )
        assertEquals(WhatsAppContextDecision.IGNORE_STALE_EVENT, decision)
        // El callback viejo de A se descarta: B queda intacto.
        assertEquals("Contacto B", WhatsAppConversationContext.current()?.chatLabelRedacted)
    }

    /** S15: retorno de foreground fuera de orden no revive el contexto viejo. */
    @Test
    fun outOfOrderForegroundReturn_doesNotRevive() {
        note("Contacto A")
        val captured = WhatsAppConversationContext.epoch()
        WhatsAppContextLifecycle.handle(WhatsAppContextEvent.LeftWhatsAppForeground) // invalida A
        assertNull(WhatsAppConversationContext.current())

        // Un "volvió al foreground" tardío de la generación vieja: obsoleto.
        val stale = WhatsAppContextLifecycle.handle(
            WhatsAppContextEvent.LateCallback(captured, WhatsAppConversationContext.epoch())
        )
        assertEquals(WhatsAppContextDecision.IGNORE_STALE_EVENT, stale)
        // Y un retorno sin snapshot fresco limpia igual: nunca reaparece A.
        WhatsAppContextLifecycle.handle(
            WhatsAppContextEvent.ReturnedToWhatsAppForeground(hasFreshSnapshot = false)
        )
        assertNull(WhatsAppConversationContext.current())
    }

    // ---------------------------------------------------------------------
    // Patrón de disciplina para un escritor asíncrono real: re-chequear epoch
    // ANTES de escribir. Una escritura tardía sobre contexto invalidado se salta.
    // ---------------------------------------------------------------------

    @Test
    fun asyncWriterRespectsEpoch_skipsStaleWrite() {
        note("Contacto A")
        val captured = WhatsAppConversationContext.epoch()
        WhatsAppContextLifecycle.handle(WhatsAppContextEvent.ExplicitCancel) // limpia + bump

        // El callback tardío quería escribir noteMessageCount(5); re-chequea:
        val decision = WhatsAppContextLifecyclePolicy.decide(
            WhatsAppContextEvent.LateCallback(captured, WhatsAppConversationContext.epoch())
        )
        if (decision != WhatsAppContextDecision.IGNORE_STALE_EVENT) {
            WhatsAppConversationContext.noteMessageCount(5, 2_000L)
        }
        // Como era obsoleto, NO escribió: el contexto sigue limpio.
        assertNull(WhatsAppConversationContext.current())
    }

    @Test
    fun asyncWriterRespectsEpoch_appliesFreshWrite() {
        note("Contacto A")
        val captured = WhatsAppConversationContext.epoch()

        val decision = WhatsAppContextLifecyclePolicy.decide(
            WhatsAppContextEvent.LateCallback(captured, WhatsAppConversationContext.epoch())
        )
        if (decision != WhatsAppContextDecision.IGNORE_STALE_EVENT) {
            WhatsAppConversationContext.noteMessageCount(5, 2_000L)
        }
        assertEquals(5, WhatsAppConversationContext.current()?.lastMessageCount)
    }

    @Test
    fun doubleCancelEpochAdvancesTwice() {
        note("Contacto A")
        val before = WhatsAppConversationContext.epoch()
        WhatsAppContextLifecycle.handle(WhatsAppContextEvent.ExplicitCancel)
        WhatsAppContextLifecycle.handle(WhatsAppContextEvent.ExplicitCancel)
        assertTrue(WhatsAppConversationContext.epoch() >= before + 2L)
        assertNull(WhatsAppConversationContext.current())
    }
}
