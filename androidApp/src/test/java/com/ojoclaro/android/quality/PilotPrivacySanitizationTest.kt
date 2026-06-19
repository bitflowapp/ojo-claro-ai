package com.ojoclaro.android.quality

import com.ojoclaro.android.llm.LlmInputSanitizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * FASE 5 (doc) — Verificación READ-ONLY de privacidad con datos SINTÉTICOS.
 *
 * Confirma el comportamiento EXISTENTE de [LlmInputSanitizer]: antes de salir al
 * LLM/backend, un secreto dictado (PIN/código/clave/teléfono/email) se redacta y
 * NO se repite. Además, el sanitizador NO "secuestra" la conversación: no rompe
 * frases normales sin secreto. No se modifica ninguna lógica de seguridad; si
 * algún secreto sintético sobreviviera, sería un HALLAZGO HIGH (reportar BLOCKED),
 * no un fix acá.
 */
class PilotPrivacySanitizationTest {

    // Secretos sintéticos (jamás reales) y la frase que los dicta.
    private val secretPhrases: List<Pair<String, String>> = listOf(
        "mi pin es 1234" to "1234",
        "mi código es 445566" to "445566",
        "mi clave es azul123" to "azul123",
        "mi clave 9988" to "9988",
        "mi teléfono es 2991234567" to "2991234567",
        "mi correo es juan@prueba.com" to "juan@prueba.com",
        "mandale mi código 4821" to "4821"
    )

    @Test
    fun dictatedSecretsAreRedactedBeforeLeavingThePhone() {
        secretPhrases.forEach { (phrase, secret) ->
            val out = LlmInputSanitizer.sanitize(phrase)
            assertFalse(
                out.contains(secret),
                "el secreto sintético \"$secret\" sobrevivió: \"$phrase\" -> \"$out\""
            )
        }
    }

    @Test
    fun redactionDoesNotRepeatTheSecretAnywhereInOutput() {
        // "no repetición del secreto": ni siquiera parcialmente como corrida.
        val out = LlmInputSanitizer.sanitize("mi pin es 1234 y mi código es 445566")
        assertFalse(out.contains("1234"), "PIN repetido: \"$out\"")
        assertFalse(out.contains("445566"), "código repetido: \"$out\"")
        assertTrue(out.contains("[dato]"), "debe quedar marcado como redactado: \"$out\"")
    }

    @Test
    fun sanitizerDoesNotHijackNormalSpeechWithoutASecretValue() {
        // Frases legítimas que NOMBRAN una palabra sensible pero SIN dictar un valor:
        // el sanitizador debe dejarlas intactas (no secuestra la conversación).
        listOf(
            "qué es un pin",
            "la clave musical",
            "cómo cambio mi contraseña",
            "cancelá",
            "ayuda",
            "abrí WhatsApp",
            "leé la pantalla"
        ).forEach { p ->
            assertEquals(p, LlmInputSanitizer.sanitize(p), "el sanitizador alteró una frase sin secreto: \"$p\"")
        }
    }

    @Test
    fun shortAddressLikeNumbersAreNotOverRedacted() {
        // Un número corto que NO es secreto (código postal, altura de calle) se
        // conserva: sobre-redactar rompería pedidos normales.
        val out = LlmInputSanitizer.sanitize("vivo en la calle 1234")
        assertTrue(out.contains("1234"), "no debería redactar una altura de calle: \"$out\"")
    }

    @Test
    fun sendRequestWithACodeStillRedactsTheCodeBeforeLlm() {
        // Aunque el pedido sea de ENVÍO (que el runtime bloquea), el código no debe
        // viajar crudo al LLM: defensa en profundidad.
        val out = LlmInputSanitizer.sanitize("mandale mi código 4821 a Ana")
        assertFalse(out.contains("4821"), "el código viajó crudo: \"$out\"")
    }

    @Test
    fun emptyInputIsHandledSafely() {
        assertEquals("", LlmInputSanitizer.sanitize(""))
        assertEquals("   ", LlmInputSanitizer.sanitize("   "))
    }
}
