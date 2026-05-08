package com.ojoclaro.android.privacy

import com.ojoclaro.android.memory.MemoryType
import com.ojoclaro.android.maps.SafeLocationMemory
import com.ojoclaro.android.memory.SafeContactMemory
import com.ojoclaro.android.memory.UserMemory
import com.ojoclaro.android.patterns.FrequentPattern
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrivacyGuardTest {

    @Test
    fun sanitizeForSpeechTruncatesAtLimit() {
        val text = "A".repeat(2_000)

        val result = PrivacyGuard.sanitizeForSpeech(text)

        assertEquals(1_200, result.length)
    }

    @Test
    fun sanitizeForSpeechPreservesShortText() {
        val text = "Precio 350"

        assertEquals(text, PrivacyGuard.sanitizeForSpeech(text))
    }

    @Test
    fun sanitizeScreenTextTruncatesAtLimit() {
        val text = "B".repeat(3_000)

        assertEquals(2_000, PrivacyGuard.sanitizeScreenText(text).length)
    }

    @Test
    fun sanitizeScreenTextPreservesShortText() {
        val text = "WhatsApp ContactoDemo hola"

        assertEquals(text, PrivacyGuard.sanitizeScreenText(text))
    }

    @Test
    fun isSafeToReadReturnsFalseForPasswordNode() {
        assertFalse(
            PrivacyGuard.isSafeToRead(
                text = "secret123",
                isPassword = true,
                isVisible = true
            )
        )
    }

    @Test
    fun isSafeToReadReturnsFalseForInvisibleNode() {
        assertFalse(
            PrivacyGuard.isSafeToRead(
                text = "texto",
                isPassword = false,
                isVisible = false
            )
        )
    }

    @Test
    fun isSafeToReadReturnsTrueForVisibleNonPassword() {
        assertTrue(
            PrivacyGuard.isSafeToRead(
                text = "Precio 350",
                isPassword = false,
                isVisible = true
            )
        )
    }

    @Test
    fun isSafeToReadReturnsFalseForBlankText() {
        assertFalse(
            PrivacyGuard.isSafeToRead(
                text = "   ",
                isPassword = false,
                isVisible = true
            )
        )
    }

    @Test
    fun canSendToCloudFalseWhenCloudDisabled() {
        assertFalse(
            PrivacyGuard.canSendToCloud(
                allowCloud = false,
                safetyMode = true,
                hasImage = false
            )
        )
    }

    @Test
    fun canSendToCloudFalseWhenSafetyModeAndImage() {
        assertFalse(
            PrivacyGuard.canSendToCloud(
                allowCloud = true,
                safetyMode = true,
                hasImage = true
            )
        )
    }

    @Test
    fun canSendToCloudTrueWhenAllowedAndNoImage() {
        assertTrue(
            PrivacyGuard.canSendToCloud(
                allowCloud = true,
                safetyMode = true,
                hasImage = false
            )
        )
    }

    @Test
    fun isSafeMessagePayloadFalseForBlank() {
        assertFalse(PrivacyGuard.isSafeMessagePayload(""))
        assertFalse(PrivacyGuard.isSafeMessagePayload("   "))
    }

    @Test
    fun isSafeMessagePayloadTrueForNormalMessage() {
        assertTrue(PrivacyGuard.isSafeMessagePayload("estoy llegando"))
    }

    @Test
    fun isSafeMessagePayloadFalseForTooLongMessage() {
        assertFalse(PrivacyGuard.isSafeMessagePayload("C".repeat(1_001)))
    }

    @Test
    fun isSafeMessagePayloadFalseForPassword() {
        assertFalse(
            PrivacyGuard.isSafeMessagePayload("mi contraseña es secreto123")
        )
    }

    @Test
    fun isSafeMessagePayloadFalseForVerificationCode() {
        assertFalse(
            PrivacyGuard.isSafeMessagePayload("mi codigo de verificacion es 123456")
        )
    }

    @Test
    fun isSafeMessagePayloadFalseForCardNumber() {
        assertFalse(
            PrivacyGuard.isSafeMessagePayload("mi tarjeta es 4111 1111 1111 1111")
        )
    }

    @Test
    fun redactSensitiveTextLeavesNormalTextUnchanged() {
        val text = "El precio es 350 pesos."

        assertEquals(text, PrivacyGuard.redactSensitiveText(text))
    }

    @Test
    fun redactSensitiveTextRedactsVerificationCode() {
        val result = PrivacyGuard.redactSensitiveText("Tu codigo es 123456")

        assertFalse(result.contains("123456"))
        assertTrue(result.contains("omitido", ignoreCase = true))
    }

    @Test
    fun redactSensitiveTextRedactsCardNumber() {
        val result = PrivacyGuard.redactSensitiveText("Tarjeta 4111 1111 1111 1111")

        assertFalse(result.contains("4111 1111 1111 1111"))
        assertTrue(result.contains("omitido", ignoreCase = true))
    }

    @Test
    fun redactSensitiveTextRedactsPassword() {
        val result = PrivacyGuard.redactSensitiveText("mi password es secreto123")

        assertFalse(result.contains("secreto123"))
        assertTrue(result.contains("omitido", ignoreCase = true))
    }

    @Test
    fun cleanTemporaryDataDoesNotThrow() {
        PrivacyGuard.cleanTemporaryData()
    }

    @Test
    fun guaranteesAreNotEmpty() {
        assertTrue(PrivacyGuard.NO_AUTO_SEND_GUARANTEE.isNotBlank())
        assertTrue(PrivacyGuard.NO_STORE_GUARANTEE.isNotBlank())
        assertTrue(PrivacyGuard.NO_PASSWORD_GUARANTEE.isNotBlank())
    }

    @Test
    fun redactsCodes() {
        assertEquals(
            "Tu codigo es [código omitido]",
            PrivacyGuard.redactVerificationCodes("Tu codigo es 123456")
        )
    }

    @Test
    fun redactsCodesWithAccent() {
        val result = PrivacyGuard.redactVerificationCodes("Tu código es 987654")

        assertFalse(result.contains("987654"))
        assertTrue(result.contains("omitido", ignoreCase = true))
    }

    @Test
    fun redactsOtpCodes() {
        val result = PrivacyGuard.redactVerificationCodes("OTP 445566")

        assertFalse(result.contains("445566"))
        assertTrue(result.contains("omitido", ignoreCase = true))
    }

    @Test
    fun redactsCardNumbers() {
        assertEquals(
            "Tarjeta [número de tarjeta omitido]",
            PrivacyGuard.redactCardLikeNumbers("Tarjeta 4111 1111 1111 1111")
        )
    }

    @Test
    fun redactsCardNumbersWithoutSpaces() {
        val result = PrivacyGuard.redactCardLikeNumbers("Tarjeta 4111111111111111")

        assertFalse(result.contains("4111111111111111"))
        assertTrue(result.contains("omitido", ignoreCase = true))
    }

    @Test
    fun redactsPasswords() {
        val result = PrivacyGuard.redactPasswords("mi password es secreto123")

        assertFalse(result.contains("secreto123"))
        assertTrue(result.contains("omitido", ignoreCase = true))
    }

    @Test
    fun redactsSpanishPasswordsWithAccent() {
        val result = PrivacyGuard.redactPasswords("mi contraseña es secreto123")

        assertFalse(result.contains("secreto123"))
        assertTrue(result.contains("omitido", ignoreCase = true))
    }

    @Test
    fun allowsSafeMemory() {
        assertTrue(
            PrivacyGuard.canStoreMemory(
                memory(
                    type = MemoryType.USER_PREFERENCE,
                    label = "respuestas cortas",
                    value = "respuestas cortas"
                )
            )
        )
    }

    @Test
    fun doesNotAllowMemoryWithoutUserApproval() {
        assertFalse(
            PrivacyGuard.canStoreMemory(
                memory(
                    type = MemoryType.USER_PREFERENCE,
                    label = "respuestas cortas",
                    value = "respuestas cortas",
                    userApproved = false
                )
            )
        )
    }

    @Test
    fun doesNotAllowMemoryMarkedSensitive() {
        assertFalse(
            PrivacyGuard.canStoreMemory(
                memory(
                    type = MemoryType.USER_PREFERENCE,
                    label = "banco",
                    value = "mi banco",
                    isSensitive = true
                )
            )
        )
    }

    @Test
    fun doesNotAllowSensitiveMemory() {
        assertFalse(
            PrivacyGuard.canStoreMemory(
                memory(
                    type = MemoryType.USER_PREFERENCE,
                    label = "password",
                    value = "mi password es secreto123"
                )
            )
        )
    }

    @Test
    fun doesNotAllowMemoryWithVerificationCode() {
        assertFalse(
            PrivacyGuard.canStoreMemory(
                memory(
                    type = MemoryType.USER_PREFERENCE,
                    label = "codigo",
                    value = "codigo de verificacion 123456"
                )
            )
        )
    }

    @Test
    fun doesNotAllowMemoryWithCardNumber() {
        assertFalse(
            PrivacyGuard.canStoreMemory(
                memory(
                    type = MemoryType.USER_PREFERENCE,
                    label = "tarjeta",
                    value = "4111 1111 1111 1111"
                )
            )
        )
    }

    @Test
    fun allowsApprovedSafeContactMemory() {
        assertTrue(
            PrivacyGuard.canStoreMemory(
                memory(
                    type = MemoryType.TRUSTED_CONTACT,
                    label = "ContactoDemo",
                    value = SafeContactMemory.phoneValue("2991234567")!!
                )
            )
        )
    }

    @Test
    fun doesNotAllowSafeContactWithoutApproval() {
        assertFalse(
            PrivacyGuard.canStoreMemory(
                memory(
                    type = MemoryType.TRUSTED_CONTACT,
                    label = "ContactoDemo",
                    value = SafeContactMemory.phoneValue("2991234567")!!,
                    userApproved = false
                )
            )
        )
    }

    @Test
    fun allowsApprovedLocationAliasMemory() {
        assertTrue(
            PrivacyGuard.canStoreMemory(
                memory(
                    type = MemoryType.LOCATION_ALIAS,
                    label = "casa",
                    value = SafeLocationMemory.value(-38.95, -68.06, 20f)
                )
            )
        )
    }

    @Test
    fun allowsSafePattern() {
        assertTrue(
            PrivacyGuard.canStorePattern(
                pattern(
                    commandType = "READ_TEXT",
                    normalizedCommand = "leer texto",
                    isSensitive = false,
                    userApprovedForSuggestions = true
                )
            )
        )
    }

    @Test
    fun doesNotAllowSensitivePattern() {
        assertFalse(
            PrivacyGuard.canStorePattern(
                pattern(
                    commandType = "COMPOSE_WHATSAPP_MESSAGE",
                    normalizedCommand = "compose_whatsapp_message",
                    isSensitive = true,
                    userApprovedForSuggestions = false
                )
            )
        )
    }

    @Test
    fun doesNotAllowPatternWithoutUserApprovalForSuggestions() {
        assertFalse(
            PrivacyGuard.canStorePattern(
                pattern(
                    commandType = "READ_TEXT",
                    normalizedCommand = "leer texto",
                    isSensitive = false,
                    userApprovedForSuggestions = false
                )
            )
        )
    }

    @Test
    fun doesNotAllowPatternWithSensitiveCommandContent() {
        assertFalse(
            PrivacyGuard.canStorePattern(
                pattern(
                    commandType = "COMPOSE_WHATSAPP_MESSAGE",
                    normalizedCommand = "mandale a ContactoDemo codigo 123456",
                    isSensitive = false,
                    userApprovedForSuggestions = true
                )
            )
        )
    }

    private fun memory(
        type: MemoryType,
        label: String,
        value: String,
        isSensitive: Boolean = false,
        userApproved: Boolean = true
    ): UserMemory =
        UserMemory(
            id = "m1",
            type = type,
            label = label,
            value = value,
            createdAtMillis = 1L,
            updatedAtMillis = 1L,
            expiresAtMillis = null,
            isSensitive = isSensitive,
            userApproved = userApproved
        )

    private fun pattern(
        commandType: String,
        normalizedCommand: String,
        isSensitive: Boolean,
        userApprovedForSuggestions: Boolean
    ): FrequentPattern =
        FrequentPattern(
            id = "p1",
            commandType = commandType,
            normalizedCommand = normalizedCommand,
            count = 1,
            firstSeenMillis = 1L,
            lastSeenMillis = 1L,
            lastAppPackage = "com.whatsapp",
            isSensitive = isSensitive,
            userApprovedForSuggestions = userApprovedForSuggestions
        )
}
