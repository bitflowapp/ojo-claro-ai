package com.ojoclaro.android.outdoor

import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * V1.10.2 — Mobility Assist:
 *  - direcciones tipo "San Martín al 500" se normalizan antes de geocodificar;
 *  - si la ruta falla, NUNCA termina en "no puedo calcular la ruta ahora":
 *    ofrece reintento / Google Maps / pedir mejor dirección, según la causa;
 *  - "pedime un Uber" ofrece abrir la app con confirmación, jamás pide viaje;
 *  - "abrí maps" abre solo con confirmación y sin navegación automática;
 *  - safety: ninguna copy nueva autoriza cruces ni viajes.
 */
class OutdoorMobilityAssistV1102Test {

    @BeforeTest
    fun resetHub() {
        OutdoorMobilityFallbackHub.clear()
    }

    // --- P1: normalización de direcciones ---

    @Test
    fun addressNormalizerCleansSpokenStreetHeights() {
        assertEquals(
            "san martin 500",
            OutdoorDestinationNormalizer.normalizeQuery("San Martín al 500")
        )
        assertEquals(
            "san martin 500",
            OutdoorDestinationNormalizer.normalizeQuery("la calle San Martín al 500")
        )
        assertEquals(
            "san martin 500",
            OutdoorDestinationNormalizer.normalizeQuery("calle san martin 500")
        )
        assertEquals(
            "san martin 500",
            OutdoorDestinationNormalizer.normalizeQuery("San Martín quinientos")
        )
        assertEquals(
            "san martin 500",
            OutdoorDestinationNormalizer.normalizeQuery("san martin al quinientos")
        )
        assertEquals(
            "san martin 555",
            OutdoorDestinationNormalizer.normalizeQuery("san martin quinientos cincuenta y cinco")
        )
        assertEquals(
            "san martin 500",
            OutdoorDestinationNormalizer.normalizeQuery("al 500 de san martín")
        )
        assertEquals(
            "mendoza",
            OutdoorDestinationNormalizer.normalizeQuery("la calle Mendoza")
        )
        assertEquals(
            "centro",
            OutdoorDestinationNormalizer.normalizeQuery("el centro")
        )
    }

    @Test
    fun addressNormalizerKeepsIntersectionsAndNamesIntact() {
        // Esquinas: el "y" se conserva tal cual (el geocoder local lo resuelve).
        assertEquals(
            "san martin y roca",
            OutdoorDestinationNormalizer.normalizeQuery("San Martín y Roca")
        )
        // Nombres con números chicos NO se convierten salvo altura explícita.
        assertEquals(
            "plaza las dos",
            OutdoorDestinationNormalizer.normalizeQuery("plaza las dos")
        )
        // Frases ya limpias pasan intactas.
        assertEquals(
            "hospital castro rendon",
            OutdoorDestinationNormalizer.normalizeQuery("hospital castro rendón")
        )
    }

    @Test
    fun homeReferencesAskForAddressInsteadOfGeocoding() {
        listOf("mi casa", "casa", "a casa", "mi domicilio").forEach { phrase ->
            assertTrue(
                OutdoorDestinationNormalizer.isHomeReference(phrase),
                "referencia a casa sin memoria: \"$phrase\""
            )
        }
        listOf("la casa de mi vieja", "casa de te", "san martin 500").forEach { phrase ->
            assertFalse(
                OutdoorDestinationNormalizer.isHomeReference(phrase),
                "JAMÁS es 'mi casa': \"$phrase\""
            )
        }
    }

    // --- P4: frases de transporte y maps ---

    @Test
    fun transportPhrasesCarryTheRequestedApp() {
        assertEquals(
            OutdoorPhrases.TransportApp.UBER,
            (OutdoorPhrases.parse("pedime un Uber") as OutdoorPhrases.Command.TransportQuery).app
        )
        assertEquals(
            OutdoorPhrases.TransportApp.UBER,
            (OutdoorPhrases.parse("necesito un uber") as OutdoorPhrases.Command.TransportQuery).app
        )
        assertEquals(
            OutdoorPhrases.TransportApp.CABIFY,
            (OutdoorPhrases.parse("abrí Cabify") as OutdoorPhrases.Command.TransportQuery).app
        )
        assertEquals(
            OutdoorPhrases.TransportApp.TAXI_REMIS,
            (OutdoorPhrases.parse("pedime un taxi") as OutdoorPhrases.Command.TransportQuery).app
        )
        assertEquals(
            OutdoorPhrases.TransportApp.TAXI_REMIS,
            (OutdoorPhrases.parse("pedime un remís") as OutdoorPhrases.Command.TransportQuery).app
        )
    }

    @Test
    fun openMapsPhrasesParseWithOptionalDestination() {
        assertIs<OutdoorPhrases.Command.OpenMaps>(OutdoorPhrases.parse("abrí maps"))
        assertIs<OutdoorPhrases.Command.OpenMaps>(OutdoorPhrases.parse("abrí google maps"))
        assertIs<OutdoorPhrases.Command.OpenMaps>(OutdoorPhrases.parse("usá maps"))
        val withDestination =
            OutdoorPhrases.parse("abrí maps con San Martín al 500")
        assertIs<OutdoorPhrases.Command.OpenMaps>(withDestination)
        assertEquals("san martin al 500", withDestination.destination)
        assertNull(
            (OutdoorPhrases.parse("abrí maps") as OutdoorPhrases.Command.OpenMaps).destination
        )
    }

    @Test
    fun passiveMapMentionsNeverHijack() {
        // Sin verbo de abrir/usar, "mapa" no dispara nada.
        assertFalse(OutdoorPhrases.parse("qué aparece en el mapa") is OutdoorPhrases.Command.OpenMaps)
        // Comandos de otros dominios siguen intactos.
        assertNull(OutdoorPhrases.parse("mandale a Marco que llego"))
        assertIs<OutdoorPhrases.Command.NavigateTo>(
            OutdoorPhrases.parse("llevame a la parada de taxis")
        )
        // Seguridad: la pregunta de cruce sigue siendo SafetyQuery.
        assertIs<OutdoorPhrases.Command.SafetyQuery>(OutdoorPhrases.parse("¿Es seguro cruzar?"))
    }

    // --- P2: degradaciones del coordinator ---

    private class FixedEngine(private val accuracy: Float) : OutdoorLocationEngine {
        override fun hasPermission(): Boolean = true
        override fun servicesEnabled(): Boolean = true
        override suspend fun freshFix(timeoutMillis: Long): OutdoorLocationFix =
            OutdoorLocationFix(-38.95, -68.06, accuracy, 500L, "gps")
    }

    private class FixedProvider(
        private val outcome: OutdoorRouteOutcome
    ) : OutdoorRouteProvider {
        var lastDestination: String? = null
        var calls: Int = 0
        override suspend fun walkingRoute(
            originLatitude: Double,
            originLongitude: Double,
            destination: String
        ): OutdoorRouteOutcome {
            calls += 1
            lastDestination = destination
            return outcome
        }
    }

    private fun coordinator(
        provider: OutdoorRouteProvider,
        spoken: MutableList<String>,
        accuracy: Float = 8f
    ) = OutdoorNavigationCoordinator(
        locationReader = OutdoorLocationReader(FixedEngine(accuracy)),
        routeProvider = provider,
        sceneDescriber = object : OutdoorSceneDescriber {
            override suspend fun describeAhead(explicitUserRequest: Boolean): OutdoorSceneOutcome =
                OutdoorSceneOutcome.Described("Veo una vereda.")
        },
        speak = { spoken += it }
    )

    @Test
    fun routeErrorOffersRetryOrMapsInsteadOfDeadEnd() = runTest {
        val spoken = mutableListOf<String>()
        val coordinator = coordinator(FixedProvider(OutdoorRouteOutcome.Error("route_timeout")), spoken)
        assertFalse(coordinator.startGuidance("la farmacia"))
        val reply = spoken.last()
        assertFalse(reply.contains("No puedo calcular la ruta ahora"))
        assertTrue(reply.contains("Google Maps"))
        assertTrue(reply.contains("¿Querés que abra Maps?"))
        assertTrue(reply.contains("probá de nuevo"))
        val offer = OutdoorMobilityFallbackHub.peek()
        assertEquals(MobilityFallbackKind.ROUTE_RETRY_OR_MAPS, offer?.kind)
        assertEquals("la farmacia", offer?.destination)
    }

    @Test
    fun geocodeMissAsksForStreetAndHeight() = runTest {
        val spoken = mutableListOf<String>()
        val coordinator = coordinator(FixedProvider(OutdoorRouteOutcome.NotFound), spoken)
        assertFalse(coordinator.startGuidance("san martin al 500"))
        val reply = spoken.last()
        assertTrue(reply.contains("No encontré bien esa dirección"))
        assertTrue(reply.contains("calle y la altura"))
        assertEquals(
            MobilityFallbackKind.ADDRESS_RETRY,
            OutdoorMobilityFallbackHub.peek()?.kind
        )
    }

    @Test
    fun tooFarDestinationSuggestsMapsAndUberWithoutRequesting() = runTest {
        val spoken = mutableListOf<String>()
        val coordinator = coordinator(FixedProvider(OutdoorRouteOutcome.TooFar), spoken)
        assertFalse(coordinator.startGuidance("el aeropuerto"))
        val reply = spoken.last()
        assertTrue(reply.contains("lejos"))
        assertTrue(reply.contains("abrí uber"))
        assertTrue(reply.contains("¿Querés que abra Maps?"))
        assertTrue(reply.contains("sin pedir el viaje"))
        assertEquals(
            MobilityFallbackKind.ROUTE_TOO_FAR_MAPS,
            OutdoorMobilityFallbackHub.peek()?.kind
        )
        assertFalse(
            OutdoorSafetyPolicy.classify(reply) ==
                OutdoorSafetyPolicy.MessageClass.BLOCKED_SAFETY_CLAIM
        )
    }

    @Test
    fun impreciseGpsOffersTryAnywayAndHonorsConfirmation() = runTest {
        val spoken = mutableListOf<String>()
        val provider = FixedProvider(
            OutdoorRouteOutcome.Route(
                OutdoorRouteData(
                    destinationName = "la plaza",
                    totalDistanceMeters = 300,
                    totalDurationSeconds = 240,
                    steps = listOf(OutdoorRouteStep("caminá derecho", 300))
                )
            )
        )
        val coordinator = coordinator(provider, spoken, accuracy = 90f)

        // Sin confirmación: oferta honesta, NINGUNA ruta pedida.
        assertFalse(coordinator.startGuidance("la plaza"))
        assertEquals(0, provider.calls)
        val offerReply = spoken.last()
        assertTrue(offerReply.contains("imprecisa"))
        assertTrue(offerReply.contains("intentá igual"))
        assertEquals(
            MobilityFallbackKind.ROUTE_RETRY_IMPRECISE,
            OutdoorMobilityFallbackHub.peek()?.kind
        )

        // Con confirmación explícita: avisa que es aproximada y arranca.
        assertTrue(coordinator.startGuidance("la plaza", allowImprecise = true))
        assertEquals(1, provider.calls)
        assertTrue(spoken.any { it.contains("ubicación aproximada") })
    }

    @Test
    fun homeWithoutMemoryAsksForRealAddressAndNeverGeocodes() = runTest {
        val spoken = mutableListOf<String>()
        val provider = FixedProvider(OutdoorRouteOutcome.NotFound)
        val coordinator = coordinator(provider, spoken)
        assertFalse(coordinator.startGuidance("mi casa"))
        assertEquals(0, provider.calls, "'mi casa' sin memoria JAMÁS se geocodifica")
        assertTrue(spoken.last().contains("no tengo guardada la dirección de tu casa"))
        assertEquals(
            MobilityFallbackKind.ADDRESS_RETRY,
            OutdoorMobilityFallbackHub.peek()?.kind
        )
    }

    @Test
    fun coordinatorSendsNormalizedQueryButSpeaksHumanPhrase() = runTest {
        val spoken = mutableListOf<String>()
        val provider = FixedProvider(OutdoorRouteOutcome.NotFound)
        val coordinator = coordinator(provider, spoken)
        coordinator.startGuidance("la calle San Martín al 500")
        assertEquals("san martin 500", provider.lastDestination)
        assertTrue(
            spoken.first().contains("Buscando una ruta a pie hacia la calle San Martín al 500")
        )
    }

    // --- hub: vencimiento y consumo ---

    @Test
    fun fallbackHubExpiresAndConsumesOnce() {
        OutdoorMobilityFallbackHub.post(
            MobilityFallbackKind.ROUTE_RETRY_OR_MAPS, "la plaza", nowMillis = 0L
        )
        assertEquals(
            "la plaza",
            OutdoorMobilityFallbackHub.peek(nowMillis = 170_000L)?.destination
        )
        assertEquals(
            MobilityFallbackKind.ROUTE_RETRY_OR_MAPS,
            OutdoorMobilityFallbackHub.consume(nowMillis = 170_000L)?.kind
        )
        assertNull(OutdoorMobilityFallbackHub.peek(nowMillis = 170_000L), "consume limpia")

        OutdoorMobilityFallbackHub.post(
            MobilityFallbackKind.ADDRESS_RETRY, null, nowMillis = 0L
        )
        assertNull(
            OutdoorMobilityFallbackHub.peek(nowMillis = 200_000L),
            "una oferta vieja jamás abre nada"
        )
    }

    // --- contratos de servicio (por inspección de fuente) ---

    @Test
    fun mobilityOpenIsConfirmedSafeAndNeverAutoNavigates() {
        val service = File(
            "src/main/java/com/ojoclaro/android/global/GlobalAssistantService.kt"
        ).readText()
        // Maps SOLO en modo búsqueda: la sección de movilidad asistida jamás
        // usa el esquema de navegación automática (el dispatcher legacy del
        // orchestrator queda fuera de este contrato y de este flujo).
        val mobilitySection = service
            .substringAfter("V1.10.2: movilidad asistida")
            .substringBefore("// --- V1.2")
        assertTrue(mobilitySection.contains("geo:0,0?q="))
        assertFalse(
            mobilitySection.contains("google.navigation"),
            "la movilidad asistida no inicia navegación automática"
        )
        // Uber: deep link de PRECARGA de destino, nunca de pedido.
        assertTrue(mobilitySection.contains("uber://?action=setPickup"))
        // El pending de apertura muere con stop/silencio/cierre de turno.
        assertTrue(
            Regex("pendingMobilityOpen = null").findAll(service).count() >= 4,
            "la oferta de abrir apps debe limpiarse en stop/silencio/cierre"
        )
        assertTrue(
            Regex("OutdoorMobilityFallbackHub\\.clear\\(\\)").findAll(service).count() >= 2,
            "los stops deben limpiar también las ofertas del coordinator"
        )
        // Orden de routing: WhatsApp pendiente > contacto > destino > movilidad.
        val sendIdx = service.indexOf("if (handlePendingWhatsAppSendReply(text)) return")
        val mobilityIdx = service.indexOf("if (handlePendingMobilityReply(text)) return")
        val affirmativeIdx = service.indexOf("isNonConfirmingAffirmative(text)")
        assertTrue(sendIdx in 1 until mobilityIdx, "el envío seguro SIEMPRE se evalúa antes")
        assertTrue(mobilityIdx in 1 until affirmativeIdx)
    }

    @Test
    fun rideAssistNeverContainsRideRequestActions() {
        val service = File(
            "src/main/java/com/ojoclaro/android/global/GlobalAssistantService.kt"
        ).readText()
        val sectionStart = service.indexOf("V1.10.2: movilidad asistida")
        assertTrue(sectionStart > 0, "la sección de movilidad asistida debe existir")
        val section = service.substring(sectionStart)
        // El flujo de movilidad jamás toca el send de WhatsApp ni clicks.
        assertFalse(section.substringBefore("// --- V1.2").contains("tapWhatsAppSend"))
        assertFalse(section.substringBefore("// --- V1.2").contains("performClick"))
        // Copys con los límites duros hablados.
        assertTrue(service.contains("no voy a pedir ni confirmar el viaje"))
        assertTrue(service.contains("no toco pagos"))
        // El acompañamiento ofrece lectura de pantalla, no acciones.
        assertTrue(service.contains("decime: leé la pantalla"))
    }

    @Test
    fun coordinatorDryDeadEndCopyIsGone() {
        val coordinatorSource = File(
            "src/main/java/com/ojoclaro/android/outdoor/OutdoorNavigationCoordinator.kt"
        ).readText()
        assertFalse(
            coordinatorSource.contains("No pude calcular la ruta ahora."),
            "la degradación seca de V1.9 no debe existir más"
        )
    }

    @Test
    fun newMobilityCopiesNeverAuthorizeCrossingOrTrips() {
        listOf(
            "Puedo abrir Google Maps con san martin 500. Solo lo abro: no toco nada más ahí.",
            "Listo, abrí Uber. Cargá vos el destino. Yo no voy a pedir ni confirmar el viaje, ni tocar pagos.",
            "Recordá: puedo orientarte, pero no puedo garantizar seguridad al cruzar; usá tu bastón o tu método habitual.",
            "Tu ubicación está imprecisa ahora. Puedo intentar igual con una ruta aproximada."
        ).forEach { copy ->
            assertFalse(
                OutdoorSafetyPolicy.classify(copy) ==
                    OutdoorSafetyPolicy.MessageClass.BLOCKED_SAFETY_CLAIM,
                "copy de movilidad bloqueada por seguridad: \"$copy\""
            )
        }
        // La política sigue bloqueando autorizaciones reales.
        assertEquals(
            OutdoorSafetyPolicy.MessageClass.BLOCKED_SAFETY_CLAIM,
            OutdoorSafetyPolicy.classify("Es seguro cruzar, cruzá ahora.")
        )
    }
}
