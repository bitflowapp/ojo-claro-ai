package com.ojoclaro.android.agent.core.screen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScreenTextSanitizerTest {

    @Test
    fun `blank line returns blank`() {
        assertEquals("", ScreenTextSanitizer.sanitizeLine("   "))
    }

    @Test
    fun `plain text passes through`() {
        val line = "Hola, estoy llegando"
        assertEquals(line, ScreenTextSanitizer.sanitizeLine(line))
    }

    @Test
    fun `password assignment is fully redacted`() {
        val line = "Mi contraseña es supersecreto123"
        assertEquals(ScreenTextSanitizer.PASSWORD_PLACEHOLDER, ScreenTextSanitizer.sanitizeLine(line))
    }

    @Test
    fun `password label with colon is redacted`() {
        val line = "Password: abcXYZ123"
        assertEquals(ScreenTextSanitizer.PASSWORD_PLACEHOLDER, ScreenTextSanitizer.sanitizeLine(line))
    }

    @Test
    fun `pin label with value is redacted`() {
        val line = "PIN: 4521"
        assertEquals(ScreenTextSanitizer.PASSWORD_PLACEHOLDER, ScreenTextSanitizer.sanitizeLine(line))
    }

    @Test
    fun `verification code line is fully redacted`() {
        val line = "Tu codigo de verificacion es 123456"
        assertEquals(ScreenTextSanitizer.CODE_PLACEHOLDER, ScreenTextSanitizer.sanitizeLine(line))
    }

    @Test
    fun `otp keyword and digits is redacted`() {
        val line = "OTP 445566"
        assertEquals(ScreenTextSanitizer.CODE_PLACEHOLDER, ScreenTextSanitizer.sanitizeLine(line))
    }

    @Test
    fun `credit card number is replaced by sensitive number placeholder`() {
        val line = "Tarjeta 4111 1111 1111 1111 vence en 2027"
        val sanitized = ScreenTextSanitizer.sanitizeLine(line)
        assertTrue(
            sanitized.contains(ScreenTextSanitizer.SENSITIVE_NUMBER_PLACEHOLDER),
            "expected sensitive number placeholder, got: $sanitized"
        )
        assertTrue(!sanitized.contains("4111"))
    }

    @Test
    fun `long document number is replaced`() {
        val line = "DNI 35422100"
        val sanitized = ScreenTextSanitizer.sanitizeLine(line)
        assertTrue(sanitized.contains(ScreenTextSanitizer.SENSITIVE_NUMBER_PLACEHOLDER))
    }

    @Test
    fun `short phone-like number is not redacted as document`() {
        // Solo 4 dígitos no debería disparar SENSITIVE_NUMBER (mínimo 7).
        val line = "Local 2024"
        val sanitized = ScreenTextSanitizer.sanitizeLine(line)
        assertTrue(!sanitized.contains(ScreenTextSanitizer.SENSITIVE_NUMBER_PLACEHOLDER))
    }

    @Test
    fun `long secret-like token is redacted as private data`() {
        val line = "Token aB9xK2mN7pQ4rS6tU8v"
        val sanitized = ScreenTextSanitizer.sanitizeLine(line)
        assertTrue(sanitized.contains(ScreenTextSanitizer.PRIVATE_DATA_PLACEHOLDER))
    }

    @Test
    fun `sanitizeText splits by newline and filters blanks`() {
        val text = "Hola\n\nMi password es 1234\nCódigo de verificacion es 999888\nChau"
        val lines = ScreenTextSanitizer.sanitizeText(text)
        assertEquals(4, lines.size)
        assertEquals("Hola", lines[0])
        assertEquals(ScreenTextSanitizer.PASSWORD_PLACEHOLDER, lines[1])
        assertEquals(ScreenTextSanitizer.CODE_PLACEHOLDER, lines[2])
        assertEquals("Chau", lines[3])
    }

    @Test
    fun `accent insensitive password detection`() {
        val line = "Mi contrasena es claveSegura1"
        assertEquals(ScreenTextSanitizer.PASSWORD_PLACEHOLDER, ScreenTextSanitizer.sanitizeLine(line))
    }

    @Test
    fun `looksFinanciallySensitive detects card number`() {
        assertTrue(ScreenTextSanitizer.looksFinanciallySensitive("4111 1111 1111 1111"))
    }

    @Test
    fun `looksFinanciallySensitive false for plain text`() {
        assertTrue(!ScreenTextSanitizer.looksFinanciallySensitive("hola que tal"))
    }
}
