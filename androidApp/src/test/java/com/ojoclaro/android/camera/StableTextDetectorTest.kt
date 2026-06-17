package com.ojoclaro.android.camera

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StableTextDetectorTest {

    @Test
    fun waitsForTextToBeStableBeforeEmitting() {
        val detector = StableTextDetector()
        detector.reset(nowMillis = 0L)

        assertNull(
            detector.onTextDetected(
                text = "Factura 123",
                nowMillis = 0L,
                isSpeechBusy = false
            )
        )

        assertNull(
            detector.onTextDetected(
                text = "Factura 123",
                nowMillis = 999L,
                isSpeechBusy = false
            )
        )

        val result = detector.onTextDetected(
            text = "Factura 123",
            nowMillis = 1_000L,
            isSpeechBusy = false
        )

        assertIs<StableTextResult.TextReady>(result)
        assertEquals("Factura 123", result.text)
    }

    @Test
    fun doesNotEmitSameTextTwiceInOneScan() {
        val detector = StableTextDetector()
        detector.reset(nowMillis = 0L)

        detector.onTextDetected(
            text = "Total 450",
            nowMillis = 0L,
            isSpeechBusy = false
        )

        val first = detector.onTextDetected(
            text = "Total 450",
            nowMillis = 1_100L,
            isSpeechBusy = false
        )

        assertIs<StableTextResult.TextReady>(first)

        val repeated = detector.onTextDetected(
            text = "Total 450",
            nowMillis = 3_000L,
            isSpeechBusy = false
        )

        assertNull(repeated)
    }

    @Test
    fun emitsNoTextOnlyOnceAfterTimeout() {
        val detector = StableTextDetector()
        detector.reset(nowMillis = 10_000L)

        assertNull(
            detector.onNoText(
                nowMillis = 15_999L,
                isSpeechBusy = false
            )
        )

        val first = detector.onNoText(
            nowMillis = 16_000L,
            isSpeechBusy = false
        )

        val second = detector.onNoText(
            nowMillis = 20_000L,
            isSpeechBusy = false
        )

        assertEquals(StableTextResult.NoTextFound, first)
        assertNull(second)
    }

    @Test
    fun pausesWhileSpeechIsBusy() {
        val detector = StableTextDetector()
        detector.reset(nowMillis = 0L)

        assertNull(
            detector.onTextDetected(
                text = "Salida",
                nowMillis = 0L,
                isSpeechBusy = true
            )
        )

        assertNull(
            detector.onNoText(
                nowMillis = 7_000L,
                isSpeechBusy = true
            )
        )
    }

    @Test
    fun clearsCandidateWhenTextDisappears() {
        val detector = StableTextDetector()
        detector.reset(nowMillis = 0L)

        assertNull(
            detector.onTextDetected(
                text = "Factura 123",
                nowMillis = 0L,
                isSpeechBusy = false
            )
        )

        // El OCR pierde el texto por un frame. Esto debe cortar la estabilidad.
        assertNull(
            detector.onTextDetected(
                text = "",
                nowMillis = 500L,
                isSpeechBusy = false
            )
        )

        // Vuelve el mismo texto, pero debe reiniciar la ventana de estabilidad.
        assertNull(
            detector.onTextDetected(
                text = "Factura 123",
                nowMillis = 1_000L,
                isSpeechBusy = false
            )
        )

        val result = detector.onTextDetected(
            text = "Factura 123",
            nowMillis = 2_000L,
            isSpeechBusy = false
        )

        assertIs<StableTextResult.TextReady>(result)
        assertEquals("Factura 123", result.text)
    }

    @Test
    fun differentTextResetsStabilityWindow() {
        val detector = StableTextDetector()
        detector.reset(nowMillis = 0L)

        assertNull(
            detector.onTextDetected(
                text = "Factura 123",
                nowMillis = 0L,
                isSpeechBusy = false
            )
        )

        // Cambia el texto antes de completar estabilidad.
        assertNull(
            detector.onTextDetected(
                text = "Factura 124",
                nowMillis = 800L,
                isSpeechBusy = false
            )
        )

        // Todavía no pasó 1 segundo desde el nuevo candidato.
        assertNull(
            detector.onTextDetected(
                text = "Factura 124",
                nowMillis = 1_500L,
                isSpeechBusy = false
            )
        )

        val result = detector.onTextDetected(
            text = "Factura 124",
            nowMillis = 1_800L,
            isSpeechBusy = false
        )

        assertIs<StableTextResult.TextReady>(result)
        assertEquals("Factura 124", result.text)
    }

    @Test
    fun resetAllowsSameTextAgainInNewScan() {
        val detector = StableTextDetector()
        detector.reset(nowMillis = 0L)

        detector.onTextDetected(
            text = "Total 450",
            nowMillis = 0L,
            isSpeechBusy = false
        )

        val first = detector.onTextDetected(
            text = "Total 450",
            nowMillis = 1_000L,
            isSpeechBusy = false
        )

        assertIs<StableTextResult.TextReady>(first)
        assertEquals("Total 450", first.text)

        detector.reset(nowMillis = 5_000L)

        detector.onTextDetected(
            text = "Total 450",
            nowMillis = 5_000L,
            isSpeechBusy = false
        )

        val second = detector.onTextDetected(
            text = "Total 450",
            nowMillis = 6_000L,
            isSpeechBusy = false
        )

        assertIs<StableTextResult.TextReady>(second)
        assertEquals("Total 450", second.text)
    }

    @Test
    fun normalizesMultilineTextBeforeEmitting() {
        val detector = StableTextDetector()
        detector.reset(nowMillis = 0L)

        val rawText = """
            Factura 123

            Total    450
        """.trimIndent()

        detector.onTextDetected(
            text = rawText,
            nowMillis = 0L,
            isSpeechBusy = false
        )

        val result = detector.onTextDetected(
            text = rawText,
            nowMillis = 1_000L,
            isSpeechBusy = false
        )

        assertIs<StableTextResult.TextReady>(result)
        assertEquals("Factura 123\nTotal 450", result.text)
    }

    @Test
    fun noTextTimeoutUsesLastDetectionAsReference() {
        val detector = StableTextDetector()
        detector.reset(nowMillis = 0L)

        detector.onTextDetected(
            text = "Texto temporal",
            nowMillis = 1_000L,
            isSpeechBusy = false
        )

        assertNull(
            detector.onNoText(
                nowMillis = 6_999L,
                isSpeechBusy = false
            )
        )

        val result = detector.onNoText(
            nowMillis = 7_000L,
            isSpeechBusy = false
        )

        assertEquals(StableTextResult.NoTextFound, result)
    }

    @Test
    fun longDetectedTextIsTrimmedToSafeLength() {
        val detector = StableTextDetector()
        detector.reset(nowMillis = 0L)

        val longText = "A".repeat(2_000)

        detector.onTextDetected(
            text = longText,
            nowMillis = 0L,
            isSpeechBusy = false
        )

        val result = detector.onTextDetected(
            text = longText,
            nowMillis = 1_000L,
            isSpeechBusy = false
        )

        assertIs<StableTextResult.TextReady>(result)
        assertTrue(result.text.length <= 901)
        assertTrue(result.text.endsWith("…"))
    }
}
