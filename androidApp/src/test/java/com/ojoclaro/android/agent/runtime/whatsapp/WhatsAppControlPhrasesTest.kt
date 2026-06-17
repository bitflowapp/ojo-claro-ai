package com.ojoclaro.android.agent.runtime.whatsapp

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WhatsAppControlPhrasesTest {

    @Test
    fun openWhatsAppVariantsMatch() {
        listOf(
            "abrir WhatsApp",
            "abrí WhatsApp",
            "abrime el whatsapp",
            "andá a WhatsApp",
            "quiero entrar a WhatsApp",
            "entrar a whatsapp",
            "llevame a WhatsApp",
            "abrí wasap"
        ).forEach { assertTrue(WhatsAppControlPhrases.isOpenWhatsAppCommand(it), "open: $it") }
    }

    @Test
    fun openDoesNotStealChatOrUnrelatedPhrases() {
        listOf(
            "abrí el chat de Sofía",        // open-chat flow, not open-app
            "abrí la conversación con Juan",
            "leeme los chats",
            "diagnóstico whatsapp",          // diagnostic, not open
            "describir entorno",
            "abrí la cámara"
        ).forEach { assertFalse(WhatsAppControlPhrases.isOpenWhatsAppCommand(it), "not-open: $it") }
    }

    @Test
    fun diagnosticVariantsMatch() {
        listOf(
            "diagnóstico WhatsApp",
            "diagnostico de whatsapp",
            "revisar WhatsApp",
            "revisá WhatsApp",
            "está listo WhatsApp",
            "estado de WhatsApp",
            "anda bien whatsapp"
        ).forEach { assertTrue(WhatsAppControlPhrases.isDiagnosticCommand(it), "diag: $it") }
    }

    @Test
    fun diagnosticDoesNotStealOpenOrUnrelated() {
        listOf(
            "abrir WhatsApp",
            "leeme los chats",
            "repetir",
            "dónde estoy"
        ).forEach { assertFalse(WhatsAppControlPhrases.isDiagnosticCommand(it), "not-diag: $it") }
    }
}
