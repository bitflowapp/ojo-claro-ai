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
        assertTrue(VoiceCommandDispatcher.isHelpCommand("qué puedes hacer"))
        assertTrue(VoiceCommandDispatcher.isHelpCommand("qué sabes hacer"))
        assertTrue(VoiceCommandDispatcher.isHelpCommand("ayuda"))
        assertTrue(VoiceCommandDispatcher.isHelpCommand("ayudame"))
        assertTrue(VoiceCommandDispatcher.isHelpCommand("cómo me podés ayudar"))
        assertTrue(VoiceCommandDispatcher.isHelpCommand("hola, qué podés hacer"))
        assertTrue(VoiceCommandDispatcher.isHelpCommand("hola Estela"))
        assertTrue(VoiceCommandDispatcher.isHelpCommand("explicame cómo usar esto"))
    }

    @Test
    fun exactSamsungHelpAndGreetingPhrasesAreDetected() {
        listOf(
            "Hola Estela",
            "Hola, qu\u00E9 pod\u00E9s hacer",
            "Qu\u00E9 pod\u00E9s hacer",
            "Qu\u00E9 puedes hacer",
            "Ayuda"
        ).forEach { phrase ->
            assertTrue(VoiceCommandDispatcher.isHelpCommand(phrase), "phrase=$phrase")
        }
    }

    @Test
    fun alexaLikeStopWordsAreDetected() {
        // Grupo E (barge-in / parar). "cancelar" NO está acá a propósito.
        listOf(
            "basta", "basta ya", "stop", "frená", "frenate", "detené",
            "detenete", "silencio", "callate", "pará", "parar"
        ).forEach { phrase ->
            assertTrue(VoiceCommandDispatcher.isStopCommand(phrase), "stop phrase=$phrase")
        }
        assertTrue(!VoiceCommandDispatcher.isStopCommand("cancelar"))
        assertTrue(!VoiceCommandDispatcher.isStopCommand("describir entorno"))
    }

    @Test
    fun alexaLikeHelpWordsAreDetected() {
        // Grupo F. Variantes nuevas de la spec.
        listOf("opciones", "comandos", "menú", "qué comandos hay", "qué puedo decir")
            .forEach { phrase ->
                assertTrue(VoiceCommandDispatcher.isHelpCommand(phrase), "help phrase=$phrase")
            }
    }

    @Test
    fun alexaLikeRepeatWordsAreDetected() {
        // Grupo D. Robusto a acentos y voseo.
        listOf(
            "repetir", "repetí", "repetilo", "repetímelo", "decilo de nuevo",
            "otra vez", "una vez más", "no escuché", "no entendí", "qué dijiste"
        ).forEach { phrase ->
            assertTrue(VoiceCommandDispatcher.isRepeatCommand(phrase), "repeat phrase=$phrase")
        }
        // No debe confundir un comando real con repetir.
        assertTrue(!VoiceCommandDispatcher.isRepeatCommand("describir entorno"))
        assertTrue(!VoiceCommandDispatcher.isRepeatCommand("dónde estoy"))
    }

    @Test
    fun bareCancelWordsAreDetectedButNotStop() {
        // Grupo E "cancelar": se maneja aparte de los pendientes; NO es stop.
        listOf(
            "cancelar", "cancelá", "cancela", "cancelalo", "anular", "dejalo",
            "olvidalo", "me arrepentí"
        )
            .forEach { phrase ->
                assertTrue(VoiceCommandDispatcher.isBareCancelCommand(phrase), "cancel phrase=$phrase")
            }
        assertTrue(!VoiceCommandDispatcher.isBareCancelCommand("describir entorno"))
        assertTrue(!VoiceCommandDispatcher.isBareCancelCommand("sí"))
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
