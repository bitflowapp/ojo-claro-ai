package com.ojoclaro.android.external

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommandRouterTest {

    private val router = CommandRouter()

    @Test
    fun detectsOpenWhatsAppCommand() {
        val command = router.parse("abrí WhatsApp")

        assertEquals(ExternalCommandType.OPEN_WHATSAPP, command.type)
    }

    @Test
    fun detectsOpenWhatsAppWithoutAccent() {
        val command = router.parse("abrir whatsapp")

        assertEquals(ExternalCommandType.OPEN_WHATSAPP, command.type)
    }

    @Test
    fun detectsExplicitOpenWhatsAppPrincipalCommand() {
        assertEquals(ExternalCommandType.OPEN_WHATSAPP, router.parse("abrí WhatsApp principal").type)
        assertEquals(ExternalCommandType.OPEN_WHATSAPP, router.parse("solo abrí wp").type)
        assertEquals(ExternalCommandType.OPEN_WHATSAPP, router.parse("abrí wsp solamente").type)
    }

    @Test
    fun detectsComposeMessageWithColon() {
        val command = router.parse("Mandale a Sofi: estoy llegando")

        assertEquals(ExternalCommandType.COMPOSE_WHATSAPP_MESSAGE, command.type)
        assertEquals("Sofi", command.contactName)
        assertEquals("estoy llegando", command.messageText)
    }

    @Test
    fun detectsComposeMessageWithQue() {
        val command = router.parse("escribile a mamá que estoy bien")

        assertEquals(ExternalCommandType.COMPOSE_WHATSAPP_MESSAGE, command.type)
        assertEquals("mamá", command.contactName)
        assertEquals("estoy bien", command.messageText)
    }

    @Test
    fun trimsComposeMessageContactAndMessage() {
        val command = router.parse("Mandale a Sofi   :    estoy llegando   ")

        assertEquals(ExternalCommandType.COMPOSE_WHATSAPP_MESSAGE, command.type)
        assertEquals("Sofi", command.contactName)
        assertEquals("estoy llegando", command.messageText)
    }

    @Test
    fun parsesNaturalWhatsAppMessageCommands() {
        assertCompose("mandale un mensaje a Sofi que estoy llegando", "Sofi", "estoy llegando")
        assertCompose("mandale un WhatsApp a Sofi que estoy llegando", "Sofi", "estoy llegando")
        assertCompose("escribile a Sofi por WhatsApp que estoy llegando", "Sofi", "estoy llegando")
        assertCompose("decile a Sofi que estoy llegando", "Sofi", "estoy llegando")
        assertCompose("decir a Sofi que estoy llegando", "Sofi", "estoy llegando")
        assertCompose("escribir a mamá que estoy bien", "mamá", "estoy bien")
        assertCompose("en WhatsApp mandale a Sofi que estoy llegando", "Sofi", "estoy llegando")
        assertCompose("abrí WhatsApp y mandale a Sofi que estoy llegando", "Sofi", "estoy llegando")
        assertCompose("mandale a mi novia que estoy llegando", "mi novia", "estoy llegando")
        assertCompose("mandale mensaje a mamá diciendo que estoy bien", "mamá", "estoy bien")
    }

    @Test
    fun parsesWhatsAppAliasesForOpenCommand() {
        listOf(
            "abrí whats app",
            "abrí wp",
            "abrí wsp",
            "abrí wpp",
            "abrí wasap",
            "abrí guasap",
            "abrí watsap",
            "abrí whasap"
        ).forEach { phrase ->
            assertEquals(
                ExternalCommandType.OPEN_WHATSAPP,
                router.parse(phrase).type,
                phrase
            )
        }
    }

    @Test
    fun messageIntentDoesNotFallBackToOpenWhatsApp() {
        val missingContactRoute = router.route("mandale un mensaje")
        val missingContact = assertIs<CommandResult.Failed>(missingContactRoute.result)

        assertEquals(ExternalCommandType.COMPOSE_WHATSAPP_MESSAGE, missingContactRoute.command.type)
        assertNull(missingContactRoute.pendingConfirmation)
        assertTrue(missingContact.spokenText.contains("A quién"))

        val missingMessageRoute = router.route("mandale a Sofi")
        val missingMessage = assertIs<CommandResult.Failed>(missingMessageRoute.result)

        assertEquals(ExternalCommandType.COMPOSE_WHATSAPP_MESSAGE, missingMessageRoute.command.type)
        assertNull(missingMessageRoute.pendingConfirmation)
        assertTrue(missingMessage.spokenText.contains("Qué mensaje"))
    }

    @Test
    fun detectsReadVisibleScreenCommands() {
        assertEquals(
            ExternalCommandType.READ_VISIBLE_SCREEN,
            router.parse("leeme este mensaje").type
        )

        assertEquals(
            ExternalCommandType.READ_VISIBLE_SCREEN,
            router.parse("qué dice la pantalla").type
        )

        assertEquals(
            ExternalCommandType.READ_VISIBLE_SCREEN,
            router.parse("leer pantalla").type
        )
    }

    @Test
    fun detectsCancelAndStrictConfirmCommands() {
        assertEquals(
            ExternalCommandType.CANCEL_PENDING_ACTION,
            router.parse("cancelar").type
        )

        assertEquals(
            ExternalCommandType.CONFIRM_PENDING_ACTION,
            router.parse("confirmar").type
        )

        assertEquals(
            ExternalCommandType.CONFIRM_PENDING_ACTION,
            router.parse("confirmo").type
        )

        assertEquals(
            ExternalCommandType.CONFIRM_PENDING_ACTION,
            router.parse("aceptar").type
        )
    }

    @Test
    fun siAndDaleDoNotConfirmSensitiveActions() {
        assertNotEquals(
            ExternalCommandType.CONFIRM_PENDING_ACTION,
            router.parse("sí").type
        )

        assertNotEquals(
            ExternalCommandType.CONFIRM_PENDING_ACTION,
            router.parse("si").type
        )

        assertNotEquals(
            ExternalCommandType.CONFIRM_PENDING_ACTION,
            router.parse("dale").type
        )
    }

    @Test
    fun detectsRememberMemoryCommands() {
        assertEquals(
            ExternalCommandType.REMEMBER_MEMORY,
            router.parse("recordá que Sofi es contacto de confianza").type
        )

        assertEquals(
            ExternalCommandType.REMEMBER_MEMORY,
            router.parse("recordame que prefiero respuestas cortas").type
        )

        assertEquals(
            ExternalCommandType.REMEMBER_MEMORY,
            router.parse("recuerda que Sofi es contacto de confianza").type
        )

        assertEquals(
            ExternalCommandType.REMEMBER_MEMORY,
            router.parse("acordate que prefiero respuestas cortas").type
        )

        assertEquals(
            ExternalCommandType.REMEMBER_MEMORY,
            router.parse("guarda que si aparece transferencia me avises").type
        )

        assertEquals(
            ExternalCommandType.REMEMBER_MEMORY,
            router.parse("quiero que recuerdes que prefiero respuestas cortas").type
        )
    }

    @Test
    fun detectsMemoryListCommand() {
        assertEquals(
            ExternalCommandType.LIST_MEMORY,
            router.parse("qué recordás de mí").type
        )

        assertEquals(
            ExternalCommandType.LIST_MEMORY,
            router.parse("qué tenés guardado").type
        )
    }

    @Test
    fun detectsForgetLastMemoryCommand() {
        assertEquals(
            ExternalCommandType.FORGET_LAST_MEMORY,
            router.parse("olvidá eso").type
        )

        assertEquals(
            ExternalCommandType.FORGET_LAST_MEMORY,
            router.parse("borrá eso").type
        )
    }

    @Test
    fun detectsClearMemoryCommand() {
        assertEquals(
            ExternalCommandType.CLEAR_MEMORY,
            router.parse("borrá tu memoria").type
        )

        assertEquals(
            ExternalCommandType.CLEAR_MEMORY,
            router.parse("limpia tu memoria").type
        )

        assertEquals(
            ExternalCommandType.CLEAR_MEMORY,
            router.parse("vaciar memoria").type
        )
    }

    @Test
    fun returnsNotSupportedForUnknownExternalAction() {
        val route = router.route("hacé algo raro")

        val result = assertIs<CommandResult.NotSupported>(route.result)
        assertEquals(CommandRouter.unsupportedText, result.spokenText)
    }

    @Test
    fun composeMessageNeedsConfirmation() {
        val route = router.route(
            rawInput = "mandale a Sofi: estoy llegando",
            nowMillis = 10_000L
        )

        val result = assertIs<CommandResult.NeedsConfirmation>(route.result)
        val pending = assertNotNull(route.pendingConfirmation)

        assertEquals("external-confirmation-10000", result.confirmationId)
        assertEquals(result.confirmationId, pending.id)
        assertEquals(ExternalCommandType.COMPOSE_WHATSAPP_MESSAGE, pending.command.type)
        assertTrue(result.spokenText.contains("Sofi"))
        assertTrue(result.spokenText.contains("estoy llegando"))
        assertTrue(result.spokenText.contains("No lo envío automáticamente"))
        assertTrue(result.spokenText.contains("decí: confirmar"))
    }

    @Test
    fun naturalComposeMessageNeedsConfirmation() {
        val route = router.route(
            rawInput = "mandale un mensaje a Sofi que estoy llegando",
            nowMillis = 10_000L
        )

        val result = assertIs<CommandResult.NeedsConfirmation>(route.result)
        val pending = assertNotNull(route.pendingConfirmation)

        assertEquals(ExternalCommandType.COMPOSE_WHATSAPP_MESSAGE, pending.command.type)
        assertEquals("Sofi", pending.command.contactName)
        assertEquals("estoy llegando", pending.command.messageText)
        assertTrue(result.spokenText.contains("No lo envío automáticamente"))
        assertTrue(result.spokenText.contains("decí: confirmar"))
    }

    @Test
    fun sensitiveMessagePayloadIsBlockedBeforeConfirmation() {
        val route = router.route("mandale a Sofi que mi contraseña es 1234")

        val result = assertIs<CommandResult.Failed>(route.result)
        assertNull(route.pendingConfirmation)
        assertTrue(result.spokenText.contains("datos sensibles"))
    }

    @Test
    fun composeConfirmationTruncatesLongMessageForSpeech() {
        val longMessage = "mensaje ".repeat(80)
        val route = router.route(
            rawInput = "mandale a Sofi: $longMessage",
            nowMillis = 10_000L
        )

        val result = assertIs<CommandResult.NeedsConfirmation>(route.result)

        assertTrue(result.spokenText.contains("No lo envío automáticamente"))
        assertTrue(result.spokenText.length < 420)
        assertTrue(result.spokenText.contains("…"))
    }

    @Test
    fun confirmPendingActionReturnsOriginalCommand() {
        val pending = router.route(
            rawInput = "mandale a Sofi: estoy llegando",
            nowMillis = 10_000L
        ).pendingConfirmation

        val route = router.route(
            rawInput = "confirmar",
            pendingConfirmation = pending,
            nowMillis = 11_000L
        )

        assertIs<CommandResult.Success>(route.result)
        assertEquals(ExternalCommandType.COMPOSE_WHATSAPP_MESSAGE, route.command.type)
        assertEquals("Sofi", route.command.contactName)
        assertEquals("estoy llegando", route.command.messageText)
        assertTrue(route.clearsPending)
    }

    @Test
    fun siDoesNotConfirmPendingAction() {
        val pending = router.route(
            rawInput = "mandale a Sofi: estoy llegando",
            nowMillis = 10_000L
        ).pendingConfirmation

        val route = router.route(
            rawInput = "sí",
            pendingConfirmation = pending,
            nowMillis = 11_000L
        )

        assertIs<CommandResult.NotSupported>(route.result)
        assertFalse(route.clearsPending)
        assertEquals(ExternalCommandType.UNSUPPORTED, route.command.type)
    }

    @Test
    fun daleDoesNotConfirmPendingAction() {
        val pending = router.route(
            rawInput = "mandale a Sofi: estoy llegando",
            nowMillis = 10_000L
        ).pendingConfirmation

        val route = router.route(
            rawInput = "dale",
            pendingConfirmation = pending,
            nowMillis = 11_000L
        )

        assertIs<CommandResult.NotSupported>(route.result)
        assertFalse(route.clearsPending)
        assertEquals(ExternalCommandType.UNSUPPORTED, route.command.type)
    }

    @Test
    fun cancelPendingActionClearsConfirmation() {
        val pending = router.route(
            rawInput = "mandale a Sofi: estoy llegando",
            nowMillis = 10_000L
        ).pendingConfirmation

        val route = router.route(
            rawInput = "cancelar",
            pendingConfirmation = pending,
            nowMillis = 11_000L
        )

        val result = assertIs<CommandResult.Success>(route.result)
        assertEquals("Acción cancelada.", result.spokenText)
        assertTrue(route.clearsPending)
    }

    @Test
    fun confirmWithoutPendingReturnsFailedMessage() {
        val route = router.route(
            rawInput = "confirmar",
            pendingConfirmation = null
        )

        val result = assertIs<CommandResult.Failed>(route.result)
        assertEquals("No hay ninguna acción pendiente para confirmar.", result.spokenText)
    }

    @Test
    fun cancelWithoutPendingReturnsSafeMessage() {
        val route = router.route(
            rawInput = "cancelar",
            pendingConfirmation = null
        )

        val result = assertIs<CommandResult.Success>(route.result)
        assertEquals("No hay ninguna acción pendiente.", result.spokenText)
        assertFalse(route.clearsPending)
    }

    @Test
    fun expiredPendingConfirmationReturnsExpiredMessage() {
        val pending = router.route(
            rawInput = "mandale a Sofi: estoy llegando",
            nowMillis = 10_000L
        ).pendingConfirmation

        val ttl = 2 * 60 * 1_000L
        val route = router.route(
            rawInput = "confirmar",
            pendingConfirmation = pending,
            nowMillis = 10_000L + ttl + 1
        )

        val result = assertIs<CommandResult.Failed>(route.result)
        assertTrue(result.spokenText.contains("venció"))
        assertTrue(route.clearsPending)
    }

    @Test
    fun blankInputIsUnsupported() {
        val command = router.parse("   ")

        assertEquals(ExternalCommandType.UNSUPPORTED, command.type)
    }

    private fun assertCompose(rawInput: String, contact: String, message: String) {
        val command = router.parse(rawInput)

        assertEquals(ExternalCommandType.COMPOSE_WHATSAPP_MESSAGE, command.type, rawInput)
        assertEquals(contact, command.contactName, rawInput)
        assertEquals(message, command.messageText, rawInput)
    }
}
