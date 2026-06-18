package com.ojoclaro.android.agent.intelligence

import java.text.Normalizer

/**
 * Mobility Copilot v1 — parser + narrador PUROS del copiloto de Uber (Level 2).
 *
 * Alcance: LEER y GUIAR. El copiloto NUNCA toca el botón final de pedir/confirmar
 * un viaje, ni pagos, ni llamadas, ni cancela un viaje en curso. Aun con la frase
 * fuerte "confirmo pedir Uber ahora", en v1 NO toca nada: deja a la persona en la
 * pantalla para que lo confirme manualmente.
 *
 * IMPORTANTE (routing): estas frases deben evaluarse ANTES del fast-path de
 * Outdoor, que reclama CUALQUIER texto con el token "uber" para ofrecer ABRIR la
 * app. Por eso el parser es PRECISO: solo reclama lectura/confirmación/cancelación;
 * "abrí Uber"/"pedime un Uber" NO se reclaman (los maneja el flujo de apertura
 * seguro existente).
 */
sealed class UberIntent {
    /** "qué dice Uber" — describir la pantalla actual. */
    object DescribeUber : UberIntent()
    /** "qué viaje estoy por pedir" — resumen del viaje en preparación. */
    object WhatRide : UberIntent()
    object AskPrice : UberIntent()
    object AskOrigin : UberIntent()
    object AskDestination : UberIntent()
    object AskRideType : UberIntent()
    /** "confirmo pedir Uber ahora" — frase fuerte; en v1 NO ejecuta el pedido. */
    object ConfirmRequest : UberIntent()
    /** "cancelá Uber" / "no pidas nada" — frenar; nunca cancela un viaje en curso. */
    object CancelUber : UberIntent()
}

object UberCopilotPhrases {

    private val DESCRIBE = setOf(
        "que dice uber", "que muestra uber", "que hay en uber", "leeme uber",
        "que dice la pantalla de uber"
    )
    private val WHAT_RIDE = setOf(
        "que viaje estoy por pedir", "que viaje voy a pedir", "que estoy por pedir",
        "que viaje estoy pidiendo", "que viaje es"
    )
    private val ASK_PRICE = setOf(
        "que precio muestra", "que precio tiene", "que precio hay", "cuanto sale",
        "cuanto cuesta el viaje", "cuanto cuesta", "que tarifa muestra", "que precio es"
    )
    private val ASK_ORIGIN = setOf(
        "que origen tiene", "que origen muestra", "cual es el origen", "de donde sale",
        "desde donde salgo", "cual es el punto de partida"
    )
    private val ASK_DESTINATION = setOf(
        "que destino tiene", "que destino muestra", "cual es el destino", "a donde voy",
        "hacia donde voy", "cual es el destino del viaje"
    )
    private val ASK_RIDE_TYPE = setOf(
        "que tipo de uber muestra", "que uber muestra", "que tipo de viaje muestra",
        "que opcion de uber", "que tipo de uber es", "que opcion muestra"
    )
    // Frase fuerte EXACTA (no se acepta nada parecido).
    private val CONFIRM_REQUEST = setOf("confirmo pedir uber ahora")
    private val CANCEL = setOf(
        "cancela uber", "cancelar uber", "no pidas nada", "no pidas el uber",
        "no pidas un uber", "no pidas el viaje", "no pidas viaje", "no quiero pedir nada"
    )

    fun parse(rawText: String): UberIntent? {
        val key = normalize(rawText)
        if (key.isBlank()) return null
        return when (key) {
            in DESCRIBE -> UberIntent.DescribeUber
            in WHAT_RIDE -> UberIntent.WhatRide
            in ASK_PRICE -> UberIntent.AskPrice
            in ASK_ORIGIN -> UberIntent.AskOrigin
            in ASK_DESTINATION -> UberIntent.AskDestination
            in ASK_RIDE_TYPE -> UberIntent.AskRideType
            in CONFIRM_REQUEST -> UberIntent.ConfirmRequest
            in CANCEL -> UberIntent.CancelUber
            else -> null
        }
    }

    private fun normalize(text: String): String {
        val lower = text.lowercase()
        val stripped = Normalizer.normalize(lower, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        return stripped.replace(Regex("[¿?¡!.,;:]"), " ").replace(Regex("\\s+"), " ").trim()
    }
}

/**
 * Texto hablado. Puede incluir destino/precio para que la persona los escuche, pero
 * el caller NO debe loguear esos textos (solo flags/longitudes).
 */
object UberCopilotNarrator {

    fun notInUber(): String =
        "Para eso necesito que Uber esté abierto. Decí: abrí Uber."

    fun describe(model: UberScreenModel): String =
        if (model.activeApp == UberApp.UBER) model.explanation else notInUber()

    private fun isRideScreen(model: UberScreenModel): Boolean =
        model.screenType == UberScreenType.CONFIRM_RIDE ||
            model.screenType == UberScreenType.RIDE_OPTIONS

    fun whatRide(model: UberScreenModel): String {
        if (model.activeApp != UberApp.UBER) return notInUber()
        val parts = buildList {
            if (!model.destinationText.isNullOrBlank()) add("destino ${model.destinationText}")
            if (!model.rideTypeText.isNullOrBlank()) add("opción ${model.rideTypeText}")
            if (!model.priceText.isNullOrBlank()) add("precio ${model.priceText}")
        }
        return if (parts.isEmpty()) {
            "Todavía no puedo leer con seguridad qué viaje se está por pedir. " +
                "Revisá la pantalla; no voy a pedir nada por mi cuenta."
        } else {
            // Si es una pantalla de viaje pero el precio no se pudo leer, avisar
            // explícito para que lo verifique a mano (Uber no siempre lo expone).
            val priceNote = if (model.priceText.isNullOrBlank() && isRideScreen(model)) {
                " No pude leer el precio: verificalo a mano antes de pedir."
            } else {
                ""
            }
            "Estarías por pedir: ${parts.joinToString(", ")}.$priceNote " +
                "No voy a confirmar el viaje: revisá y, si está bien, tocá vos."
        }
    }

    fun price(model: UberScreenModel): String = when {
        model.activeApp != UberApp.UBER -> notInUber()
        !model.priceText.isNullOrBlank() -> "Uber muestra el precio: ${model.priceText}."
        // En una pantalla de opciones/confirmación, el precio DEBERÍA estar pero
        // Uber a veces no lo expone de forma accesible (lo dibuja sin texto). No lo
        // invento: respondo honesto y pido verificación manual.
        isRideScreen(model) ->
            "No pude leer el precio: Uber no lo muestra de forma accesible en esta " +
                "pantalla. Revisalo a mano antes de pedir."
        else -> "No veo un precio en la pantalla de Uber todavía."
    }

    fun origin(model: UberScreenModel): String = when {
        model.activeApp != UberApp.UBER -> notInUber()
        !model.pickupText.isNullOrBlank() -> "El origen es ${model.pickupText}."
        else -> "No pude leer el origen con seguridad. Revisá la pantalla."
    }

    fun destination(model: UberScreenModel): String = when {
        model.activeApp != UberApp.UBER -> notInUber()
        !model.destinationText.isNullOrBlank() -> "El destino es ${model.destinationText}."
        else -> "No pude leer el destino con seguridad. Revisá la pantalla."
    }

    fun rideType(model: UberScreenModel): String = when {
        model.activeApp != UberApp.UBER -> notInUber()
        !model.rideTypeText.isNullOrBlank() -> "El tipo de viaje es ${model.rideTypeText}."
        else -> "No veo el tipo de viaje en la pantalla todavía."
    }

    /** "confirmo pedir Uber ahora": v1 NO ejecuta el pedido, pase lo que pase. */
    fun confirmRequestBlocked(): String =
        "Todavía no tengo habilitado pedir el viaje automáticamente. Puedo dejarte " +
            "en la pantalla de confirmación para que lo toques vos cuando quieras."

    fun cancel(model: UberScreenModel): String =
        if (model.activeApp == UberApp.UBER && model.screenType == UberScreenType.TRIP_ACTIVE) {
            "Hay un viaje en curso. No voy a cancelarlo por mi cuenta; si querés " +
                "cancelarlo, hacelo vos en la pantalla."
        } else {
            "Listo, no pido nada y no toco ningún botón de Uber."
        }

    /** Respuesta cuando, estando en Uber, preguntan por personas/chats (no aplica). */
    fun notAChatApp(): String =
        "Estás en Uber. Acá no hay personas ni chats. Puedo leer origen, destino, " +
            "precio y tipo de viaje si aparecen. Decí: qué dice Uber."
}
