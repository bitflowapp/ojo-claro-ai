package com.ojoclaro.android.agent

import com.ojoclaro.android.agent.messaging.MessagingChannel
import com.ojoclaro.android.agent.messaging.MessagingReplyOutcome
import com.ojoclaro.android.agent.messaging.MessagingTaskIntent
import com.ojoclaro.android.agent.messaging.MessagingTaskRouter
import com.ojoclaro.android.agent.payments.PaymentGuidePhrases
import com.ojoclaro.android.agent.runtime.instagram.InstagramDirectPhrases
import com.ojoclaro.android.agent.runtime.instagram.InstagramNameMatcher
import com.ojoclaro.android.agent.runtime.screen.ScreenNavigationCommand
import com.ojoclaro.android.agent.runtime.screen.ScreenNavigationCommandParser
import com.ojoclaro.android.voice.EstelaColloquialNormalizer
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * V1.12 — Instagram Direct como segundo canal de mensajería.
 *
 * Contratos verificados:
 *  - el router SOLO clasifica frases con marca explícita de Instagram:
 *    sin marca devuelve null y las rutas WhatsApp del piloto no cambian;
 *  - "sí"/"dale" JAMÁS confirman un envío de texto (en ningún canal),
 *    pero SÍ confirman el toque reversible de videollamada;
 *  - pagos/tarjetas nunca se clasifican como mensajería;
 *  - el toque de videollamada y el de enviar tienen UN solo call site,
 *    re-verificado en el momento del toque;
 *  - el audio es guía hablada: no existe función que toque el micrófono.
 */
class EstelaInstagramDirectV112Test {

    // --- A. Normalizador: insta/ig → instagram ---

    @Test
    fun colloquialNormalizerCanonicalizesInstagramSlang() {
        assertEquals("abri instagram", EstelaColloquialNormalizer.normalize("abrí insta"))
        assertEquals(
            "escribile a sofia por instagram que ya salgo",
            EstelaColloquialNormalizer.normalize("escribile a sofia por ig que ya salgo")
        )
        // Sin slang el texto pasa intacto (mayúsculas y acentos incluidos).
        assertEquals(
            "abrí el chat de Sofia en instagram",
            EstelaColloquialNormalizer.normalize("abrí el chat de Sofia en instagram")
        )
        // El slang viejo sigue intacto.
        assertEquals("quiero pagar el viaje", EstelaColloquialNormalizer.normalize("quiero garpar el viaje"))
    }

    // --- B. Router: frases reales → intent con canal INSTAGRAM ---

    @Test
    fun routerClassifiesRealInstagramPhrases() {
        val openApp = MessagingTaskRouter.classify("abrí instagram")
        assertNotNull(openApp)
        assertEquals(MessagingChannel.INSTAGRAM, openApp.channel)
        assertEquals(MessagingTaskIntent.OPEN_MESSAGING_APP, openApp.intent)
        assertFalse(openApp.inboxRequested)

        val inbox = MessagingTaskRouter.classify("abrí los mensajes de instagram")
        assertNotNull(inbox)
        assertEquals(MessagingTaskIntent.OPEN_MESSAGING_APP, inbox.intent)
        assertTrue(inbox.inboxRequested)

        val inboxDm = MessagingTaskRouter.classify("abrí los dm")
        assertNotNull(inboxDm)
        assertEquals(MessagingTaskIntent.OPEN_MESSAGING_APP, inboxDm.intent)
        assertTrue(inboxDm.inboxRequested)

        val openChat = MessagingTaskRouter.classify("abrí el chat de Sofia en instagram")
        assertNotNull(openChat)
        assertEquals(MessagingTaskIntent.OPEN_CHAT, openChat.intent)
        assertEquals("sofia", openChat.contactQuery)

        val send = MessagingTaskRouter.classify("mandale a Sofia por instagram que llego tarde")
        assertNotNull(send)
        assertEquals(MessagingTaskIntent.SEND_TEXT_PENDING_CONFIRMATION, send.intent)
        assertEquals("sofia", send.contactQuery)
        assertEquals("llego tarde", send.messageText)

        val tell = MessagingTaskRouter.classify("decile a Sofia por instagram que llego tarde")
        assertNotNull(tell)
        assertEquals(MessagingTaskIntent.SEND_TEXT_PENDING_CONFIRMATION, tell.intent)

        val write = MessagingTaskRouter.classify("escribile a Sofia en insta que ya salgo")
        assertNotNull(write)
        assertEquals(MessagingTaskIntent.SEND_TEXT_PENDING_CONFIRMATION, write.intent)
        assertEquals("ya salgo", write.messageText)

        val video = MessagingTaskRouter.classify("hacé videollamada con Sofia por instagram")
        assertNotNull(video)
        assertEquals(MessagingTaskIntent.START_VIDEO_CALL_PENDING_CONFIRMATION, video.intent)
        assertEquals("sofia", video.contactQuery)

        val videoNoContact = MessagingTaskRouter.classify("llamala por video en instagram")
        assertNotNull(videoNoContact)
        assertEquals(
            MessagingTaskIntent.START_VIDEO_CALL_PENDING_CONFIRMATION,
            videoNoContact.intent
        )

        val audio = MessagingTaskRouter.classify("mandale un audio a Sofia por instagram")
        assertNotNull(audio)
        assertEquals(MessagingTaskIntent.START_AUDIO_FLOW, audio.intent)

        val audioActivate =
            MessagingTaskRouter.classify("activame el audio en el chat de Sofia por instagram")
        assertNotNull(audioActivate)
        assertEquals(MessagingTaskIntent.START_AUDIO_FLOW, audioActivate.intent)
    }

    // --- C. No robar rutas existentes ---

    @Test
    fun phrasesWithoutInstagramMarkNeverClassifyAsInstagram() {
        listOf(
            "mandale a Marco que llego tarde",
            "videollamada",
            "videollamada con Marco",
            "mandale un audio a Marco",
            "leé la pantalla",
            "abrí whatsapp",
            "avisame cuando llegue el viaje",
            "dónde estoy",
            "llevame directo a casa",
            "enviá",
            "mandalo"
        ).forEach { phrase ->
            assertNull(
                MessagingTaskRouter.classify(phrase),
                "\"$phrase\" no tiene marca de Instagram: jamás se clasifica acá"
            )
        }
        assertFalse(InstagramDirectPhrases.mentionsInstagram("llevame directo a casa"))
    }

    // --- D. Pagos/tarjetas jamás se mezclan con mensajería ---

    @Test
    fun paymentPhrasesNeverClassifyAsInstagramMessaging() {
        listOf(
            "vinculá mercado pago a instagram",
            "poné el cvv en instagram",
            "registrame una tarjeta en instagram",
            "pagá la suscripción de instagram"
        ).forEach { phrase ->
            assertNull(
                MessagingTaskRouter.classify(phrase),
                "\"$phrase\" es de pagos: va a la guía segura, no a mensajería"
            )
        }
        // Sanidad: esas frases las atrapa la capa de pagos de V1.11.
        assertNotNull(PaymentGuidePhrases.classify("vinculá mercado pago a instagram"))
        assertNotNull(PaymentGuidePhrases.classify("poné el cvv en instagram"))
    }

    // --- E. Contrato dual de confirmación (INTOCABLE, igual que WhatsApp) ---

    @Test
    fun weakYesNeverSendsInstagramTextButConfirmsReversibleCallTap() {
        listOf("sí", "dale", "ok").forEach { phrase ->
            assertEquals(
                MessagingReplyOutcome.WEAK_REJECTED,
                MessagingTaskRouter.classifyTextSendReply(phrase),
                "\"$phrase\" JAMÁS confirma un envío de texto por Instagram"
            )
        }
        listOf("mandalo", "enviá", "confirmo", "sí mandalo", "sí enviá").forEach { phrase ->
            assertEquals(
                MessagingReplyOutcome.CONFIRM,
                MessagingTaskRouter.classifyTextSendReply(phrase),
                "\"$phrase\" es confirmación fuerte de envío"
            )
        }
        listOf("no", "cancelar", "no lo mandes").forEach { phrase ->
            assertEquals(
                MessagingReplyOutcome.CANCEL,
                MessagingTaskRouter.classifyTextSendReply(phrase),
                "\"$phrase\" siempre cancela el envío"
            )
        }

        // Videollamada: tocar es reversible (se corta) → "sí"/"dale" alcanzan.
        listOf("sí", "dale", "tocalo", "tocá", "confirmo").forEach { phrase ->
            assertEquals(
                MessagingReplyOutcome.CONFIRM,
                MessagingTaskRouter.classifyVideoTapReply(phrase),
                "\"$phrase\" confirma el toque de videollamada"
            )
        }
        listOf("no", "cancelar", "mejor no").forEach { phrase ->
            assertEquals(
                MessagingReplyOutcome.CANCEL,
                MessagingTaskRouter.classifyVideoTapReply(phrase),
                "\"$phrase\" cancela la videollamada"
            )
        }
        assertEquals(
            MessagingReplyOutcome.OTHER,
            MessagingTaskRouter.classifyVideoTapReply("qué hora es")
        )
    }

    // --- F. Slots seguros ---

    @Test
    fun sendSlotsRejectEmptyMessagesAndDigitContacts() {
        assertNull(InstagramDirectPhrases.parseSendText("mandale a Sofia por instagram que"))
        assertNull(
            InstagramDirectPhrases.parseSendText("mandale a 0000005678 por instagram que hola")
        )
        val stripped =
            InstagramDirectPhrases.stripChannelMention("mandale a sofia por instagram que llego tarde")
        assertTrue(stripped.contains("a sofia que llego tarde"))
        assertFalse(stripped.contains("instagram"))
    }

    // --- G. GAS: routing, pendings y call sites únicos ---

    @Test
    fun instagramDispatchRunsBeforeWhatsAppTaskAssistAndCleansPendings() {
        val service = File(
            "src/main/java/com/ojoclaro/android/global/GlobalAssistantService.kt"
        ).readText()

        val igIdx = service.indexOf("if (handleInstagramTaskCommand(text)) return")
        val taskIdx = service.indexOf("if (handleTaskAssistCommand(text)) return")
        assertTrue(
            igIdx in 1 until taskIdx,
            "Instagram corre ANTES de las tareas V1.11 para no perder la videollamada del canal"
        )

        // Los pendientes se limpian en stops, cierre de turno, cancelación,
        // confirmación y vencimiento.
        assertTrue(Regex("pendingInstagramSend = null").findAll(service).count() >= 4)
        assertTrue(Regex("pendingInstagramVideoCallArmedAt = null").findAll(service).count() >= 4)

        // Re-escucha del turno single-shot cuando hay pregunta pendiente.
        val awaiting = service
            .substringAfter("private fun awaitingFollowUp")
            .substringBefore("private fun")
        assertTrue(awaiting.contains("pendingInstagramSend != null"))
        assertTrue(awaiting.contains("pendingInstagramVideoCallArmedAt != null"))

        // UN solo call site para cada toque sensible.
        assertEquals(
            1,
            Regex("tapInstagramVideoCall\\(\\)").findAll(service).count(),
            "el toque de videollamada de Instagram tiene UN solo camino"
        )
        assertEquals(
            1,
            Regex("tapInstagramSend\\(").findAll(service).count(),
            "el toque de enviar de Instagram tiene UN solo camino"
        )

        // Vencimiento duro de 3 minutos.
        assertTrue(service.contains("MESSAGING_PENDING_TTL_MILLIS = 180_000L"))

        // El rechazo débil queda instrumentado.
        val section = service
            .substringAfter("V1.12: Instagram Direct asistido")
            .substringBefore("V1.10.3: lectura de pantalla")
        assertTrue(section.contains("instagramSend outcome=weak_confirmation_rejected"))
        assertTrue(section.contains("no grabo ni envío audios por vos"))
        assertFalse(section.contains("dispatchGesture"), "gestos automatizados prohibidos")
    }

    // --- H. Accesibilidad: detección fuerte y sin toques de audio ---

    @Test
    fun accessibilityDetectorsUseStrongLabelsAndNeverTapAudio() {
        val accessibility = File(
            "src/main/java/com/ojoclaro/android/accessibility/OjoClaroAccessibilityService.kt"
        ).readText()

        // Paquete EXACTO por contrato.
        assertTrue(accessibility.contains("packageName == INSTAGRAM_PACKAGE"))

        // La detección de videollamada usa etiquetas de llamada, jamás
        // "video" a secas (un mensaje con video del hilo la dispararía).
        val finder = accessibility
            .substringAfter("findInstagramVideoCallButton(")
            .substringBefore("hasInstagramVideoCallButtonInternal")
        assertTrue(finder.contains("videollamada"))
        assertTrue(finder.contains("video call"))
        assertFalse(finder.contains("description == \"video\""))

        // Ids reales capturados en device (con fallback por etiqueta).
        assertTrue(accessibility.contains("row_thread_composer_send_button_container"))
        assertTrue(accessibility.contains("row_inbox_username"))
        assertTrue(accessibility.contains("direct_tab"))
        assertTrue(accessibility.contains("avatar_container"))
        assertTrue(accessibility.contains("findInstagramInboxAvatarMetadataMatch"))
        assertTrue(accessibility.contains("avatar_metadata_row_descendant"))

        // El botón de mensaje de voz SOLO se detecta: su función no clickea.
        val audioFn = accessibility
            .substringAfter("private fun findInstagramAudioButtonInternal")
            .substringBefore("private fun findInstagramEndCallButton")
        assertFalse(audioFn.contains("ACTION_CLICK"), "el micrófono jamás se toca")
        assertFalse(audioFn.contains("performAction"), "detección pura, sin acciones")
    }

    // --- I. V1.12.1: matcher de nombres y handles ---

    @Test
    fun nameMatcherResolvesNamesAndDictatedHandles() {
        assertEquals(0, InstagramNameMatcher.score("Sofia", "sofia"))
        assertTrue(InstagramNameMatcher.matches("Sofia", "Sofi"))
        assertTrue(InstagramNameMatcher.matches("Sofia", "sofie"))
        // El handle dictado por voz llega con espacios; el real usa guiones
        // bajos y puede venir con @.
        assertTrue(InstagramNameMatcher.matches("so_roomero", "so roomero"))
        assertTrue(InstagramNameMatcher.matches("so_roomero", "@so_roomero"))
        assertTrue(InstagramNameMatcher.matches("so_roomero", "so roomero "))
        assertTrue(
            InstagramNameMatcher.matchesInboxAvatarMetadata(
                "Abrir historia de so_roomero",
                "so_roomero"
            )
        )
        assertTrue(
            InstagramNameMatcher.matchesInboxAvatarMetadata(
                "so_roomero",
                "so roomero"
            )
        )
        assertTrue(
            InstagramNameMatcher.matchesInboxAvatarMetadata(
                "Abrir historia de so_roomero",
                "@so_roomero"
            )
        )
        assertFalse(
            InstagramNameMatcher.matchesInboxAvatarMetadata(
                "Sofia, vista previa privada de so_roomero",
                "so_roomero"
            )
        )
        assertFalse(InstagramNameMatcher.matches("Francisco Vera", "sofia"))
        assertFalse(InstagramNameMatcher.matches("Lautaro Riquelme", "so roomero"))
        assertFalse(InstagramNameMatcher.matches(null, "sofia"))
    }

    @Test
    fun inboxAvatarHandleFallbackIsRowFirstAndNeverClicksTheAvatar() {
        val accessibility = File(
            "src/main/java/com/ojoclaro/android/accessibility/OjoClaroAccessibilityService.kt"
        ).readText()

        val openChat = accessibility
            .substringAfter("private fun openInstagramChatByVisibleNameInternal")
            .substringBefore("private data class InstagramInboxAvatarMetadataMatch")
        assertTrue(openChat.contains("for (row in rowNodes)"))
        assertTrue(openChat.contains("findInstagramInboxAvatarMetadataMatch(row, targetName)"))
        assertTrue(openChat.contains("bestNode = row"))
        assertTrue(openChat.contains("bestField = \"avatar_metadata_row_descendant\""))
        assertFalse(openChat.contains("bestNode = avatar"))
        assertFalse(openChat.contains("findAccessibilityNodeInfosByViewId(IG_ID_INBOX_AVATAR)"))

        val rowDescendantMatcher = accessibility
            .substringAfter("private fun findInstagramInboxAvatarMetadataMatch")
            .substringBefore("private fun findInstagramComposer")
        assertTrue(rowDescendantMatcher.contains("viewId == IG_ID_INBOX_AVATAR"))
        assertTrue(rowDescendantMatcher.contains("scoreInboxAvatarMetadata(metadata, targetName)"))

        // Row "Sofia" may expose the real handle only in avatar metadata.
        assertTrue(
            InstagramNameMatcher.matchesInboxAvatarMetadata(
                "Abrir historia de so_roomero",
                "so_roomero"
            )
        )
    }

    // --- J. V1.12.1: "volver"/"atrás" como ruta local global ---

    @Test
    fun globalBackReusesPhase2AParserAndNeverRunsBeforeCriticalPendings() {
        assertEquals(ScreenNavigationCommand.Back, ScreenNavigationCommandParser.parse("volver"))
        assertEquals(ScreenNavigationCommand.Back, ScreenNavigationCommandParser.parse("atrás"))
        assertEquals(ScreenNavigationCommand.Back, ScreenNavigationCommandParser.parse("andá atrás"))
        assertEquals(ScreenNavigationCommand.Back, ScreenNavigationCommandParser.parse("volvé"))

        val service = File(
            "src/main/java/com/ojoclaro/android/global/GlobalAssistantService.kt"
        ).readText()
        val backIdx = service.indexOf("if (handleGlobalBackCommand(text)) return")
        val screenIdx = service.indexOf("if (handleGlobalScreenQuery(text)) return")
        val companionIdx = service.indexOf("EstelaCompanionPhrases.respond(text)")
        assertTrue(
            backIdx in (screenIdx + 1) until companionIdx,
            "back global corre tras el screen query y antes de companion/fallback"
        )
        // Con un envío o una llamada pendiente, la respuesta la consume el
        // pending ANTES: "volver" jamás puede disparar la ruta de back ni,
        // mucho menos, un envío o una llamada.
        val waSendReplyIdx = service.indexOf("if (handlePendingWhatsAppSendReply(text)) return")
        val igSendReplyIdx = service.indexOf("if (handlePendingInstagramSendReply(text)) return")
        val igVideoReplyIdx =
            service.indexOf("if (handlePendingInstagramVideoCallReply(text)) return")
        assertTrue(waSendReplyIdx in 1 until backIdx)
        assertTrue(igSendReplyIdx in 1 until backIdx)
        assertTrue(igVideoReplyIdx in 1 until backIdx)
        assertTrue(service.contains("navigationBack outcome="))
        assertTrue(
            service.contains("ScreenNavigationCommandParser.parse(text)"),
            "reusa el parser de Fase 2A, sin frases nuevas"
        )
    }

    // --- K. V1.12.1: corte de llamada SOLO para limpieza de QA ---

    @Test
    fun qaEndCallHelperHasHardGuardsAndIsNeverWiredToVoiceRouting() {
        val accessibility = File(
            "src/main/java/com/ojoclaro/android/accessibility/OjoClaroAccessibilityService.kt"
        ).readText()

        val finder = accessibility
            .substringAfter("private fun findInstagramEndCallButton")
            .substringBefore("private fun tapInstagramEndCallForQaInternal")
        assertTrue(finder.contains("finalizar llamada"))
        assertTrue(finder.contains("end call"))
        assertTrue(finder.contains("colgar"))

        val tapFn = accessibility
            .substringAfter("private fun tapInstagramEndCallForQaInternal")
            .substringBefore("private fun firstVisibleByViewId")
        assertTrue(tapFn.contains("packageNameLooksLikeInstagram"), "paquete EXACTO")
        assertTrue(tapFn.contains("NotInCallScreen"), "fuera de la llamada no toca")
        assertTrue(tapFn.contains("instagramEndCall outcome=not_found"), "sin botón: honesto y sin tap")

        // QA-only: el routing de voz del GAS jamás llama al corte.
        val service = File(
            "src/main/java/com/ojoclaro/android/global/GlobalAssistantService.kt"
        ).readText()
        assertEquals(
            0,
            Regex("tapInstagramEndCallForQa").findAll(service).count(),
            "ninguna frase de usuario corta llamadas"
        )
        // Acción de debug registrada para poder limpiarse por adb sin
        // force-stop (force-stop mata el servicio de accesibilidad).
        assertTrue(accessibility.contains("DEBUG_IG_ENDCALL_ACTION"))
    }
}
