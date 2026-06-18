package com.ojoclaro.android.agent.core.screen

import com.ojoclaro.android.agent.mission.AgentMissionPhrases
import com.ojoclaro.android.agent.runtime.conversation.ConversationGate
import com.ojoclaro.android.agent.runtime.screen.ScreenUnderstandingResult
import com.ojoclaro.android.agent.runtime.screen.ScreenUnderstandingUseCase
import com.ojoclaro.android.outdoor.OutdoorPhrases
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * V1.10.3 — lectura de pantalla en CUALQUIER app externa (Maps/DiDi/...):
 *  - todas las frases de la misión entran a la ruta local (jamás LLM/fallback);
 *  - funciona con cualquier package foreground;
 *  - "qué puedo tocar" responde orientado a acciones;
 *  - opciones sensibles (pedir/confirmar/pagar) se LEEN pero con la
 *    aclaración de que Estela no las toca;
 *  - el fast path de GAS corre después de outdoor/WhatsApp contextual y
 *    antes de companion/ConversationGate/fallback;
 *  - cero clicks: el camino nuevo solo describe.
 */
class ScreenReadAnyAppV1103Test {

    private val missionPhrases = listOf(
        "leé la pantalla",
        "lee la pantalla",
        "léeme la pantalla",
        "que aparece",
        "qué aparece",
        "qué hay en pantalla",
        "que hay en la pantalla",
        "describime la pantalla",
        "describí la pantalla",
        "qué puedo tocar",
        "que puedo tocar",
        "qué opciones tengo",
        "ayudame con esta pantalla"
    )

    private class FakeProvider(var snapshot: ScreenSnapshot?) : ScreenContextProvider {
        override fun current(): ScreenSnapshot? = snapshot
    }

    private fun snapshotFor(packageName: String): ScreenSnapshot = ScreenSnapshot(
        packageName = packageName,
        text = "¿A dónde vas?\nBuscar un destino",
        elements = listOf(
            ScreenElement("¿A dónde vas?", ScreenElementRole.HEADING, isInteractive = false),
            ScreenElement("Buscar", ScreenElementRole.BUTTON, isInteractive = true),
            ScreenElement("Inicio", ScreenElementRole.BUTTON, isInteractive = true)
        ),
        capturedAtMillis = 1_000L
    )

    // --- frases ---

    @Test
    fun allMissionPhrasesAreLocalScreenQueries() {
        missionPhrases.forEach { phrase ->
            val isScreenQuery = ScreenQueryPhrases.classify(phrase) != null ||
                NextStepQueryPhrases.classify(phrase) != null
            assertTrue(isScreenQuery, "debe ser consulta de pantalla local: \"$phrase\"")
            // Jamás conversación libre (LLM) ni misión de agente.
            assertFalse(
                ConversationGate.isConversational(phrase),
                "JAMÁS va al LLM: \"$phrase\""
            )
            assertFalse(
                AgentMissionPhrases.isMissionGoal(phrase),
                "JAMÁS arranca una misión: \"$phrase\""
            )
        }
    }

    @Test
    fun quePuedoTocarMapsToActionOrientedMode() {
        assertEquals(ScreenSummaryMode.WHAT_CAN_I_DO, ScreenQueryPhrases.classify("qué puedo tocar"))
        assertEquals(ScreenSummaryMode.WHAT_CAN_I_DO, ScreenQueryPhrases.classify("qué opciones tengo"))
        assertEquals(ScreenSummaryMode.SHORT, ScreenQueryPhrases.classify("qué aparece"))
        // "dónde estoy" sigue siendo GPS: outdoor corre ANTES del fast path.
        assertIs<OutdoorPhrases.Command.WhereAmI>(OutdoorPhrases.parse("¿Dónde estoy?"))
    }

    // --- use case sobre apps externas ---

    @Test
    fun screenReadingWorksForAnyForegroundPackage() {
        listOf(
            "com.google.android.apps.maps",
            "com.didiglobal.passenger",
            "com.ubercab",
            "com.cabify.rider",
            "com.android.settings"
        ).forEach { pkg ->
            val useCase = ScreenUnderstandingUseCase(
                provider = FakeProvider(snapshotFor(pkg)),
                isAccessibilityReady = { true }
            )
            val result = useCase.handle("leé la pantalla")
            assertIs<ScreenUnderstandingResult.Spoken>(result, "debe leer en $pkg")
            assertTrue(result.spokenText.isNotBlank())
            assertTrue(result.isSafeToReadAloud, "pantalla benigna debe leerse en $pkg")

            val actions = useCase.handle("qué puedo tocar")
            assertIs<ScreenUnderstandingResult.Spoken>(actions)
            assertEquals(ScreenSummaryMode.WHAT_CAN_I_DO, actions.mode)
            assertTrue(actions.spokenText.contains("Buscar"), "lista los botones reales")
        }
    }

    @Test
    fun missingSnapshotIsHonestAndAccessibilityOffAsksToEnable() {
        val noSnapshot = ScreenUnderstandingUseCase(
            provider = FakeProvider(null),
            isAccessibilityReady = { true }
        ).handle("leé la pantalla")
        assertIs<ScreenUnderstandingResult.Spoken>(noSnapshot)
        assertTrue(noSnapshot.isLimited, "sin snapshot la lectura es limitada y honesta")

        val accessibilityOff = ScreenUnderstandingUseCase(
            provider = FakeProvider(snapshotFor("com.didiglobal.passenger")),
            isAccessibilityReady = { false }
        ).handle("leé la pantalla")
        assertIs<ScreenUnderstandingResult.NeedsAccessibilityService>(accessibilityOff)
    }

    // --- protección de acciones sensibles (P4) ---

    @Test
    fun sensitiveRideActionsAreSpokenWithNoTouchNote() {
        val useCase = ScreenUnderstandingUseCase(
            provider = FakeProvider(
                ScreenSnapshot(
                    packageName = "com.didiglobal.passenger",
                    text = "Elegí cómo viajar",
                    elements = listOf(
                        ScreenElement("Confirmar viaje", ScreenElementRole.BUTTON, isInteractive = true),
                        ScreenElement("Cambiar destino", ScreenElementRole.BUTTON, isInteractive = true)
                    ),
                    capturedAtMillis = 1_000L
                )
            ),
            isAccessibilityReady = { true }
        )
        val result = useCase.handle("qué puedo tocar")
        assertIs<ScreenUnderstandingResult.Spoken>(result)
        assertTrue(result.spokenText.contains("Confirmar viaje"), "la opción se LEE")
        val spoken = ScreenActionSafetyNote.appendIfSensitive(result.spokenText)
        assertTrue(spoken.contains(ScreenActionSafetyNote.NOTE), "y se aclara que no se toca")
    }

    @Test
    fun safetyNoteOnlyAppendsWhenNeededAndNeverTwice() {
        listOf(
            "Podés: Confirmar viaje, Cambiar destino.",
            "Podés: Pagar, Buscar.",
            "Acción principal posible: pedir viaje",
            "Veo: solicitar ahora, tarjeta terminada en cuatro."
        ).forEach { summary ->
            val once = ScreenActionSafetyNote.appendIfSensitive(summary)
            assertTrue(once.contains(ScreenActionSafetyNote.NOTE), "nota requerida: \"$summary\"")
            assertEquals(once, ScreenActionSafetyNote.appendIfSensitive(once), "jamás duplicada")
        }
        listOf(
            "Podés: Buscar, Inicio, Ajustes.",
            "Estás en: ¿A dónde vas?.",
            "No detecté acciones claras en esta pantalla."
        ).forEach { summary ->
            assertEquals(
                summary,
                ScreenActionSafetyNote.appendIfSensitive(summary),
                "sin opciones sensibles no hay nota: \"$summary\""
            )
        }
    }

    // --- contratos del servicio (por inspección de fuente) ---

    @Test
    fun gasRoutesScreenQueriesBeforeConversationAndFallback() {
        val service = File(
            "src/main/java/com/ojoclaro/android/global/GlobalAssistantService.kt"
        ).readText()
        val hookIdx = service.indexOf("if (handleGlobalScreenQuery(text)) return")
        assertTrue(hookIdx > 0, "el fast path global de pantalla debe existir")
        val outdoorIdx = service.indexOf("if (handleOutdoorCommand(text)) return")
        val contextualIdx = service.indexOf("handleContextualWhatsApp(text)?.let")
        val companionIdx = service.indexOf("EstelaCompanionPhrases.respond(text)")
        val gateIdx = service.indexOf("ConversationGate.isConversational(text)")
        val fallbackIdx = service.indexOf("fallbackReason=no_local_match")
        assertTrue(outdoorIdx in 1 until hookIdx, "outdoor (GPS) SIEMPRE gana antes")
        assertTrue(contextualIdx in 1 until hookIdx, "los lectores ricos de WhatsApp van antes")
        assertTrue(hookIdx < companionIdx, "pantalla antes que compañía")
        assertTrue(hookIdx < gateIdx, "pantalla antes que el LLM")
        assertTrue(hookIdx < fallbackIdx, "pantalla antes que el fallback no_local_match")
    }

    // --- V1.10.4: WhatsApp también instrumenta la lectura genérica ---

    @Test
    fun whatsAppContextualScreenReadIsInstrumentedLikeAnyApp() {
        val service = File(
            "src/main/java/com/ojoclaro/android/global/GlobalAssistantService.kt"
        ).readText()
        val contextualReader = service
            .substringAfter("private fun handleWhatsAppReadFollowUp")
            .substringBefore("private fun whatsAppMessagesOutcome")
        assertTrue(contextualReader.isNotBlank(), "el lector contextual debe existir")
        // Mismos logs que Maps/DiDi: la QA por logs ve la ruta local.
        assertTrue(
            contextualReader.contains("screenQuery=local"),
            "la lectura genérica en WhatsApp debe loguear screenQuery=local"
        )
        // Misma decoración de seguridad que en cualquier app.
        assertTrue(
            contextualReader.contains("ScreenActionSafetyNote.appendIfSensitive"),
            "la nota de acciones sensibles aplica también en WhatsApp"
        )
        assertTrue(contextualReader.contains("poca información accesible"))
        // La lectura genérica corre ANTES de los lectores ricos, y estos
        // siguen existiendo para sus frases específicas.
        val genericIdx = contextualReader.indexOf("ScreenQueryPhrases.classify")
        val richIdx = contextualReader.indexOf("WhatsAppMessageReadPhrases.classify")
        assertTrue(genericIdx in 0 until richIdx, "genérico primero, ricos después")
        // Jamás acciones desde la lectura.
        listOf("performClick", "ACTION_CLICK", "dispatchGesture", "tapWhatsAppSend")
            .forEach { forbidden ->
                assertFalse(contextualReader.contains(forbidden), "sin acciones: $forbidden")
            }
    }

    @Test
    fun whatsAppRichReaderPhrasesAreNeverStolenByGenericScreenRead() {
        // Las frases RICAS de WhatsApp no clasifican como lectura genérica:
        // siguen yendo a sus lectores de mensajes/chats.
        listOf(
            "leé los mensajes",
            "leeme los mensajes",
            "leeme los chats",
            "qué mensajes hay",
            "abrí el primer chat",
            "leé este chat"
        ).forEach { phrase ->
            assertEquals(
                null,
                ScreenQueryPhrases.classify(phrase),
                "frase rica de WhatsApp jamás va al resumen genérico: \"$phrase\""
            )
        }
        // Y sus rutas ricas existen.
        assertTrue(
            com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppMessageReadPhrases
                .classify("leeme los mensajes") != null
        )
        assertTrue(
            com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppChatListPhrases
                .isChatListCommand("leeme los chats")
        )
    }

    // --- V1.10.4b: "leé los chats" jamás cae al fallback ---

    @Test
    fun chatListPhrasesMatchTheirRichRoute() {
        listOf(
            "leé los chats",
            "lee los chats",
            "leeme los chats",
            "qué chats tengo",
            "qué chats hay",
            "chats visibles"
        ).forEach { phrase ->
            assertTrue(
                com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppChatListPhrases
                    .isChatListCommand(phrase),
                "frase de chats debe matchear la ruta rica: \"$phrase\""
            )
            // Nunca el resumen genérico: la ruta rica es la dueña.
            assertEquals(null, ScreenQueryPhrases.classify(phrase))
        }
    }

    @Test
    fun foregroundWhatsAppReadHookExistsAndOnlyReads() {
        val service = File(
            "src/main/java/com/ojoclaro/android/global/GlobalAssistantService.kt"
        ).readText()
        val hookIdx = service.indexOf("if (handleForegroundWhatsAppReadCommand(text)) return")
        val contextualIdx = service.indexOf("handleContextualWhatsApp(text)?.let")
        val screenHookIdx = service.indexOf("if (handleGlobalScreenQuery(text)) return")
        assertTrue(hookIdx > 0, "el hook global de lectura de WhatsApp debe existir")
        assertTrue(
            contextualIdx in 1 until hookIdx,
            "el flujo contextual rico mantiene prioridad"
        )
        assertTrue(hookIdx < screenHookIdx, "chats/mensajes antes que el resumen genérico")
        // El hook solo lee: delega en los readers verbales existentes.
        val body = service
            .substringAfter("private suspend fun handleForegroundWhatsAppReadCommand")
            .substringBefore("// --- V1.10.3")
        assertTrue(body.contains("whatsAppChatsOutcome"))
        assertTrue(body.contains("whatsAppMessagesOutcome"))
        listOf("performClick", "ACTION_CLICK", "dispatchGesture", "tapWhatsAppSend")
            .forEach { forbidden ->
                assertFalse(body.contains(forbidden), "lectura jamás ejecuta: $forbidden")
            }
    }

    @Test
    fun globalScreenQuerySectionOnlyDescribesNeverClicks() {
        val service = File(
            "src/main/java/com/ojoclaro/android/global/GlobalAssistantService.kt"
        ).readText()
        val section = service
            .substringAfter("V1.10.3: lectura de pantalla en CUALQUIER app (fast path local)")
            .substringBefore("private fun handleVisibleScreenFollowUp")
        assertTrue(section.isNotBlank(), "la sección V1.10.3 debe existir")
        listOf("performClick", "ACTION_CLICK", "dispatchGesture", "tapWhatsAppSend", "startActivity")
            .forEach { forbidden ->
                assertFalse(
                    section.contains(forbidden),
                    "la lectura de pantalla jamás ejecuta acciones: $forbidden"
                )
            }
        // El aviso honesto de poca información accesible existe.
        assertTrue(section.contains("poca información accesible"))
    }
}
