package com.ojoclaro.android.ui.home

import com.ojoclaro.android.agent.apps.AppCapabilityRegistry
import com.ojoclaro.android.external.ExternalActionEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CriticalLocalWhatsAppRouterTest {

    @Test
    fun directWhatsAppPhrasesResolveLocally() {
        listOf(
            "abri WhatsApp",
            "abre WhatsApp",
            "abrir WhatsApp",
            "abrime WhatsApp",
            "abri el WhatsApp",
            "abrime el WhatsApp",
            "quiero abrir WhatsApp",
            "abri wsp",
            "abri wp",
            "abri guasap",
            "abri wasap"
        ).forEach { phrase ->
            val command = assertNotNull(detectCriticalLocalWhatsAppCommand(phrase), phrase)

            assertEquals("LOCAL_WHATSAPP", command.routeLabel, phrase)
            assertEquals(AppCapabilityRegistry.WHATSAPP_PACKAGE, command.targetPackageName, phrase)
            assertEquals(ExternalActionEvent.OpenWhatsApp, command.externalAction, phrase)
        }
    }

    @Test
    fun messageIntentOpensWhatsAppWithoutSending() {
        listOf(
            "quiero mandar un WhatsApp",
            "quiero mandar un mensaje por WhatsApp",
            "mandar WhatsApp",
            "mandale un mensaje a alguien por WhatsApp"
        ).forEach { phrase ->
            val command = assertNotNull(detectCriticalLocalWhatsAppCommand(phrase), phrase)

            assertEquals("LOCAL_WHATSAPP_GUIDED_OPEN", command.routeLabel, phrase)
            assertTrue(command.spokenText.contains("dictame el mensaje", ignoreCase = true), phrase)
            assertEquals(ExternalActionEvent.OpenWhatsApp, command.externalAction, phrase)
        }
    }

    @Test
    fun whatsappBusinessPrefersBusinessPackage() {
        listOf("abri WhatsApp Business", "abrime WhatsApp Business").forEach { phrase ->
            val command = assertNotNull(detectCriticalLocalWhatsAppCommand(phrase), phrase)
            val action = command.externalAction as? ExternalActionEvent.OpenSafeApp

            assertEquals(AppCapabilityRegistry.WHATSAPP_BUSINESS_PACKAGE, command.targetPackageName, phrase)
            assertNotNull(action)
            assertEquals(AppCapabilityRegistry.WHATSAPP_BUSINESS_PACKAGE, action.packageName, phrase)
            assertEquals("WhatsApp Business", action.appName, phrase)
        }
    }

    @Test
    fun unrelatedWhatsAppQuestionsAreNotOpened() {
        assertNull(detectCriticalLocalWhatsAppCommand("estoy en WhatsApp"))
        assertNull(detectCriticalLocalWhatsAppCommand("leer mensajes de WhatsApp"))
    }
}
