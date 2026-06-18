package com.ojoclaro.android.notifications

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WhatsAppNotificationQueryResponderTest {

    private fun wa(
        sender: String = "Marco Antonio",
        preview: String = "Estoy llegando",
        group: Boolean = false,
        at: Long = 1_700_000_000_000L,
        pkg: String = "com.whatsapp"
    ) = WhatsAppNotification(
        packageName = pkg,
        sender = sender,
        messagePreview = preview,
        isGroup = group,
        postedAtMillis = at
    )

    @Test
    fun emptyStoreGivesNoNewMessages() {
        val r = WhatsAppNotificationQueryResponder.respond(emptyList())
        assertEquals(WhatsAppNotificationQueryResponder.Outcome.EMPTY, r.outcome)
        assertEquals("Por ahora no tengo mensajes nuevos de WhatsApp registrados.", r.spokenText)
        assertEquals("count=0", r.logSummary)
    }

    @Test
    fun singleNotificationReadsSenderAndPreview() {
        val r = WhatsAppNotificationQueryResponder.respond(listOf(wa()))
        assertEquals(WhatsAppNotificationQueryResponder.Outcome.SINGLE, r.outcome)
        assertEquals("Tenés un mensaje nuevo de WhatsApp de Marco Antonio: Estoy llegando.", r.spokenText)
    }

    @Test
    fun multipleNotificationsSummarizeWithLatestSender() {
        val list = listOf(
            wa(sender = "Ana", preview = "hola", at = 1L),
            wa(sender = "Beto", preview = "che", at = 2L),
            wa(sender = "Caro", preview = "nos vemos", at = 3L)
        )
        val r = WhatsAppNotificationQueryResponder.respond(list)
        assertEquals(WhatsAppNotificationQueryResponder.Outcome.MULTIPLE, r.outcome)
        assertEquals("Tenés 3 mensajes recientes de WhatsApp. El último es de Caro.", r.spokenText)
    }

    @Test
    fun hiddenContentGivesSafeResponse() {
        val r = WhatsAppNotificationQueryResponder.respond(listOf(wa(preview = "   ")))
        assertEquals(WhatsAppNotificationQueryResponder.Outcome.CONTENT_HIDDEN, r.outcome)
        assertEquals("Tenés un mensaje nuevo de WhatsApp, pero Android ocultó el contenido.", r.spokenText)
    }

    @Test
    fun nonWhatsAppNotificationsAreNeverIncluded() {
        // Solo no-WhatsApp → tratado como vacío.
        val onlyForeign = WhatsAppNotificationQueryResponder.respond(
            listOf(wa(pkg = "com.instagram.android"))
        )
        assertEquals(WhatsAppNotificationQueryResponder.Outcome.EMPTY, onlyForeign.outcome)

        // Mezcla → solo cuenta WhatsApp y el ultimo WhatsApp es el remitente.
        val mixed = WhatsAppNotificationQueryResponder.respond(
            listOf(
                wa(sender = "Ana", at = 1L),
                wa(sender = "Spammer", pkg = "com.instagram.android", at = 2L)
            )
        )
        assertEquals(WhatsAppNotificationQueryResponder.Outcome.SINGLE, mixed.outcome)
        assertTrue(mixed.spokenText.contains("Ana"))
        assertFalse(mixed.spokenText.contains("Spammer"))
    }

    @Test
    fun logSummaryNeverLeaksSenderOrPreview() {
        val r = WhatsAppNotificationQueryResponder.respond(
            listOf(wa(sender = "Marco Antonio", preview = "clave secreta del banco"))
        )
        val log = r.logSummary
        assertFalse(log.contains("Marco"), "log leaked sender: $log")
        assertFalse(log.contains("secreta"), "log leaked preview: $log")
        assertTrue(log.contains("latestSenderLen="))
        assertTrue(log.contains("latestPreviewLen="))
        assertTrue(log.contains("latestPkg=com.whatsapp"))
        assertTrue(log.contains("latestGroup="))
        assertTrue(log.contains("latestTs="))
    }
}
