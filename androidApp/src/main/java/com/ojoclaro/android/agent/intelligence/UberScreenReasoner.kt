package com.ojoclaro.android.agent.intelligence

import java.text.Normalizer

/**
 * Mobility Copilot v1 — modelo de pantalla de Uber (app de PASAJERO `com.ubercab`).
 *
 * Componente PURO (sin Android): recibe nodos ya leídos (un adapter los llena
 * desde OjoClaroAccessibilityService.readVisibleNodeSummaries(); reusa
 * [ReasonerNode]). Clasifica la pantalla y extrae, best-effort, origen/destino/
 * precio/tipo/pago + controles riesgosos, con un nivel de confianza.
 *
 * Diseño: MARCADORES DE TEXTO tolerantes (ES + EN), NO resource-ids (Uber ofusca
 * y rota ids). Filosofía "copiloto con frenos": describe y avisa; NUNCA decide
 * pedir/confirmar un viaje (eso lo gobierna el router con confirmación fuerte y,
 * en v1, ni siquiera entonces toca el botón final).
 *
 * Seguridad/privacidad: el texto hablado puede incluir destino para que la
 * persona lo escuche, pero el caller NUNCA debe loguear pickup/destino/precio
 * (solo longitudes/flags). La app de CONDUCTOR `com.ubercab.driver` se trata como
 * OTHER: jamás se automatiza.
 */
enum class UberApp { UBER, OTHER }

enum class UberScreenType {
    LOGIN, HOME, DESTINATION_SEARCH, ROUTE_REVIEW, RIDE_OPTIONS,
    CONFIRM_RIDE, DRIVER_SEARCH, TRIP_ACTIVE, UNKNOWN
}

enum class UberRiskyControl {
    CONFIRM_RIDE, REQUEST_RIDE, ADD_PAYMENT, CHANGE_PAYMENT,
    CANCEL_TRIP, CALL_DRIVER, SHARE_LOCATION
}

data class UberScreenModel(
    val activeApp: UberApp,
    val screenType: UberScreenType,
    val pickupText: String?,
    val destinationText: String?,
    val priceText: String?,
    val rideTypeText: String?,
    val paymentText: String?,
    val riskyControls: List<UberRiskyControl>,
    val confidence: Confidence,
    val explanation: String,
)

object UberScreenReasoner {

    const val UBER_RIDER_PACKAGE = "com.ubercab"
    const val UBER_DRIVER_PACKAGE = "com.ubercab.driver"

    // --- marcadores de texto (ya normalizados: minúsculas, sin acentos) ---
    // Solo frases ESPECÍFICAS de login. Se quitaron "ingresar" y "verify"
    // (laxos): "Ingresar otra cantidad" (un botón de PROPINA del tip step)
    // disparaba LOGIN. Mismo patrón de bug que "tu viaje" → TRIP_ACTIVE.
    private val LOGIN_MARKERS = setOf(
        "iniciar sesion", "inicia sesion", "log in", "login", "sign in",
        "continuar con google", "continuar con apple", "crear cuenta", "registrate",
        "ingresa tu numero", "ingresa tu correo", "ingresa tu telefono",
        "ingresa tu contrasena", "codigo de verificacion",
        "verifica tu numero", "verifica tu correo", "enter your number"
    )
    private val DESTINATION_PROMPTS = setOf(
        "a donde vas", "adonde vas", "where to", "buscar destino", "ingresa tu destino",
        "destino", "buscar lugar", "search destination"
    )
    private val CONFIRM_MARKERS = setOf(
        "confirmar uber", "confirmar viaje", "confirmar", "confirm uber", "confirm ride",
        "confirm pickup", "confirm", "elegir uber", "elegir este"
    )
    // OJO: el botón real de Uber AR dice "Solicita un viaje" (solicita, sin 'r'),
    // no "solicitar". Sin "solicita" el botón final no se marcaba como riesgoso.
    private val REQUEST_MARKERS = setOf(
        "solicita un viaje", "solicita", "solicitar uber", "solicitar viaje", "solicitar",
        "pedir uber", "pedir viaje", "request uber", "request ride", "request"
    )
    // Marcador → nombre canónico del producto (orden: específico primero, p.ej.
    // "uberxl" antes que "uberx", "uber comfort" antes que "comfort").
    private val RIDE_TYPE_CANONICAL = listOf(
        "uber comfort" to "Uber Comfort",
        "comfort" to "Uber Comfort",
        "uberxl" to "UberXL",
        "uber xl" to "UberXL",
        "uberx" to "UberX",
        "uber x" to "UberX",
        "uber moto" to "Uber Moto",
        "moto" to "Uber Moto",
        "uber black" to "Uber Black",
        "black" to "Uber Black",
        "uber green" to "Uber Green",
        "uber flash" to "Uber Flash",
        "uber pet" to "Uber Pet",
        "uberpool" to "Uber Pool",
        "pool" to "Uber Pool",
        "taxis" to "Taxi",
        "taxi" to "Taxi",
    )
    private val PAYMENT_MARKERS = listOf(
        "efectivo", "cash", "visa", "mastercard", "amex", "tarjeta", "credito", "debito",
        "google pay", "mercado pago", "cuenta", "•••", "....", "saldo uber", "uber cash"
    )
    private val ADD_PAYMENT_MARKERS = setOf(
        "agregar metodo de pago", "agregar tarjeta", "añadir tarjeta", "add payment",
        "add card", "agregar pago", "vincular tarjeta"
    )
    private val CHANGE_PAYMENT_MARKERS = setOf(
        "cambiar metodo de pago", "metodo de pago", "cambiar pago", "change payment",
        "payment method", "formas de pago"
    )
    private val CANCEL_MARKERS = setOf(
        "cancelar viaje", "cancelar uber", "cancel trip", "cancel ride", "cancelar pedido"
    )
    private val CALL_DRIVER_MARKERS = setOf(
        "llamar al conductor", "llamar conductor", "contactar conductor", "call driver",
        "contact driver", "llamar"
    )
    private val SHARE_LOCATION_MARKERS = setOf(
        "compartir viaje", "compartir ubicacion", "share trip", "share status",
        "share location", "compartir estado"
    )
    private val DRIVER_SEARCH_MARKERS = setOf(
        "buscando tu viaje", "buscando conductor", "conectando con", "finding your driver",
        "finding driver", "buscando", "confirmando tu viaje", "solicitando"
    )
    // Solo marcadores FUERTES de viaje en curso. Se quitaron "tu viaje"/"your
    // trip" (laxos): el label de precio "Tu viaje fue de ARS 2.443" los disparaba
    // y clasificaba mal una pantalla de opciones como TRIP_ACTIVE.
    private val TRIP_ACTIVE_MARKERS = setOf(
        "tu conductor", "conductor asignado", "conductor en camino", "en camino",
        "llegando", "esta llegando", "viaje en curso", "on the way", "arriving",
        "driver is on the way"
    )

    fun reason(raw: RawScreen): UberScreenModel {
        val app = activeApp(raw.packageName)
        if (app == UberApp.OTHER) {
            return UberScreenModel(
                UberApp.OTHER, UberScreenType.UNKNOWN, null, null, null, null, null,
                emptyList(), Confidence.UNKNOWN,
                "No estoy en la app de pasajero de Uber."
            )
        }

        val labels = raw.nodes.map { normalize(it.label) }.filter { it.isNotBlank() }
        fun anyContains(markers: Iterable<String>) =
            labels.any { l -> markers.any { l.contains(it) } }
        fun firstRawMatching(markers: List<String>): String? =
            raw.nodes.firstOrNull { n ->
                val l = normalize(n.label); l.isNotBlank() && markers.any { l.contains(it) }
            }?.label?.trim()

        // --- controles riesgosos (cualquier botón visible cuenta) ---
        val risky = mutableListOf<UberRiskyControl>()
        if (anyContains(CONFIRM_MARKERS)) risky += UberRiskyControl.CONFIRM_RIDE
        if (anyContains(REQUEST_MARKERS)) risky += UberRiskyControl.REQUEST_RIDE
        if (anyContains(ADD_PAYMENT_MARKERS)) risky += UberRiskyControl.ADD_PAYMENT
        if (anyContains(CHANGE_PAYMENT_MARKERS)) risky += UberRiskyControl.CHANGE_PAYMENT
        if (anyContains(CANCEL_MARKERS)) risky += UberRiskyControl.CANCEL_TRIP
        if (anyContains(CALL_DRIVER_MARKERS)) risky += UberRiskyControl.CALL_DRIVER
        if (anyContains(SHARE_LOCATION_MARKERS)) risky += UberRiskyControl.SHARE_LOCATION

        // --- extracción best-effort (puede quedar null → baja confianza) ---
        // Precio: solo el monto (ARS/$), no la frase entera.
        val priceText = raw.nodes.asSequence()
            .mapNotNull { extractPrice(it.label) }
            .firstOrNull()
        // Tipo: nombre canónico del producto, preferido en el botón/CTA clickeable.
        val rideTypeText = extractRideType(raw)
        val paymentText = firstRawMatching(PAYMENT_MARKERS)
        val hasSearchField = raw.nodes.any { n ->
            n.isEditable && DESTINATION_PROMPTS.any { normalize(n.hint ?: n.label).contains(it) }
        }

        // --- tipo de pantalla (orden de prioridad) ---
        val screenType = when {
            anyContains(LOGIN_MARKERS) || raw.nodes.any { it.isPassword } -> UberScreenType.LOGIN
            anyContains(TRIP_ACTIVE_MARKERS) -> UberScreenType.TRIP_ACTIVE
            anyContains(DRIVER_SEARCH_MARKERS) -> UberScreenType.DRIVER_SEARCH
            anyContains(CONFIRM_MARKERS) || anyContains(REQUEST_MARKERS) -> UberScreenType.CONFIRM_RIDE
            rideTypeText != null && priceText != null -> UberScreenType.RIDE_OPTIONS
            hasSearchField -> UberScreenType.DESTINATION_SEARCH
            anyContains(DESTINATION_PROMPTS) -> UberScreenType.HOME
            else -> UberScreenType.UNKNOWN
        }

        val confidence = when {
            screenType == UberScreenType.UNKNOWN -> Confidence.LOW
            screenType == UberScreenType.LOGIN -> Confidence.LOW
            screenType == UberScreenType.CONFIRM_RIDE && priceText != null -> Confidence.HIGH
            screenType == UberScreenType.RIDE_OPTIONS && priceText != null -> Confidence.HIGH
            screenType == UberScreenType.HOME || screenType == UberScreenType.DESTINATION_SEARCH -> Confidence.MEDIUM
            else -> Confidence.MEDIUM
        }

        val explanation = buildExplanation(
            screenType, destinationText = null, priceText, rideTypeText, paymentText, risky
        )

        return UberScreenModel(
            activeApp = UberApp.UBER,
            screenType = screenType,
            pickupText = null,
            destinationText = null,
            priceText = priceText,
            rideTypeText = rideTypeText,
            paymentText = paymentText,
            riskyControls = risky.distinct(),
            confidence = confidence,
            explanation = explanation,
        )
    }

    /**
     * Compuerta PURA de "¿se puede pedir el viaje?". El router la consulta, pero en
     * v1 NUNCA toca el botón final aunque esto sea true. Exige: pantalla de
     * confirmación, sin login/desconocido, y origen + destino + precio + tipo +
     * pago todos leídos, sin ambigüedad.
     */
    fun readyToRequest(model: UberScreenModel): Boolean =
        model.activeApp == UberApp.UBER &&
            model.screenType == UberScreenType.CONFIRM_RIDE &&
            model.confidence == Confidence.HIGH &&
            !model.pickupText.isNullOrBlank() &&
            !model.destinationText.isNullOrBlank() &&
            !model.priceText.isNullOrBlank() &&
            !model.rideTypeText.isNullOrBlank() &&
            !model.paymentText.isNullOrBlank()

    private fun activeApp(pkg: String?): UberApp =
        if (pkg?.trim()?.lowercase() == UBER_RIDER_PACKAGE) UberApp.UBER else UberApp.OTHER

    // Monto con moneda: "ARS 2,443.00", "ARS 2.443", "$ 2.443", "$2,443.00", y
    // rangos "$1.200 - $1.600". Devuelve solo el monto, no la frase.
    private val PRICE_REGEX = Regex(
        "(?i)(?:ar\\$|us\\$|ars|\\$)\\s?\\d[\\d.,]*" +
            "(?:\\s?[-–—]\\s?(?:ar\\$|us\\$|ars|\\$)?\\s?\\d[\\d.,]*)?"
    )

    private fun extractPrice(label: String): String? =
        PRICE_REGEX.find(label)?.value
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.takeIf { it.any(Char::isDigit) }

    /**
     * Nombre canónico del producto (UberX/Uber Comfort/Taxi/…). Lo busca primero
     * en nodos clickeables (el botón/CTA = la opción elegida) y después en el
     * resto. Devuelve el canónico, no el copy completo ("Llega a tu destino con
     * Uber Comfort" → "Uber Comfort").
     */
    private fun extractRideType(raw: RawScreen): String? {
        val ordered = raw.nodes.sortedByDescending { it.isClickable }
        for (node in ordered) {
            val l = normalize(node.label)
            if (l.isBlank()) continue
            for ((marker, canonical) in RIDE_TYPE_CANONICAL) {
                if (l.contains(marker)) return canonical
            }
        }
        return null
    }

    private fun buildExplanation(
        type: UberScreenType,
        destinationText: String?,
        priceText: String?,
        rideTypeText: String?,
        paymentText: String?,
        risky: List<UberRiskyControl>,
    ): String {
        val base = when (type) {
            UberScreenType.LOGIN ->
                "Uber está pidiendo iniciar sesión. No puedo seguir hasta que ingreses vos."
            UberScreenType.HOME ->
                "Estás en la pantalla principal de Uber. Decime el destino para empezar."
            UberScreenType.DESTINATION_SEARCH ->
                "Estás buscando un destino en Uber."
            UberScreenType.ROUTE_REVIEW ->
                "Estás revisando la ruta en Uber."
            UberScreenType.RIDE_OPTIONS ->
                "Uber está mostrando opciones de viaje."
            UberScreenType.CONFIRM_RIDE ->
                "Estás en la pantalla de confirmación de Uber. No voy a pedir el viaje: " +
                    "revisá y confirmá vos."
            UberScreenType.DRIVER_SEARCH ->
                "Uber está buscando un conductor."
            UberScreenType.TRIP_ACTIVE ->
                "Hay un viaje de Uber en curso."
            UberScreenType.UNKNOWN ->
                "No pude reconocer la pantalla de Uber con seguridad."
        }
        val details = buildList {
            if (rideTypeText != null) add("opción $rideTypeText")
            if (priceText != null) add("precio $priceText")
            if (paymentText != null) add("pago $paymentText")
        }
        val detailNote = if (details.isNotEmpty()) " Muestra ${details.joinToString(", ")}." else ""
        val riskNote = if (risky.isNotEmpty()) " Hay controles sensibles a la vista." else ""
        return base + detailNote + riskNote
    }

    private fun normalize(text: String): String {
        val lower = text.lowercase()
        val stripped = Normalizer.normalize(lower, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        return stripped.replace(Regex("[¿?¡!.,;:]"), " ").replace(Regex("\\s+"), " ").trim()
    }
}
