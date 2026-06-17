package com.ojoclaro.android.agent.runtime.routine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HumanRoutineCommandParserTest {

    @Test
    fun setShortResponseFromMultipleVariants() {
        listOf(
            "hablame más corto",
            "respuestas cortas",
            "usá respuestas cortas",
            "no me leas todo",
            "no me leas todo, resumime",
            "respondéme corto"
        ).forEach { phrase ->
            val cmd = HumanRoutineCommandParser.parse(phrase)
            assertTrue(
                cmd is HumanRoutineMemoryCommand.SetPreference,
                "phrase '$phrase' should set short response preference"
            )
            cmd as HumanRoutineMemoryCommand.SetPreference
            assertEquals(RoutinePreferenceKeys.RESPONSE_LENGTH, cmd.key)
            assertEquals(RoutinePreferenceValues.LENGTH_SHORT, cmd.value)
        }
    }

    @Test
    fun setSlowSpeechRecognized() {
        val cmd = HumanRoutineCommandParser.parse("hablame más lento")
        assertTrue(cmd is HumanRoutineMemoryCommand.SetPreference)
        cmd as HumanRoutineMemoryCommand.SetPreference
        assertEquals(RoutinePreferenceKeys.RESPONSE_SPEED, cmd.key)
        assertEquals(RoutinePreferenceValues.SPEED_SLOW, cmd.value)
    }

    @Test
    fun setClearSpeechRecognized() {
        val cmd = HumanRoutineCommandParser.parse("repetí más claro")
        assertTrue(cmd is HumanRoutineMemoryCommand.SetPreference)
        cmd as HumanRoutineMemoryCommand.SetPreference
        assertEquals(RoutinePreferenceKeys.RESPONSE_CLARITY, cmd.key)
        assertEquals(RoutinePreferenceValues.CLARITY_CLEAR, cmd.value)
    }

    @Test
    fun disableLearningRecognized() {
        listOf(
            "no guardes preferencias",
            "no guardes mis preferencias",
            "no aprendas de mí",
            "no quiero que aprendas"
        ).forEach { phrase ->
            assertEquals(
                HumanRoutineMemoryCommand.DisableLearning,
                HumanRoutineCommandParser.parse(phrase),
                "phrase '$phrase' should disable learning"
            )
        }
    }

    @Test
    fun forgetAllPreferencesRecognized() {
        assertEquals(
            HumanRoutineMemoryCommand.ForgetAllPreferences,
            HumanRoutineCommandParser.parse("olvidá mis preferencias")
        )
    }

    @Test
    fun setPrimaryContactRecognized() {
        val cmd = HumanRoutineCommandParser.parse("recordá que Sofi es mi contacto principal")
        assertTrue(cmd is HumanRoutineMemoryCommand.SetFrequentContact)
        cmd as HumanRoutineMemoryCommand.SetFrequentContact
        assertEquals("sofi", cmd.name)
        assertTrue(cmd.isPrimary)
    }

    @Test
    fun setFrequentContactRecognized() {
        val cmd = HumanRoutineCommandParser.parse("recordá que Marco es contacto frecuente")
        assertTrue(cmd is HumanRoutineMemoryCommand.SetFrequentContact)
        cmd as HumanRoutineMemoryCommand.SetFrequentContact
        assertEquals("marco", cmd.name)
        assertEquals(false, cmd.isPrimary)
    }

    @Test
    fun forgetFrequentContactRecognized() {
        val cmd = HumanRoutineCommandParser.parse("olvidá a Marco como contacto frecuente")
        assertTrue(cmd is HumanRoutineMemoryCommand.ForgetFrequentContact)
        cmd as HumanRoutineMemoryCommand.ForgetFrequentContact
        assertEquals("marco", cmd.name)
    }

    @Test
    fun saveQuickMessageRecognized() {
        val cmd = HumanRoutineCommandParser.parse("recordá este mensaje rápido: estoy yendo")
        assertTrue(cmd is HumanRoutineMemoryCommand.SaveQuickMessage)
        cmd as HumanRoutineMemoryCommand.SaveQuickMessage
        assertEquals("estoy yendo", cmd.text)
    }

    @Test
    fun saveQuickMessageQuotedRecognized() {
        val cmd = HumanRoutineCommandParser.parse("guardá 'estoy llegando' como mensaje rápido")
        assertTrue(cmd is HumanRoutineMemoryCommand.SaveQuickMessage)
        cmd as HumanRoutineMemoryCommand.SaveQuickMessage
        assertEquals("estoy llegando", cmd.text)
    }

    @Test
    fun forgetQuickMessageRecognized() {
        assertEquals(
            HumanRoutineMemoryCommand.ForgetLastQuickMessage,
            HumanRoutineCommandParser.parse("olvidá ese mensaje rápido")
        )
    }

    @Test
    fun doesNotConsumeRepeatLast() {
        listOf("repetí", "repetir", "que dijiste").forEach { phrase ->
            assertEquals(
                HumanRoutineMemoryCommand.NotARoutineCommand,
                HumanRoutineCommandParser.parse(phrase),
                "REPEAT_LAST '$phrase' must not be a routine command"
            )
        }
    }

    @Test
    fun doesNotConsumeScreenUnderstandingPhrases() {
        listOf(
            "qué hay en pantalla",
            "resumí la pantalla",
            "dónde estoy",
            "qué puedo hacer acá",
            "leeme lo importante"
        ).forEach { phrase ->
            assertEquals(
                HumanRoutineMemoryCommand.NotARoutineCommand,
                HumanRoutineCommandParser.parse(phrase)
            )
        }
    }

    @Test
    fun doesNotConsumeWhatsAppGuidedOrChatList() {
        listOf(
            "estoy en WhatsApp",
            "qué chats ves",
            "leeme los chats",
            "cómo mando una foto",
            "cómo mando ubicación"
        ).forEach { phrase ->
            assertEquals(
                HumanRoutineMemoryCommand.NotARoutineCommand,
                HumanRoutineCommandParser.parse(phrase)
            )
        }
    }

    @Test
    fun doesNotConsumeLegacyWhatsAppActions() {
        listOf(
            "abrí WhatsApp",
            "mandale a Marco",
            "mandale un mensaje a Marco",
            "abrí el chat de Marco",
            "llamá a Marco"
        ).forEach { phrase ->
            assertEquals(
                HumanRoutineMemoryCommand.NotARoutineCommand,
                HumanRoutineCommandParser.parse(phrase)
            )
        }
    }

    @Test
    fun doesNotConsumeControlPhrases() {
        listOf("callate", "callar", "cancelar", "confirmar", "ayuda").forEach { phrase ->
            assertEquals(
                HumanRoutineMemoryCommand.NotARoutineCommand,
                HumanRoutineCommandParser.parse(phrase)
            )
        }
    }

    @Test
    fun blankIsNotARoutineCommand() {
        assertEquals(HumanRoutineMemoryCommand.NotARoutineCommand,
            HumanRoutineCommandParser.parse(""))
        assertEquals(HumanRoutineMemoryCommand.NotARoutineCommand,
            HumanRoutineCommandParser.parse("   "))
    }
}
