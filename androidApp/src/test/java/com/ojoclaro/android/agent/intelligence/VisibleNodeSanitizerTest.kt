package com.ojoclaro.android.agent.intelligence

import com.ojoclaro.android.accessibility.AccessibilityNodeSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Sanitización del volcado de nodos visibles. Lo CRÍTICO: jamás filtrar datos
 * sensibles (direcciones, teléfonos, tarjetas, montos) al log/dump.
 */
class VisibleNodeSanitizerTest {

    private fun n(
        text: String? = null,
        desc: String? = null,
        hint: String? = null,
        className: String? = "android.widget.TextView",
        clickable: Boolean = false,
        editable: Boolean = false,
        password: Boolean = false,
    ) = AccessibilityNodeSummary(
        text = text, contentDescription = desc, hint = hint, className = className,
        isClickable = clickable, isEditable = editable, isCheckable = false, isChecked = false,
        isPassword = password, isHeading = false, isEnabled = true
    )

    private fun rendered(vararg nodes: AccessibilityNodeSummary): String =
        VisibleNodeSanitizer.renderLines(VisibleNodeSanitizer.dump("com.ubercab", nodes.toList()))
            .joinToString("\n")

    // ---- privacidad: nada sensible sale ----

    @Test fun realAddressNeverAppears() {
        val out = rendered(n(text = "Av. Siempreviva 742, Springfield"))
        assertFalse(out.contains("Siempreviva", ignoreCase = true), out)
        assertFalse(out.contains("742"), out)
        assertTrue(out.contains("UNKNOWN_TEXT"))
    }

    @Test fun phoneNeverAppears() {
        // Número FICTICIO (no usar datos reales en fixtures de privacidad).
        val out = rendered(n(text = "+54 9 351 7654321"))
        assertFalse(out.contains("7654321"), out)
        assertFalse(out.contains("351"), out)
    }

    @Test fun cardNumberNeverAppears() {
        val out = rendered(n(text = "Visa 4111 1111 1111 1111"))
        assertFalse(out.contains("4111"), out)
        // Pero sí debe reconocerse como etiqueta de pago.
        assertTrue(out.contains("PAYMENT_LABEL"), out)
    }

    @Test fun priceRedactedToCategory() {
        val out = rendered(n(text = "Tu viaje fue de ARS 2,443.00"))
        assertFalse(out.contains("2,443"), out)
        assertFalse(out.contains("2.443"), out)
        assertTrue(out.contains("ARS_AMOUNT_REDACTED"), out)
    }

    @Test fun requestButtonLabeled() {
        // Botón puro de pedido → token de categoría REQUEST_RIDE_LABEL.
        assertTrue(rendered(n(text = "Solicita un viaje", clickable = true)).contains("REQUEST_RIDE_LABEL"))
        // El nodo combinado real (tipo + solicita) marca ambos; el token prioriza
        // el producto (seguro), pero el MARCADOR de request igual queda registrado.
        val combined = rendered(n(desc = "Llega a tu destino con Uber Comfort, Solicita un viaje", clickable = true))
        assertTrue(combined.contains("UBER_REQUEST_RIDE"), combined)
        assertTrue(combined.contains("Uber Comfort"), combined)
    }

    @Test fun rideTypesAreSafeTokens() {
        assertTrue(rendered(n(text = "Uber Comfort")).contains("Uber Comfort"))
        assertTrue(rendered(n(text = "Taxis a pedido")).contains("Taxi"))
        assertTrue(rendered(n(text = "UberX")).contains("UberX"))
    }

    @Test fun loginAndTripActiveLabeled() {
        assertTrue(rendered(n(text = "Iniciar sesión")).contains("LOGIN_LABEL"))
        assertTrue(rendered(n(text = "Tu conductor está llegando")).contains("TRIP_ACTIVE_LABEL"))
    }

    // ---- marcadores y conteos globales ----

    @Test fun markerCountsAreCorrect() {
        val dump = VisibleNodeSanitizer.dump(
            "com.ubercab",
            listOf(
                n(text = "Tu viaje fue de ARS 1,234.00"),
                n(text = "Uber Comfort"),
                n(text = "Taxis a pedido"),
                n(desc = "Solicita un viaje", clickable = true),
                n(text = "Av. Falsa 123"),
            )
        )
        assertEquals("UBER", dump.resolvedApp)
        assertEquals(5, dump.nodeCount)
        assertEquals(1, dump.priceMarkerCount)
        assertEquals(2, dump.rideTypeMarkerCount)   // Comfort + Taxi
        assertTrue(dump.requestRideMarkerCount >= 1)
        assertTrue(dump.hasRequestRideRisky)
    }

    @Test fun globalDumpHasNoSensitiveValues() {
        val out = rendered(
            n(text = "Casa de Marcos, Calle Larga 9999"),
            n(text = "ARS 7,777.00"),
            n(text = "Mastercard 5555 4444 3333 2222"),
        )
        for (s in listOf("Marcos", "Calle Larga", "9999", "7,777", "5555", "3333")) {
            assertFalse(out.contains(s), "filtró dato sensible: $s en\n$out")
        }
    }
}
