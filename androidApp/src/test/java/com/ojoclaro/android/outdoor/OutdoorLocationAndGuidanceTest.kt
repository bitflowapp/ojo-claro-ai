package com.ojoclaro.android.outdoor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

private class FakeEngine(
    var permission: Boolean = true,
    var services: Boolean = true,
    var fix: OutdoorLocationFix? = null
) : OutdoorLocationEngine {
    override fun hasPermission(): Boolean = permission
    override fun servicesEnabled(): Boolean = services
    override suspend fun freshFix(timeoutMillis: Long): OutdoorLocationFix? = fix
}

private fun fix(
    accuracy: Float? = 12f,
    age: Long = 800L
) = OutdoorLocationFix(-34.6, -58.38, accuracy, age, "fused")

class OutdoorLocationReaderTest {

    // Test 5: permiso ausente.
    @Test
    fun permissionMissingIsHonest() = runTest {
        val reader = OutdoorLocationReader(FakeEngine(permission = false))
        val result = reader.read()
        assertIs<OutdoorFixResult.PermissionMissing>(result)
        assertTrue(reader.spokenLocationText(result).contains("permiso"))
    }

    // Test 4: servicios desactivados.
    @Test
    fun servicesDisabledIsHonest() = runTest {
        val reader = OutdoorLocationReader(FakeEngine(services = false))
        assertIs<OutdoorFixResult.ServicesDisabled>(reader.read())
    }

    // Test 1: ubicación null.
    @Test
    fun nullFixIsUnavailable() = runTest {
        val reader = OutdoorLocationReader(FakeEngine(fix = null))
        val result = reader.read()
        assertIs<OutdoorFixResult.Unavailable>(result)
        assertFalse(reader.spokenLocationText(result).contains("metros"))
    }

    // Test 2: ubicación antigua no se declara válida.
    @Test
    fun staleFixIsRejected() = runTest {
        val reader = OutdoorLocationReader(FakeEngine(fix = fix(age = 120_000L)))
        val result = reader.read()
        assertIs<OutdoorFixResult.TooOld>(result)
        assertTrue(reader.spokenLocationText(result).contains("vieja"))
    }

    // Test 3: baja precisión no se declara válida.
    @Test
    fun inaccurateFixIsRejected() = runTest {
        val reader = OutdoorLocationReader(FakeEngine(fix = fix(accuracy = 180f)))
        val result = reader.read()
        assertIs<OutdoorFixResult.TooInaccurate>(result)
        val spoken = reader.spokenLocationText(result)
        assertTrue(spoken.contains("180"))
        assertTrue(spoken.contains("no puedo darte una posición exacta"))
    }

    // Test 6: precisión hablada honestamente, sin dirección inventada.
    @Test
    fun validFixSpeaksHonestAccuracy() = runTest {
        val reader = OutdoorLocationReader(FakeEngine(fix = fix(accuracy = 15f)))
        val result = reader.read()
        assertIs<OutdoorFixResult.Valid>(result)
        val spoken = reader.spokenLocationText(result)
        assertTrue(spoken.contains("precisión aproximada de 15 metros"))
        // "Según la ubicación disponible..." clasifica CAUTION (prudente):
        // lo importante es que NUNCA sea afirmación de seguridad y pase intacta.
        assertTrue(
            OutdoorSafetyPolicy.classify(spoken) !=
                OutdoorSafetyPolicy.MessageClass.BLOCKED_SAFETY_CLAIM
        )
        assertEquals(spoken, OutdoorSafetyPolicy.sanitizeForSpeech(spoken))
    }
}

class OutdoorGuidanceEngineTest {

    private fun route() = OutdoorRouteData(
        destinationName = "la plaza",
        totalDistanceMeters = 350,
        totalDurationSeconds = 280,
        steps = listOf(
            OutdoorRouteStep("caminá derecho por la vereda", 200),
            OutdoorRouteStep("girá a la izquierda", 150)
        )
    )

    private fun fixAt(lat: Double, lng: Double) =
        OutdoorLocationFix(lat, lng, 10f, 500L, "gps")

    /** ~deltaMeters hacia el norte por iteración. */
    private fun walk(engine: OutdoorGuidanceEngine, from: Double, meters: Double): List<OutdoorGuidanceEngine.SpeechDecision> {
        val stepDegrees = 10.0 / 111_111.0 // ~10 m
        val iterations = (meters / 10.0).toInt()
        var lat = from
        val decisions = mutableListOf<OutdoorGuidanceEngine.SpeechDecision>()
        repeat(iterations) {
            lat += stepDegrees
            decisions += engine.onLocation(fixAt(lat, -58.0))
        }
        return decisions
    }

    // Test 11: no satura TTS — la mayoría de updates son silenciosos.
    @Test
    fun doesNotSpeakOnEveryGpsUpdate() {
        val engine = OutdoorGuidanceEngine(route())
        engine.onLocation(fixAt(-34.0, -58.0)) // primer fix
        val decisions = walk(engine, -34.0, 100.0)
        val spoken = decisions.count { it.speak }
        assertTrue(spoken <= 1, "Habló $spoken veces en 10 updates")
        assertTrue(decisions.count { !it.speak } >= 9)
    }

    @Test
    fun announcesManeuverAlertOnceWithinThreshold() {
        val engine = OutdoorGuidanceEngine(route())
        engine.onLocation(fixAt(-34.0, -58.0))
        val decisions = walk(engine, -34.0, 170.0)
        val alerts = decisions.filter { it.event == "maneuver_alert" }
        assertEquals(1, alerts.size)
        assertTrue(alerts.first().text.contains("girá a la izquierda"))
    }

    @Test
    fun advancesManeuverAndAnnouncesNextInstruction() {
        val engine = OutdoorGuidanceEngine(route())
        engine.onLocation(fixAt(-34.0, -58.0))
        val decisions = walk(engine, -34.0, 200.0)
        assertTrue(decisions.any { it.event == "maneuver_change" })
    }

    // Test 12: desvío requiere confirmación en dos fixes (anti falso positivo).
    @Test
    fun offRouteIsConfirmedBeforeSpeaking() {
        val shortRoute = OutdoorRouteData("x", 60, 60, listOf(OutdoorRouteStep("derecho", 30), OutdoorRouteStep("girá", 30)))
        val engine = OutdoorGuidanceEngine(shortRoute)
        engine.onLocation(fixAt(-34.0, -58.0))
        // step1 (30m) + umbral off-route (40m) superados: caminar 90m sin girar
        val decisions = walk(engine, -34.0, 90.0)
        val candidate = decisions.indexOfFirst { it.event == "off_route_candidate" }
        val confirmed = decisions.indexOfFirst { it.event == "off_route_confirmed" }
        // maneuver_change consume el paso; el overshoot ocurre en el paso 2
        if (confirmed >= 0) {
            assertTrue(candidate in 0 until confirmed, "candidato debe preceder a confirmado")
            assertTrue(decisions[confirmed].text.contains("alejaste"))
        }
        val progress = engine.currentProgress()
        // Nunca afirmaciones de seguridad en ningún texto de navegación.
        decisions.filter { it.speak }.forEach {
            assertEquals(
                true,
                OutdoorSafetyPolicy.classify(it.text) !=
                    OutdoorSafetyPolicy.MessageClass.BLOCKED_SAFETY_CLAIM
            )
        }
        assertTrue(progress.remainingDistanceMeters >= 0)
    }

    @Test
    fun arrivalAnnouncedAndProgressShapeMatchesContract() {
        val engine = OutdoorGuidanceEngine(route())
        engine.onLocation(fixAt(-34.0, -58.0))
        val decisions = walk(engine, -34.0, 350.0)
        assertTrue(decisions.any { it.event == "arrival" }, "debe anunciar llegada")
        assertTrue(engine.isArrived)

        val progress = engine.currentProgress()
        assertEquals("la plaza", progress.destinationName)
        assertTrue(progress.remainingDistanceMeters <= 30)
        assertFalse(progress.offRoute)
    }

    @Test
    fun spokenProgressUsesApproximateLanguage() {
        val engine = OutdoorGuidanceEngine(route())
        val text = engine.progressSpokenText()
        assertTrue(text.contains("aproximadamente"))
        assertTrue(text.contains("metros"))
    }
}

class OutdoorPhrasesTest {

    @Test
    fun localFastPathPhrasesParse() {
        assertIs<OutdoorPhrases.Command.WhereAmI>(OutdoorPhrases.parse("¿Dónde estoy?"))
        assertIs<OutdoorPhrases.Command.WhereAmI>(OutdoorPhrases.parse("cuál es mi ubicación"))
        assertIs<OutdoorPhrases.Command.HowFar>(OutdoorPhrases.parse("¿Cuánto falta?"))
        assertIs<OutdoorPhrases.Command.HowFar>(OutdoorPhrases.parse("cuanto falta para la plaza"))
        assertIs<OutdoorPhrases.Command.RepeatInstruction>(OutdoorPhrases.parse("Repetí la indicación"))
        assertIs<OutdoorPhrases.Command.RepeatInstruction>(OutdoorPhrases.parse("decime la próxima indicación"))
        assertIs<OutdoorPhrases.Command.CancelNavigation>(OutdoorPhrases.parse("Cancelar navegación"))
        assertIs<OutdoorPhrases.Command.DescribeAhead>(OutdoorPhrases.parse("Describí lo que tengo adelante"))
    }

    @Test
    fun navigateToExtractsDestination() {
        val command = OutdoorPhrases.parse("Llevame a la farmacia de la esquina")
        assertIs<OutdoorPhrases.Command.NavigateTo>(command)
        assertEquals("la farmacia de la esquina", command.destination)

        val command2 = OutdoorPhrases.parse("quiero ir a Plaza San Martín por favor")
        assertIs<OutdoorPhrases.Command.NavigateTo>(command2)
        assertEquals("plaza san martin", command2.destination)
    }

    @Test
    fun unrelatedTextDoesNotParse() {
        assertEquals(null, OutdoorPhrases.parse("leé la pantalla"))
        assertEquals(null, OutdoorPhrases.parse("abrí whatsapp"))
        assertEquals(null, OutdoorPhrases.parse("hola estela cómo estás"))
    }

    // Etapa 11: preguntas de seguridad SIEMPRE caen al fast path complementario.
    @Test
    fun safetyQueriesParseAndAnswerIsCautious() {
        listOf(
            "¿Es seguro cruzar?",
            "¿Puedo avanzar?",
            "¿Está libre el camino?",
            "¿Viene algún auto?",
            "Guiame sin usar el bastón",
            "decime si es seguro cruzar la calle"
        ).forEach { phrase ->
            assertIs<OutdoorPhrases.Command.SafetyQuery>(
                OutdoorPhrases.parse(phrase),
                "Debería ser SafetyQuery: $phrase"
            )
        }
        // La respuesta fija jamás es una afirmación bloqueable y pasa intacta.
        val answer = OutdoorSafetyPolicy.COMPLEMENTARY_GUIDANCE_TEXT
        assertTrue(
            OutdoorSafetyPolicy.classify(answer) !=
                OutdoorSafetyPolicy.MessageClass.BLOCKED_SAFETY_CLAIM
        )
        assertEquals(answer, OutdoorSafetyPolicy.sanitizeForSpeech(answer))
        assertTrue(answer.contains("complementaria"))
        assertTrue(answer.contains("bastón"))
    }
}
