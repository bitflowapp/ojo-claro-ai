package com.ojoclaro.android.global

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GlobalAssistantNotifierTest {

    @Test
    fun notificationActionsAreSafeActivationActionsOnly() {
        val source = File("src/main/java/com/ojoclaro/android/global/GlobalAssistantNotifier.kt")
            .readText()

        assertTrue(source.contains("\"Hablar\""))
        assertTrue(source.contains("\"Callar\""))
        assertTrue(source.contains("\"Cerrar\""))
        assertTrue(source.contains("GlobalAssistantMode.ACTION_LISTEN"))
        assertTrue(source.contains("GlobalAssistantMode.ACTION_SILENCE"))
        assertTrue(source.contains("GlobalAssistantMode.ACTION_STOP"))
        assertFalse(source.contains("ACTION_START_GUIDANCE"))
        assertFalse(source.contains("whatsappSend"))
        assertFalse(source.contains("tapWhatsAppSend"))
    }
}
