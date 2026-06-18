package com.ojoclaro.android.outdoor

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Contratos de integración por inspección de fuente (mismo patrón que
 * AccessibilityOverlayContractTest): garantizan reglas de la misión que no
 * dependen de runtime Android.
 */
class OutdoorIntegrationContractTest {

    private fun source(path: String): String =
        File("src/main/java/com/ojoclaro/android/$path").readText()

    // Tests 31/32/33: la imagen nunca toca disco, el buffer se libera y no
    // hay análisis continuo en el camino de descripción.
    @Test
    fun sceneCaptureIsMemoryOnlySingleShot() {
        val scene = source("outdoor/OutdoorScene.kt")
        assertFalse(scene.contains("java.io.File"))
        assertFalse(scene.contains("FileOutputStream"))
        assertFalse(scene.contains("MediaStore"))
        assertFalse(scene.contains("openFileOutput"))
        assertTrue(scene.contains("jpeg = null"), "el buffer debe liberarse tras serializar")
        // El camino de DESCRIPCIÓN es captura única: nada de ImageAnalysis ahí.
        // (El prototipo experimental apagado puede mencionarlo solo en docs.)
        val describerSection = scene.substringBefore("Prototipo EXPERIMENTAL")
        assertFalse(
            describerSection.contains("ImageAnalysis"),
            "describe es captura única, no análisis continuo"
        )

        val service = source("outdoor/OutdoorForegroundService.kt")
        assertFalse(service.contains("ImageAnalysis"))
        assertTrue(service.contains("unbindAll()"), "la cámara debe cerrarse al terminar")
        assertTrue(service.contains("ToneGenerator"), "aviso sonoro antes de capturar")
    }

    // Test 60: navegación sin MainActivity.
    @Test
    fun outdoorModuleNeverReferencesMainActivity() {
        val dir = File("src/main/java/com/ojoclaro/android/outdoor")
        dir.listFiles().orEmpty().filter { it.extension == "kt" }.forEach { file ->
            assertFalse(
                file.readText().contains("MainActivity"),
                "${file.name} no debe depender de MainActivity"
            )
        }
    }

    // Test 63: estrategia de servicio elegida conscientemente + limpieza.
    @Test
    fun outdoorServiceLifecycleContract() {
        val service = source("outdoor/OutdoorForegroundService.kt")
        assertTrue(service.contains("START_NOT_STICKY"))
        assertTrue(service.contains("stopForeground(STOP_FOREGROUND_REMOVE)"))
        // Anti-carrera (bug físico real en Moto G15): el apagado diferido por
        // TTS debe abortarse si llegó un request nuevo mientras esperaba.
        assertTrue(service.contains("stopAborted=concurrent_work"))
        // Contrato Android 12+ (crash físico real): TODO onStartCommand debe
        // llamar startForeground ANTES de despachar la acción, incluso STOP.
        val onStart = service.substringAfter("override fun onStartCommand")
        val foregroundIdx = onStart.indexOf("startForegroundWithNotification(")
        val dispatchIdx = onStart.indexOf("when (intent?.action)")
        assertTrue(
            foregroundIdx in 0 until dispatchIdx,
            "startForeground debe preceder al dispatch de acciones"
        )
        assertTrue(service.contains("removeUpdates"), "los updates de ubicación deben limpiarse")
        assertTrue(service.contains("speechController.shutdown()"))
        // Android 14: tipo camera SOLO al describir.
        assertTrue(service.contains("FOREGROUND_SERVICE_TYPE_CAMERA"))
        assertTrue(
            service.contains("includeCamera = intent?.action == ACTION_DESCRIBE"),
            "el tipo camera debe activarse exclusivamente para ACTION_DESCRIBE"
        )
    }

    // Tests 57/58/62: orden del fast path outdoor en el routing de voz.
    @Test
    fun globalAssistantRoutesOutdoorBeforeGptWithV12LocationSemantics() {
        val service = source("global/GlobalAssistantService.kt")
        val outdoorHook = service.indexOf("handleOutdoorCommand(text)")
        val missionHook = service.indexOf("AgentMissionPhrases.isMissionGoal(text)")
        assertTrue(outdoorHook in 1 until missionHook, "outdoor fast path debe evaluarse antes que GPT")
        // V1.2: "dónde estoy" es SIEMPRE ubicación GPS; el guard legacy de
        // lectura de pantalla quedó eliminado a pedido del piloto rider.
        assertFalse(service.contains("SCREEN_WHERE_AM_I_LEGACY"))
        // "repetí" a secas (sin navegación activa) se difiere al handler de
        // comandos básicos conversacionales, con matcher robusto a acentos.
        assertTrue(service.contains("VoiceCommandDispatcher.isRepeatCommand(text)"))
        assertTrue(service.contains("handleBasicConversationCommand(text)"))
        // El turno single-shot se cierra sin duplicar TTS.
        assertTrue(service.contains("completeOverlayVoiceTurn(\"outdoor_dispatch\")"))
    }

    // Test 46: todo TTS del coordinator pasa por la política de seguridad.
    @Test
    fun coordinatorSpeaksOnlyThroughSafetyPolicy() {
        val coordinator = source("outdoor/OutdoorNavigationCoordinator.kt")
        assertTrue(coordinator.contains("OutdoorSafetyPolicy.sanitizeForSpeech"))
        // El helper say() es el único camino: speak( solo aparece en su definición.
        val rawSpeaks = Regex("speak\\(").findAll(coordinator).count()
        assertTrue(rawSpeaks <= 2, "los textos deben salir por say() sanitizado")
    }

    // Ruta peatonal (test 11, lado Android): el provider parsea el contrato.
    @Test
    fun routeProviderParsesBackendContract() {
        val provider = BackendOutdoorRouteProvider(
            config = com.ojoclaro.android.llm.LlmAgentClientConfig(baseUrl = "http://x"),
            networkClient = object : com.ojoclaro.android.llm.LlmAgentNetworkClient {
                override suspend fun postJson(
                    url: String,
                    jsonBody: String,
                    timeoutMillis: Long,
                    headers: Map<String, String>
                ) = com.ojoclaro.android.llm.LlmHttpResponse(200, "")
            }
        )

        val ok = provider.parseRoute(
            """
            {"ok":true,"configured":true,"status":"ROUTE","destination_name":"la plaza",
             "total_distance_meters":350,"total_duration_seconds":280,
             "steps":[{"instruction":"caminá derecho","distance_meters":200},
                      {"instruction":"girá a la izquierda","distance_meters":150}],
             "error_code":null}
            """.trimIndent()
        )
        assertIs<OutdoorRouteOutcome.Route>(ok)
        assertTrue(ok.data.steps.size == 2)
        assertTrue(ok.data.destinationName == "la plaza")

        assertIs<OutdoorRouteOutcome.Unconfigured>(
            provider.parseRoute("""{"ok":false,"configured":false,"status":"UNCONFIGURED"}""")
        )
        assertIs<OutdoorRouteOutcome.NotFound>(
            provider.parseRoute("""{"ok":true,"configured":true,"status":"NOT_FOUND","steps":[]}""")
        )
        assertIs<OutdoorRouteOutcome.Error>(provider.parseRoute("{not json"))
        assertIs<OutdoorRouteOutcome.Error>(
            provider.parseRoute("""{"ok":false,"configured":true,"status":"ERROR","error_code":"route_upstream_500"}""")
        )
    }
}
