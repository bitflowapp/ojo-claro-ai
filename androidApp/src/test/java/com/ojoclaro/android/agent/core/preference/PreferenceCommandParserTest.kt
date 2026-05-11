package com.ojoclaro.android.agent.core.preference

import com.ojoclaro.android.agent.core.AgentPreferenceKeys
import com.ojoclaro.android.agent.core.AgentPreferenceSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PreferenceCommandParserTest {

    private val parser = PreferenceCommandParser(nowProvider = { 100L })

    @Test
    fun recognizesShortLengthPhrases() {
        val results = listOf(
            "hablame más corto",
            "respuestas cortas",
            "no me leas todo",
            "respondéme corto",
            "resumime todo"
        ).map { parser.parse(it) }

        results.forEach { result ->
            assertTrue(result is PreferenceCommandResult.UpdatePreference)
            val update = result as PreferenceCommandResult.UpdatePreference
            assertEquals(AgentPreferenceKeys.RESPONSE_LENGTH, update.preference.key)
            assertEquals("short", update.preference.value)
            assertEquals(AgentPreferenceSource.USER_EXPLICIT, update.preference.source)
        }
    }

    @Test
    fun recognizesNormalLengthPhrases() {
        val result = parser.parse("hablame normal")
        assertTrue(result is PreferenceCommandResult.UpdatePreference)
        val update = result as PreferenceCommandResult.UpdatePreference
        assertEquals("normal", update.preference.value)
    }

    @Test
    fun recognizesOptOutOfLearning() {
        val result = parser.parse("no guardes mis preferencias")
        assertTrue(result is PreferenceCommandResult.UpdatePreference)
        val update = result as PreferenceCommandResult.UpdatePreference
        assertEquals(AgentPreferenceKeys.LEARNING_OPT_IN, update.preference.key)
        assertEquals("false", update.preference.value)
    }

    @Test
    fun recognizesOptInToLearning() {
        val result = parser.parse("podés aprender de mí")
        assertTrue(result is PreferenceCommandResult.UpdatePreference)
        val update = result as PreferenceCommandResult.UpdatePreference
        assertEquals("true", update.preference.value)
    }

    @Test
    fun recognizesPrimaryContact() {
        val result = parser.parse("recordá que Sofi es mi contacto principal")
        assertTrue(result is PreferenceCommandResult.UpdatePreference)
        val update = result as PreferenceCommandResult.UpdatePreference
        assertEquals(AgentPreferenceKeys.PRIMARY_CONTACT, update.preference.key)
        assertEquals("sofi", update.preference.value)
    }

    @Test
    fun recognizesForgetRequest() {
        val result = parser.parse("olvidá eso")
        assertTrue(result is PreferenceCommandResult.ForgetRequest)
    }

    @Test
    fun ignoresUnrelatedSpeech() {
        listOf(
            "qué hora es",
            "mandale un mensaje a sofi",
            "abrí maps",
            "hola"
        ).forEach { text ->
            assertEquals(PreferenceCommandResult.NotARecognizedPreference, parser.parse(text))
        }
    }

    @Test
    fun preferenceUpdateAlwaysHasExplicitSource() {
        // Sanidad: NUNCA devolvemos preferencias inferred — solo USER_EXPLICIT,
        // porque este parser solo se llama cuando el usuario habló textual.
        val texts = listOf(
            "hablame corto",
            "no aprendas de mí",
            "podés aprender",
            "respuestas cortas"
        )
        texts.forEach { text ->
            val result = parser.parse(text) as? PreferenceCommandResult.UpdatePreference
            if (result != null) {
                assertEquals(AgentPreferenceSource.USER_EXPLICIT, result.preference.source)
            }
        }
    }

    @Test
    fun parserIsCaseAndAccentInsensitive() {
        val r1 = parser.parse("HABLAME MAS CORTO")
        val r2 = parser.parse("hablame mas corto")
        assertTrue(r1 is PreferenceCommandResult.UpdatePreference)
        assertTrue(r2 is PreferenceCommandResult.UpdatePreference)
    }

    @Test
    fun ackTextsAreShortAndDoNotAffirmDangerousActions() {
        val update = parser.parse("hablame más corto") as PreferenceCommandResult.UpdatePreference
        assertTrue(update.spokenAck.length < 80)
        assertTrue(!update.spokenAck.contains("enviado", ignoreCase = true))
    }
}
