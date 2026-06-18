package com.ojoclaro.android.outdoor

import com.ojoclaro.android.llm.LlmAgentClientConfig
import com.ojoclaro.android.llm.LlmAgentNetworkClient
import com.ojoclaro.android.llm.LlmHttpResponse
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * V1.4 — "¿dónde estoy?" con calle/barrio útil.
 */
class OutdoorReverseLabelTest {

    private fun provider() = BackendOutdoorRouteProvider(
        config = LlmAgentClientConfig(baseUrl = "http://x"),
        networkClient = object : LlmAgentNetworkClient {
            override suspend fun postJson(
                url: String,
                jsonBody: String,
                timeoutMillis: Long,
                headers: Map<String, String>
            ) = LlmHttpResponse(200, "")
        }
    )

    @Test
    fun parsesLabelFromBackendContract() {
        assertEquals(
            "San Martín 500, Centro, Rosario",
            provider().parseReverseLabel(
                """{"ok":true,"configured":true,"status":"OK",
                    "label":"San Martín 500, Centro, Rosario","error_code":null}"""
            )
        )
    }

    @Test
    fun failsClosedOnAnyProblem() {
        val p = provider()
        assertEquals(null, p.parseReverseLabel("""{"ok":true,"status":"NOT_FOUND","label":null}"""))
        assertEquals(null, p.parseReverseLabel("""{"ok":false,"status":"ERROR","label":"x"}"""))
        assertEquals(null, p.parseReverseLabel("""{"ok":true,"label":"   "}"""))
        assertEquals(null, p.parseReverseLabel("{not json"))
    }

    @Test
    fun whereAmIUsesReverseLabelWithHonestFallback() {
        val coordinator = File(
            "src/main/java/com/ojoclaro/android/outdoor/OutdoorNavigationCoordinator.kt"
        ).readText()
        assertTrue(coordinator.contains("reverseLabel(fix.latitude, fix.longitude)"))
        assertTrue(
            coordinator.contains("reverseLabelPresent="),
            "el label nunca se loguea: solo su presencia"
        )
        assertTrue(coordinator.contains("No pude obtener la calle ahora."))
        assertTrue(coordinator.contains("Estás cerca de \$label"))
    }

    @Test
    fun routeReadyAnnouncementIsASingleUtterance() {
        // Fallo físico real: dos say() seguidos → el segundo pisa al primero
        // y la persona nunca escucha la ruta.
        val coordinator = File(
            "src/main/java/com/ojoclaro/android/outdoor/OutdoorNavigationCoordinator.kt"
        ).readText()
        val routeReadyBlock = coordinator
            .substringAfter("transition(OutdoorState.NAVIGATING, \"route_ready\")")
            .substringBefore("OutdoorRouteOutcome.Unconfigured")
        val sayCalls = Regex("\\bsay\\(").findAll(routeReadyBlock).count()
        assertEquals(1, sayCalls, "route_ready debe hablar UNA sola vez (hay $sayCalls say())")
        assertTrue(routeReadyBlock.contains("startAnnouncement() + \" \" +"))
    }
}
