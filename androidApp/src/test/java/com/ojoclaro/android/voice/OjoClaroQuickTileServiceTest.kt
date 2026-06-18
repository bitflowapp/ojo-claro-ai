package com.ojoclaro.android.voice

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OjoClaroQuickTileServiceTest {

    @Test
    fun tileStartsGlobalOnlyWhenMicrophoneAndAccessibilityAreReady() {
        assertTrue(
            estelaQuickTileReadiness(
                hasMicrophonePermission = true,
                isAccessibilityActive = true
            ).canStartGlobal
        )
        assertFalse(
            estelaQuickTileReadiness(
                hasMicrophonePermission = false,
                isAccessibilityActive = true
            ).canStartGlobal
        )
        assertFalse(
            estelaQuickTileReadiness(
                hasMicrophonePermission = true,
                isAccessibilityActive = false
            ).canStartGlobal
        )
    }

    @Test
    fun tileUnavailableMessageIsHonestAndNonSensitive() {
        val message = estelaQuickTileReadiness(
            hasMicrophonePermission = false,
            isAccessibilityActive = false
        ).messageWhenUnavailable

        assertTrue(message.contains("micrófono"))
        assertTrue(message.contains("accesibilidad"))
        assertFalse(message.contains("WhatsApp", ignoreCase = true))
        assertFalse(message.contains("env"))
    }

    @Test
    fun tileUsesGlobalOverlayVoiceAndNeverMentionsWhatsAppSendFlow() {
        val source = File("src/main/java/com/ojoclaro/android/voice/OjoClaroQuickTileService.kt")
            .readText()

        assertTrue(source.contains("GlobalAssistantService.startOverlayVoice"))
        assertTrue(source.contains("ACTION_START_LISTENING"))
        assertFalse(source.contains("WhatsAppIntentHelper"))
        assertFalse(source.contains("tapWhatsAppSend"))
        assertFalse(source.contains("pendingWhatsAppSendDraft"))
    }
}
