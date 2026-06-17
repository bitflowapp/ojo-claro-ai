package com.ojoclaro.android.outdoor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OutdoorSafetyPolicyTest {

    // Test 21: la política bloquea "es seguro cruzar" y variaciones.
    @Test
    fun blocksItIsSafeToCrossVariations() {
        listOf(
            "Es seguro cruzar la calle",
            "Ya es seguro, podés cruzar",
            "Podes cruzar ahora",
            "Puede cruzar tranquilo",
            "Cruzá ahora que no viene nadie",
            "cruza ahora",
            "Podés avanzar sin problema",
            "avanzá",
            "El auto se detuvo, podés pasar",
            "Seguí sin el bastón, yo te guío"
        ).forEach { phrase ->
            assertEquals(
                OutdoorSafetyPolicy.MessageClass.BLOCKED_SAFETY_CLAIM,
                OutdoorSafetyPolicy.classify(phrase),
                "Debería bloquear: $phrase"
            )
        }
    }

    // Test 22: bloquea "camino libre" y variaciones.
    @Test
    fun blocksPathIsClearVariations() {
        listOf(
            "El camino está libre",
            "el camino esta despejado",
            "Camino libre adelante",
            "El paso está libre",
            "No hay peligro",
            "No hay obstáculos adelante",
            "No viene ningún auto",
            "no viene ninguna moto",
            "Todo está despejado",
            "Vía libre"
        ).forEach { phrase ->
            assertEquals(
                OutdoorSafetyPolicy.MessageClass.BLOCKED_SAFETY_CLAIM,
                OutdoorSafetyPolicy.classify(phrase),
                "Debería bloquear: $phrase"
            )
        }
    }

    // El texto bloqueado se reemplaza COMPLETO por el fallback prudente.
    @Test
    fun blockedClaimIsReplacedBySafeFallback() {
        val spoken = OutdoorSafetyPolicy.sanitizeForSpeech("Podés cruzar, el camino está libre.")
        assertEquals(OutdoorSafetyPolicy.SAFE_FALLBACK_TEXT, spoken)
        assertTrue(spoken.contains("bastón"))
    }

    // Test 23: posible obstáculo usa lenguaje prudente (CAUTION, no bloqueado).
    @Test
    fun cautiousObstacleLanguageIsAllowedAsCaution() {
        listOf(
            "Detecté un posible obstáculo en la zona central.",
            "Hay un posible desnivel adelante.",
            "Parece que hay un objeto que podría obstaculizar el paso.",
            "No puedo confirmar que el paso esté libre."
        ).forEach { phrase ->
            assertEquals(
                OutdoorSafetyPolicy.MessageClass.CAUTION,
                OutdoorSafetyPolicy.classify(phrase),
                "Debería ser CAUTION: $phrase"
            )
            assertEquals(phrase, OutdoorSafetyPolicy.sanitizeForSpeech(phrase))
        }
    }

    @Test
    fun informationalDistanceMessagesPassThrough() {
        val phrase = "Faltan aproximadamente 350 metros. En 45 metros, girá a la izquierda."
        assertEquals(
            OutdoorSafetyPolicy.MessageClass.INFORMATIONAL,
            OutdoorSafetyPolicy.classify(phrase)
        )
        assertEquals(phrase, OutdoorSafetyPolicy.sanitizeForSpeech(phrase))
    }

    // Descripción de escena: siempre cierra con el disclaimer prudente.
    @Test
    fun sceneDescriptionAlwaysCarriesDisclaimer() {
        val described = OutdoorSafetyPolicy.sanitizeSceneDescription(
            "Veo una vereda y un vehículo estacionado a la derecha."
        )
        assertTrue(described.contains("No puedo confirmar"))

        val blocked = OutdoorSafetyPolicy.sanitizeSceneDescription("El camino está libre.")
        assertEquals(OutdoorSafetyPolicy.SAFE_FALLBACK_TEXT, blocked)
    }

    // El prototipo de peligros está apagado por defecto y solo habla prudente.
    @Test
    fun hazardPrototypeIsOffByDefaultAndCautious() {
        assertEquals(false, OutdoorHazardPrototype.ENABLED_BY_DEFAULT)

        val stats = OutdoorHazardPrototype.FrameStats(
            width = 640, height = 480, centerBottomLuma = 30, surroundLuma = 140
        )
        val disabled = OutdoorHazardPrototype.analyze(stats)
        assertEquals(false, disabled.possibleObstacleAhead)

        val enabled = OutdoorHazardPrototype.analyze(stats, enabled = true)
        assertTrue(enabled.possibleObstacleAhead)
        val text = enabled.cautiousText.orEmpty()
        assertTrue(text.contains("posible"))
        assertEquals(
            OutdoorSafetyPolicy.MessageClass.CAUTION,
            OutdoorSafetyPolicy.classify(text)
        )
    }
}
