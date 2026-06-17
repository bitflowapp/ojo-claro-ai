package com.ojoclaro.android.memory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PersonalAgentMemoryTest {

    @Test
    fun approvedMemoryIsSummarizedLocally() {
        val snapshot = PersonalMemorySnapshot(
            listOf(
                memory(
                    id = "contact-1",
                    type = PersonalMemoryType.CONTACT,
                    label = "ContactoDemo",
                    value = "ContactoDemo es mi novia",
                    userApproved = true
                )
            )
        )
        assertEquals(listOf("ContactoDemo"), snapshot.labelsFor(PersonalMemoryType.CONTACT))
        assertTrue(snapshot.summary().contains("ContactoDemo"))
    }

    @Test
    fun sensitiveMemoryIsRejectedByPolicyAndNotPersisted() {
        assertFalse(PersonalMemoryPolicy.canStore(
            memory(
                id = "secret-1",
                type = PersonalMemoryType.PREFERENCE,
                label = "banco",
                value = "mi clave es 1234",
                userApproved = true
            )
        ))
    }

    @Test
    fun snapshotReflectsMultipleMemoryTypes() {
        val snapshot = PersonalMemorySnapshot(
            listOf(
                memory(
                    id = "contact-1",
                    type = PersonalMemoryType.CONTACT,
                    label = "Marco",
                    value = "Marco es contacto de confianza",
                    userApproved = true
                ),
                memory(
                    id = "place-1",
                    type = PersonalMemoryType.PLACE,
                    label = "laburo",
                    value = "laburo",
                    userApproved = true
                )
            )
        )

        assertEquals(listOf("Marco"), snapshot.labelsFor(PersonalMemoryType.CONTACT))
        assertEquals(listOf("laburo"), snapshot.labelsFor(PersonalMemoryType.PLACE))
        assertTrue(snapshot.summary().contains("Marco"))
        assertTrue(snapshot.summary().contains("laburo"))
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
