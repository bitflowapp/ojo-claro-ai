package com.ojoclaro.android.outdoor

import com.ojoclaro.android.agent.mission.AgentMissionPhrases
import com.ojoclaro.android.agent.mission.AgentOutdoorFixSummary
import com.ojoclaro.android.agent.mission.AgentToolName
import com.ojoclaro.android.agent.mission.AgentSessionState
import com.ojoclaro.android.agent.mission.AgentValidatedAction
import com.ojoclaro.android.agent.mission.AndroidAgentToolExecutor
import com.ojoclaro.android.llm.LlmAgentClientConfig
import com.ojoclaro.android.llm.LlmAgentNetworkClient
import com.ojoclaro.android.llm.LlmHttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

// --- fakes compartidos ---

private fun validFix() = OutdoorLocationFix(-34.6, -58.38, 12f, 700L, "gps")

private class FakeReaderEngine(var result: OutdoorLocationFix? = validFix()) : OutdoorLocationEngine {
    override fun hasPermission(): Boolean = true
    override fun servicesEnabled(): Boolean = true
    override suspend fun freshFix(timeoutMillis: Long): OutdoorLocationFix? = result
}

private class FakeRouteProvider(
    var outcome: OutdoorRouteOutcome = OutdoorRouteOutcome.Unconfigured,
    val gate: CompletableDeferred<Unit>? = null
) : OutdoorRouteProvider {
    override suspend fun walkingRoute(
        originLatitude: Double,
        originLongitude: Double,
        destination: String
    ): OutdoorRouteOutcome {
        gate?.await()
        return outcome
    }
}

private class FakeDescriber(
    var outcome: OutdoorSceneOutcome = OutdoorSceneOutcome.Described("Veo una pared."),
    var explicitReceived: Boolean? = null
) : OutdoorSceneDescriber {
    override suspend fun describeAhead(explicitUserRequest: Boolean): OutdoorSceneOutcome {
        explicitReceived = explicitUserRequest
        return outcome
    }
}

private fun route(destination: String = "la plaza") = OutdoorRouteData(
    destinationName = destination,
    totalDistanceMeters = 300,
    totalDurationSeconds = 240,
    steps = listOf(OutdoorRouteStep("caminá derecho", 200), OutdoorRouteStep("girá a la derecha", 100))
)

private fun coordinator(
    routeProvider: OutdoorRouteProvider = FakeRouteProvider(),
    describer: OutdoorSceneDescriber = FakeDescriber(),
    spoken: MutableList<String> = mutableListOf(),
    logs: MutableList<String> = mutableListOf(),
    engineFix: OutdoorLocationFix? = validFix()
): Triple<OutdoorNavigationCoordinator, MutableList<String>, MutableList<String>> {
    val coordinator = OutdoorNavigationCoordinator(
        locationReader = OutdoorLocationReader(FakeReaderEngine(engineFix)),
        routeProvider = routeProvider,
        sceneDescriber = describer,
        speak = { spoken += it },
        log = { logs += it }
    )
    return Triple(coordinator, spoken, logs)
}

class OutdoorNavigationCoordinatorTest {

    // Test 12 (key ausente): degradación honesta y vuelta a IDLE.
    @Test
    fun unconfiguredRouteDegradesHonestly() = runTest {
        val (coord, spoken, _) = coordinator(FakeRouteProvider(OutdoorRouteOutcome.Unconfigured))
        val active = coord.startGuidance("la plaza")
        assertFalse(active)
        assertEquals(OutdoorState.IDLE, coord.state)
        assertTrue(spoken.any { it.contains("no está configurado") })
        assertTrue(spoken.none { it.contains("girá") })
    }

    // Test 15: destino ambiguo/no encontrado → re-pregunta, nunca adivina.
    // V1.10.2 — la re-pregunta ahora pide calle y altura (guía concreta).
    @Test
    fun notFoundAsksForBetterDestination() = runTest {
        val (coord, spoken, _) = coordinator(FakeRouteProvider(OutdoorRouteOutcome.NotFound))
        assertFalse(coord.startGuidance("lo de siempre"))
        assertTrue(spoken.any { it.contains("Decime la calle y la altura") })
        assertEquals(OutdoorState.IDLE, coord.state)
    }

    // Test 16: error de ruta → honesto + IDLE.
    @Test
    fun routeErrorReturnsToIdle() = runTest {
        val (coord, spoken, _) = coordinator(FakeRouteProvider(OutdoorRouteOutcome.Error("route_timeout")))
        assertFalse(coord.startGuidance("la plaza"))
        assertTrue(spoken.any { it.contains("No pude calcular la ruta") })
        assertEquals(OutdoorState.IDLE, coord.state)
    }

    // Test 17 + 50: inicio de navegación anuncia ruta y rol complementario.
    @Test
    fun startGuidanceAnnouncesRouteAndComplementaryRole() = runTest {
        val (coord, spoken, _) = coordinator(FakeRouteProvider(OutdoorRouteOutcome.Route(route())))
        val active = coord.startGuidance("la plaza")
        assertTrue(active)
        assertEquals(OutdoorState.NAVIGATING, coord.state)
        assertTrue(spoken.any { it.contains("Empiezo a orientarte") && it.contains("a pie") })
        // Nunca se presenta como sustituto del bastón/perro guía.
        assertTrue(spoken.any { it.contains("complementaria") && it.contains("bastón") })
        assertTrue(coord.currentProgress() != null)
    }

    // Tests 18 + 25: detención limpia y cancelación.
    @Test
    fun cancelStopsCleanly() = runTest {
        val (coord, spoken, _) = coordinator(FakeRouteProvider(OutdoorRouteOutcome.Route(route())))
        coord.startGuidance("la plaza")
        coord.cancelGuidance("user")
        assertEquals(OutdoorState.IDLE, coord.state)
        assertFalse(coord.isNavigating)
        assertEquals(null, coord.currentProgress())
        assertTrue(spoken.any { it.contains("Navegación cancelada") })
    }

    // Test 24: resultado viejo ignorado (cancelar mientras calcula ruta).
    @Test
    fun staleRouteResultIsIgnoredAfterCancel() = runTest {
        val gate = CompletableDeferred<Unit>()
        val provider = FakeRouteProvider(OutdoorRouteOutcome.Route(route()), gate)
        val (coord, spoken, logs) = coordinator(provider)

        val job = launch { coord.startGuidance("la plaza") }
        // Cancelar ANTES de que la ruta llegue.
        kotlinx.coroutines.yield()
        coord.cancelGuidance("user")
        gate.complete(Unit)
        job.join()

        assertEquals(OutdoorState.IDLE, coord.state)
        assertFalse(coord.isNavigating)
        assertTrue(spoken.none { it.contains("Empiezo a orientarte") })
        assertTrue(logs.any { it.contains("staleResultIgnored=true") })
    }

    // Tests 9 + 25(privacidad): coordenadas jamás en logs ni en lo hablado.
    @Test
    fun coordinatesNeverAppearInLogsOrSpeech() = runTest {
        val (coord, spoken, logs) = coordinator()
        coord.whereAmI()
        val all = (logs + spoken).joinToString(" ")
        assertFalse(all.contains("-34"), "lat filtrada en: $all")
        assertFalse(all.contains("-58"), "lng filtrada en: $all")
        assertTrue(logs.any { it.contains("accuracyBucket=") })
        assertTrue(spoken.any { it.contains("precisión aproximada de 12 metros") })
    }

    // Tests 47/48: texto peligroso de CUALQUIER origen se sanea antes del TTS.
    @Test
    fun dangerousDescriptionFromBackendIsSanitizedBeforeTts() = runTest {
        val describer = FakeDescriber(
            OutdoorSceneOutcome.Described("El camino está libre, podés avanzar.")
        )
        val (coord, spoken, _) = coordinator(describer = describer)
        coord.describeAhead()
        assertTrue(spoken.contains(OutdoorSafetyPolicy.SAFE_FALLBACK_TEXT))
        assertTrue(spoken.none { it.contains("podés avanzar") })
        assertEquals(OutdoorState.IDLE, coord.state)
    }

    // Test 36 + 61: error de escena vuelve a estado utilizable.
    @Test
    fun sceneErrorReturnsToUsableState() = runTest {
        val describer = FakeDescriber(OutdoorSceneOutcome.Error("vision_http_500"))
        val (coord, spoken, _) = coordinator(describer = describer)
        coord.describeAhead()
        assertEquals(OutdoorState.IDLE, coord.state)
        assertTrue(spoken.any { it.contains("No pude describir") })
        // Sigue utilizable: una segunda operación funciona.
        coord.whereAmI()
        assertTrue(spoken.any { it.contains("precisión") })
    }

    // Test 26 (cadena completa): el coordinator siempre pide explícito=true.
    @Test
    fun describePassesExplicitFlag() = runTest {
        val describer = FakeDescriber()
        val (coord, _, logs) = coordinator(describer = describer)
        coord.describeAhead()
        assertEquals(true, describer.explicitReceived)
        assertTrue(logs.any { it.contains("explicitRequest=true") })
    }
}

// --- BackendSceneDescriber con red/capturer fake ---

private class FakeCapturer(
    var permission: Boolean = true,
    var bytes: ByteArray? = ByteArray(16) { 1 },
    var invoked: Int = 0
) : OutdoorSceneCapturer {
    override fun hasCameraPermission(): Boolean = permission
    override suspend fun captureSingleJpeg(timeoutMillis: Long): ByteArray? {
        invoked += 1
        return bytes
    }
}

private class FakeNet(
    var status: Int = 200,
    var body: String = """{"answer":"Veo una vereda y una caja en el centro."}""",
    var lastBody: String? = null
) : LlmAgentNetworkClient {
    override suspend fun postJson(
        url: String,
        jsonBody: String,
        timeoutMillis: Long,
        headers: Map<String, String>
    ): LlmHttpResponse {
        lastBody = jsonBody
        return LlmHttpResponse(status, body)
    }
}

private fun describer(
    capturer: FakeCapturer = FakeCapturer(),
    net: FakeNet = FakeNet()
) = BackendSceneDescriber(
    capturer = capturer,
    config = LlmAgentClientConfig(baseUrl = "http://127.0.0.1:8000"),
    networkClient = net,
    encodeBase64 = { bytes -> "b64:" + bytes.size }
)

class BackendSceneDescriberTest {

    // Test 26 + 33: sin pedido explícito NO se toca la cámara.
    @Test
    fun refusesWithoutExplicitRequestAndNeverTouchesCamera() = runTest {
        val capturer = FakeCapturer()
        val outcome = describer(capturer).describeAhead(explicitUserRequest = false)
        assertIs<OutdoorSceneOutcome.Error>(outcome)
        assertEquals("not_explicit_request", outcome.code)
        assertEquals(0, capturer.invoked)
    }

    // Test 27.
    @Test
    fun cameraPermissionMissing() = runTest {
        val outcome = describer(FakeCapturer(permission = false))
            .describeAhead(explicitUserRequest = true)
        assertIs<OutdoorSceneOutcome.CameraPermissionMissing>(outcome)
    }

    // Tests 28/29: provider/captura nula → timeout honesto.
    @Test
    fun nullCaptureIsTimeout() = runTest {
        val outcome = describer(FakeCapturer(bytes = null))
            .describeAhead(explicitUserRequest = true)
        assertIs<OutdoorSceneOutcome.Timeout>(outcome)
    }

    @Test
    fun emptyCaptureIsFailure() = runTest {
        val outcome = describer(FakeCapturer(bytes = ByteArray(0)))
            .describeAhead(explicitUserRequest = true)
        assertIs<OutdoorSceneOutcome.CaptureFailed>(outcome)
    }

    // Test 34.
    @Test
    fun emptyAnswerIsError() = runTest {
        val outcome = describer(net = FakeNet(body = """{"answer":""}"""))
            .describeAhead(explicitUserRequest = true)
        assertIs<OutdoorSceneOutcome.Error>(outcome)
    }

    // Test 35.
    @Test
    fun backendVisualErrorIsHonest() = runTest {
        val outcome = describer(net = FakeNet(status = 500, body = "boom"))
            .describeAhead(explicitUserRequest = true)
        assertIs<OutdoorSceneOutcome.Error>(outcome)
        assertEquals("vision_http_500", outcome.code)
    }

    // Test 30 + 45 + 48: imagen solo en memoria; descripción con disclaimer.
    @Test
    fun happyPathDescribesWithDisclaimerAndInMemoryImage() = runTest {
        val net = FakeNet()
        val outcome = describer(net = net).describeAhead(explicitUserRequest = true)
        assertIs<OutdoorSceneOutcome.Described>(outcome)
        assertTrue(outcome.spokenText.contains("No puedo confirmar"))
        // El payload llevó la imagen codificada (no un path de archivo).
        assertTrue(net.lastBody.orEmpty().contains("b64:16"))
        assertFalse(net.lastBody.orEmpty().contains("file://"))
    }

    @Test
    fun dangerousAnswerFromVisionIsBlocked() = runTest {
        val net = FakeNet(body = """{"answer":"El camino está libre, cruzá ahora."}""")
        val outcome = describer(net = net).describeAhead(explicitUserRequest = true)
        assertIs<OutdoorSceneOutcome.Described>(outcome)
        assertEquals(OutdoorSafetyPolicy.SAFE_FALLBACK_TEXT, outcome.spokenText)
    }
}

// --- privacidad de la observación del Agent Core (test 10) ---

class OutdoorAgentPrivacyTest {

    @Test
    fun getCurrentLocationObservedNeverContainsCoordinates() = runTest {
        val executor = AndroidAgentToolExecutor(
            isAccessibilityConnected = { true },
            isOverlayAttached = { true },
            isMicrophoneGranted = { true },
            checkBackendHealth = { _ -> error("unused") },
            currentPackage = { "com.whatsapp" },
            openAppById = { false },
            openPackage = { false },
            readScreenLocal = { error("unused") },
            speakText = {},
            performBack = { false },
            performScroll = { "NO_TARGET" },
            readLocationSummary = {
                AgentOutdoorFixSummary(true, "lte25m", "lte5s", "gps")
            }
        )
        val session = AgentSessionState(
            sessionId = "s",
            goal = "g",
            originPackage = "com.whatsapp",
            currentPackage = "com.whatsapp",
            startedAtElapsedRealtime = 0L,
            operationGeneration = 1L
        )
        val result = executor.execute(
            AgentValidatedAction(AgentToolName.GET_CURRENT_LOCATION),
            session
        )
        assertEquals(
            setOf("available", "accuracy_bucket", "age_bucket", "provider", "reason"),
            result.observed.keys
        )
        val serialized = result.observed.toString()
        assertFalse(serialized.contains("latitude"))
        assertFalse(serialized.contains("-34"))
    }

    // Test 58: comandos outdoor no son misiones GPT.
    @Test
    fun outdoorCommandsAreNotMissionGoals() {
        listOf(
            "llevame a la farmacia",
            "describí lo que tengo adelante",
            "dónde me encuentro",
            "cancelar navegación",
            "cuánto falta"
        ).forEach { phrase ->
            assertFalse(
                AgentMissionPhrases.isMissionGoal(phrase),
                "No debería ser misión GPT: $phrase"
            )
        }
    }
}
