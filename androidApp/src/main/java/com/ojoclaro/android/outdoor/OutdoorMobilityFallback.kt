package com.ojoclaro.android.outdoor

/**
 * V1.10.2 — oferta de fallback de movilidad pendiente de respuesta.
 *
 * La pregunta la hace el [OutdoorNavigationCoordinator] (vive en el
 * OutdoorForegroundService, sin micrófono); la respuesta llega en un turno
 * NUEVO del GlobalAssistantService. Este hub es el puente: una sola oferta
 * viva, con vencimiento corto, que el GAS consume SOLO ante una respuesta
 * explícita (sí/no/reintentar/dirección). Cualquier otra frase sigue su
 * ruta normal: el hub jamás secuestra comandos.
 *
 * Seguridad: las ofertas solo reintentan rutas o abren apps con confirmación.
 * Nunca envían nada, nunca piden viajes, nunca pagan.
 */
enum class MobilityFallbackKind {
    /** El servicio de rutas falló: ofrecer reintento o abrir Google Maps. */
    ROUTE_RETRY_OR_MAPS,

    /** Destino demasiado lejos para ruta a pie: ofrecer Maps (o que pida "abrí uber"). */
    ROUTE_TOO_FAR_MAPS,

    /** GPS impreciso: ofrecer intentar igual con ruta aproximada. */
    ROUTE_RETRY_IMPRECISE,

    /** Geocode sin resultado: la próxima frase puede ser calle y altura. */
    ADDRESS_RETRY
}

data class OutdoorMobilityFallback(
    val kind: MobilityFallbackKind,
    /** Destino HABLADO (jamás coordenadas, jamás se loguea el contenido). */
    val destination: String?,
    val postedAtMillis: Long
)

object OutdoorMobilityFallbackHub {

    /** Vencimiento corto: un "sí" suelto minutos después no abre nada. */
    const val TTL_MILLIS: Long = 180_000L

    @Volatile
    private var current: OutdoorMobilityFallback? = null

    fun post(
        kind: MobilityFallbackKind,
        destination: String?,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        current = OutdoorMobilityFallback(kind, destination, nowMillis)
    }

    /** Oferta vigente sin consumirla (limpia y devuelve null si venció). */
    fun peek(nowMillis: Long = System.currentTimeMillis()): OutdoorMobilityFallback? {
        val offer = current ?: return null
        if (nowMillis - offer.postedAtMillis > TTL_MILLIS) {
            current = null
            return null
        }
        return offer
    }

    /** Consume la oferta vigente (o null si no hay/venció). */
    fun consume(nowMillis: Long = System.currentTimeMillis()): OutdoorMobilityFallback? {
        val offer = peek(nowMillis)
        current = null
        return offer
    }

    fun clear() {
        current = null
    }
}
