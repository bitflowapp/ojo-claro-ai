package com.ojoclaro.android.agent.intelligence

import com.ojoclaro.android.accessibility.AccessibilityNodeSummary
import java.text.Normalizer

/**
 * Debug tool — vuelca, SANITIZADO, lo que realmente consume
 * `readVisibleNodeSummaries()` en runtime, para alinear los fixtures con la vista
 * REAL de Estela (que difiere de `uiautomator dump`).
 *
 * PRIVACIDAD (regla dura): NUNCA emite texto sensible. Por nodo solo expone
 * flags, longitudes, marcadores (categorías) y un `safeToken` que es una etiqueta
 * de categoría o un nombre de producto whitelisteado (Uber Comfort/Taxi/…), jamás
 * una dirección/teléfono/tarjeta/nombre. Es un componente PURO (testeable).
 *
 * Nota: `AccessibilityNodeSummary` NO trae bounds, focusable ni visibleToUser
 * (solo text/contentDescription/hint/className + flags), así que el dump no los
 * incluye — eso mismo es un dato útil de por qué Estela no usa posición/visibilidad.
 */
enum class NodeMarker {
    UBER_PRICE, UBER_RIDE_TYPE, UBER_REQUEST_RIDE, UBER_PAYMENT,
    UBER_PICKUP_HINT, UBER_DESTINATION_HINT, UBER_LOGIN, UBER_TRIP_ACTIVE,
    SYSTEM_UI, UNKNOWN
}

data class SanitizedNode(
    val index: Int,
    val classSimple: String,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val isCheckable: Boolean,
    val isChecked: Boolean,
    val isPassword: Boolean,
    val isHeading: Boolean,
    val isEnabled: Boolean,
    val hasText: Boolean,
    val hasContentDescription: Boolean,
    val textLen: Int,
    val contentDescriptionLen: Int,
    val markers: List<NodeMarker>,
    val safeToken: String,
)

data class VisibleNodeDump(
    val activePackage: String?,
    val resolvedApp: String,
    val nodeCount: Int,
    val clickableCount: Int,
    val priceMarkerCount: Int,
    val rideTypeMarkerCount: Int,
    val requestRideMarkerCount: Int,
    val paymentMarkerCount: Int,
    val pickupMarkerCount: Int,
    val destinationMarkerCount: Int,
    val topClasses: List<Pair<String, Int>>,
    val suspectedScreenType: String,
    val hasPrice: Boolean,
    val hasRideType: Boolean,
    val hasPickup: Boolean,
    val hasDestination: Boolean,
    val hasRequestRideRisky: Boolean,
    val hasPaymentRisky: Boolean,
    val nodes: List<SanitizedNode>,
)

object VisibleNodeSanitizer {

    private val PRICE_REGEX = Regex("(?i)(?:ar\\$|us\\$|ars|\\$)\\s?\\d[\\d.,]*")
    private val RIDE_TYPE = listOf(
        "uber comfort" to "Uber Comfort", "comfort" to "Uber Comfort",
        "uberxl" to "UberXL", "uber xl" to "UberXL",
        "uberx" to "UberX", "uber x" to "UberX",
        "uber moto" to "Uber Moto", "moto" to "Uber Moto",
        "uber black" to "Uber Black", "black" to "Uber Black",
        "uber green" to "Uber Green", "uber flash" to "Uber Flash",
        "taxis" to "Taxi", "taxi" to "Taxi", "pool" to "Uber Pool",
    )
    private val REQUEST = setOf(
        "solicita un viaje", "solicita", "solicitar", "confirmar", "confirm",
        "pedir uber", "pedir viaje", "request", "elegir uber",
    )
    private val PAYMENT = listOf(
        "efectivo", "cash", "visa", "mastercard", "amex", "tarjeta", "credito",
        "debito", "mercado pago", "google pay", "•••", "....",
    )
    private val PICKUP = setOf("origen", "punto de partida", "tu ubicacion", "pickup", "recoger", "desde")
    private val DESTINATION = setOf("destino", "a donde vas", "adonde vas", "where to", "hacia")
    private val LOGIN = setOf(
        "iniciar sesion", "inicia sesion", "log in", "login", "sign in",
        "ingresa tu numero", "ingresa tu correo", "ingresa tu contrasena",
        "codigo de verificacion", "crear cuenta",
    )
    private val TRIP_ACTIVE = setOf(
        "tu conductor", "conductor asignado", "en camino", "llegando", "viaje en curso",
    )

    fun dump(activePackage: String?, summaries: List<AccessibilityNodeSummary>): VisibleNodeDump {
        val nodes = summaries.mapIndexed { i, s -> sanitize(i, s) }

        // Interpretación del reasoner sobre LO QUE VE ESTELA (mismos nodos).
        val model = UberScreenReasoner.reason(
            RawScreen(
                packageName = activePackage,
                nodes = summaries.map { s ->
                    ReasonerNode(
                        text = s.text, contentDescription = s.contentDescription, hint = s.hint,
                        className = s.className, isClickable = s.isClickable, isEditable = s.isEditable,
                        isPassword = s.isPassword, isHeading = s.isHeading,
                    )
                }
            )
        )

        fun count(m: NodeMarker) = nodes.count { it.markers.contains(m) }
        val topClasses = nodes.groupingBy { it.classSimple }.eachCount()
            .entries.sortedByDescending { it.value }.take(8).map { it.key to it.value }

        return VisibleNodeDump(
            activePackage = activePackage,
            resolvedApp = model.activeApp.name,
            nodeCount = nodes.size,
            clickableCount = nodes.count { it.isClickable },
            priceMarkerCount = count(NodeMarker.UBER_PRICE),
            rideTypeMarkerCount = count(NodeMarker.UBER_RIDE_TYPE),
            requestRideMarkerCount = count(NodeMarker.UBER_REQUEST_RIDE),
            paymentMarkerCount = count(NodeMarker.UBER_PAYMENT),
            pickupMarkerCount = count(NodeMarker.UBER_PICKUP_HINT),
            destinationMarkerCount = count(NodeMarker.UBER_DESTINATION_HINT),
            topClasses = topClasses,
            suspectedScreenType = model.screenType.name,
            hasPrice = model.priceText != null,
            hasRideType = model.rideTypeText != null,
            hasPickup = model.pickupText != null,
            hasDestination = model.destinationText != null,
            hasRequestRideRisky = model.riskyControls.contains(UberRiskyControl.REQUEST_RIDE),
            hasPaymentRisky = model.riskyControls.any {
                it == UberRiskyControl.ADD_PAYMENT || it == UberRiskyControl.CHANGE_PAYMENT
            },
            nodes = nodes,
        )
    }

    /** Render delimitado, una línea por nodo + resumen. SIN datos sensibles. */
    fun renderLines(dump: VisibleNodeDump): List<String> {
        val out = mutableListOf<String>()
        out += "dump|activePkg=${dump.activePackage ?: "null"}|resolvedApp=${dump.resolvedApp}" +
            "|nodes=${dump.nodeCount}|clickable=${dump.clickableCount}" +
            "|price=${dump.priceMarkerCount}|rideType=${dump.rideTypeMarkerCount}" +
            "|requestRide=${dump.requestRideMarkerCount}|payment=${dump.paymentMarkerCount}" +
            "|pickup=${dump.pickupMarkerCount}|destination=${dump.destinationMarkerCount}" +
            "|screenType=${dump.suspectedScreenType}|hasPrice=${dump.hasPrice}" +
            "|hasRideType=${dump.hasRideType}|hasPickup=${dump.hasPickup}" +
            "|hasDest=${dump.hasDestination}|hasReqRisky=${dump.hasRequestRideRisky}" +
            "|hasPayRisky=${dump.hasPaymentRisky}"
        out += "dumpClasses|" + dump.topClasses.joinToString(",") { "${it.first}=${it.second}" }
        dump.nodes.forEach { n ->
            out += "node|i=${n.index}|cls=${n.classSimple}|clk=${b(n.isClickable)}|edit=${b(n.isEditable)}" +
                "|chk=${b(n.isCheckable)}|chkd=${b(n.isChecked)}|pwd=${b(n.isPassword)}|head=${b(n.isHeading)}" +
                "|en=${b(n.isEnabled)}|hasT=${b(n.hasText)}|hasD=${b(n.hasContentDescription)}" +
                "|tLen=${n.textLen}|dLen=${n.contentDescriptionLen}" +
                "|mk=${n.markers.joinToString("+") { it.name }}|tok=${n.safeToken}"
        }
        return out
    }

    private fun b(v: Boolean) = if (v) "1" else "0"

    private fun sanitize(index: Int, s: AccessibilityNodeSummary): SanitizedNode {
        val text = s.text.orEmpty()
        val desc = s.contentDescription.orEmpty()
        val hint = s.hint.orEmpty()
        val combined = normalize("$text $desc $hint")
        val markers = mutableListOf<NodeMarker>()
        if (PRICE_REGEX.containsMatchIn("$text $desc $hint")) markers += NodeMarker.UBER_PRICE
        if (RIDE_TYPE.any { combined.contains(it.first) }) markers += NodeMarker.UBER_RIDE_TYPE
        if (REQUEST.any { combined.contains(it) }) markers += NodeMarker.UBER_REQUEST_RIDE
        if (PAYMENT.any { combined.contains(it) }) markers += NodeMarker.UBER_PAYMENT
        if (PICKUP.any { combined.contains(it) }) markers += NodeMarker.UBER_PICKUP_HINT
        if (DESTINATION.any { combined.contains(it) }) markers += NodeMarker.UBER_DESTINATION_HINT
        if (LOGIN.any { combined.contains(it) }) markers += NodeMarker.UBER_LOGIN
        if (TRIP_ACTIVE.any { combined.contains(it) }) markers += NodeMarker.UBER_TRIP_ACTIVE
        if (s.className?.contains("keyguard", true) == true) markers += NodeMarker.SYSTEM_UI
        if (markers.isEmpty()) markers += NodeMarker.UNKNOWN

        return SanitizedNode(
            index = index,
            classSimple = simpleClass(s.className),
            isClickable = s.isClickable,
            isEditable = s.isEditable,
            isCheckable = s.isCheckable,
            isChecked = s.isChecked,
            isPassword = s.isPassword,
            isHeading = s.isHeading,
            isEnabled = s.isEnabled,
            hasText = text.isNotBlank(),
            hasContentDescription = desc.isNotBlank(),
            textLen = text.length,
            contentDescriptionLen = desc.length,
            markers = markers,
            safeToken = safeToken(combined, markers),
        )
    }

    /** Token de categoría o producto whitelisteado. JAMÁS texto crudo sensible. */
    private fun safeToken(combined: String, markers: List<NodeMarker>): String = when {
        NodeMarker.UBER_RIDE_TYPE in markers ->
            RIDE_TYPE.firstOrNull { combined.contains(it.first) }?.second ?: "RIDE_TYPE_LABEL"
        NodeMarker.UBER_REQUEST_RIDE in markers -> "REQUEST_RIDE_LABEL"
        NodeMarker.UBER_PAYMENT in markers -> "PAYMENT_LABEL"
        NodeMarker.UBER_PRICE in markers -> "ARS_AMOUNT_REDACTED"
        NodeMarker.UBER_PICKUP_HINT in markers -> "PICKUP_LABEL"
        NodeMarker.UBER_DESTINATION_HINT in markers -> "DESTINATION_LABEL"
        NodeMarker.UBER_LOGIN in markers -> "LOGIN_LABEL"
        NodeMarker.UBER_TRIP_ACTIVE in markers -> "TRIP_ACTIVE_LABEL"
        NodeMarker.SYSTEM_UI in markers -> "SYSTEM_UI_LABEL"
        else -> "UNKNOWN_TEXT"
    }

    private fun simpleClass(className: String?): String =
        className?.substringAfterLast('.')?.takeIf { it.isNotBlank() } ?: "?"

    private fun normalize(text: String): String {
        val lower = text.lowercase()
        val stripped = Normalizer.normalize(lower, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        return stripped.replace(Regex("[¿?¡!.,;:]"), " ").replace(Regex("\\s+"), " ").trim()
    }
}
