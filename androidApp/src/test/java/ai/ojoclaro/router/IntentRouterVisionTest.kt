package ai.ojoclaro.router

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Tests for the vision handlers on IntentRouter:
 *   handleReadOcrText      → CameraAction(READ_OCR)
 *   handleReadVisibleScreen → CameraAction(READ_SCREEN)
 *
 * The router-level dispatch for these intents is also exercised, so we know
 * the full path from JSON → route() → handler is wired correctly.
 *
 * Defensive parity with the gate: even if a handler is invoked directly with
 * safety_level=blocked_sensitive, it MUST return null.
 */
class IntentRouterVisionTest {

    // --- handleReadOcrText ---

    @Test
    fun handleReadOcrTextWithAllowSafeReturnsCameraActionWithReadOcr() {
        val handler = EmptyIntentRouteHandler()

        val action = handler.handleReadOcrText(visionRequest(safetyLevel = "allow_safe"))

        assertNotNull(action)
        assertEquals(CameraActionType.READ_OCR, action.type)
    }

    @Test
    fun handleReadOcrTextWithBlockedSensitiveTopLevelReturnsNull() {
        val handler = EmptyIntentRouteHandler()

        val action = handler.handleReadOcrText(visionRequest(safetyLevel = "blocked_sensitive"))

        assertNull(action)
    }

    @Test
    fun handleReadOcrTextWithBlockedSensitiveInParamsReturnsNull() {
        val handler = EmptyIntentRouteHandler()

        val action = handler.handleReadOcrText(
            visionRequest(
                safetyLevel = "allow_safe",
                params = mapOf("safety_level" to "blocked_sensitive")
            )
        )

        assertNull(action)
    }

    @Test
    fun handleReadOcrTextIgnoresUnrelatedParams() {
        val handler = EmptyIntentRouteHandler()

        val action = handler.handleReadOcrText(
            visionRequest(
                safetyLevel = "allow_safe",
                params = mapOf("contact_query" to "irrelevant", "destination" to "nowhere")
            )
        )

        assertNotNull(action)
        assertEquals(CameraActionType.READ_OCR, action.type)
    }

    // --- handleReadVisibleScreen ---

    @Test
    fun handleReadVisibleScreenWithAllowSafeReturnsCameraActionWithReadScreen() {
        val handler = EmptyIntentRouteHandler()

        val action = handler.handleReadVisibleScreen(visionRequest(safetyLevel = "allow_safe"))

        assertNotNull(action)
        assertEquals(CameraActionType.READ_SCREEN, action.type)
    }

    @Test
    fun handleReadVisibleScreenWithRequiresConfirmStillReturnsDescriptor() {
        // requires_confirm is the canonical safety level for read_visible_screen
        // per prompt v3. The descriptor is still produced; the gate (one layer
        // up) is responsible for the confirmation flow.
        val handler = EmptyIntentRouteHandler()

        val action = handler.handleReadVisibleScreen(visionRequest(safetyLevel = "requires_confirm"))

        assertNotNull(action)
        assertEquals(CameraActionType.READ_SCREEN, action.type)
    }

    @Test
    fun handleReadVisibleScreenWithBlockedSensitiveTopLevelReturnsNull() {
        val handler = EmptyIntentRouteHandler()

        val action = handler.handleReadVisibleScreen(
            visionRequest(safetyLevel = "blocked_sensitive")
        )

        assertNull(action)
    }

    @Test
    fun handleReadVisibleScreenWithBlockedSensitiveInParamsReturnsNull() {
        val handler = EmptyIntentRouteHandler()

        val action = handler.handleReadVisibleScreen(
            visionRequest(
                safetyLevel = "allow_safe",
                params = mapOf("safety_level" to "blocked_sensitive")
            )
        )

        assertNull(action)
    }

    // --- READ_OCR vs READ_SCREEN are distinct types ---

    @Test
    fun readOcrAndReadScreenTypesAreDistinct() {
        val handler = EmptyIntentRouteHandler()

        val ocr = handler.handleReadOcrText(visionRequest(safetyLevel = "allow_safe"))
        val screen = handler.handleReadVisibleScreen(visionRequest(safetyLevel = "allow_safe"))

        assertNotNull(ocr)
        assertNotNull(screen)
        assertEquals(CameraActionType.READ_OCR, ocr.type)
        assertEquals(CameraActionType.READ_SCREEN, screen.type)
        assertEquals(false, ocr.type == screen.type)
    }

    // --- End-to-end router dispatch ---

    @Test
    fun routeReadOcrTextAllowSafeDispatchesToReadOcrHandler() {
        val handler = RecordingVisionHandler()
        val router = IntentRouter(handler, VisionRecordingSafeExecutionDelegate())

        router.route(visionIntentJson(intent = "read_ocr_text", safetyLevel = "allow_safe"))

        assertEquals(listOf("read_ocr_text"), handler.calls)
    }

    @Test
    fun routeReadVisibleScreenAllowSafeDispatchesToReadScreenHandler() {
        val handler = RecordingVisionHandler()
        val router = IntentRouter(handler, VisionRecordingSafeExecutionDelegate())

        router.route(
            visionIntentJson(intent = "read_visible_screen", safetyLevel = "allow_safe")
        )

        assertEquals(listOf("read_visible_screen"), handler.calls)
    }

    @Test
    fun routeReadOcrTextBlockedSensitiveDispatchesToBlockedHandler() {
        // The router-level guard fires BEFORE the vision handler is even reached.
        val handler = RecordingVisionHandler()
        val router = IntentRouter(handler, VisionRecordingSafeExecutionDelegate())

        router.route(
            visionIntentJson(intent = "read_ocr_text", safetyLevel = "blocked_sensitive")
        )

        assertEquals(listOf("blocked"), handler.calls)
    }

    @Test
    fun routeReadVisibleScreenBlockedSensitiveDispatchesToBlockedHandler() {
        val handler = RecordingVisionHandler()
        val router = IntentRouter(handler, VisionRecordingSafeExecutionDelegate())

        router.route(
            visionIntentJson(intent = "read_visible_screen", safetyLevel = "blocked_sensitive")
        )

        assertEquals(listOf("blocked"), handler.calls)
    }

    @Test
    fun routeReadVisibleScreenRequiresConfirmDelegatesToGate() {
        val handler = RecordingVisionHandler()
        val gate = VisionRecordingSafeExecutionDelegate()
        val router = IntentRouter(handler, gate)

        router.route(
            visionIntentJson(intent = "read_visible_screen", safetyLevel = "requires_confirm")
        )

        assertEquals(emptyList(), handler.calls)
        assertEquals(1, gate.submittedCount)
    }

    // --- Test helpers ---

    private fun visionRequest(
        safetyLevel: String,
        params: Map<String, Any?> = emptyMap()
    ): IntentRouteRequest =
        IntentRouteRequest(
            intent = "read_ocr_text",
            confidence = 0.9,
            params = params,
            safetyLevel = safetyLevel,
            voiceResponse = null,
            voiceResponseTemplate = null,
            rawText = "leé el cartel"
        )

    private fun visionIntentJson(
        intent: String,
        safetyLevel: String,
        confidence: Double = 0.9
    ): Map<String, Any?> =
        mapOf(
            "intent" to intent,
            "confidence" to confidence,
            "params" to emptyMap<String, Any?>(),
            "safety_level" to safetyLevel,
            "voice_response" to null,
            "voice_response_template" to null,
            "raw_text" to "leé el cartel"
        )
}

private class RecordingVisionHandler : IntentRouteHandler {
    val calls = mutableListOf<String>()

    override fun handleReadOcrText(request: IntentRouteRequest): CameraAction? {
        calls += "read_ocr_text"
        return CameraAction(type = CameraActionType.READ_OCR)
    }

    override fun handleReadVisibleScreen(request: IntentRouteRequest): CameraAction? {
        calls += "read_visible_screen"
        return CameraAction(type = CameraActionType.READ_SCREEN)
    }

    override fun handleBlocked(request: IntentRouteRequest): ConversationAction? {
        calls += "blocked"
        return null
    }

    override fun handleUnknown(request: IntentRouteRequest): ConversationAction? {
        calls += "unknown"
        return null
    }
}

private class VisionRecordingSafeExecutionDelegate : AgentSafeExecutionDelegate {
    var submittedCount: Int = 0
        private set

    override fun submit(request: IntentRouteRequest) {
        submittedCount += 1
    }
}
