package com.ojoclaro.android.voice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OjoClaroIntentsTest {

    @Test
    fun actionStartListeningTriggersListeningRequest() {
        assertTrue(
            OjoClaroIntents.isListeningRequest(
                action = OjoClaroIntents.ACTION_START_LISTENING,
                startListeningExtra = false
            )
        )
    }

    @Test
    fun extraStartListeningTrueTriggersListeningRequest() {
        assertTrue(
            OjoClaroIntents.isListeningRequest(
                action = null,
                startListeningExtra = true
            )
        )
    }

    @Test
    fun mainLauncherActionDoesNotTriggerListeningRequest() {
        assertFalse(
            OjoClaroIntents.isListeningRequest(
                action = "android.intent.action.MAIN",
                startListeningExtra = false
            )
        )
    }

    @Test
    fun nullActionWithoutExtraDoesNotTriggerListeningRequest() {
        assertFalse(
            OjoClaroIntents.isListeningRequest(
                action = null,
                startListeningExtra = false
            )
        )
    }

    @Test
    fun actionStopSpeakingTriggersStopRequest() {
        assertTrue(
            OjoClaroIntents.isStopSpeakingRequest(
                action = OjoClaroIntents.ACTION_STOP_SPEAKING,
                stopSpeakingExtra = false
            )
        )
    }

    @Test
    fun extraStopSpeakingTrueTriggersStopRequest() {
        assertTrue(
            OjoClaroIntents.isStopSpeakingRequest(
                action = null,
                stopSpeakingExtra = true
            )
        )
    }

    @Test
    fun startListeningDoesNotTriggerStopRequest() {
        assertFalse(
            OjoClaroIntents.isStopSpeakingRequest(
                action = OjoClaroIntents.ACTION_START_LISTENING,
                stopSpeakingExtra = false
            )
        )
    }

    @Test
    fun bothActionAndExtraStillTriggersListeningRequest() {
        assertTrue(
            OjoClaroIntents.isListeningRequest(
                action = OjoClaroIntents.ACTION_START_LISTENING,
                startListeningExtra = true
            )
        )
    }

    @Test
    fun actionConstantMatchesPublicName() {
        assertEquals(
            "com.ojoclaro.android.ACTION_START_LISTENING",
            OjoClaroIntents.ACTION_START_LISTENING
        )
        assertEquals(
            "com.ojoclaro.android.ACTION_STOP_SPEAKING",
            OjoClaroIntents.ACTION_STOP_SPEAKING
        )
        assertEquals(
            "start_listening",
            OjoClaroIntents.EXTRA_START_LISTENING
        )
        assertEquals(
            "stop_speaking",
            OjoClaroIntents.EXTRA_STOP_SPEAKING
        )
    }
}
