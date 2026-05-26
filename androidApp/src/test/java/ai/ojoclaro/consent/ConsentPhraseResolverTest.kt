package ai.ojoclaro.consent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConsentPhraseResolverTest {

    // --- Edge cases ---

    @Test
    fun nullTemplateReturnsNull() {
        assertNull(ConsentPhraseResolver.resolve(template = null))
    }

    @Test
    fun emptyTemplateReturnsNull() {
        assertNull(ConsentPhraseResolver.resolve(template = ""))
    }

    @Test
    fun blankTemplateReturnsNull() {
        assertNull(ConsentPhraseResolver.resolve(template = "   "))
    }

    @Test
    fun unknownTemplateReturnsNull() {
        assertNull(ConsentPhraseResolver.resolve(template = "WHATEVER_NEW_PHRASE"))
    }

    // --- Static phrases: each known ID resolves to a non-blank string ---

    @Test
    fun readVisibleMessageResolves() {
        val text = ConsentPhraseResolver.resolve("READ_VISIBLE_MESSAGE")
        assertNotNull(text)
        assertTrue(text.isNotBlank())
    }

    @Test
    fun readPasswordFieldRejectedResolves() {
        val text = ConsentPhraseResolver.resolve("READ_PASSWORD_FIELD_REJECTED")
        assertNotNull(text)
        assertTrue(text.isNotBlank())
    }

    @Test
    fun readBankingScreenResolves() {
        val text = ConsentPhraseResolver.resolve("READ_BANKING_SCREEN")
        assertNotNull(text)
        assertTrue(text.isNotBlank())
    }

    @Test
    fun protectedAppRejectedResolves() {
        val text = ConsentPhraseResolver.resolve("PROTECTED_APP_REJECTED")
        assertNotNull(text)
        assertTrue(text.isNotBlank())
    }

    @Test
    fun saveMemoryGenericResolves() {
        val text = ConsentPhraseResolver.resolve("SAVE_MEMORY_GENERIC")
        assertNotNull(text)
        assertTrue(text.isNotBlank())
    }

    @Test
    fun clearMemoryConfirmResolves() {
        val text = ConsentPhraseResolver.resolve("CLEAR_MEMORY_CONFIRM")
        assertNotNull(text)
        assertTrue(text.isNotBlank())
    }

    @Test
    fun confirmRepromptResolves() {
        val text = ConsentPhraseResolver.resolve("CONFIRM_REPROMPT")
        assertNotNull(text)
        assertTrue(text.isNotBlank())
    }

    @Test
    fun expiredActionResolves() {
        val text = ConsentPhraseResolver.resolve("EXPIRED_ACTION")
        assertNotNull(text)
        assertTrue(text.isNotBlank())
    }

    @Test
    fun noPendingConfirmationResolves() {
        val text = ConsentPhraseResolver.resolve("NO_PENDING_CONFIRMATION")
        assertNotNull(text)
        assertTrue(text.isNotBlank())
    }

    @Test
    fun actionCancelledResolves() {
        val text = ConsentPhraseResolver.resolve("ACTION_CANCELLED")
        assertNotNull(text)
        assertTrue(text.isNotBlank())
    }

    // --- Templated phrases with params present ---

    @Test
    fun callContactConfirmIncludesContact() {
        val text = ConsentPhraseResolver.resolve(
            "CALL_CONTACT_CONFIRM",
            mapOf("contact_query" to "mamá")
        )
        assertNotNull(text)
        assertTrue(text.contains("mamá"), "expected 'mamá' in: $text")
    }

    @Test
    fun rideAppOpenDisclaimerTitleCasesApp() {
        val text = ConsentPhraseResolver.resolve(
            "RIDE_APP_OPEN_DISCLAIMER",
            mapOf("preferred_app" to "uber")
        )
        assertNotNull(text)
        assertTrue(text.contains("Uber"), "expected 'Uber' in: $text")
    }

    @Test
    fun navigateToDestinationConfirmIncludesDestination() {
        val text = ConsentPhraseResolver.resolve(
            "NAVIGATE_TO_DESTINATION_CONFIRM",
            mapOf("destination" to "el hospital")
        )
        assertNotNull(text)
        assertTrue(text.contains("hospital"), "expected 'hospital' in: $text")
    }

    @Test
    fun saveContactConfirmIncludesName() {
        val text = ConsentPhraseResolver.resolve(
            "SAVE_CONTACT_CONFIRM",
            mapOf("name" to "Tía Carla")
        )
        assertNotNull(text)
        assertTrue(text.contains("Tía Carla"), "expected 'Tía Carla' in: $text")
    }

    @Test
    fun saveContactPhoneConfirmIncludesContact() {
        val text = ConsentPhraseResolver.resolve(
            "SAVE_CONTACT_PHONE_CONFIRM",
            mapOf("contact_query" to "mamá")
        )
        assertNotNull(text)
        assertTrue(text.contains("mamá"), "expected 'mamá' in: $text")
    }

    @Test
    fun deleteContactConfirmIncludesContact() {
        val text = ConsentPhraseResolver.resolve(
            "DELETE_CONTACT_CONFIRM",
            mapOf("contact_query" to "Marco")
        )
        assertNotNull(text)
        assertTrue(text.contains("Marco"), "expected 'Marco' in: $text")
    }

    // --- Templated phrases with missing / blank params ---
    // Documented behavior: resolver returns null when a required param is missing.
    // The intent engine should never emit a templated ID without its params; if it
    // does, the orchestrator falls back to its own error path (NO canonical phrase
    // and NO text with empty %s holes).

    @Test
    fun saveContactConfirmWithNullNameReturnsNull() {
        val text = ConsentPhraseResolver.resolve(
            "SAVE_CONTACT_CONFIRM",
            mapOf("name" to null)
        )
        assertNull(text)
    }

    @Test
    fun saveContactConfirmWithMissingNameReturnsNull() {
        val text = ConsentPhraseResolver.resolve(
            "SAVE_CONTACT_CONFIRM",
            emptyMap()
        )
        assertNull(text)
    }

    @Test
    fun callContactConfirmWithBlankContactReturnsNull() {
        val text = ConsentPhraseResolver.resolve(
            "CALL_CONTACT_CONFIRM",
            mapOf("contact_query" to "   ")
        )
        assertNull(text)
    }

    @Test
    fun rideAppOpenDisclaimerWithMissingAppReturnsNull() {
        val text = ConsentPhraseResolver.resolve(
            "RIDE_APP_OPEN_DISCLAIMER",
            emptyMap()
        )
        assertNull(text)
    }

    // --- Sanity check: contains expected static prefixes ---

    @Test
    fun callContactConfirmStartsWithDialerPhrase() {
        val text = ConsentPhraseResolver.resolve(
            "CALL_CONTACT_CONFIRM",
            mapOf("contact_query" to "mamá")
        )
        assertNotNull(text)
        assertTrue(text.startsWith("Abro el marcador"), "got: $text")
    }

    @Test
    fun rideAppOpenDisclaimerMentionsNoAutoRide() {
        val text = ConsentPhraseResolver.resolve(
            "RIDE_APP_OPEN_DISCLAIMER",
            mapOf("preferred_app" to "Cabify")
        )
        assertNotNull(text)
        assertTrue(text.contains("No voy a pedir el viaje"), "got: $text")
    }

    @Test
    fun confirmRepromptMentionsValidWords() {
        val text = ConsentPhraseResolver.resolve("CONFIRM_REPROMPT")
        assertNotNull(text)
        assertTrue(text.contains("confirmar"), "got: $text")
    }

    // --- Defensive: numeric / non-string params should still work via toString() ---

    @Test
    fun stringParamsAreCoercedFromAny() {
        // The resolver accepts Map<String, Any?>. A non-string value (e.g. an Int)
        // is coerced via toString(). This guards against orchestrators that wrap
        // params in typed values.
        val text = ConsentPhraseResolver.resolve(
            "SAVE_CONTACT_PHONE_CONFIRM",
            mapOf("contact_query" to 42)
        )
        assertNotNull(text)
        assertTrue(text.contains("42"), "got: $text")
    }

    @Test
    fun defaultParamsMapResolvesStaticPhrases() {
        // Static phrases should resolve without passing params.
        assertEquals(
            ConsentPhraseResolver.resolve("CONFIRM_REPROMPT"),
            ConsentPhraseResolver.resolve("CONFIRM_REPROMPT", emptyMap())
        )
    }
}
