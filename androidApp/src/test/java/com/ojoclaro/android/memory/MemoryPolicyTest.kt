package com.ojoclaro.android.memory

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import com.ojoclaro.android.maps.SafeLocationMemory

class MemoryPolicyTest {

    @Test
    fun allowsTrustedContact() {
        assertTrue(
            MemoryPolicy.canStore(
                memory(
                    type = MemoryType.TRUSTED_CONTACT,
                    value = "Sofi"
                )
            )
        )
    }

    @Test
    fun allowsPreference() {
        assertTrue(
            MemoryPolicy.canStore(
                memory(
                    type = MemoryType.USER_PREFERENCE,
                    value = "respuestas cortas"
                )
            )
        )
    }

    @Test
    fun allowsFrequentCommand() {
        assertTrue(
            MemoryPolicy.canStore(
                memory(
                    type = MemoryType.FREQUENT_COMMAND,
                    value = "leer texto"
                )
            )
        )
    }

    @Test
    fun allowsSafetyRule() {
        assertTrue(
            MemoryPolicy.canStore(
                memory(
                    type = MemoryType.SAFETY_RULE,
                    value = "advertirme si aparece transferencia"
                )
            )
        )
    }

    @Test
    fun allowsWarningKeyword() {
        assertTrue(
            MemoryPolicy.canStore(
                memory(
                    type = MemoryType.WARNING_KEYWORD,
                    value = "transferencia"
                )
            )
        )
    }

    @Test
    fun blocksMemoryWithoutUserApproval() {
        assertFalse(
            MemoryPolicy.canStore(
                memory(
                    type = MemoryType.USER_PREFERENCE,
                    value = "respuestas cortas",
                    userApproved = false
                )
            )
        )
    }

    @Test
    fun blocksMemoryMarkedSensitive() {
        assertFalse(
            MemoryPolicy.canStore(
                memory(
                    type = MemoryType.USER_PREFERENCE,
                    value = "mi banco",
                    isSensitive = true
                )
            )
        )
    }

    @Test
    fun blocksBlankLabel() {
        assertFalse(
            MemoryPolicy.canStore(
                memory(
                    type = MemoryType.USER_PREFERENCE,
                    label = "",
                    value = "respuestas cortas"
                )
            )
        )
    }

    @Test
    fun blocksBlankValue() {
        assertFalse(
            MemoryPolicy.canStore(
                memory(
                    type = MemoryType.USER_PREFERENCE,
                    label = "preferencia",
                    value = ""
                )
            )
        )
    }

    @Test
    fun blocksTooLongValue() {
        val longValue = "A".repeat(300)

        assertFalse(
            MemoryPolicy.canStore(
                memory(
                    type = MemoryType.USER_PREFERENCE,
                    value = longValue
                )
            )
        )
    }

    @Test
    fun blocksTooManyLines() {
        val multiline = """
            línea uno
            línea dos
            línea tres
            línea cuatro
        """.trimIndent()

        assertFalse(
            MemoryPolicy.canStore(
                memory(
                    type = MemoryType.USER_PREFERENCE,
                    value = multiline
                )
            )
        )
    }

    @Test
    fun blocksFullChat() {
        assertFalse(
            MemoryPolicy.canStore(
                memory(
                    type = MemoryType.USER_PREFERENCE,
                    value = "chat completo con mensajes privados"
                )
            )
        )
    }

    @Test
    fun blocksFullConversationWithAccent() {
        assertFalse(
            MemoryPolicy.canStore(
                memory(
                    type = MemoryType.USER_PREFERENCE,
                    value = "conversación completa con Sofi"
                )
            )
        )
    }

    @Test
    fun blocksFullScreenText() {
        assertFalse(
            MemoryPolicy.canStore(
                memory(
                    type = MemoryType.USER_PREFERENCE,
                    value = "texto completo de la pantalla"
                )
            )
        )
    }

    @Test
    fun blocksFullOcr() {
        assertFalse(
            MemoryPolicy.canStore(
                memory(
                    type = MemoryType.USER_PREFERENCE,
                    value = "ocr completo de una pantalla privada"
                )
            )
        )
    }

    @Test
    fun blocksBase64Image() {
        assertFalse(
            MemoryPolicy.canStore(
                memory(
                    type = MemoryType.USER_PREFERENCE,
                    value = "imagen completa en base64"
                )
            )
        )
    }

    @Test
    fun blocksFullAudio() {
        assertFalse(
            MemoryPolicy.canStore(
                memory(
                    type = MemoryType.USER_PREFERENCE,
                    value = "audio completo de WhatsApp"
                )
            )
        )
    }

    @Test
    fun blocksTwoFactorCode() {
        assertFalse(
            MemoryPolicy.canStore(
                memory(
                    type = MemoryType.USER_PREFERENCE,
                    value = "codigo de verificacion 123456"
                )
            )
        )
    }

    @Test
    fun blocksOtpCode() {
        assertFalse(
            MemoryPolicy.canStore(
                memory(
                    type = MemoryType.USER_PREFERENCE,
                    value = "otp 987654"
                )
            )
        )
    }

    @Test
    fun blocksCodeWithAccent() {
        assertFalse(
            MemoryPolicy.canStore(
                memory(
                    type = MemoryType.USER_PREFERENCE,
                    value = "código de verificación 123456"
                )
            )
        )
    }

    @Test
    fun blocksCardNumber() {
        assertFalse(
            MemoryPolicy.canStore(
                memory(
                    type = MemoryType.USER_PREFERENCE,
                    value = "4111 1111 1111 1111"
                )
            )
        )
    }

    @Test
    fun blocksCardNumberWithoutSpaces() {
        assertFalse(
            MemoryPolicy.canStore(
                memory(
                    type = MemoryType.USER_PREFERENCE,
                    value = "4111111111111111"
                )
            )
        )
    }

    @Test
    fun blocksPassword() {
        assertFalse(
            MemoryPolicy.canStore(
                memory(
                    type = MemoryType.USER_PREFERENCE,
                    value = "mi password es secreto123"
                )
            )
        )
    }

    @Test
    fun blocksSpanishPasswordWithAccent() {
        assertFalse(
            MemoryPolicy.canStore(
                memory(
                    type = MemoryType.USER_PREFERENCE,
                    value = "mi contraseña es secreto123"
                )
            )
        )
    }

    @Test
    fun blocksPin() {
        assertFalse(
            MemoryPolicy.canStore(
                memory(
                    type = MemoryType.USER_PREFERENCE,
                    value = "mi pin es 1234"
                )
            )
        )
    }

    @Test
    fun blocksApiKey() {
        assertFalse(
            MemoryPolicy.canStore(
                memory(
                    type = MemoryType.USER_PREFERENCE,
                    value = "api key es placeholder"
                )
            )
        )
    }

    @Test
    fun blocksToken() {
        assertFalse(
            MemoryPolicy.canStore(
                memory(
                    type = MemoryType.USER_PREFERENCE,
                    value = "token: abc123"
                )
            )
        )
    }

    @Test
    fun blocksDni() {
        assertFalse(
            MemoryPolicy.canStore(
                memory(
                    type = MemoryType.USER_PREFERENCE,
                    value = "dni 12345678"
                )
            )
        )
    }

    @Test
    fun blocksCuil() {
        assertFalse(
            MemoryPolicy.canStore(
                memory(
                    type = MemoryType.USER_PREFERENCE,
                    value = "cuil 20123456789"
                )
            )
        )
    }

    @Test
    fun blocksCuit() {
        assertFalse(
            MemoryPolicy.canStore(
                memory(
                    type = MemoryType.USER_PREFERENCE,
                    value = "cuit 30123456789"
                )
            )
        )
    }

    @Test
    fun blocksPersonalAddress() {
        assertFalse(
            MemoryPolicy.canStore(
                memory(
                    type = MemoryType.USER_PREFERENCE,
                    value = "mi direccion es calle falsa 123"
                )
            )
        )
    }

    @Test
    fun blocksWhereUserLives() {
        assertFalse(
            MemoryPolicy.canStore(
                memory(
                    type = MemoryType.USER_PREFERENCE,
                    value = "vivo en Neuquén calle falsa 123"
                )
            )
        )
    }

    @Test
    fun blocksCbu() {
        assertFalse(
            MemoryPolicy.canStore(
                memory(
                    type = MemoryType.USER_PREFERENCE,
                    value = "mi cbu es 1234567890123456789012"
                )
            )
        )
    }

    @Test
    fun blocksCvu() {
        assertFalse(
            MemoryPolicy.canStore(
                memory(
                    type = MemoryType.USER_PREFERENCE,
                    value = "mi cvu es 1234567890123456789012"
                )
            )
        )
    }

    @Test
    fun blocksBankAlias() {
        assertFalse(
            MemoryPolicy.canStore(
                memory(
                    type = MemoryType.USER_PREFERENCE,
                    value = "alias bancario marco.luna.mp"
                )
            )
        )
    }

    @Test
    fun normalizeRemovesAccentsAndExtraSpaces() {
        val normalized = MemoryPolicy.normalize("  Código   de   Verificación  ")

        assertTrue(normalized == "codigo de verificacion")
    }

    @Test
    fun containsCardLikeNumberDetectsSeparatedDigits() {
        assertTrue(
            MemoryPolicy.containsCardLikeNumber("4111 1111 1111 1111")
        )
    }

    @Test
    fun containsCardLikeNumberDetectsDashedDigits() {
        assertTrue(
            MemoryPolicy.containsCardLikeNumber("4111-1111-1111-1111")
        )
    }

    @Test
    fun containsCardLikeNumberIgnoresShortNumbers() {
        assertFalse(
            MemoryPolicy.containsCardLikeNumber("123456")
        )
    }

    @Test
    fun allowsApprovedSafeContactWithPhone() {
        assertTrue(
            MemoryPolicy.canStore(
                memory(
                    type = MemoryType.TRUSTED_CONTACT,
                    label = "Sofi",
                    value = SafeContactMemory.phoneValue("2991234567")!!
                )
            )
        )
    }

    @Test
    fun allowsApprovedEmergencyContact() {
        assertTrue(
            MemoryPolicy.canStore(
                memory(
                    type = MemoryType.EMERGENCY_CONTACT,
                    label = "mamá",
                    value = SafeContactMemory.contactValue("mamá")
                )
            )
        )
    }

    @Test
    fun blocksInvalidContactPhone() {
        assertFalse(
            MemoryPolicy.canStore(
                memory(
                    type = MemoryType.TRUSTED_CONTACT,
                    label = "Sofi",
                    value = "phone:123"
                )
            )
        )
    }

    @Test
    fun blocksCardLikeContactPhone() {
        assertFalse(
            MemoryPolicy.canStore(
                memory(
                    type = MemoryType.TRUSTED_CONTACT,
                    label = "Sofi",
                    value = "phone:4111111111111111"
                )
            )
        )
    }

    @Test
    fun blocksDniCbuCvuInContactMemory() {
        listOf(
            "dni 12345678",
            "cbu 1234567890123456789012",
            "cvu 1234567890123456789012"
        ).forEach { value ->
            assertFalse(
                MemoryPolicy.canStore(
                    memory(
                        type = MemoryType.TRUSTED_CONTACT,
                        label = "Sofi",
                        value = value
                    )
                ),
                value
            )
        }
    }

    @Test
    fun blocksSafeContactWithoutUserApproval() {
        assertFalse(
            MemoryPolicy.canStore(
                memory(
                    type = MemoryType.TRUSTED_CONTACT,
                    label = "Sofi",
                    value = SafeContactMemory.phoneValue("2991234567")!!,
                    userApproved = false
                )
            )
        )
    }

    @Test
    fun allowsApprovedLocationAlias() {
        assertTrue(
            MemoryPolicy.canStore(
                memory(
                    type = MemoryType.LOCATION_ALIAS,
                    label = "casa",
                    value = SafeLocationMemory.value(-38.95, -68.06, 25f)
                )
            )
        )
    }

    @Test
    fun blocksLocationAliasWithoutUserApproval() {
        assertFalse(
            MemoryPolicy.canStore(
                memory(
                    type = MemoryType.LOCATION_ALIAS,
                    label = "casa",
                    value = SafeLocationMemory.value(-38.95, -68.06, 25f),
                    userApproved = false
                )
            )
        )
    }

    @Test
    fun blocksBlankLocationAlias() {
        assertFalse(
            MemoryPolicy.canStore(
                memory(
                    type = MemoryType.LOCATION_ALIAS,
                    label = "",
                    value = SafeLocationMemory.value(-38.95, -68.06, 25f)
                )
            )
        )
    }

    @Test
    fun blocksSensitiveLocationAlias() {
        assertFalse(
            MemoryPolicy.canStore(
                memory(
                    type = MemoryType.LOCATION_ALIAS,
                    label = "banco",
                    value = SafeLocationMemory.value(-38.95, -68.06, 25f)
                )
            )
        )
    }

    private fun memory(
        type: MemoryType,
        value: String,
        label: String = value,
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
}
