package com.ojoclaro.android.llm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProxyHealthProbeTest {

    @Test
    fun blankBaseUrlReportsDisconnectedWithoutNetwork() {
        val probe = ProxyHealthProbe(baseUrl = "")
        assertEquals(ProxyHealthState.Disconnected, probe.check())
    }

    @Test
    fun parseHealthAcceptsExpectedShape() {
        val probe = ProxyHealthProbe(baseUrl = "http://localhost")
        val parsed = probe.parseHealth("""{"ok":true,"model":"gpt-5.4-mini","hasApiKey":true,"host":"127.0.0.1","port":8787}""")
        assertTrue(parsed is ProxyHealthState.Available)
        assertEquals("gpt-5.4-mini", (parsed as ProxyHealthState.Available).model)
    }

    @Test
    fun parseHealthRejectsWhenHasApiKeyFalse() {
        val probe = ProxyHealthProbe(baseUrl = "http://localhost")
        val parsed = probe.parseHealth("""{"ok":true,"model":"gpt-5.4-mini","hasApiKey":false}""")
        assertEquals(ProxyHealthState.Disconnected, parsed)
    }

    @Test
    fun parseHealthRejectsWhenModelMissing() {
        val probe = ProxyHealthProbe(baseUrl = "http://localhost")
        val parsed = probe.parseHealth("""{"ok":true,"hasApiKey":true}""")
        assertEquals(ProxyHealthState.Disconnected, parsed)
    }

    @Test
    fun describeProxyHealthShowsHumanLabelsForEveryState() {
        assertEquals(
            "GPT mini: disponible",
            describeProxyHealth(ProxyHealthState.Available("gpt-5.4-mini"), assistantBaseUrlConfigured = true)
        )
        assertEquals(
            "GPT mini: sin conexión",
            describeProxyHealth(ProxyHealthState.Disconnected, assistantBaseUrlConfigured = true)
        )
        assertEquals(
            "GPT mini: verificando",
            describeProxyHealth(ProxyHealthState.Unknown, assistantBaseUrlConfigured = true)
        )
        assertEquals(
            "GPT mini: sin configurar",
            describeProxyHealth(ProxyHealthState.Unknown, assistantBaseUrlConfigured = false)
        )
    }
}
