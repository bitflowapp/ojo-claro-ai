package com.ojoclaro.android.llm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins Android-side LLM config:
 *  - Default model is gpt-5.4-mini.
 *  - isConfigured() is false when baseUrl is blank (degrada silenciosamente).
 *  - interpretUrl is built from baseUrl + "/v1/interpret" sin doble slash.
 *  - Emulator and LAN base URLs son aceptados.
 */
class LlmAgentClientConfigTest {

    @Test
    fun defaultModelIsGpt54Mini() {
        val cfg = LlmAgentClientConfig(baseUrl = "http://10.0.2.2:8787")
        assertEquals("gpt-5.4-mini", cfg.model)
        assertEquals("gpt-5.4-mini", LlmAgentClientConfig.DEFAULT_MODEL)
    }

    @Test
    fun blankBaseUrlIsNotConfigured() {
        val cfg = LlmAgentClientConfig(baseUrl = "")
        assertFalse(cfg.isConfigured())
        assertEquals("", cfg.interpretUrl)
    }

    @Test
    fun nonHttpBaseUrlIsNotConfigured() {
        val cfg = LlmAgentClientConfig(baseUrl = "10.0.2.2:8787")
        // Sin esquema http:// la guarda rechaza por seguridad.
        assertFalse(cfg.isConfigured())
    }

    @Test
    fun emulatorBaseUrlIsAccepted() {
        val cfg = LlmAgentClientConfig(baseUrl = "http://10.0.2.2:8787")
        assertTrue(cfg.isConfigured())
        assertEquals("http://10.0.2.2:8787/v1/interpret", cfg.interpretUrl)
    }

    @Test
    fun lanBaseUrlIsAcceptedForPhysicalDevice() {
        val cfg = LlmAgentClientConfig(baseUrl = "http://192.168.1.42:8787")
        assertTrue(cfg.isConfigured())
        assertEquals("http://192.168.1.42:8787/v1/interpret", cfg.interpretUrl)
    }

    @Test
    fun trailingSlashIsNormalised() {
        val cfg = LlmAgentClientConfig(baseUrl = "http://10.0.2.2:8787/")
        assertEquals("http://10.0.2.2:8787/v1/interpret", cfg.interpretUrl)
    }
}
