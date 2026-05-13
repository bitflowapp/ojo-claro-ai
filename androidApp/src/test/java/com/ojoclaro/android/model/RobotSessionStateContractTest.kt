package com.ojoclaro.android.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RobotSessionStateContractTest {

    @Test
    fun offNeverTransitionsDirectlyToActiveListeningOrProcessing() {
        assertFalse(RobotSessionState.OFF.canTransitionTo(RobotSessionState.LISTENING))
        assertFalse(RobotSessionState.OFF.canTransitionTo(RobotSessionState.PROCESSING))
        assertTrue(RobotSessionState.OFF.requiresMicPaused())
    }

    @Test
    fun readyListeningAndProcessingFollowRobotLoopContract() {
        assertTrue(RobotSessionState.READY.canTransitionTo(RobotSessionState.LISTENING))
        assertTrue(RobotSessionState.LISTENING.canTransitionTo(RobotSessionState.PROCESSING))
        assertTrue(RobotSessionState.LISTENING.canTransitionTo(RobotSessionState.ERROR_RECOVERABLE))
        assertTrue(RobotSessionState.PROCESSING.canTransitionTo(RobotSessionState.SPEAKING))
        assertTrue(RobotSessionState.PROCESSING.canTransitionTo(RobotSessionState.READY))
        assertTrue(RobotSessionState.PROCESSING.canTransitionTo(RobotSessionState.WAITING_WHATSAPP))
        assertTrue(RobotSessionState.PROCESSING.canTransitionTo(RobotSessionState.WAITING_CONFIRMATION))
    }

    @Test
    fun speakingAndErrorStatesKeepMicrophonePaused() {
        assertTrue(RobotSessionState.SPEAKING.requiresMicPaused())
        assertTrue(RobotSessionState.ERROR_RECOVERABLE.requiresMicPaused())
        assertTrue(RobotSessionState.SPEAKING.canTransitionTo(RobotSessionState.READY))
        assertTrue(RobotSessionState.SPEAKING.canTransitionTo(RobotSessionState.LISTENING))
        assertFalse(RobotSessionState.SPEAKING.canTransitionTo(RobotSessionState.WAITING_WHATSAPP))
    }

    @Test
    fun waitingStatesCanBeInterruptedByGlobalSafeCommands() {
        assertTrue(RobotSessionState.WAITING_WHATSAPP.canTransitionTo(RobotSessionState.PROCESSING))
        assertTrue(RobotSessionState.WAITING_WHATSAPP.canTransitionTo(RobotSessionState.SPEAKING))
        assertTrue(RobotSessionState.WAITING_WHATSAPP.canTransitionTo(RobotSessionState.READY))
        assertTrue(RobotSessionState.WAITING_CONFIRMATION.canTransitionTo(RobotSessionState.PROCESSING))
        assertTrue(RobotSessionState.WAITING_CONFIRMATION.canTransitionTo(RobotSessionState.SPEAKING))
        assertTrue(RobotSessionState.WAITING_CONFIRMATION.canTransitionTo(RobotSessionState.READY))
    }
}
