package com.ojoclaro.android.qa

import com.ojoclaro.android.agent.AgentIntent
import com.ojoclaro.android.agent.AgentState

object VoiceTrainingDataset {
    val cases: List<VoiceRealWorldCase> = listOf(
        VoiceRealWorldCase(
            spokenByUser = "abrí wp y buscame el chat de Marco",
            recognizedByAndroid = "abrí WhatsApp y el del chat de Marco",
            normalizedText = "abrir whatsapp y el chat de marco",
            agentState = AgentState.WAITING_WHATSAPP_ACTION,
            expectedIntent = AgentIntent.OPEN_WHATSAPP_CHAT,
            expectedSlots = mapOf("contactName" to "Marco"),
            actualResult = "abría WhatsApp general y se perdía el contexto",
            expectedResult = "OpenWhatsAppChat con contacto Marco",
            notes = "Sirve para entrenar guided mode real."
        ),
        VoiceRealWorldCase(
            spokenByUser = "dale buscá el chat de Marco",
            recognizedByAndroid = "buscá el chat de Marco",
            normalizedText = "buscar el chat de marco",
            agentState = AgentState.WAITING_WHATSAPP_ACTION,
            expectedIntent = AgentIntent.OPEN_WHATSAPP_CHAT,
            expectedSlots = mapOf("contactName" to "Marco"),
            actualResult = "no ejecutaba nada o pedía repetir",
            expectedResult = "OpenWhatsAppChat sin tratar dale como confirmacion",
            notes = "dale nunca confirma."
        ),
        VoiceRealWorldCase(
            spokenByUser = "decile que llego en 10",
            recognizedByAndroid = "decile que llego en 10",
            normalizedText = "decir que llego en 10",
            agentState = AgentState.WAITING_MESSAGE,
            expectedIntent = AgentIntent.COMPOSE_WHATSAPP_MESSAGE,
            expectedSlots = mapOf("contactName" to "Marco", "messageText" to "llego en 10"),
            actualResult = "faltaba contexto de contacto",
            expectedResult = "ComposeWhatsAppMessage con contacto contextual",
            notes = "Usa el ultimo contacto contextual si existe."
        ),
        VoiceRealWorldCase(
            spokenByUser = "decile a Sofi que llego tarde pero decilo bien",
            recognizedByAndroid = "decile a Sofi que llego tarde pero decilo bien",
            normalizedText = "decir a sofi que llego tarde pero decirlo bien",
            agentState = AgentState.WAITING_MESSAGE,
            expectedIntent = AgentIntent.COMPOSE_WHATSAPP_MESSAGE,
            expectedSlots = mapOf("contactName" to "Sofi"),
            actualResult = "mensaje tosco o incompleto",
            expectedResult = "propuesta humana breve y cálida",
            notes = "Debe pasar por el compositor humano local."
        )
    )
}

