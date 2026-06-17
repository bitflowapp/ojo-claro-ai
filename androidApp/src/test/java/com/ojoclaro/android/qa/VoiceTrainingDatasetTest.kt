package com.ojoclaro.android.qa

import com.ojoclaro.android.agent.AgentIntent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VoiceTrainingDatasetTest {

    @Test
    fun datasetContainsMarcoWhatsAppChatCase() {
        val case = VoiceTrainingDataset.cases.first {
            it.spokenByUser.contains("wp", ignoreCase = true) &&
                it.expectedIntent == AgentIntent.OPEN_WHATSAPP_CHAT
        }

        assertEquals(AgentIntent.OPEN_WHATSAPP_CHAT, case.expectedIntent)
        assertEquals("Marco", case.expectedSlots["contactName"])
        assertTrue(case.expectedResult.contains("OpenWhatsAppChat"))
    }

    @Test
    fun formatterProducesReadableBlock() {
        val formatted = VoiceTrainingCaseFormatter.format(VoiceTrainingDataset.cases.first())

        assertTrue(formatted.contains("spoken:"))
        assertTrue(formatted.contains("expected:"))
        assertTrue(formatted.contains("notes:"))
    }
}
