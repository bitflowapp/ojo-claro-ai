package com.ojoclaro.android.agent.contacts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ContactLookupResultTest {

    @Test
    fun notFound_preservesQuery() {
        val result = ContactLookupResult.NotFound(query = "Juan")

        assertEquals("Juan", result.query)
    }

    @Test
    fun multipleMatches_rejectsEmptyList() {
        assertFailsWith<IllegalArgumentException> {
            ContactLookupResult.MultipleMatches(emptyList())
        }
    }

    @Test
    fun contactCandidate_rejectsBlankDisplayName() {
        assertFailsWith<IllegalArgumentException> {
            ContactCandidate(
                displayName = " ",
                phoneNumber = null,
                source = ContactSource.USER_TYPED
            )
        }
    }

    @Test
    fun contactCandidate_keepsSource() {
        val candidate = ContactCandidate(
            displayName = "Juan",
            phoneNumber = "+5491111111111",
            source = ContactSource.WHATSAPP_VISIBLE_SCREEN
        )

        assertEquals(ContactSource.WHATSAPP_VISIBLE_SCREEN, candidate.source)
    }
}
