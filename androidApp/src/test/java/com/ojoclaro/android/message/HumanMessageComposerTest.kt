package com.ojoclaro.android.message

import com.ojoclaro.android.memory.PersonalAgentMemory
import com.ojoclaro.android.memory.PersonalMemorySnapshot
import com.ojoclaro.android.memory.PersonalMemoryType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HumanMessageComposerTest {

    @Test
    fun localComposerBuildsWarmLateMessageAndRequiresConfirmation() {
        val composer = LocalMessageTemplateComposer()
        val snapshot = PersonalMemorySnapshot(
            listOf(
                memory(
                    id = "style-1",
                    type = PersonalMemoryType.MESSAGE_STYLE,
                    label = "Sofi",
                    value = "cariñoso",
                    userApproved = true
                )
            )
        )

        val result = composer.compose(
            MessageCompositionRequest(
                originalText = "Decile a Sofi que llego tarde pero decilo bien.",
                contactName = "Sofi",
                messageHint = "llego tarde",
                style = MessageStyle.NEUTRAL,
                memorySnapshot = snapshot
            )
        )

        assertEquals("Amor, voy un poco demorado. Llego en unos minutos.", result.proposedMessage)
        assertTrue(result.spokenProposal.startsWith("Te propongo: "))
        assertTrue(result.requiresConfirmation)
        assertFalse(result.shouldSendAutomatically)
        assertEquals(MessageStyle.WARM, result.styleUsed)
    }

    @Test
    fun composerBlocksSensitiveMessages() {
        val result = LocalMessageTemplateComposer().compose(
            MessageCompositionRequest(
                originalText = "Mandale a Marco mi clave es 1234",
                contactName = "Marco",
                messageHint = "mi clave es 1234",
                style = MessageStyle.NEUTRAL
            )
        )

        assertEquals("", result.proposedMessage)
        assertFalse(result.shouldSendAutomatically)
        assertNotNull(result.blockedReason)
        assertTrue(result.spokenProposal.contains("sensible", ignoreCase = true))
    }

    private fun memory(
        id: String,
        type: PersonalMemoryType,
        label: String,
        value: String,
        userApproved: Boolean
    ): PersonalAgentMemory =
        PersonalAgentMemory(
            id = id,
            type = type,
            label = label,
            value = value,
            createdAtMillis = 1_000L,
            updatedAtMillis = 1_000L,
            userApproved = userApproved
        )
}
