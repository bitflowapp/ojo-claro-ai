package com.ojoclaro.android.agent.runtime.conversation

/**
 * Si el LLM alguna vez devuelve una ACCIÓN estructurada (en lugar de texto), esta
 * política decide su destino. Regla dura: el LLM NUNCA ejecuta nada.
 *
 *  - Acciones mutantes/irreversibles (enviar, borrar, llamar, pagar, foto, perfil,
 *    ubicación, reenviar, bloquear…) → BLOCK.
 *  - Acciones de lectura/navegación/apertura → ROUTE_LOCAL_VALIDATION: el handler
 *    local existente vuelve a resolver destino, rechaza avatar/foto, pide
 *    confirmación, etc. NO es un tap directo.
 *  - Desconocido / vacío → BLOCK (fail-closed).
 *
 * PURO: sin Android, sin estado, sin IO.
 */
enum class ProposedActionVerdict { BLOCK, ROUTE_LOCAL_VALIDATION }

object LlmProposedActionPolicy {

    // Únicas acciones que pueden seguir a la validación local (lectura/navegación).
    private val ROUTE_LOCAL = setOf(
        "open_chat", "search_chat", "find_chat", "read_messages", "read_message",
        "read_chats", "read_screen", "describe_screen", "scroll", "scroll_up",
        "scroll_down", "go_back", "open_app", "what_is_on_screen"
    )

    fun verdict(action: String?): ProposedActionVerdict {
        if (action.isNullOrBlank()) return ProposedActionVerdict.BLOCK
        val key = action.trim().lowercase().replace(Regex("[\\s\\-]+"), "_")
        return if (key in ROUTE_LOCAL) {
            ProposedActionVerdict.ROUTE_LOCAL_VALIDATION
        } else {
            // send_message / delete_chat / call_contact / video_call / send_audio /
            // share_location / pay / forward / block / report / open_profile / … →
            // y cualquier acción desconocida → bloqueada.
            ProposedActionVerdict.BLOCK
        }
    }
}
