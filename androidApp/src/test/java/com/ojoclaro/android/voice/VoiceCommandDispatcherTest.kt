package com.ojoclaro.android.voice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VoiceCommandDispatcherTest {

    @Test
    fun recognizedReadScreenCommandIsSentToAgentFlow() {
        val commands = mutableListOf<String>()
        val dispatcher = VoiceCommandDispatcher(
            executeCommand = commands::add,
            stopSpeechNow = {}
        )

        dispatcher.onFinalText("qué dice la pantalla")

        assertEquals(listOf("qué dice la pantalla"), commands)
    }

    @Test
    fun confirmCommandIsSentToAgentFlow() {
        val commands = mutableListOf<String>()
        val dispatcher = VoiceCommandDispatcher(
            executeCommand = commands::add,
            stopSpeechNow = {}
        )

        dispatcher.onFinalText("confirmar")

        assertEquals(listOf("confirmar"), commands)
    }

    @Test
    fun siIsOnlySentAsTextAndDoesNotBecomeConfirm() {
        val commands = mutableListOf<String>()
        val dispatcher = VoiceCommandDispatcher(
            executeCommand = commands::add,
            stopSpeechNow = {}
        )

        dispatcher.onFinalText("sí")

        assertEquals(listOf("sí"), commands)
        assertTrue(!VoiceCommandDispatcher.isStopCommand("sí"))
    }

    @Test
    fun callarStopsImmediatelyFromPartialText() {
        var stopped = false
        val commands = mutableListOf<String>()
        val dispatcher = VoiceCommandDispatcher(
            executeCommand = commands::add,
            stopSpeechNow = { stopped = true }
        )

        dispatcher.onPartialText("callar")

        assertTrue(stopped)
        assertTrue(commands.isEmpty())
    }

    @Test
    fun paraStopsImmediatelyFromPartialText() {
        var stopped = false
        val commands = mutableListOf<String>()
        val dispatcher = VoiceCommandDispatcher(
            executeCommand = commands::add,
            stopSpeechNow = { stopped = true }
        )

        dispatcher.onPartialText("pará por favor")

        assertTrue(stopped)
        assertTrue(commands.isEmpty())
    }

    @Test
    fun partialWithCallarInsideStopsImmediately() {
        var stopped = false
        val dispatcher = VoiceCommandDispatcher(
            executeCommand = {},
            stopSpeechNow = { stopped = true }
        )

        dispatcher.onPartialText("callar por favor")

        assertTrue(stopped)
    }

    @Test
    fun callarStopsImmediatelyFromFinalText() {
        var stopped = false
        val commands = mutableListOf<String>()
        val dispatcher = VoiceCommandDispatcher(
            executeCommand = commands::add,
            stopSpeechNow = { stopped = true }
        )

        dispatcher.onFinalText("callar")

        assertTrue(stopped)
        assertTrue(commands.isEmpty())
    }

    @Test
    fun unknownMessageStillGoesToAgentWithoutCrash() {
        val commands = mutableListOf<String>()
        val dispatcher = VoiceCommandDispatcher(
            executeCommand = commands::add,
            stopSpeechNow = {}
        )

        dispatcher.onFinalText("hacer algo raro")

        assertEquals(listOf("hacer algo raro"), commands)
    }

    @Test
    fun ayudaVivaCommandsAreDetected() {
        assertTrue(VoiceCommandDispatcher.isHelpCommand("qué podés hacer"))
        assertTrue(VoiceCommandDispatcher.isHelpCommand("ayuda"))
        assertTrue(VoiceCommandDispatcher.isHelpCommand("explicame cómo usar esto"))
    }

    @Test
    fun blankFinalTextDoesNothing() {
        val commands = mutableListOf<String>()
        val dispatcher = VoiceCommandDispatcher(
            executeCommand = commands::add,
            stopSpeechNow = {}
        )

        dispatcher.onFinalText("   ")

        assertTrue(commands.isEmpty())
    }
}
