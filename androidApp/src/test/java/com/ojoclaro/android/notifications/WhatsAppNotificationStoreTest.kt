package com.ojoclaro.android.notifications

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WhatsAppNotificationStoreTest {

    @BeforeTest
    fun reset() {
        // El store es un singleton: limpiar para aislar cada test.
        WhatsAppNotificationStore.clear()
    }

    private fun notif(sender: String, at: Long) = WhatsAppNotification(
        packageName = "com.whatsapp",
        sender = sender,
        messagePreview = "msg-$sender",
        isGroup = false,
        postedAtMillis = at
    )

    @Test
    fun startsEmpty() {
        assertEquals(0, WhatsAppNotificationStore.size())
        assertNull(WhatsAppNotificationStore.latest())
        assertTrue(WhatsAppNotificationStore.recent().isEmpty())
    }

    @Test
    fun recordAndLatest() {
        WhatsAppNotificationStore.record(notif("Ana", 1L))
        WhatsAppNotificationStore.record(notif("Beto", 2L))
        assertEquals(2, WhatsAppNotificationStore.size())
        assertEquals("Beto", WhatsAppNotificationStore.latest()?.sender)
    }

    @Test
    fun recentKeepsChronologicalOrderAndRespectsLimit() {
        WhatsAppNotificationStore.record(notif("Ana", 1L))
        WhatsAppNotificationStore.record(notif("Beto", 2L))
        WhatsAppNotificationStore.record(notif("Caro", 3L))

        val lastTwo = WhatsAppNotificationStore.recent(2)
        assertEquals(listOf("Beto", "Caro"), lastTwo.map { it.sender })
    }

    @Test
    fun recentWithNonPositiveLimitReturnsEmpty() {
        WhatsAppNotificationStore.record(notif("Ana", 1L))
        assertTrue(WhatsAppNotificationStore.recent(0).isEmpty())
        assertTrue(WhatsAppNotificationStore.recent(-5).isEmpty())
    }

    @Test
    fun ringBufferEvictsOldestBeyondMax() {
        val total = WhatsAppNotificationStore.MAX_ENTRIES + 10
        repeat(total) { i -> WhatsAppNotificationStore.record(notif("S$i", i.toLong())) }

        assertEquals(WhatsAppNotificationStore.MAX_ENTRIES, WhatsAppNotificationStore.size())
        // El más viejo retenido es el índice 10 (los 0..9 fueron descartados).
        assertEquals("S10", WhatsAppNotificationStore.recent().first().sender)
        assertEquals("S${total - 1}", WhatsAppNotificationStore.latest()?.sender)
    }

    @Test
    fun clearEmptiesStore() {
        WhatsAppNotificationStore.record(notif("Ana", 1L))
        WhatsAppNotificationStore.clear()
        assertEquals(0, WhatsAppNotificationStore.size())
        assertNull(WhatsAppNotificationStore.latest())
    }
}
