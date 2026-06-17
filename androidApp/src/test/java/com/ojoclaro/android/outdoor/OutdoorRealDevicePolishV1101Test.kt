package com.ojoclaro.android.outdoor

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * V1.10.1 — Real Device Polish:
 *  - transporte (Uber/remís/taxi): respuesta fija honesta, jamás acción real;
 *  - rutas largas: sugerencia de transporte sin pedir nada;
 *  - respuesta a "¿a dónde querés ir?": destino, sí/no, negativas, y jamás
 *    secuestrar otros comandos;
 *  - apertura de WhatsApp confiable (contrato por inspección de fuente).
 */
class OutdoorRealDevicePolishV1101Test {

    // --- transporte ---

    @Test
    fun transportRequestsParseLocallyAndNeverReachTheLlm() {
        listOf(
            "pedime un uber",
            "llamá un taxi",
            "podés pedir un remís",
            "pedí un Uber a casa",
            "quiero un cabify"
        ).forEach { phrase ->
            assertIs<OutdoorPhrases.Command.TransportQuery>(
                OutdoorPhrases.parse(phrase),
                "debería ser transporte local: \"$phrase\""
            )
        }
        // Mencionar la parada de taxis como DESTINO no es pedir transporte.
        assertIs<OutdoorPhrases.Command.NavigateTo>(
            OutdoorPhrases.parse("llevame a la parada de taxis")
        )
    }

    @Test
    fun transportReplyOffersAssistedOpenAndNeverRequestsRide() {
        // V1.10.2 — cambio de spec: "pedime un Uber" ya no responde "pedilo
        // vos" en seco; ofrece ABRIR la app con confirmación. Los límites
        // duros siguen: jamás pedir, confirmar ni pagar un viaje.
        val service = File(
            "src/main/java/com/ojoclaro/android/global/GlobalAssistantService.kt"
        ).readText()
        assertFalse(
            service.contains("pedilo vos desde la app"),
            "la respuesta seca de V1.10.1 no debe existir más"
        )
        assertTrue(
            service.contains("no voy a pedir ni confirmar"),
            "el límite hablado (no pedir/confirmar viaje) debe existir"
        )
        assertTrue(
            service.contains("¿Querés que abra"),
            "abrir la app SIEMPRE pide confirmación"
        )
        // La oferta hablada no dispara el bloqueo de seguridad.
        assertFalse(
            OutdoorSafetyPolicy.classify(
                "Puedo ayudarte a abrir Uber, pero no voy a pedir ni confirmar el viaje por vos."
            ) == OutdoorSafetyPolicy.MessageClass.BLOCKED_SAFETY_CLAIM
        )
    }

    @Test
    fun longWalkAnnouncementSuggestsTransportWithoutRequestingIt() = runTest {
        val spoken = mutableListOf<String>()
        val longRoute = OutdoorRouteData(
            destinationName = "el centro",
            totalDistanceMeters = 2_500,
            totalDurationSeconds = 1_900,
            steps = listOf(OutdoorRouteStep("caminá derecho", 2_500))
        )
        val coordinator = OutdoorNavigationCoordinator(
            locationReader = OutdoorLocationReader(object : OutdoorLocationEngine {
                override fun hasPermission(): Boolean = true
                override fun servicesEnabled(): Boolean = true
                override suspend fun freshFix(timeoutMillis: Long): OutdoorLocationFix =
                    OutdoorLocationFix(-34.6, -58.38, 10f, 500L, "gps")
            }),
            routeProvider = object : OutdoorRouteProvider {
                override suspend fun walkingRoute(
                    originLatitude: Double,
                    originLongitude: Double,
                    destination: String
                ): OutdoorRouteOutcome = OutdoorRouteOutcome.Route(longRoute)
            },
            sceneDescriber = object : OutdoorSceneDescriber {
                override suspend fun describeAhead(explicitUserRequest: Boolean): OutdoorSceneOutcome =
                    OutdoorSceneOutcome.Described("Veo una vereda.")
            },
            speak = { spoken += it }
        )
        assertTrue(coordinator.startGuidance("el centro"))
        val announcement = spoken.last()
        assertTrue(announcement.contains("trayecto largo"))
        // V1.10.2 — sugiere abrir Uber/Maps SIN pedir nada real.
        assertTrue(announcement.contains("abrí uber"))
        assertTrue(announcement.contains("sin pedir el viaje"))
    }

    @Test
    fun shortWalkAnnouncementHasNoTransportNote() = runTest {
        val spoken = mutableListOf<String>()
        val shortRoute = OutdoorRouteData(
            destinationName = "la plaza",
            totalDistanceMeters = 300,
            totalDurationSeconds = 240,
            steps = listOf(OutdoorRouteStep("caminá derecho", 300))
        )
        val coordinator = OutdoorNavigationCoordinator(
            locationReader = OutdoorLocationReader(object : OutdoorLocationEngine {
                override fun hasPermission(): Boolean = true
                override fun servicesEnabled(): Boolean = true
                override suspend fun freshFix(timeoutMillis: Long): OutdoorLocationFix =
                    OutdoorLocationFix(-34.6, -58.38, 10f, 500L, "gps")
            }),
            routeProvider = object : OutdoorRouteProvider {
                override suspend fun walkingRoute(
                    originLatitude: Double,
                    originLongitude: Double,
                    destination: String
                ): OutdoorRouteOutcome = OutdoorRouteOutcome.Route(shortRoute)
            },
            sceneDescriber = object : OutdoorSceneDescriber {
                override suspend fun describeAhead(explicitUserRequest: Boolean): OutdoorSceneOutcome =
                    OutdoorSceneOutcome.Described("Veo una vereda.")
            },
            speak = { spoken += it }
        )
        assertTrue(coordinator.startGuidance("la plaza"))
        assertFalse(spoken.last().contains("trayecto largo"))
    }

    // --- respuesta de destino ---

    @Test
    fun destinationRepliesAreUnderstood() {
        assertEquals("san martin", OutdoorDestinationReply.extractDestination("a San Martín"))
        assertEquals("hospital", OutdoorDestinationReply.extractDestination("al hospital"))
        assertEquals("la plaza", OutdoorDestinationReply.extractDestination("la plaza"))
        assertEquals(
            "la farmacia",
            OutdoorDestinationReply.extractDestination("llevame a la farmacia")
        )
        assertEquals(
            "la casa de mi vieja",
            OutdoorDestinationReply.extractDestination("hasta la casa de mi vieja")
        )
    }

    @Test
    fun nonDestinationsNeverBecomeRoutes() {
        listOf(
            "leé la pantalla",
            "describí lo que tengo enfrente",
            "qué hora es",
            "mandale a Marco que llego",
            "cancelar",
            "no",
            "nada",
            "sí",
            "pedime un uber",
            "gracias"
        ).forEach { phrase ->
            assertNull(
                OutdoorDestinationReply.extractDestination(phrase),
                "JAMÁS es destino: \"$phrase\""
            )
        }
    }

    @Test
    fun vaguePlacesAskForClarificationInsteadOfInventingDestinations() {
        listOf("allá", "ahí", "por allá", "cerca", "a la derecha", "no sé").forEach { phrase ->
            assertTrue(
                OutdoorDestinationReply.isVaguePlace(phrase),
                "lugar vago: \"$phrase\""
            )
            assertNull(
                OutdoorDestinationReply.extractDestination(phrase),
                "jamás es destino geocodificable: \"$phrase\""
            )
        }
        // El servicio re-pregunta sin soltar el turno ni inventar.
        val service = File(
            "src/main/java/com/ojoclaro/android/global/GlobalAssistantService.kt"
        ).readText()
        assertTrue(service.contains("outcome=vague_place"))
        assertTrue(service.contains("Decime el nombre del lugar"))
    }

    @Test
    fun yesAndDeclineSetsBehaveAndNeverOverlap() {
        listOf("sí", "dale", "sí, dale", "claro", "ok").forEach {
            assertTrue(OutdoorDestinationReply.isYes(it), "debe confirmar ruta: \"$it\"")
        }
        listOf("no", "nada", "mejor no", "después", "cancelar").forEach {
            assertTrue(OutdoorDestinationReply.isDecline(it), "debe declinar: \"$it\"")
            assertFalse(OutdoorDestinationReply.isYes(it))
        }
    }

    // --- contratos de servicio (por inspección de fuente) ---

    @Test
    fun whereAmIConversationalKeepsTurnAndPendingsDieWithIt() {
        val service = File(
            "src/main/java/com/ojoclaro/android/global/GlobalAssistantService.kt"
        ).readText()
        // El flujo conversacional existe y pregunta a dónde ir.
        assertTrue(service.contains("¿A dónde querés ir?"))
        // La rama WhereAmI sin ruta vuelve ANTES del cierre de turno outdoor.
        val branchIdx = service.indexOf("handleWhereAmIConversational()")
        val dispatchCloseIdx = service.indexOf("completeOverlayVoiceTurn(\"outdoor_dispatch\")")
        assertTrue(branchIdx in 1 until dispatchCloseIdx)
        // El pending muere con stop/silencio/cierre + flujos propios.
        assertTrue(
            Regex("clearOutdoorDestinationAsk\\(\\)").findAll(service).count() >= 5,
            "el pending de destino debe limpiarse en stop/silencio/cierre/decline/confirm"
        )
        // La guía solo arranca tras la confirmación explícita del destino.
        val confirmedIdx = service.indexOf("destinationAsk outcome=confirmed")
        val startIdx = service.indexOf("OutdoorForegroundService.startGuidance(this, candidate)")
        assertTrue(confirmedIdx in 1 until startIdx)
    }

    @Test
    fun whatsAppOpensThroughAccessibilityResistantPath() {
        val helper = File(
            "src/main/java/com/ojoclaro/android/external/WhatsAppIntentHelper.kt"
        ).readText()
        assertTrue(
            helper.contains("launchIntentFromService"),
            "la apertura debe intentar primero el contexto de accesibilidad"
        )
        val service = File(
            "src/main/java/com/ojoclaro/android/global/GlobalAssistantService.kt"
        ).readText()
        assertTrue(
            service.contains("OjoClaroAccessibilityService.launchIntentFromService(Intent(chatIntent))"),
            "el borrador wa.me también usa el camino resistente"
        )
        val policy = File(
            "src/main/java/com/ojoclaro/android/domain/AgentExecutionPolicy.kt"
        ).readText()
        assertFalse(
            policy.contains("Para seguir, activa notificaciones"),
            "el bloqueo confuso por notificaciones no debe existir más"
        )
        assertTrue(policy.contains("EXECUTE_EXTERNAL_DEGRADED_RETURN"))
    }
}
