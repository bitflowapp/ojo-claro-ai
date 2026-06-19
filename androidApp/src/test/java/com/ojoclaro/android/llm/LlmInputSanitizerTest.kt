package com.ojoclaro.android.llm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Privacidad: nada de teléfonos/emails/tokens viaja al LLM. Números SINTÉTICOS. */
class LlmInputSanitizerTest {

    @Test
    fun redactsSyntheticPhonesEmailsAndTokens() {
        // teléfonos sintéticos (no reales)
        listOf(
            "llamá al 1150000000",
            "mi número es +5491150000000",
            "anotá 011 5000 0000 por favor",
            "el contacto 0000005678"
        ).forEach { s ->
            val out = LlmInputSanitizer.sanitize(s)
            assertTrue(out.contains("[número]"), "debía redactar el teléfono en: \"$s\" -> \"$out\"")
            assertFalse(Regex("\\d{7,}").containsMatchIn(out), "no deben quedar 7+ dígitos: \"$out\"")
        }
        assertEquals("escribile a [email]", LlmInputSanitizer.sanitize("escribile a juan.perez@mail.com"))
        // token largo mixto sintético (sin prefijo de proveedor)
        assertTrue(LlmInputSanitizer.sanitize("la clave Abc123Def456Ghi789Jkl012").contains("[clave]"))
    }

    @Test
    fun keepsSafeShortNumbersAndPlainText() {
        // pocos dígitos NO son PII
        assertEquals("vivo en la calle 1234", LlmInputSanitizer.sanitize("vivo en la calle 1234"))
        assertEquals("nos vemos 10:30", LlmInputSanitizer.sanitize("nos vemos 10:30"))
        // Q&A sin PII queda intacto (no degradar la conversación)
        assertEquals("qué significa factura A", LlmInputSanitizer.sanitize("qué significa factura A"))
        assertEquals(
            "explicame qué es una transferencia",
            LlmInputSanitizer.sanitize("explicame qué es una transferencia")
        )
    }

    @Test
    fun redactsDictatedShortSecretsByKeyword() {
        // FASE 4 fuzz: secreto dictado corto (PIN de 4 dígitos < 7) que el DIGIT_RUN
        // no atrapa y que ConversationGate no filtra ("pin/código/cvv"). Debe quedar
        // redactado en el egress a /conversation.
        listOf(
            "mi pin es 1234",
            "el código es 4821",
            "la clave es gato7",
            "cvv: 123",
            "mi password es hola123",
            "el otp es 9090"
        ).forEach { s ->
            val out = LlmInputSanitizer.sanitize(s)
            assertTrue(out.contains("[dato]"), "secreto dictado debía redactarse: \"$s\" -> \"$out\"")
            assertFalse(
                Regex("\\b(1234|4821|gato7|123|hola123|9090)\\b").containsMatchIn(out),
                "no debe quedar el secreto crudo: \"$out\""
            )
        }
    }

    @Test
    fun doesNotOverRedactNormalTextWithSecretWords() {
        // La palabra clave SIN conector+valor NO debe redactar texto normal.
        listOf(
            "qué es un pin",
            "la clave musical de sol",
            "para qué sirve el cvv",
            "cuál es mi clave",
            "el código de la felicidad"
        ).forEach { s ->
            assertEquals(s, LlmInputSanitizer.sanitize(s), "no debía tocar texto normal: \"$s\"")
        }
        // El token largo SIN conector sigue redactándose como [clave] (no [dato]).
        assertTrue(LlmInputSanitizer.sanitize("la clave Abc123Def456Ghi789Jkl012").contains("[clave]"))
    }
}
