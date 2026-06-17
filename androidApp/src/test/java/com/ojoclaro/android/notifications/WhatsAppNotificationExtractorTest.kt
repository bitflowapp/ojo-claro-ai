package com.ojoclaro.android.notifications

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WhatsAppNotificationExtractorTest {

    private fun raw(
        packageName: String? = "com.whatsapp",
        title: String? = "Marco Antonio",
        text: String? = "Estoy llegando",
        category: String? = "msg",
        isOngoing: Boolean = false,
        isGroupSummary: Boolean = false,
        postedAtMillis: Long = 1_700_000_000_000L
    ) = RawWhatsAppNotification(
        packageName = packageName,
        title = title,
        text = text,
        category = category,
        isOngoing = isOngoing,
        isGroupSummary = isGroupSummary,
        postedAtMillis = postedAtMillis
    )

    @Test
    fun whatsAppMessageIsExtracted() {
        val result = WhatsAppNotificationExtractor.extract(raw())
        assertNotNull(result)
        assertEquals("com.whatsapp", result.packageName)
        assertEquals("Marco Antonio", result.sender)
        assertEquals("Estoy llegando", result.messagePreview)
        assertFalse(result.isGroup)
        assertEquals(1_700_000_000_000L, result.postedAtMillis)
    }

    @Test
    fun whatsAppBusinessMessageIsExtracted() {
        val result = WhatsAppNotificationExtractor.extract(raw(packageName = "com.whatsapp.w4b"))
        assertNotNull(result)
        assertEquals("com.whatsapp.w4b", result.packageName)
    }

    @Test
    fun nonWhatsAppPackageIsIgnored() {
        assertNull(WhatsAppNotificationExtractor.extract(raw(packageName = "com.instagram.android")))
        assertNull(WhatsAppNotificationExtractor.extract(raw(packageName = "org.telegram.messenger")))
        assertNull(WhatsAppNotificationExtractor.extract(raw(packageName = null)))
    }

    @Test
    fun ongoingNotificationIsIgnored() {
        // El servicio en primer plano de WhatsApp / una llamada en curso.
        assertNull(WhatsAppNotificationExtractor.extract(raw(isOngoing = true)))
    }

    @Test
    fun groupSummaryIsIgnored() {
        assertNull(WhatsAppNotificationExtractor.extract(raw(isGroupSummary = true)))
    }

    @Test
    fun callCategoryIsIgnored() {
        assertNull(WhatsAppNotificationExtractor.extract(raw(category = "call")))
        assertNull(WhatsAppNotificationExtractor.extract(raw(category = "CALL")))
    }

    @Test
    fun emptyTitleOrTextIsIgnored() {
        assertNull(WhatsAppNotificationExtractor.extract(raw(title = null)))
        assertNull(WhatsAppNotificationExtractor.extract(raw(title = "   ")))
        assertNull(WhatsAppNotificationExtractor.extract(raw(text = null)))
        assertNull(WhatsAppNotificationExtractor.extract(raw(text = "")))
    }

    @Test
    fun whatsAppSystemTitlesAreIgnored() {
        listOf(
            "WhatsApp",
            "WhatsApp Business",
            "Buscando mensajes nuevos…",
            "Checking for new messages"
        ).forEach { systemTitle ->
            assertNull(
                WhatsAppNotificationExtractor.extract(raw(title = systemTitle)),
                "should ignore system title: $systemTitle"
            )
        }
    }

    @Test
    fun aggregatedSummaryTextIsIgnored() {
        assertNull(WhatsAppNotificationExtractor.extract(raw(text = "5 mensajes de 3 chats")))
        assertNull(WhatsAppNotificationExtractor.extract(raw(text = "12 messages from 4 chats")))
    }

    @Test
    fun groupMessageIsDetected() {
        val result = WhatsAppNotificationExtractor.extract(
            raw(title = "Asado del domingo", text = "Sofía: llevo la carne")
        )
        assertNotNull(result)
        assertTrue(result.isGroup)
        assertEquals("Asado del domingo", result.sender)
    }

    @Test
    fun directMessageWithColonInsideIsNotMistakenForGroupSenderPrefix() {
        // Un prefijo demasiado largo no es "Remitente: …" → no es grupo.
        val longPrefix = "x".repeat(40) + ": resto"
        val result = WhatsAppNotificationExtractor.extract(raw(text = longPrefix))
        assertNotNull(result)
        assertFalse(result.isGroup)
    }

    @Test
    fun longFieldsAreTruncated() {
        val result = WhatsAppNotificationExtractor.extract(
            raw(
                title = "a".repeat(1_000),
                text = "b".repeat(1_000)
            )
        )
        assertNotNull(result)
        assertEquals(WhatsAppNotificationExtractor.MAX_FIELD_CHARS, result.sender.length)
        assertEquals(WhatsAppNotificationExtractor.MAX_FIELD_CHARS, result.messagePreview.length)
    }

    @Test
    fun redactedLogNeverLeaksSenderOrPreview() {
        val result = WhatsAppNotificationExtractor.extract(
            raw(title = "Marco Antonio", text = "secreto bancario")
        )
        assertNotNull(result)
        val line = result.redactedForLog()
        assertFalse(line.contains("Marco"), "log leaked sender: $line")
        assertFalse(line.contains("secreto"), "log leaked preview: $line")
        assertTrue(line.contains("senderLen="))
        assertTrue(line.contains("previewLen="))
    }
}
