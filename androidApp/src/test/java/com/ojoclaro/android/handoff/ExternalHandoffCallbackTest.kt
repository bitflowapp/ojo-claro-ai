package com.ojoclaro.android.handoff

import com.ojoclaro.android.external.ExternalActionEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExternalHandoffCallbackTest {

    private fun handoff(
        externalAppName: String,
        delegate: ExternalActionEvent = ExternalActionEvent.OpenWhatsApp
    ): ExternalActionEvent.ExternalAppHandoff = ExternalActionEvent.ExternalAppHandoff(
        externalAppName = externalAppName,
        reason = "irrelevant",
        returnHint = "irrelevant",
        spokenText = "irrelevant",
        delegate = delegate
    )

    @Test
    fun whatsAppCallbackDoesNotClaimMessageWasSent() {
        val text = ExternalHandoffCallbacks.textFor(ExternalHandoffKind.WHATSAPP)

        assertTrue(text.contains("WhatsApp"))
        assertTrue(
            text.contains("no envié", ignoreCase = true),
            "WhatsApp callback debe aclarar que Estela no envió nada."
        )
        assertFalse(
            text.contains("mensaje enviado", ignoreCase = true),
            "Estela nunca debe decir 'mensaje enviado'."
        )
        assertFalse(
            text.contains("envié el mensaje", ignoreCase = true),
            "Estela no auto-envía."
        )
    }

    @Test
    fun mapsCallbackSaysNavigationWasOpened() {
        val text = ExternalHandoffCallbacks.textFor(ExternalHandoffKind.MAPS)

        assertTrue(text.contains("Maps", ignoreCase = true))
        assertTrue(
            text.contains("navegación", ignoreCase = true),
            "Maps callback debe mencionar que abrió la navegación."
        )
    }

    @Test
    fun phoneCallbackDoesNotClaimCallWasCompleted() {
        val text = ExternalHandoffCallbacks.textFor(ExternalHandoffKind.PHONE)

        assertTrue(text.contains("teléfono", ignoreCase = true))
        assertTrue(
            text.contains("depende de Android", ignoreCase = true),
            "Phone callback debe aclarar que la llamada depende de Android."
        )
        assertFalse(
            text.contains("llamada completada", ignoreCase = true),
            "Phone callback no debe afirmar que la llamada se completó."
        )
        assertFalse(
            text.contains("llamada realizada", ignoreCase = true),
            "Phone callback no debe afirmar que la llamada se realizó."
        )
    }

    @Test
    fun genericCallbackIsHonestAboutAutoComplete() {
        val text = ExternalHandoffCallbacks.textFor(ExternalHandoffKind.GENERIC)

        assertTrue(text.contains("Estela"))
        assertFalse(
            text.contains("envié", ignoreCase = true) && !text.contains("no", ignoreCase = true),
            "El callback genérico no debe afirmar envío."
        )
    }

    @Test
    fun classifyDetectsWhatsAppByName() {
        val kind = ExternalHandoffCallbacks.classify(handoff(externalAppName = "WhatsApp"))
        assertEquals(ExternalHandoffKind.WHATSAPP, kind)
    }

    @Test
    fun classifyDetectsMapsByName() {
        val kind = ExternalHandoffCallbacks.classify(
            handoff(externalAppName = "Maps", delegate = ExternalActionEvent.OpenMaps)
        )
        assertEquals(ExternalHandoffKind.MAPS, kind)
    }

    @Test
    fun classifyDetectsPhoneByNameWithAccent() {
        val kind = ExternalHandoffCallbacks.classify(
            handoff(externalAppName = "Teléfono", delegate = ExternalActionEvent.OpenPhone)
        )
        assertEquals(ExternalHandoffKind.PHONE, kind)
    }

    @Test
    fun classifyFallsBackToDelegateWhenNameIsUnknown() {
        val mapsKind = ExternalHandoffCallbacks.classify(
            handoff(
                externalAppName = "????",
                delegate = ExternalActionEvent.NavigateToDestination("avenida corrientes")
            )
        )
        val phoneKind = ExternalHandoffCallbacks.classify(
            handoff(
                externalAppName = "",
                delegate = ExternalActionEvent.DialPhoneNumber("Marco", "+5491100000000")
            )
        )
        val whatsAppKind = ExternalHandoffCallbacks.classify(
            handoff(
                externalAppName = "",
                delegate = ExternalActionEvent.ComposeWhatsAppMessage(
                    confirmationId = "c1",
                    contactName = "Marco",
                    messageText = "estoy llegando"
                )
            )
        )

        assertEquals(ExternalHandoffKind.MAPS, mapsKind)
        assertEquals(ExternalHandoffKind.PHONE, phoneKind)
        assertEquals(ExternalHandoffKind.WHATSAPP, whatsAppKind)
    }

    @Test
    fun classifyReturnsGenericWhenDelegateIsNotMapped() {
        val kind = ExternalHandoffCallbacks.classify(
            handoff(
                externalAppName = "Lector de pantalla",
                delegate = ExternalActionEvent.ReadVisibleScreen
            )
        )
        assertEquals(ExternalHandoffKind.GENERIC, kind)
    }

    @Test
    fun trackerStartsEmpty() {
        val tracker = ExternalHandoffCallbackTracker()

        assertFalse(tracker.hasPending)
        assertNull(tracker.consumeIfPending())
    }

    @Test
    fun trackerEmitsOnceAfterMark() {
        val tracker = ExternalHandoffCallbackTracker()
        tracker.markStarted(ExternalHandoffKind.WHATSAPP)

        assertTrue(tracker.hasPending)
        assertEquals(ExternalHandoffKind.WHATSAPP, tracker.consumeIfPending())
        assertFalse(tracker.hasPending)
        assertNull(tracker.consumeIfPending())
    }

    @Test
    fun trackerDoesNotRepeatCallbackTwice() {
        val tracker = ExternalHandoffCallbackTracker()
        tracker.markStarted(ExternalHandoffKind.MAPS)

        val firstReturn = tracker.consumeIfPending()
        val secondReturn = tracker.consumeIfPending()
        val thirdReturn = tracker.consumeIfPending()

        assertEquals(ExternalHandoffKind.MAPS, firstReturn)
        assertNull(secondReturn)
        assertNull(thirdReturn)
    }

    @Test
    fun trackerKeepsLatestKindWhenMarkedTwice() {
        val tracker = ExternalHandoffCallbackTracker()
        tracker.markStarted(ExternalHandoffKind.WHATSAPP)
        tracker.markStarted(ExternalHandoffKind.PHONE)

        assertEquals(ExternalHandoffKind.PHONE, tracker.consumeIfPending())
        assertNull(tracker.consumeIfPending())
    }

    @Test
    fun trackerClearDropsPendingWithoutEmitting() {
        val tracker = ExternalHandoffCallbackTracker()
        tracker.markStarted(ExternalHandoffKind.WHATSAPP)
        tracker.clear()

        assertFalse(tracker.hasPending)
        assertNull(tracker.consumeIfPending())
    }

    @Test
    fun consumeWithoutMarkReturnsNull() {
        val tracker = ExternalHandoffCallbackTracker()

        assertNull(tracker.consumeIfPending())
        assertNull(tracker.consumeIfPending())
    }
}
