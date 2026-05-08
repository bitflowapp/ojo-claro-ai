package com.ojoclaro.android.capabilities

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CapabilityRegistryTest {

    private fun registryWith(
        vararg pairs: Pair<Capability, Boolean>
    ): CapabilityRegistry =
        CapabilityRegistry(
            context = null,
            availabilityOverrides = mapOf(*pairs)
        )

    @Test
    fun capabilityWithoutContextAndWithoutOverrideIsMissingByDefault() {
        val registry = registryWith()

        Capability.entries.forEach { capability ->
            assertFalse(
                actual = registry.isAvailable(capability),
                message = "${capability.name} should be unavailable without Context or override."
            )
        }
    }

    @Test
    fun whatsAppAvailableWhenOverrideTrue() {
        val registry = registryWith(Capability.WHATSAPP to true)

        assertTrue(registry.isAvailable(Capability.WHATSAPP))
    }

    @Test
    fun whatsAppMissingReturnsCorrectMessage() {
        val registry = registryWith(Capability.WHATSAPP to false)

        val status = registry.status(Capability.WHATSAPP)

        assertFalse(status.isAvailable)
        assertEquals(Capability.MSG_WHATSAPP_MISSING, status.userMessageWhenMissing)
        assertFalse(status.canRequestUserAction)
        assertEquals("whatsapp", status.technicalName)
    }

    @Test
    fun accessibilityServiceMissingReturnsCorrectMessage() {
        val registry = registryWith(Capability.ACCESSIBILITY_SERVICE to false)

        val status = registry.status(Capability.ACCESSIBILITY_SERVICE)

        assertFalse(status.isAvailable)
        assertEquals(Capability.MSG_ACCESSIBILITY_MISSING, status.userMessageWhenMissing)
        assertTrue(status.canRequestUserAction)
        assertEquals("accessibility_service", status.technicalName)
    }

    @Test
    fun cameraMissingReturnsCorrectMessage() {
        val registry = registryWith(Capability.CAMERA to false)

        val status = registry.status(Capability.CAMERA)

        assertFalse(status.isAvailable)
        assertEquals(Capability.MSG_CAMERA_MISSING, status.userMessageWhenMissing)
        assertTrue(status.userMessageWhenMissing.contains("No tengo permiso", ignoreCase = true))
        assertTrue(status.userMessageWhenMissing.contains("voz", ignoreCase = true))
        assertTrue(status.canRequestUserAction)
        assertEquals("camera", status.technicalName)
    }

    @Test
    fun cloudAiMissingByDefault() {
        val registry = registryWith()

        val status = registry.status(Capability.CLOUD_AI)

        assertFalse(status.isAvailable)
        assertEquals(Capability.MSG_CLOUD_AI_MISSING, status.userMessageWhenMissing)
        assertTrue(status.userMessageWhenMissing.contains("IA flexible está apagada", ignoreCase = true))
        assertTrue(status.userMessageWhenMissing.contains("funciones locales", ignoreCase = true))
        assertFalse(status.canRequestUserAction)
        assertEquals("cloud_ai", status.technicalName)
    }

    @Test
    fun cloudAiCanBeEnabledByOverride() {
        val registry = registryWith(Capability.CLOUD_AI to true)

        val status = registry.status(Capability.CLOUD_AI)

        assertTrue(status.isAvailable)
        assertEquals(Capability.MSG_CLOUD_AI_MISSING, status.userMessageWhenMissing)
        assertFalse(status.canRequestUserAction)
    }

    @Test
    fun textToSpeechCanBeAvailableByOverride() {
        val registry = registryWith(Capability.TEXT_TO_SPEECH to true)

        val status = registry.status(Capability.TEXT_TO_SPEECH)

        assertTrue(status.isAvailable)
        assertEquals(Capability.MSG_TTS_MISSING, status.userMessageWhenMissing)
        assertFalse(status.canRequestUserAction)
        assertEquals("text_to_speech", status.technicalName)
    }

    @Test
    fun ocrLocalCanBeAvailableByOverride() {
        val registry = registryWith(Capability.OCR_LOCAL to true)

        val status = registry.status(Capability.OCR_LOCAL)

        assertTrue(status.isAvailable)
        assertEquals(Capability.MSG_OCR_MISSING, status.userMessageWhenMissing)
        assertFalse(status.canRequestUserAction)
        assertEquals("ocr_local", status.technicalName)
    }

    @Test
    fun snapshotContainsAllCapabilities() {
        val registry = registryWith()

        val snapshot = registry.snapshot()

        assertEquals(Capability.entries.size, snapshot.size)
        assertEquals(
            Capability.entries.toSet(),
            snapshot.map { it.capability }.toSet()
        )
    }

    @Test
    fun snapshotReflectsOverrides() {
        val registry = registryWith(
            Capability.WHATSAPP to true,
            Capability.ACCESSIBILITY_SERVICE to false,
            Capability.CLOUD_AI to true
        )

        val byCapability = registry.snapshot().associateBy { it.capability }

        assertTrue(byCapability.getValue(Capability.WHATSAPP).isAvailable)
        assertFalse(byCapability.getValue(Capability.ACCESSIBILITY_SERVICE).isAvailable)
        assertTrue(byCapability.getValue(Capability.CLOUD_AI).isAvailable)
    }

    @Test
    fun cameraMissingCanRequestUserAction() {
        val registry = registryWith(Capability.CAMERA to false)

        val status = registry.status(Capability.CAMERA)

        assertTrue(status.canRequestUserAction)
    }

    @Test
    fun whatsappMissingCannotRequestUserActionInsideApp() {
        val registry = registryWith(Capability.WHATSAPP to false)

        val status = registry.status(Capability.WHATSAPP)

        assertFalse(status.canRequestUserAction)
    }
}
