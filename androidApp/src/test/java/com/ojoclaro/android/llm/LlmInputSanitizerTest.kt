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
}
