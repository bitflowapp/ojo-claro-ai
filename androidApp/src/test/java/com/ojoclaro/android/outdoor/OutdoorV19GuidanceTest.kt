package com.ojoclaro.android.outdoor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * V1.9 — GPS más útil y menos frágil:
 *  - "cuánto falta" tiene respuesta propia (con y sin ruta);
 *  - "recalculá" existe, avisa y JAMÁS corre en loop automático;
 *  - "dónde estoy" nunca termina en seco: ofrece ruta o dice cómo retomarla;
 *  - precisión mala no alimenta el odómetro de navegación;
 *  - copy honesto y humano para ubicación imprecisa.
 */

private fun v19Fix(accuracy: Float? = 12f) =
    OutdoorLocationFix(-34.6, -58.38, accuracy, 700L, "gps")

private class V19FakeEngine(var result: OutdoorLocationFix? = v19Fix()) : OutdoorLocationEngine {
    override fun hasPermission(): Boolean = true
    override fun servicesEnabled(): Boolean = true
    override suspend fun freshFix(timeoutMillis: Long): OutdoorLocationFix? = result
}

private class V19FakeRouteProvider(
    var outcome: OutdoorRouteOutcome = OutdoorRouteOutcome.Unconfigured,
    var label: String? = null,
    var routeCalls: Int = 0
) : OutdoorRouteProvider {
    override suspend fun walkingRoute(
        originLatitude: Double,
        originLongitude: Double,
        destination: String
    ): OutdoorRouteOutcome {
        routeCalls += 1
        return outcome
    }

    override suspend fun reverseLabel(latitude: Double, longitude: Double): String? = label
}

private class V19FakeDescriber : OutdoorSceneDescriber {
    override suspend fun describeAhead(explicitUserRequest: Boolean): OutdoorSceneOutcome =
        OutdoorSceneOutcome.Described("Veo una vereda.")
}

private fun v19Route() = OutdoorRouteData(
    destinationName = "la plaza",
    totalDistanceMeters = 300,
    totalDurationSeconds = 240,
    steps = listOf(
        OutdoorRouteStep("caminá derecho", 200),
        OutdoorRouteStep("girá a la derecha", 100)
    )
)

private fun v19Coordinator(
    provider: V19FakeRouteProvider = V19FakeRouteProvider()
): Triple<OutdoorNavigationCoordinator, MutableList<String>, MutableList<String>> {
    val spoken = mutableListOf<String>()
    val logs = mutableListOf<String>()
    val coordinator = OutdoorNavigationCoordinator(
        locationReader = OutdoorLocationReader(V19FakeEngine()),
        routeProvider = provider,
        sceneDescriber = V19FakeDescriber(),
        speak = { spoken += it },
        log = { logs += it }
    )
    return Triple(coordinator, spoken, logs)
}

class OutdoorV19GuidanceTest {

    // --- "cuánto falta" ---

    @Test
    fun howFarWithoutRouteIsHonestAndSuggestsStartingOne() = runTest {
        val (coord, spoken, _) = v19Coordinator()
        coord.howFar()
        assertTrue(spoken.single().contains("No tengo una ruta activa"))
        assertTrue(spoken.single().contains("llevame a"))
    }

    @Test
    fun howFarWithRouteSpeaksRemainingAndNextInstruction() = runTest {
        val provider = V19FakeRouteProvider(OutdoorRouteOutcome.Route(v19Route()))
        val (coord, spoken, _) = v19Coordinator(provider)
        assertTrue(coord.startGuidance("la plaza"))
        spoken.clear()
        coord.howFar()
        assertTrue(spoken.single().contains("Faltan aproximadamente"))
        assertTrue(spoken.single().contains("La próxima indicación es"))
    }

    // --- recalcular ---

    @Test
    fun recalculateWithoutRouteIsHonest() = runTest {
        val (coord, spoken, _) = v19Coordinator()
        assertFalse(coord.recalculate())
        assertTrue(spoken.single().contains("No hay una ruta activa para recalcular"))
    }

    @Test
    fun recalculateAnnouncesAndFetchesRouteAgainOnlyOnDemand() = runTest {
        val provider = V19FakeRouteProvider(OutdoorRouteOutcome.Route(v19Route()))
        val (coord, spoken, _) = v19Coordinator(provider)
        assertTrue(coord.startGuidance("la plaza"))
        assertEquals(1, provider.routeCalls)

        spoken.clear()
        assertTrue(coord.recalculate())
        // Avisa ANTES de recalcular y vuelve a anunciar la ruta nueva.
        assertTrue(spoken.first().contains("Recalculando la ruta"))
        assertTrue(spoken.any { it.contains("Empiezo a orientarte") })
        // Exactamente UNA llamada extra: recalcular es a pedido, nunca loop.
        assertEquals(2, provider.routeCalls)
    }

    @Test
    fun offRouteAnnouncementOffersRecalculationButNeverRecalculatesAlone() = runTest {
        val provider = V19FakeRouteProvider(OutdoorRouteOutcome.Route(v19Route()))
        val (coord, _, _) = v19Coordinator(provider)
        assertTrue(coord.startGuidance("la plaza"))
        val callsAfterStart = provider.routeCalls

        // Caminar de más dispara el aviso de desvío (dos fixes de confirmación),
        // pero la ruta NO se recalcula sola.
        var lat = -34.6
        repeat(40) {
            lat += 10.0 / 111_111.0
            coord.onLocationUpdate(OutdoorLocationFix(lat, -58.0, 10f, 500L, "gps"))
        }
        assertEquals(callsAfterStart, provider.routeCalls)
    }

    // --- "dónde estoy" nunca termina en seco ---

    @Test
    fun whereAmIWithoutRouteOffersStartingOne() = runTest {
        val (coord, spoken, _) = v19Coordinator()
        coord.whereAmI()
        assertTrue(spoken.single().contains("te ayudo a iniciar una ruta"))
    }

    @Test
    fun whereAmIWithLabelSpeaksStreetAndPrecision() = runTest {
        val provider = V19FakeRouteProvider(label = "San Martín 500, Centro")
        val (coord, spoken, _) = v19Coordinator(provider)
        coord.whereAmI()
        assertTrue(spoken.single().contains("Estás cerca de San Martín 500"))
        assertTrue(spoken.single().contains("precisión es de unos 12 metros"))
    }

    @Test
    fun whereAmIDuringNavigationSaysRouteIsStillActive() = runTest {
        val provider = V19FakeRouteProvider(OutdoorRouteOutcome.Route(v19Route()))
        val (coord, spoken, _) = v19Coordinator(provider)
        assertTrue(coord.startGuidance("la plaza"))
        spoken.clear()
        coord.whereAmI()
        assertTrue(spoken.single().contains("sigue activa"))
        assertTrue(spoken.single().contains("recalculá"))
        assertEquals(OutdoorState.NAVIGATING, coord.state)
    }

    // --- precisión mala durante navegación ---

    @Test
    fun inaccurateFixesDoNotFeedTheNavigationOdometer() = runTest {
        val provider = V19FakeRouteProvider(OutdoorRouteOutcome.Route(v19Route()))
        val (coord, _, logs) = v19Coordinator(provider)
        assertTrue(coord.startGuidance("la plaza"))
        val before = coord.currentProgress()!!.remainingDistanceMeters

        // Fix con 150 m de error: rechazado, sin avance ni evento hablado.
        val finished = coord.onLocationUpdate(
            OutdoorLocationFix(-34.59, -58.37, 150f, 500L, "gps")
        )
        assertFalse(finished)
        assertEquals(before, coord.currentProgress()!!.remainingDistanceMeters)
        assertTrue(logs.any { it.contains("fix_rejected_inaccurate") })
    }

    // --- copy humano de ubicación imprecisa ---

    @Test
    fun inaccurateLocationCopyIsHumanAndActionable() = runTest {
        val reader = OutdoorLocationReader(V19FakeEngine(v19Fix(accuracy = 180f)))
        val result = reader.read()
        assertIs<OutdoorFixResult.TooInaccurate>(result)
        val spoken = reader.spokenLocationText(result)
        assertTrue(spoken.contains("imprecisa"))
        assertTrue(spoken.contains("ventana") || spoken.contains("zona más abierta"))
        // Sin jerga técnica hablada.
        assertFalse(spoken.contains("accuracy"))
        assertFalse(spoken.contains("bucket"))
        assertFalse(spoken.contains("fix"))
    }

    // --- frases ---

    @Test
    fun recalculatePhrasesParseLocally() {
        listOf(
            "recalculá", "recalcula la ruta", "recalcular ruta", "buscá otra ruta"
        ).forEach { phrase ->
            assertIs<OutdoorPhrases.Command.Recalculate>(
                OutdoorPhrases.parse(phrase),
                "debería recalcular: \"$phrase\""
            )
        }
    }

    @Test
    fun feelingLostIsConversationNotGps() {
        // "estoy perdido" = GPS local; "me siento perdido" = contención.
        assertIs<OutdoorPhrases.Command.WhereAmI>(OutdoorPhrases.parse("estoy perdido"))
        assertNull(OutdoorPhrases.parse("me siento perdido"))
    }

    @Test
    fun userCancelWithoutRouteIsNeverSilent() = runTest {
        val (coord, spoken, _) = v19Coordinator()
        coord.cancelGuidance("user_stop")
        assertTrue(spoken.single().contains("No había una ruta activa"))

        // Los cierres internos (destroy del service) siguen siendo mudos.
        val (quietCoord, quietSpoken, _) = v19Coordinator()
        quietCoord.cancelGuidance("service_destroy")
        assertTrue(quietSpoken.isEmpty())
    }

    @Test
    fun safetyQueriesStillNeverAuthorizeCrossing() {
        assertIs<OutdoorPhrases.Command.SafetyQuery>(OutdoorPhrases.parse("¿puedo cruzar?"))
        val reply = OutdoorSafetyPolicy.COMPLEMENTARY_GUIDANCE_TEXT
        assertTrue(reply.contains("No puedo confirmar"))
        assertEquals(
            OutdoorSafetyPolicy.MessageClass.CAUTION,
            OutdoorSafetyPolicy.classify(reply)
        )
    }
}
