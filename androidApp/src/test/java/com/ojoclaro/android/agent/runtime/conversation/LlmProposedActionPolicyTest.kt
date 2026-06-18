package com.ojoclaro.android.agent.runtime.conversation

import kotlin.test.Test
import kotlin.test.assertEquals

/** El LLM nunca ejecuta: mutantes → BLOCK, lectura/navegación → validación local. */
class LlmProposedActionPolicyTest {

    @Test
    fun mutatingActionsAreBlocked() {
        listOf(
            "send_message", "send", "delete_chat", "delete", "call_contact", "call",
            "video_call", "send_audio", "record_audio", "share_location", "pay",
            "transfer", "forward", "block", "report", "archive", "open_profile",
            "open_contact_info", "send_photo", "send_file", "send_sticker"
        ).forEach {
            assertEquals(ProposedActionVerdict.BLOCK, LlmProposedActionPolicy.verdict(it), "debe bloquear: $it")
        }
        // tolerante a mayúsculas/espacios/guiones
        assertEquals(ProposedActionVerdict.BLOCK, LlmProposedActionPolicy.verdict("Send Message"))
        assertEquals(ProposedActionVerdict.BLOCK, LlmProposedActionPolicy.verdict("DELETE-CHAT"))
    }

    @Test
    fun readAndNavigationRouteToLocalValidation() {
        listOf(
            "open_chat", "search_chat", "read_messages", "read_chats", "read_screen",
            "scroll", "scroll_down", "go_back", "open_app"
        ).forEach {
            assertEquals(
                ProposedActionVerdict.ROUTE_LOCAL_VALIDATION,
                LlmProposedActionPolicy.verdict(it),
                "debe ir a validación local: $it"
            )
        }
        assertEquals(ProposedActionVerdict.ROUTE_LOCAL_VALIDATION, LlmProposedActionPolicy.verdict("open chat"))
    }

    @Test
    fun unknownOrEmptyFailsClosed() {
        assertEquals(ProposedActionVerdict.BLOCK, LlmProposedActionPolicy.verdict(null))
        assertEquals(ProposedActionVerdict.BLOCK, LlmProposedActionPolicy.verdict(""))
        assertEquals(ProposedActionVerdict.BLOCK, LlmProposedActionPolicy.verdict("frobnicate"))
    }
}
