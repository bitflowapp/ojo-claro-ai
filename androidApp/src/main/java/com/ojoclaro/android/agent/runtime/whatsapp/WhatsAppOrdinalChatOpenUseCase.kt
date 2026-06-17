package com.ojoclaro.android.agent.runtime.whatsapp

import com.ojoclaro.android.accessibility.OjoClaroAccessibilityService
import com.ojoclaro.android.agent.core.screen.ScreenContextProvider

/**
 * Capa de borde inyectable para ejecutar la apertura de un chat visible.
 * La implementación real delega en el AccessibilityService, que solo clickea
 * nodos visibles, habilitados y NO sensibles. Los tests usan un fake.
 */
interface VisibleChatOpener {
    fun open(targetName: String): VisibleChatOpenResult
}

/**
 * Implementación real: usa el click seguro ya auditado del servicio.
 */
class AccessibilityVisibleChatOpener : VisibleChatOpener {
    override fun open(targetName: String): VisibleChatOpenResult =
        OjoClaroAccessibilityService.openVisibleWhatsAppChatByName(targetName)
}

/**
 * Respuesta verbal del uso "abrí el primer/segundo chat".
 */
sealed class WhatsAppOrdinalChatResponse {
    object NotAnOrdinalCommand : WhatsAppOrdinalChatResponse()
    data class NeedsAccessibilityService(val spokenText: String) : WhatsAppOrdinalChatResponse()
    data class NotInWhatsApp(val spokenText: String) : WhatsAppOrdinalChatResponse()
    data class AlreadyInChat(val spokenText: String) : WhatsAppOrdinalChatResponse()
    data class OutOfRange(val spokenText: String) : WhatsAppOrdinalChatResponse()

    /**
     * Resolvió un chat concreto por posición pero NO lo abrió. Espera la
     * confirmación explícita del usuario (preferencia de producto Fase 2A).
     */
    data class NeedsConfirmation(
        val displayName: String,
        val index: Int,
        val spokenText: String
    ) : WhatsAppOrdinalChatResponse()

    /** Resultado de [WhatsAppOrdinalChatOpenUseCase.confirmOpen]. */
    data class Opened(val displayName: String, val spokenText: String) : WhatsAppOrdinalChatResponse()
    data class CouldNotOpen(val spokenText: String) : WhatsAppOrdinalChatResponse()
}

/**
 * Resuelve y (tras confirmación) abre un chat visible por POSICIÓN
 * ("abrí el primer chat").
 *
 * Flujo en dos pasos (Fase 2A):
 *  1. [handle] reconoce el ordinal y lo resuelve contra la lista visible real.
 *     NO abre: devuelve [WhatsAppOrdinalChatResponse.NeedsConfirmation] con el
 *     nombre encontrado para que Estela pregunte "¿Querés que lo abra?".
 *  2. [confirmOpen] hace el click seguro (re-resuelve por nombre sobre el árbol
 *     vivo del servicio) solo cuando el usuario confirmó.
 *
 * Seguridad:
 *  - Solo opera en la LISTA de chats (no dentro de un chat).
 *  - El click final pasa por el gate seguro del servicio: nunca toca Enviar,
 *    Llamar, Borrar ni acciones sensibles. Abrir un chat es navegación
 *    reversible y no envía nada.
 *  - Si el índice excede lo visible, lo dice con honestidad (no abre al azar).
 */
class WhatsAppOrdinalChatOpenUseCase(
    private val provider: ScreenContextProvider,
    private val opener: VisibleChatOpener = AccessibilityVisibleChatOpener(),
    private val detector: WhatsAppScreenDetector = WhatsAppScreenDetector(),
    private val chatListDetector: WhatsAppChatListDetector = WhatsAppChatListDetector(),
    private val isAccessibilityReady: () -> Boolean = { true }
) {

    /** Paso 1: reconocer + resolver el nombre. No abre nada. */
    fun handle(rawText: String): WhatsAppOrdinalChatResponse {
        val index = WhatsAppOrdinalChatParser.parse(rawText)
            ?: return WhatsAppOrdinalChatResponse.NotAnOrdinalCommand

        if (!isAccessibilityReady()) {
            return WhatsAppOrdinalChatResponse.NeedsAccessibilityService(NEEDS_ACCESSIBILITY_TEXT)
        }

        val snapshot = runCatching { provider.current() }.getOrNull()
        val state = detector.detect(snapshot)
        if (state.isUnknown || !state.isOpen) {
            return WhatsAppOrdinalChatResponse.NotInWhatsApp(NOT_IN_WHATSAPP_TEXT)
        }
        if (state.isInChat) {
            return WhatsAppOrdinalChatResponse.AlreadyInChat(ALREADY_IN_CHAT_TEXT)
        }

        val chats = chatListDetector.extractChats(snapshot)
        if (chats.isEmpty() || index >= chats.size) {
            return WhatsAppOrdinalChatResponse.OutOfRange(outOfRangeText(chats.size))
        }

        val targetName = chats[index].displayName
        return WhatsAppOrdinalChatResponse.NeedsConfirmation(
            displayName = targetName,
            index = index,
            spokenText = "Encontré el chat número ${index + 1}: $targetName. " +
                "¿Querés que lo abra? Decí sí para abrirlo o cancelá."
        )
    }

    /** Paso 2: abrir tras confirmación explícita del usuario. */
    fun confirmOpen(displayName: String): WhatsAppOrdinalChatResponse {
        if (!isAccessibilityReady()) {
            return WhatsAppOrdinalChatResponse.NeedsAccessibilityService(NEEDS_ACCESSIBILITY_TEXT)
        }
        return when (val result = runCatching { opener.open(displayName) }.getOrNull()) {
            is VisibleChatOpenResult.Opened -> WhatsAppOrdinalChatResponse.Opened(
                displayName = result.displayName,
                spokenText = "Abrí el chat de ${result.displayName}. No envié ningún mensaje."
            )
            is VisibleChatOpenResult.NotInWhatsApp ->
                WhatsAppOrdinalChatResponse.NotInWhatsApp(NOT_IN_WHATSAPP_TEXT)
            is VisibleChatOpenResult.Unsafe -> WhatsAppOrdinalChatResponse.CouldNotOpen(
                "Veo ${result.displayName ?: displayName}, pero no puedo abrirlo de forma segura. " +
                    "Tocá dos veces sobre ese chat para abrirlo."
            )
            is VisibleChatOpenResult.NoMatch,
            is VisibleChatOpenResult.Failed,
            null -> WhatsAppOrdinalChatResponse.CouldNotOpen(
                "No pude abrir el chat. Probá leyendo los chats visibles otra vez."
            )
        }
    }

    private fun outOfRangeText(visibleCount: Int): String = when (visibleCount) {
        0 -> "No veo chats en la lista. Abrí WhatsApp en la pantalla principal de chats y volvé a pedirme."
        1 -> "Solo veo un chat en la lista. Pedime: abrí el primer chat."
        else -> "Solo veo $visibleCount chats en la lista. Pedime un número menor o leé los chats visibles."
    }

    companion object {
        const val NEEDS_ACCESSIBILITY_TEXT: String =
            "Para abrir un chat necesito el servicio de Accesibilidad activo. " +
                "Activá Estela en Ajustes de Accesibilidad."
        const val NOT_IN_WHATSAPP_TEXT: String =
            "No estás en WhatsApp o no veo la lista de chats. Abrí WhatsApp y volvé a pedirme."
        const val ALREADY_IN_CHAT_TEXT: String =
            "Ya estás dentro de un chat. Volvé a la lista de chats y decime qué número abrir."
    }
}
