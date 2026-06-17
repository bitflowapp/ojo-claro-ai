package com.ojoclaro.android.agent.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RobotCapabilityMatrixTest {

    @Test
    fun defaultStaticMatrix_containsAllCapabilitiesOnce() {
        val matrix = RobotCapabilityMatrix.defaultStaticMatrix()

        assertEquals(RobotCapability.entries.size, matrix.size)
        assertEquals(RobotCapability.entries.toSet(), matrix.map { it.capability }.toSet())
    }

    @Test
    fun prepareWhatsAppMessage_documentsSafePreparationWithoutAutoSendWording() {
        val status = RobotCapabilityMatrix.defaultStaticMatrix().statusFor(
            RobotCapability.PREPARE_WHATSAPP_MESSAGE
        )

        assertFalse(status.reason.contains("auto-send", ignoreCase = true))
        assertTrue(status.reason.contains("Intent.ACTION_SEND"))
        assertTrue(status.reason.contains("no envia automaticamente", ignoreCase = true))
    }

    @Test
    fun openDialer_documentsActionDialAndNoCallPhone() {
        val status = RobotCapabilityMatrix.defaultStaticMatrix().statusFor(RobotCapability.OPEN_DIALER)

        assertTrue(status.reason.contains("Intent.ACTION_DIAL"))
        assertTrue(status.reason.contains("CALL_PHONE"))
        assertTrue(status.reason.contains("no requiere ni usa CALL_PHONE"))
    }

    @Test
    fun contactLookup_doesNotPretendDeviceContactsAreReady() {
        val status = RobotCapabilityMatrix.defaultStaticMatrix().statusFor(RobotCapability.CONTACT_LOOKUP)

        assertTrue(status.status == CapabilityReadiness.PARTIAL || status.status == CapabilityReadiness.NOT_IMPLEMENTED)
        assertTrue(status.reason.contains("READ_CONTACTS"))
    }

    @Test
    fun whatsappVisibleChatDetection_mentionsVisibleScreenOnly() {
        val status = RobotCapabilityMatrix.defaultStaticMatrix().statusFor(
            RobotCapability.WHATSAPP_VISIBLE_CHAT_DETECTION
        )

        assertTrue(status.reason.contains("pantalla visible", ignoreCase = true))
    }

    private fun List<CapabilityStatus>.statusFor(capability: RobotCapability): CapabilityStatus =
        first { it.capability == capability }
}
