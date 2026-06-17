package com.ojoclaro.android.llm

import com.ojoclaro.android.agent.AgentConversationManager
import com.ojoclaro.android.agent.LocalIntentParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class EstelaIntentRequestBuilderTest {

    @Test
    fun buildsPromptV3ContextPayload() {
        val manager = AgentConversationManager()
        manager.handle(LocalIntentParser().parse("mandale un mensaje"))
        val builder = EstelaIntentRequestBuilder(
            installedAppsProvider = { listOf("whatsapp", "uber") },
            memoryContactsProvider = { listOf("mamá", "Marco") },
            activeAppProvider = { "com.whatsapp" },
            permissionsProvider = {
                mapOf(
                    "fine_location" to true,
                    "camera" to false
                )
            }
        )

        val request = builder.build(
            userText = "a mamá",
            conversationManager = manager
        )

        assertEquals("a mamá", request.userText)
        assertEquals("waiting_contact", request.conversationState)
        assertEquals(listOf("whatsapp", "uber"), request.installedApps)
        assertEquals(listOf("mamá", "Marco"), request.memoryContacts)
        assertEquals("com.whatsapp", request.activeApp)
        assertEquals(true, request.permissionsGranted["fine_location"])
        assertEquals(false, request.permissionsGranted["camera"])

        val pending = assertNotNull(request.pendingAction)
        assertEquals("compose_whatsapp_message", pending.intent)
        assertEquals(emptyMap(), pending.params)
    }

    @Test
    fun proxyPayloadUsesSingleModelConfigLocation() {
        val request = EstelaIntentRequest(
            userText = "abrime WhatsApp",
            conversationState = "idle",
            installedApps = listOf("whatsapp"),
            permissionsGranted = mapOf("camera" to false)
        )

        val payload = Json.parseToJsonElement(
            EstelaIntentJsonContract.proxyRequestToJson(request)
        ).jsonObject

        assertEquals(EstelaIntentConfig.MODEL, payload["model"]?.jsonPrimitive?.content)
        assertEquals("gpt-5.4-mini", EstelaIntentConfig.MODEL)
        assertEquals(
            EstelaIntentConfig.SYSTEM_PROMPT_ID,
            payload["system_prompt_id"]?.jsonPrimitive?.content
        )
        val input = payload["input"]?.jsonObject
        assertNotNull(input)
        assertEquals("abrime WhatsApp", input["user_text"]?.jsonPrimitive?.content)
        assertEquals("idle", input["conversation_state"]?.jsonPrimitive?.content)
        assertEquals(
            "whatsapp",
            input["installed_apps"]?.jsonArray?.first()?.jsonPrimitive?.content
        )
    }
}
