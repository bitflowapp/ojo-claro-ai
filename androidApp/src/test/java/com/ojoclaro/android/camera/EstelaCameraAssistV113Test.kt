package com.ojoclaro.android.camera

import com.ojoclaro.android.agent.messaging.MessagingTaskRouter
import com.ojoclaro.android.camera.CameraAssistPhrases.Command
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * V1.13 — Camera Assist:
 *  - router de cámara: solo frases con marca de cámara/entorno (o cortas
 *    con la cámara YA activa); jamás roba lectura de PANTALLA, GPS,
 *    WhatsApp ni Instagram;
 *  - sesión pura: one-shot con deadline, watch con estabilidad de dos
 *    frames, dedup de lecturas, gap anti-pisado de TTS y presupuesto duro;
 *  - privacidad: el texto leído jamás va a logs (solo longitudes).
 */
class EstelaCameraAssistV113Test {

    // --- A. Router de comandos de cámara ---

    @Test
    fun cameraPhrasesRouteToTheRightCommands() {
        assertEquals(Command.CAMERA_START, parse("activá la cámara"))
        assertEquals(Command.CAMERA_START, parse("abrí la cámara de estela"))
        assertEquals(Command.CAMERA_START, parse("modo cámara"))
        assertEquals(Command.CAMERA_START, parse("modo visión"))

        assertEquals(Command.CAMERA_STOP, parse("cerrá la cámara"))
        assertEquals(Command.CAMERA_STOP, parse("apagá la cámara"))
        assertEquals(Command.CAMERA_STOP, parse("detené la cámara"))
        assertEquals(Command.CAMERA_PAUSE, parse("pausá la cámara"))
        assertEquals(Command.CAMERA_PAUSE, parse("pausá visión"))

        assertEquals(Command.TEXT_SCAN, parse("leé el texto que tengo enfrente"))
        assertEquals(Command.TEXT_SCAN, parse("qué dice este cartel"))
        assertEquals(Command.TEXT_SCAN, parse("qué dice esta hoja"))
        assertEquals(Command.TEXT_SCAN, parse("qué dice la pantalla que estoy apuntando"))
        assertEquals(Command.TEXT_SCAN, parse("capturá texto"))
        assertEquals(Command.TEXT_SCAN, parse("escaneá texto"))

        assertEquals(Command.SCENE_DESCRIBE, parse("describime qué estoy apuntando"))
        assertEquals(Command.SCENE_DESCRIBE, parse("qué estoy enfocando"))
        assertEquals(Command.SCENE_DESCRIBE, parse("mirá con la cámara"))

        assertEquals(Command.WATCH_TEXT, parse("avisame si aparece texto"))
        assertEquals(Command.WATCH_TEXT, parse("leé cuando detectes texto"))
        assertEquals(Command.WATCH_TEXT, parse("quedate mirando si aparece un cartel"))
    }

    @Test
    fun shortPhrasesOnlyWorkWithCameraActive() {
        // Sin cámara: ambiguas → null (siguen su ruta normal).
        // "leé el texto" subió a OCR siempre (BUG 2): ya no es ambigua.
        assertNull(parse("qué ves"))
        assertNull(parse("describime"))
        assertNull(parse("pará"))
        // Con cámara activa: van a cámara.
        assertEquals(Command.TEXT_SCAN, parseActive("leé el texto"))
        assertEquals(Command.SCENE_DESCRIBE, parseActive("qué ves"))
        assertEquals(Command.SCENE_DESCRIBE, parseActive("describí la escena"))
        assertEquals(Command.CAMERA_STOP, parseActive("pará"))
        assertEquals(Command.CAMERA_STOP, parseActive("cancelar"))
    }

    // --- B. No robar rutas existentes ---

    @Test
    fun screenReadingGpsAndMessagingPhrasesNeverRouteToCamera() {
        listOf(
            // Lectura de PANTALLA (V1.10.3/V1.10.4): intocable.
            "leé la pantalla",
            "qué aparece",
            "qué puedo tocar",
            "explicame esta pantalla",
            "leé los mensajes",
            "leé los chats",
            // GPS / outdoor: intocable ("describí" pelado es de Outdoor).
            "dónde estoy",
            "describí",
            "describime qué hay enfrente",
            "llevame a la farmacia",
            // Mensajería: intocable.
            "mandale a Sofia por instagram que llego tarde",
            "videollamada con Marco",
            "enviá",
            "mandalo"
        ).forEach { phrase ->
            assertNull(parse(phrase), "\"$phrase\" jamás es comando de cámara sin marca")
        }
        // Y las frases de cámara no se clasifican como mensajería.
        assertNull(MessagingTaskRouter.classify("leé el texto que tengo enfrente"))
        assertNull(MessagingTaskRouter.classify("activá la cámara"))
    }

    // --- C. Sesión: one-shot, watch, dedup, gap y presupuesto ---

    @Test
    fun oneShotSpeaksFirstTextAndTimesOutHonestly() {
        var now = 0L
        val session = CameraAssistSession(nowMillis = { now })
        session.onCameraReady()

        session.beginTextScan(deadlineMillis = 4_000L)
        assertEquals(CameraAssistSession.State.TEXT_SCAN_ONESHOT, session.state)

        val decision = session.onOcrText("FARMACIA ABIERTA")
        val speak = assertIs<CameraAssistSession.OcrDecision.Speak>(decision)
        assertEquals("FARMACIA ABIERTA", speak.text)
        assertFalse(speak.fromWatch)
        assertEquals(CameraAssistSession.State.CAMERA_READY, session.state)

        // Segundo escaneo sin texto: vence honesto.
        session.beginTextScan(deadlineMillis = 4_000L)
        now += 4_001L
        assertEquals(
            CameraAssistSession.DeadlineEvent.ScanTimedOutWithoutText,
            session.checkDeadlines()
        )
        assertEquals(CameraAssistSession.State.CAMERA_READY, session.state)
    }

    @Test
    fun watchRequiresTwoStableFramesAndNeverRepeats() {
        var now = 0L
        val session = CameraAssistSession(nowMillis = { now })
        session.onCameraReady()
        session.beginWatch(budgetMillis = 120_000L)

        // Primer frame: candidato, no se habla todavía.
        assertEquals(CameraAssistSession.OcrDecision.Ignore, session.onOcrText("SALIDA"))
        now += 1_000L
        // Segundo frame igual: estable → se habla.
        val spoken = assertIs<CameraAssistSession.OcrDecision.Speak>(session.onOcrText("SALIDA"))
        assertTrue(spoken.fromWatch)

        // El mismo texto poco después: deduplicado.
        now += 3_000L
        assertEquals(CameraAssistSession.OcrDecision.Ignore, session.onOcrText("SALIDA"))
        now += 1_000L
        assertEquals(CameraAssistSession.OcrDecision.Ignore, session.onOcrText("SALIDA"))

        // Texto NUEVO estable, pero respetando el gap mínimo de habla.
        now += 1_000L
        assertEquals(CameraAssistSession.OcrDecision.Ignore, session.onOcrText("ENTRADA"))
        now += 3_000L
        assertIs<CameraAssistSession.OcrDecision.Speak>(session.onOcrText("ENTRADA"))
    }

    @Test
    fun watchGapPreventsTtsFromTalkingOverItself() {
        var now = 0L
        val session = CameraAssistSession(nowMillis = { now })
        session.onCameraReady()
        session.beginWatch()

        session.onOcrText("UNO")
        now += 1_000L
        assertIs<CameraAssistSession.OcrDecision.Speak>(session.onOcrText("UNO"))

        // Otro texto estable pero ANTES del gap mínimo: se espera.
        now += 500L
        assertEquals(CameraAssistSession.OcrDecision.Ignore, session.onOcrText("DOS"))
        now += 500L
        assertEquals(CameraAssistSession.OcrDecision.Ignore, session.onOcrText("DOS"))
        // Pasado el gap, el mismo candidato estable se habla.
        now += CameraAssistSession.MIN_SPEAK_GAP_MILLIS
        assertIs<CameraAssistSession.OcrDecision.Speak>(session.onOcrText("DOS"))
    }

    @Test
    fun watchBudgetExpiresAndStopClearsState() {
        var now = 0L
        val session = CameraAssistSession(nowMillis = { now })
        session.onCameraReady()
        session.beginWatch(budgetMillis = 120_000L)

        now += 120_001L
        assertEquals(
            CameraAssistSession.DeadlineEvent.WatchBudgetExhausted,
            session.checkDeadlines()
        )
        assertEquals(CameraAssistSession.State.CAMERA_READY, session.state)

        // stop/cierre limpian todo.
        session.beginWatch()
        session.stopWatch()
        assertEquals(CameraAssistSession.State.CAMERA_READY, session.state)
        session.onClosed()
        assertEquals(CameraAssistSession.State.IDLE, session.state)
        assertFalse(session.isActive)
    }

    @Test
    fun sensitiveTextIsFlaggedSoTheCallerNeverReadsItAloud() {
        var now = 0L
        val session = CameraAssistSession(
            nowMillis = { now },
            sensitivePredicate = { it.contains("4111") }
        )
        session.onCameraReady()
        session.beginTextScan()
        val speak = assertIs<CameraAssistSession.OcrDecision.Speak>(
            session.onOcrText("TARJETA 4111 1111 1111 1111")
        )
        assertTrue(speak.sensitive)
    }

    // --- D. GAS: routing, limpieza y privacidad de logs ---

    @Test
    fun cameraRunsAfterTasksBeforeOutdoorAndCleansUpOnStops() {
        val service = File(
            "src/main/java/com/ojoclaro/android/global/GlobalAssistantService.kt"
        ).readText()

        val taskIdx = service.indexOf("if (handleTaskAssistCommand(text)) return")
        val cameraIdx = service.indexOf("if (handleCameraAssistCommand(text)) return")
        val outdoorIdx = service.indexOf("if (handleOutdoorCommand(text)) return")
        assertTrue(
            cameraIdx in (taskIdx + 1) until outdoorIdx,
            "cámara corre tras tareas y antes de outdoor (GPS intacto)"
        )

        // La cámara se cierra en los stops y en el cierre de turno.
        assertTrue(
            Regex("stopCameraAssist\\(spoken = false\\)").findAll(service).count() >= 3,
            "stops y cierre de turno cierran la cámara"
        )

        // Watch exige modo asistente (igual que el monitoreo de viaje).
        val section = service
            .substringAfter("V1.13: Camera Assist")
            .substringBefore("V1.10.3: lectura de pantalla")
        assertTrue(section.contains("accessibilityOverlayVoiceSingleShot"))

        // Privacidad: los logs de cámara llevan longitudes, jamás contenido.
        assertTrue(section.contains("cameraTextScan textLen="))
        assertTrue(section.contains("source=camera_frame"))
        assertFalse(
            section.contains("text=\${decision.text}") ||
                section.contains("text=\${detected}"),
            "el texto leído jamás se loguea"
        )
        // El copy sensible existe y no repite contenido.
        assertTrue(section.contains("no la voy a"))
    }

    @Test
    fun controllerNeverPersistsFramesAndManifestDeclaresOnDemandCamera() {
        val controller = File(
            "src/main/java/com/ojoclaro/android/camera/CameraAssistController.kt"
        ).readText()
        // Nada de archivos: la captura es en memoria y se descarta.
        assertFalse(controller.contains("OutputFileOptions"), "jamás persistir frames")
        assertFalse(controller.contains("FileOutputStream"))
        assertTrue(controller.contains("STRATEGY_KEEP_ONLY_LATEST"))
        assertTrue(controller.contains("ToneGenerator"), "cámara jamás silenciosa")

        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:foregroundServiceType=\"microphone|camera\""))
    }

    // --- BUG 2 (MEDIUM): "leé el texto" abre OCR aunque la cámara esté off ---

    @Test
    fun readTextWorksWithCameraInactiveAndNeverStealsScreenReading() {
        // OCR de cámara aun con cámara inactiva.
        assertEquals(Command.TEXT_SCAN, parse("leé el texto"))
        assertEquals(Command.TEXT_SCAN, parse("leer texto"))
        assertEquals(Command.TEXT_SCAN, parse("qué dice este cartel"))
        assertEquals(Command.TEXT_SCAN, parse("qué dice esta hoja"))
        assertEquals(Command.TEXT_SCAN, parse("leé lo que tengo enfrente"))
        // Lectura de PANTALLA jamás cae a cámara (su ruta es screen reading).
        assertNull(parse("leé la pantalla"))
        assertNull(parse("qué aparece"))
        assertNull(parse("qué puedo tocar"))
        assertNull(parseActive("leé la pantalla"))
    }

    // --- BUG 3 (LOW): aliases de cerrar cámara ---

    @Test
    fun allCloseAliasesMapToCameraStopWithoutBreakingStartOrScan() {
        listOf(
            "cerrar cámara", "cerrar la cámara", "cerrá cámara", "cerrá la cámara",
            "apagar cámara", "apagá cámara", "apagar la cámara", "apagá la cámara",
            "detener cámara", "detené cámara"
        ).forEach { phrase ->
            assertEquals(
                Command.CAMERA_STOP,
                parse(phrase),
                "alias de cierre debe ser CAMERA_STOP: \"$phrase\""
            )
        }
        // No se rompió arrancar ni escanear.
        assertEquals(Command.CAMERA_START, parse("activá la cámara"))
        assertEquals(Command.CAMERA_START, parse("modo cámara"))
        assertEquals(Command.TEXT_SCAN, parse("leé el texto"))
    }

    private fun parse(phrase: String): Command? =
        CameraAssistPhrases.parse(phrase, cameraActive = false)

    private fun parseActive(phrase: String): Command? =
        CameraAssistPhrases.parse(phrase, cameraActive = true)
}
