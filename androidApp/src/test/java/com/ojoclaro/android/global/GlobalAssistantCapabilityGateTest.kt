package com.ojoclaro.android.global

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GlobalAssistantCapabilityGateTest {
    @Test
    fun micNoListoNoPrometeContinuidad() {
        val capability = GlobalAssistantCapabilityGate(
            overrideCapability = GlobalAssistantCapabilityGate.fromFlags(
                foregroundServiceReady = true,
                notificationReady = true,
                overlayReady = true,
                microphoneContinuationReady = false,
                fallbackReturnReady = true
            )
        ).evaluate()

        assertFalse(capability.canSafelyContinueOutsideApp)
        assertFalse(capability.microphoneContinuationReady)
        assertTrue(capability.fallbackReturnReady)
    }

    @Test
    fun overlayNoListoNoPrometeContinuidad() {
        val capability = GlobalAssistantCapabilityGate(
            overrideCapability = GlobalAssistantCapabilityGate.fromFlags(
                foregroundServiceReady = true,
                notificationReady = true,
                overlayReady = false,
                microphoneContinuationReady = true,
                fallbackReturnReady = true
            )
        ).evaluate()

        assertFalse(capability.canSafelyContinueOutsideApp)
        assertFalse(capability.overlayReady)
    }

    @Test
    fun notificacionNoListaNoPrometeContinuidadNiFallback() {
        val capability = GlobalAssistantCapabilityGate(
            overrideCapability = GlobalAssistantCapabilityGate.fromFlags(
                foregroundServiceReady = true,
                notificationReady = false,
                overlayReady = true,
                microphoneContinuationReady = true,
                fallbackReturnReady = false
            )
        ).evaluate()

        assertFalse(capability.canSafelyContinueOutsideApp)
        assertFalse(capability.notificationReady)
        assertFalse(capability.fallbackReturnReady)
    }

    @Test
    fun fallbackVisiblePermiteRetornoSinPrometerMicrofono() {
        val capability = GlobalAssistantCapabilityGate(
            overrideCapability = GlobalAssistantCapabilityGate.fromFlags(
                foregroundServiceReady = true,
                notificationReady = true,
                overlayReady = false,
                microphoneContinuationReady = false,
                fallbackReturnReady = true
            )
        ).evaluate()

        assertFalse(capability.canSafelyContinueOutsideApp)
        assertFalse(capability.microphoneContinuationReady)
        assertTrue(capability.fallbackReturnReady)
    }

    @Test
    fun todoListoPermiteContinuidadSegura() {
        val capability = GlobalAssistantCapabilityGate(
            overrideCapability = GlobalAssistantCapabilityGate.fromFlags(
                foregroundServiceReady = true,
                notificationReady = true,
                overlayReady = true,
                microphoneContinuationReady = true,
                fallbackReturnReady = true
            )
        ).evaluate()

        assertTrue(capability.canSafelyContinueOutsideApp)
        assertTrue(capability.microphoneContinuationReady)
    }
}
