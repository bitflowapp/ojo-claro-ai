package com.ojoclaro.android.agent.intelligence

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Mobility Copilot v1 — Uber. PURO. Cubre el reasoner de pantalla y las compuertas
 * de seguridad del router (FASE 2 + FASE 5). No toca Android.
 */
class UberCopilotTest {

    private val RIDER = UberScreenReasoner.UBER_RIDER_PACKAGE
    private val DRIVER = UberScreenReasoner.UBER_DRIVER_PACKAGE

    private fun node(
        text: String? = null,
        hint: String? = null,
        clickable: Boolean = false,
        editable: Boolean = false,
        password: Boolean = false,
    ) = ReasonerNode(
        text = text, hint = hint, isClickable = clickable, isEditable = editable, isPassword = password
    )

    private fun screen(pkg: String?, vararg nodes: ReasonerNode) =
        UberScreenReasoner.reason(RawScreen(pkg, nodes.toList()))

    // ---- activeApp: rider vs driver vs otra ----

    @Test fun riderPackageIsUber() {
        assertEquals(UberApp.UBER, screen(RIDER, node("¿A dónde vas?", clickable = true)).activeApp)
    }

    @Test fun driverPackageIsNotUber() {
        // La app de CONDUCTOR jamás se trata como Uber pasajero.
        val m = screen(DRIVER, node("En línea", clickable = true))
        assertEquals(UberApp.OTHER, m.activeApp)
        assertEquals(UberScreenType.UNKNOWN, m.screenType)
    }

    @Test fun otherPackageIsNotUber() {
        assertEquals(UberApp.OTHER, screen("com.whatsapp", node("Marco")).activeApp)
    }

    // ---- screenType ----

    @Test fun homeDetected() {
        assertEquals(UberScreenType.HOME, screen(RIDER, node("¿A dónde vas?", clickable = true)).screenType)
    }

    @Test fun destinationSearchDetected() {
        val m = screen(RIDER, node(hint = "¿A dónde vas?", editable = true))
        assertEquals(UberScreenType.DESTINATION_SEARCH, m.screenType)
    }

    @Test fun rideOptionsDetectedWithTypeAndPrice() {
        val m = screen(RIDER, node("UberX"), node("\$3.500"))
        assertEquals(UberScreenType.RIDE_OPTIONS, m.screenType)
        assertEquals("UberX", m.rideTypeText)
        assertTrue(m.priceText!!.contains("3.500"))
        assertEquals(Confidence.HIGH, m.confidence)
    }

    @Test fun confirmRideDetectedAndRisky() {
        val m = screen(
            RIDER,
            node("UberX"), node("\$3.500"), node("Confirmar Uber", clickable = true)
        )
        assertEquals(UberScreenType.CONFIRM_RIDE, m.screenType)
        assertTrue(m.riskyControls.contains(UberRiskyControl.CONFIRM_RIDE))
        assertEquals(Confidence.HIGH, m.confidence)
    }

    @Test fun requestButtonClassifiedRisky() {
        val m = screen(RIDER, node("\$3.500"), node("Solicitar Uber", clickable = true))
        assertTrue(m.riskyControls.contains(UberRiskyControl.REQUEST_RIDE))
        assertEquals(UberScreenType.CONFIRM_RIDE, m.screenType)
    }

    @Test fun loginDetectedLowConfidence() {
        val m = screen(RIDER, node("Iniciar sesión", clickable = true))
        assertEquals(UberScreenType.LOGIN, m.screenType)
        assertEquals(Confidence.LOW, m.confidence)
    }

    @Test fun loginDetectedByPasswordField() {
        val m = screen(RIDER, node(hint = "Contraseña", editable = true, password = true))
        assertEquals(UberScreenType.LOGIN, m.screenType)
    }

    @Test fun tripActiveDetected() {
        assertEquals(
            UberScreenType.TRIP_ACTIVE,
            screen(RIDER, node("Tu conductor está llegando")).screenType
        )
    }

    @Test fun addPaymentClassifiedRisky() {
        val m = screen(RIDER, node("Agregar método de pago", clickable = true))
        assertTrue(m.riskyControls.contains(UberRiskyControl.ADD_PAYMENT))
    }

    @Test fun changePaymentClassifiedRisky() {
        val m = screen(RIDER, node("Cambiar método de pago", clickable = true))
        assertTrue(m.riskyControls.contains(UberRiskyControl.CHANGE_PAYMENT))
    }

    @Test fun paymentMethodExtractedNotLoggedSensitive() {
        val m = screen(RIDER, node("UberX"), node("\$3.500"), node("Visa ••••1234"))
        assertTrue(m.paymentText!!.contains("Visa"))
    }

    @Test fun unknownScreenDoesNotAct() {
        val m = screen(RIDER, node("Texto cualquiera sin marcadores"))
        assertEquals(UberScreenType.UNKNOWN, m.screenType)
        assertEquals(Confidence.LOW, m.confidence)
    }

    // ---- readyToRequest: compuerta del pedido (v1 SIEMPRE cerrada en la práctica) ----

    @Test fun readyToRequestTrueOnlyWithAllFields() {
        val full = UberScreenModel(
            activeApp = UberApp.UBER,
            screenType = UberScreenType.CONFIRM_RIDE,
            pickupText = "origen",
            destinationText = "destino",
            priceText = "\$3.500",
            rideTypeText = "UberX",
            paymentText = "Visa",
            riskyControls = listOf(UberRiskyControl.CONFIRM_RIDE),
            confidence = Confidence.HIGH,
            explanation = "x",
        )
        assertTrue(UberScreenReasoner.readyToRequest(full))
        // Faltando cualquier campo → cerrada.
        assertFalse(UberScreenReasoner.readyToRequest(full.copy(destinationText = null)))
        assertFalse(UberScreenReasoner.readyToRequest(full.copy(priceText = null)))
        assertFalse(UberScreenReasoner.readyToRequest(full.copy(confidence = Confidence.MEDIUM)))
    }

    @Test fun realConfirmScreenIsNotReadyBecausePickupDestNotExtracted() {
        // El reasoner v1 no extrae origen/destino (devuelve null) → la compuerta
        // queda SIEMPRE cerrada aunque haya precio/tipo/pago. Freno estructural.
        val m = screen(
            RIDER, node("UberX"), node("\$3.500"), node("Visa ••••1234"),
            node("Confirmar Uber", clickable = true)
        )
        assertFalse(UberScreenReasoner.readyToRequest(m))
    }

    // ---- router: precisión del parser + seguridad ----

    @Test fun parserClaimsReadPhrases() {
        assertEquals(UberIntent.DescribeUber, UberCopilotPhrases.parse("¿qué dice Uber?"))
        assertEquals(UberIntent.AskPrice, UberCopilotPhrases.parse("qué precio muestra"))
        assertEquals(UberIntent.AskOrigin, UberCopilotPhrases.parse("qué origen tiene"))
        assertEquals(UberIntent.AskDestination, UberCopilotPhrases.parse("qué destino tiene"))
        assertEquals(UberIntent.AskRideType, UberCopilotPhrases.parse("qué tipo de Uber muestra"))
        assertEquals(UberIntent.WhatRide, UberCopilotPhrases.parse("qué viaje estoy por pedir"))
    }

    @Test fun parserClaimsCancel() {
        assertEquals(UberIntent.CancelUber, UberCopilotPhrases.parse("cancelá Uber"))
        assertEquals(UberIntent.CancelUber, UberCopilotPhrases.parse("no pidas nada"))
    }

    @Test fun strongConfirmPhraseRecognizedExactly() {
        assertEquals(UberIntent.ConfirmRequest, UberCopilotPhrases.parse("confirmo pedir Uber ahora"))
    }

    @Test fun weakConfirmationsDoNotConfirmUber() {
        // "sí"/"dale"/"ok" NUNCA son una intención de Uber (jamás confirman viaje).
        assertNull(UberCopilotPhrases.parse("sí"))
        assertNull(UberCopilotPhrases.parse("dale"))
        assertNull(UberCopilotPhrases.parse("ok"))
        assertNull(UberCopilotPhrases.parse("confirmo"))
        assertNull(UberCopilotPhrases.parse("pedilo"))
    }

    @Test fun openAndPrepareNotClaimedSoExistingFlowHandlesThem() {
        // "abrí Uber"/"pedime un Uber" siguen al flujo de apertura seguro existente.
        assertNull(UberCopilotPhrases.parse("abrí Uber"))
        assertNull(UberCopilotPhrases.parse("pedime un Uber"))
        assertNull(UberCopilotPhrases.parse("quiero pedir un Uber"))
    }

    @Test fun confirmBlockedMessagePromisesNoTap() {
        val msg = UberCopilotNarrator.confirmRequestBlocked()
        assertTrue(msg.contains("no tengo habilitado", ignoreCase = true))
        assertTrue(msg.contains("toques vos", ignoreCase = true))
    }

    @Test fun cancelNeverCancelsActiveTrip() {
        val active = UberScreenModel(
            UberApp.UBER, UberScreenType.TRIP_ACTIVE, null, null, null, null, null,
            emptyList(), Confidence.MEDIUM, "x"
        )
        val msg = UberCopilotNarrator.cancel(active)
        assertTrue(msg.contains("en curso", ignoreCase = true))
        assertTrue(msg.contains("no voy a cancelarlo", ignoreCase = true))
    }

    @Test fun describeOutsideUberAsksToOpen() {
        val other = screen("com.whatsapp", node("Marco"))
        assertTrue(UberCopilotNarrator.describe(other).contains("Uber esté abierto", ignoreCase = true))
    }

    // ---- precio: formatos argentinos + no confundir min/km/rating ----

    @Test fun priceFormatsArgentineExtracted() {
        for ((label, needle) in listOf(
            "ARS 2.443,00" to "2.443",
            "ARS 2,443.00" to "2,443",
            "ARS 2443" to "2443",
            "\$ 2.443" to "2.443",
            "\$2,443.00" to "2,443",
        )) {
            val p = screen(RIDER, node(label)).priceText
            assertTrue(p != null && p.contains(needle), "no extrajo precio de '$label' -> $p")
        }
    }

    @Test fun etaDistanceRatingNotConfusedWithPrice() {
        // "3 min", "2 km", rating "4.9" no son precio (no hay $/ARS).
        val m = screen(RIDER, node("UberX"), node("3 min"), node("2 km"), node("4.9 estrellas"))
        assertNull(m.priceText)
    }

    @Test fun priceResponseHonestWhenNotAccessibleOnRideScreen() {
        // Pantalla de confirmación con tipo+botón pero SIN precio accesible.
        val m = screen(RIDER, node("Uber Comfort"), node("Solicita un viaje", clickable = true))
        assertEquals(UberScreenType.CONFIRM_RIDE, m.screenType)
        assertNull(m.priceText)
        val said = UberCopilotNarrator.price(m)
        assertTrue(said.contains("accesible", ignoreCase = true), said)
        assertTrue(said.contains("a mano", ignoreCase = true), said)
    }

    @Test fun whatRideWarnsWhenPriceMissingOnRideScreen() {
        val m = screen(RIDER, node("Uber Comfort"), node("Solicita un viaje", clickable = true))
        val said = UberCopilotNarrator.whatRide(m)
        assertTrue(said.contains("Uber Comfort"), said)
        assertTrue(said.contains("verificalo a mano", ignoreCase = true), said)
        // Y sigue prometiendo NO confirmar.
        assertTrue(said.contains("No voy a confirmar", ignoreCase = true), said)
    }
}
