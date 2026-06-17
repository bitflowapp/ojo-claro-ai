package com.ojoclaro.android.agent.intelligence

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Tests del reasoner de Uber contra un fixture SANITIZADO derivado del dump REAL
 * de la pantalla ride_options del Moto (2026-06-13). Verifica los ajustes:
 *  - "Tu viaje fue de ARS ..." NO clasifica TRIP_ACTIVE,
 *  - "Solicita un viaje" se detecta como REQUEST_RIDE riesgoso,
 *  - precio se extrae como monto (ARS 1,234.00), no la frase,
 *  - tipo se extrae canónico (Uber Comfort), no el copy completo,
 *  - origen/destino quedan null (no hay etiqueta confiable).
 */
class UberRideOptionsFixtureTest {

    private fun loadFixture(path: String): List<ReasonerNode> {
        val xml = this::class.java.getResourceAsStream(path)
            ?.bufferedReader()?.use { it.readText() }
            ?: error("fixture no encontrado: $path")
        fun attr(attrs: String, name: String): String? =
            Regex("(?:^|\\s)" + Regex.escape(name) + "=\"([^\"]*)\"")
                .find(attrs)?.groupValues?.get(1)
        return Regex("<node\\b([^>]*)>").findAll(xml).map { m ->
            val a = m.groupValues[1]
            val cls = attr(a, "class").orEmpty()
            ReasonerNode(
                text = attr(a, "text")?.takeIf { it.isNotBlank() },
                contentDescription = attr(a, "content-desc")?.takeIf { it.isNotBlank() },
                hint = null,
                className = cls.takeIf { it.isNotBlank() },
                isClickable = attr(a, "clickable") == "true",
                isEditable = cls.contains("EditText", ignoreCase = true),
                isPassword = attr(a, "password") == "true",
                isHeading = false,
            )
        }.toList()
    }

    private fun rideOptions(): UberScreenModel =
        UberScreenReasoner.reason(
            RawScreen("com.ubercab", loadFixture("/uber/ride_options_sanitized.xml"))
        )

    @Test fun fixtureParsesIntoNodes() {
        val nodes = loadFixture("/uber/ride_options_sanitized.xml")
        assertTrue(nodes.size >= 7, "esperaba >=7 nodos, hubo ${nodes.size}")
        assertTrue(nodes.any { it.isClickable }, "esperaba al menos un nodo clickeable")
    }

    @Test fun isUberAndNotTripActive() {
        val m = rideOptions()
        assertEquals(UberApp.UBER, m.activeApp)
        // El bug: "Tu viaje fue de ARS 1,234.00" NO debe dar TRIP_ACTIVE.
        assertNotEquals(UberScreenType.TRIP_ACTIVE, m.screenType)
    }

    @Test fun classifiesAsConfirmOrOptions() {
        val t = rideOptions().screenType
        assertTrue(
            t == UberScreenType.CONFIRM_RIDE || t == UberScreenType.RIDE_OPTIONS,
            "screenType inesperado: $t"
        )
    }

    @Test fun finalButtonIsRiskyRequest() {
        // "Solicita un viaje" (solicita, sin 'r') debe marcar REQUEST_RIDE.
        assertTrue(rideOptions().riskyControls.contains(UberRiskyControl.REQUEST_RIDE))
    }

    @Test fun priceExtractedAsAmountNotPhrase() {
        val price = rideOptions().priceText
        assertNotNull2(price)
        assertTrue(price!!.contains("1,234"), "precio: $price")
        // No debe arrastrar la frase "Tu viaje fue de".
        assertTrue(!price.contains("viaje", ignoreCase = true), "precio con frase: $price")
        assertTrue(price.contains("ARS", ignoreCase = true), "precio sin moneda: $price")
    }

    @Test fun rideTypeExtractedCanonical() {
        // Del copy "Llega a tu destino con Uber Comfort" → "Uber Comfort".
        assertEquals("Uber Comfort", rideOptions().rideTypeText)
    }

    @Test fun originAndDestinationStayNullWhenUnlabeled() {
        val m = rideOptions()
        assertNull(m.pickupText)
        assertNull(m.destinationText)
    }

    @Test fun readyToRequestStaysClosed() {
        // Aun en CONFIRM_RIDE con precio/tipo, sin origen/destino → cerrada.
        assertTrue(!UberScreenReasoner.readyToRequest(rideOptions()))
    }

    // ---- confirm_ride: pantalla de confirmación con DOS direcciones visibles ----

    private fun confirmRide(): UberScreenModel =
        UberScreenReasoner.reason(
            RawScreen("com.ubercab", loadFixture("/uber/confirm_ride_sanitized.xml"))
        )

    @Test fun confirmRideClassifiesConfirmNotTripActive() {
        val m = confirmRide()
        assertEquals(UberApp.UBER, m.activeApp)
        assertNotEquals(UberScreenType.TRIP_ACTIVE, m.screenType)
        // "Ingresar otra cantidad" (botón de propina) NO debe dar LOGIN.
        assertNotEquals(UberScreenType.LOGIN, m.screenType)
        assertEquals(UberScreenType.CONFIRM_RIDE, m.screenType)
    }

    @Test fun confirmRideHasRequestRiskyTypeAndPrice() {
        val m = confirmRide()
        assertTrue(m.riskyControls.contains(UberRiskyControl.REQUEST_RIDE))
        assertEquals("Uber Comfort", m.rideTypeText)
        assertTrue(m.priceText?.contains("1,234") == true, "precio: ${m.priceText}")
    }

    @Test fun confirmRideDoesNotGuessUnlabeledAddresses() {
        // Hay DOS direcciones en pantalla, pero SIN etiqueta "Origen/Destino":
        // el reasoner NO debe adivinarlas por posición → null honesto.
        val m = confirmRide()
        assertNull(m.pickupText)
        assertNull(m.destinationText)
    }

    @Test fun confirmRideStaysClosedForRequest() {
        assertTrue(!UberScreenReasoner.readyToRequest(confirmRide()))
    }

    // kotlin.test.assertNotNull existe, pero evitamos el smart-cast helper duplicado.
    private fun assertNotNull2(v: Any?) = assertTrue(v != null, "esperaba no-null")
}
