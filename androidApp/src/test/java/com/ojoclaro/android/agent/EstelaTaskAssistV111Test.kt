package com.ojoclaro.android.agent

import com.ojoclaro.android.agent.EstelaTaskIntentRouter.SafetyClass
import com.ojoclaro.android.agent.EstelaTaskIntentRouter.TaskIntent
import com.ojoclaro.android.agent.payments.PaymentGuidePhrases
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppCallPhrases
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppVoiceSendPhrases
import com.ojoclaro.android.outdoor.OutdoorPhrases
import com.ojoclaro.android.outdoor.RideMonitorPhrases
import com.ojoclaro.android.voice.EstelaColloquialNormalizer
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * V1.11 — asistente de tareas reales:
 *  - normalizador coloquial argentino;
 *  - router de intención nombrado + matriz de seguridad;
 *  - videollamada con confirmación (tocar SOLO tras sí explícito);
 *  - audio: guía del gesto, jamás automatizado;
 *  - monitoreo de viaje: solo lectura;
 *  - pagos/tarjetas: solo guía; ingresar datos/confirmar dinero = prohibido.
 *
 * Contrato INTOCABLE verificado acá: "sí"/"dale" JAMÁS envían un mensaje
 * de texto (solo enviá/mandalo/confirmo), aunque SÍ confirman acciones
 * reversibles como tocar el botón de videollamada.
 */
class EstelaTaskAssistV111Test {

    // --- A. Normalizador coloquial ---

    @Test
    fun colloquialPhrasesMapToCanonicalCommands() {
        assertEquals("lee la pantalla", EstelaColloquialNormalizer.normalize("fijate qué dice"))
        assertEquals("explicame esta pantalla", EstelaColloquialNormalizer.normalize("¿qué onda esto?"))
        assertEquals("que puedo tocar", EstelaColloquialNormalizer.normalize("tocá ahí"))
        assertEquals("abri el primer chat", EstelaColloquialNormalizer.normalize("pasame al primero"))
        assertEquals(
            "avisame cuando llegue el viaje",
            EstelaColloquialNormalizer.normalize("avisame cuando llegue")
        )
        // Tokens sueltos: lunfardo → canónico.
        assertEquals("quiero pagar el viaje", EstelaColloquialNormalizer.normalize("quiero garpar el viaje"))
        assertEquals("no tengo plata", EstelaColloquialNormalizer.normalize("no tengo guita"))
        // Muletillas iniciales se pelan sin tocar el resto.
        assertEquals("lee la pantalla", EstelaColloquialNormalizer.normalize("che estela fijate que dice"))
    }

    @Test
    fun colloquialNormalizerNeverBreaksExistingCommands() {
        listOf(
            "dónde estoy",
            "leé la pantalla",
            "mandale a Marco que llego tarde",
            "enviá",
            "cancelar",
            "sí",
            "dale"
        ).forEach { phrase ->
            val normalized = EstelaColloquialNormalizer.normalize(phrase)
            // "dale" solo NO es muletilla: es una respuesta de confirmación.
            assertTrue(normalized.isNotBlank(), "jamás vacía: \"$phrase\"")
        }
        assertEquals("dale", EstelaColloquialNormalizer.normalize("dale"))
        assertEquals("sí", EstelaColloquialNormalizer.normalize("sí"))
        // Sin slang, el texto original pasa intacto (con mayúsculas y todo).
        assertEquals(
            "mandale a Marco que llego tarde",
            EstelaColloquialNormalizer.normalize("mandale a Marco que llego tarde")
        )
    }

    // --- B. Router de intención ---

    @Test
    fun routerClassifiesRealPhrases() {
        val cases = mapOf(
            "leé la pantalla" to TaskIntent.READ_SCREEN,
            "qué puedo tocar" to TaskIntent.EXPLAIN_SCREEN,
            "ayudame con esta pantalla" to TaskIntent.EXPLAIN_SCREEN,
            "mandale un mensaje a Marco por WhatsApp diciendo que llego tarde" to
                TaskIntent.SEND_WHATSAPP_TEXT_PENDING_CONFIRMATION,
            "enviá el mensaje" to TaskIntent.SEND_WHATSAPP_TEXT_PENDING_CONFIRMATION,
            "enviá" to TaskIntent.CONFIRM_SEND_WHATSAPP_TEXT,
            "quiero hacer una videollamada con Marco" to
                TaskIntent.START_WHATSAPP_VIDEO_CALL_PENDING_CONFIRMATION,
            "llamalo por video" to TaskIntent.START_WHATSAPP_VIDEO_CALL_PENDING_CONFIRMATION,
            "mandale un audio a Marco" to TaskIntent.START_WHATSAPP_AUDIO_FLOW,
            "activame el audio en el chat de Marco" to TaskIntent.START_WHATSAPP_AUDIO_FLOW,
            "pedime un DiDi" to TaskIntent.REQUEST_RIDE_PENDING_CONFIRMATION,
            "avisame cuando llegue el viaje" to TaskIntent.MONITOR_RIDE_STATUS,
            "vinculá Mercado Pago a DiDi" to TaskIntent.GUIDE_PAYMENT_LINKING,
            "registrame una tarjeta" to TaskIntent.GUIDE_CARD_REGISTRATION,
            "poné el CVV por mí" to TaskIntent.SENSITIVE_PAYMENT_BLOCK,
            "confirmá el pago" to TaskIntent.SENSITIVE_PAYMENT_BLOCK,
            "abrí maps" to TaskIntent.OPEN_APP,
            "me gusta el dulce de leche" to TaskIntent.UNKNOWN
        )
        cases.forEach { (phrase, expected) ->
            assertEquals(
                expected,
                EstelaTaskIntentRouter.classify(phrase),
                "intent equivocado para: \"$phrase\""
            )
        }
    }

    @Test
    fun safetyPolicyMatrixMatchesTheMission() {
        assertEquals(SafetyClass.SAFE, EstelaTaskIntentRouter.policyFor(TaskIntent.READ_SCREEN))
        assertEquals(SafetyClass.SAFE, EstelaTaskIntentRouter.policyFor(TaskIntent.EXPLAIN_SCREEN))
        assertEquals(SafetyClass.SAFE, EstelaTaskIntentRouter.policyFor(TaskIntent.MONITOR_RIDE_STATUS))
        assertEquals(
            SafetyClass.CONFIRM_REQUIRED,
            EstelaTaskIntentRouter.policyFor(TaskIntent.SEND_WHATSAPP_TEXT_PENDING_CONFIRMATION)
        )
        assertEquals(
            SafetyClass.CONFIRM_REQUIRED,
            EstelaTaskIntentRouter.policyFor(TaskIntent.START_WHATSAPP_VIDEO_CALL_PENDING_CONFIRMATION)
        )
        assertEquals(
            SafetyClass.SENSITIVE_GUIDED,
            EstelaTaskIntentRouter.policyFor(TaskIntent.GUIDE_CARD_REGISTRATION)
        )
        assertEquals(
            SafetyClass.FORBIDDEN_AUTOMATIC,
            EstelaTaskIntentRouter.policyFor(TaskIntent.SENSITIVE_PAYMENT_BLOCK)
        )
    }

    // --- Contrato dual de confirmación (INTOCABLE) ---

    @Test
    fun weakYesNeverSendsTextButCanConfirmReversibleCallTap() {
        listOf("sí", "dale", "ok", "sí dale").forEach { phrase ->
            assertFalse(
                WhatsAppVoiceSendPhrases.isConfirmSend(phrase),
                "\"$phrase\" JAMÁS confirma un envío de texto"
            )
        }
        // Tocar videollamada es reversible (se corta): "sí"/"dale" alcanzan.
        assertTrue(WhatsAppCallPhrases.isConfirmTap("sí"))
        assertTrue(WhatsAppCallPhrases.isConfirmTap("dale"))
        assertTrue(WhatsAppCallPhrases.isConfirmTap("tocalo"))
        // "no" jamás es confirmación de nada.
        assertFalse(WhatsAppCallPhrases.isConfirmTap("no"))
    }

    // --- Videollamada ---

    @Test
    fun videoCallPhrasesParseContactAndNeverMisfire() {
        assertEquals(
            "marco",
            WhatsAppCallPhrases.parseVideoCall("quiero hacer videollamada con Marco")?.contactQuery
        )
        assertEquals(
            "marco",
            WhatsAppCallPhrases.parseVideoCall(
                "quiero entrar a WhatsApp y hacer videollamada con Marco"
            )?.contactQuery
        )
        assertNull(WhatsAppCallPhrases.parseVideoCall("llamalo por video")?.contactQuery)
        // Negativos: videos del chat no son llamadas.
        assertNull(WhatsAppCallPhrases.parseVideoCall("poné el video"))
        assertNull(WhatsAppCallPhrases.parseVideoCall("mandale un video a Marco"))
        assertNull(WhatsAppCallPhrases.parseVideoCall("mirá el video que mandó"))
    }

    @Test
    fun videoCallTapOnlyHappensAfterPendingConfirmation() {
        val service = File(
            "src/main/java/com/ojoclaro/android/global/GlobalAssistantService.kt"
        ).readText()
        // El toque vive SOLO en la respuesta al pending (un único call site).
        val tapCount = Regex("tapWhatsAppVideoCall\\(\\)").findAll(service).count()
        assertEquals(1, tapCount, "el toque de videollamada tiene UN solo camino")
        val replyIdx = service.indexOf("private fun handlePendingVideoCallReply")
        val tapIdx = service.indexOf("OjoClaroAccessibilityService.tapWhatsAppVideoCall()")
        assertTrue(replyIdx in 1 until tapIdx, "el toque está dentro del handler del pending")
        // El pending muere con stop/cierre (4+ limpiezas).
        assertTrue(
            Regex("pendingVideoCallTap = false").findAll(service).count() >= 4,
            "pendingVideoCallTap debe limpiarse en stop/silencio/cierre/respuesta"
        )
        // La detección del botón JAMÁS matchea "video" a secas (mensajes de
        // video del chat): solo etiquetas de llamada.
        val accessibility = File(
            "src/main/java/com/ojoclaro/android/accessibility/OjoClaroAccessibilityService.kt"
        ).readText()
        val finder = accessibility
            .substringAfter("findWhatsAppVideoCallButton(")
            .substringBefore("hasWhatsAppVideoCallButtonInternal")
        assertTrue(finder.contains("videollamada"))
        assertFalse(
            finder.contains("description == \"video\""),
            "\"video\" a secas tocaría un mensaje de video"
        )
    }

    // --- Audio: guía, jamás gesto automatizado ---

    @Test
    fun audioFlowIsGuidedAndNeverAutomatesGestures() {
        assertTrue(EstelaTaskIntentRouter.isAudioFlowActivation("activame el audio en el chat de Marco"))
        assertFalse(EstelaTaskIntentRouter.isAudioFlowActivation("poné el audio"))
        val service = File(
            "src/main/java/com/ojoclaro/android/global/GlobalAssistantService.kt"
        ).readText()
        assertTrue(service.contains("speakAudioFlowGuide"))
        assertTrue(service.contains("Yo no grabo ni envío audios por vos"))
        // La sección V1.11 jamás automatiza gestos ni clicks de envío.
        val section = service
            .substringAfter("V1.11: tareas asistidas")
            .substringBefore("// --- V1.10.3")
        assertTrue(section.isNotBlank())
        assertFalse(section.contains("dispatchGesture("), "gestos automatizados prohibidos")
        assertFalse(section.contains("tapWhatsAppSend("), "la sección de tareas jamás envía texto")
    }

    // --- Monitoreo de viaje ---

    @Test
    fun rideMonitorOnlyReadsAndHasHardBudget() {
        assertTrue(RideMonitorPhrases.isStartCommand("avisame cuando llegue el viaje"))
        assertTrue(RideMonitorPhrases.isStartCommand("seguí mirando el viaje"))
        assertTrue(RideMonitorPhrases.isStopCommand("dejá de mirar"))
        assertFalse(RideMonitorPhrases.isStartCommand("dónde estoy"))
        assertFalse(RideMonitorPhrases.isStartCommand("avisame cualquier cosa"))

        val service = File(
            "src/main/java/com/ojoclaro/android/global/GlobalAssistantService.kt"
        ).readText()
        val monitor = service
            .substringAfter("private fun startRideMonitor")
            .substringBefore("private fun isRideAppPackage")
        assertTrue(monitor.contains("RIDE_MONITOR_MAX_MILLIS"), "presupuesto duro de tiempo")
        listOf("performClick", "ACTION_CLICK", "tapWhatsApp", "dispatchGesture").forEach {
            assertFalse(monitor.contains(it), "el monitoreo SOLO lee: $it")
        }
        // El estado se anuncia con copy prudente, sin afirmar lo que no se ve.
        assertTrue(
            RideMonitorPhrases.STATUS_KEYWORDS.any { it.second.contains("parece") },
            "los estados inciertos se hablan con 'parece'"
        )
    }

    // --- Pagos: solo guía ---

    @Test
    fun paymentsAreGuideOnlyAndSensitiveRequestsAreRefused() {
        assertEquals(
            PaymentGuidePhrases.Kind.LINK_PAYMENT,
            PaymentGuidePhrases.classify("vinculá mercado pago a didi")
        )
        assertEquals(
            PaymentGuidePhrases.Kind.REGISTER_CARD,
            PaymentGuidePhrases.classify("registrame una tarjeta")
        )
        listOf(
            "poné el cvv 123",
            "ingresá la clave",
            "confirmá el pago",
            "pagalo vos",
            "hacé la transferencia"
        ).forEach { phrase ->
            assertEquals(
                PaymentGuidePhrases.Kind.SENSITIVE_BLOCK,
                PaymentGuidePhrases.classify(phrase),
                "pedido sensible debe rechazarse: \"$phrase\""
            )
        }
        // Las guías dicen el límite en voz alta.
        assertTrue(PaymentGuidePhrases.GUIDE_PREAMBLE.contains("no voy a ingresar"))
        assertTrue(PaymentGuidePhrases.SENSITIVE_REFUSAL.contains("ni con confirmación"))
        // Y la guía de tarjeta deja claro quién escribe los números.
        assertTrue(
            PaymentGuidePhrases.spokenGuide(PaymentGuidePhrases.Kind.REGISTER_CARD)
                .contains("los escribís vos")
        )
    }

    // --- BUG 1 (HIGH): toda frase financiera sensible se bloquea LOCAL ---

    @Test
    fun everyCriticalFinancialPhraseClassifiesLocallyAndNeverReachesConversation() {
        // Pagar / pago: acción que Estela jamás ejecuta → bloqueo.
        listOf(
            "pagar", "pagá", "paga", "tocá pagar", "toca pagar",
            "apretá pagar", "apreta pagar", "confirmá el pago", "confirma el pago",
            "pagar ahora", "pagar ya", "botón pagar", "boton pagar",
            "realizar pago", "hacer pago"
        ).forEach { phrase ->
            assertEquals(
                PaymentGuidePhrases.Kind.SENSITIVE_BLOCK,
                PaymentGuidePhrases.classify(phrase),
                "pago debe bloquearse: \"$phrase\""
            )
        }
        // Dinero / transferencia → bloqueo.
        listOf(
            "mandá plata", "manda plata", "mandá dinero", "manda dinero",
            "transferí plata", "transferi plata", "transferir plata",
            "enviar plata", "enviá plata", "envia plata", "mandale plata",
            "enviar dinero", "transferencia", "hacer transferencia"
        ).forEach { phrase ->
            assertEquals(
                PaymentGuidePhrases.Kind.SENSITIVE_BLOCK,
                PaymentGuidePhrases.classify(phrase),
                "dinero/transferencia debe bloquearse: \"$phrase\""
            )
        }
        // Tarjetas sensibles (datos) → bloqueo.
        listOf(
            "poné el cvv", "pone el cvv", "cvv", "código de seguridad",
            "codigo de seguridad", "número de tarjeta", "numero de tarjeta"
        ).forEach { phrase ->
            assertEquals(
                PaymentGuidePhrases.Kind.SENSITIVE_BLOCK,
                PaymentGuidePhrases.classify(phrase),
                "dato sensible de tarjeta debe bloquearse: \"$phrase\""
            )
        }
        // Registrar/agregar/cargar/poner tarjeta → guía.
        listOf(
            "registrar tarjeta", "registrame una tarjeta", "agregá tarjeta",
            "agrega tarjeta", "cargar tarjeta", "poner tarjeta"
        ).forEach { phrase ->
            assertEquals(
                PaymentGuidePhrases.Kind.REGISTER_CARD,
                PaymentGuidePhrases.classify(phrase),
                "registrar tarjeta es guía: \"$phrase\""
            )
        }
        // Mercado Pago → guía/bloqueo seguro (jamás UNKNOWN).
        listOf(
            "mercado pago", "mercadopago", "vinculá mercado pago",
            "vincula mercado pago", "pagar con mercado pago"
        ).forEach { phrase ->
            assertTrue(
                PaymentGuidePhrases.classify(phrase) != null,
                "mercado pago jamás cae a conversacional: \"$phrase\""
            )
        }
    }

    @Test
    fun financialBlockingNeverHitsNormalOrCameraPhrases() {
        // Frases normales NO financieras → no se bloquean (null).
        listOf(
            "dónde estoy", "leé la pantalla", "qué hora es",
            "contame un chiste", "cómo estás", "leé el texto",
            "qué dice este cartel"
        ).forEach { phrase ->
            assertNull(
                PaymentGuidePhrases.classify(phrase),
                "frase no financiera no debe bloquearse: \"$phrase\""
            )
        }
        // CRÍTICO: "apagá la cámara"/"apago" contienen "paga"/"pago" como
        // substring; el matcheo por palabra completa NO los bloquea (si no,
        // jamás se podría cerrar la cámara).
        listOf(
            "apagá la cámara", "apaga la camara", "apagar cámara",
            "apago la cámara", "cerrá la cámara", "pausá la cámara"
        ).forEach { phrase ->
            assertNull(
                PaymentGuidePhrases.classify(phrase),
                "comando de cámara NO es financiero: \"$phrase\""
            )
        }
    }

    // --- Routing: orden y no-regresión ---

    @Test
    fun taskAssistRunsBeforeOutdoorAndNeverStealsGps() {
        val service = File(
            "src/main/java/com/ojoclaro/android/global/GlobalAssistantService.kt"
        ).readText()
        val taskIdx = service.indexOf("if (handleTaskAssistCommand(text)) return")
        val outdoorIdx = service.indexOf("if (handleOutdoorCommand(text)) return")
        assertTrue(taskIdx in 1 until outdoorIdx, "pagos con 'didi' no van al fast path de transporte")
        // GPS y seguridad intactos: ninguna frase nueva los captura.
        assertIs<OutdoorPhrases.Command.WhereAmI>(OutdoorPhrases.parse("dónde estoy"))
        assertNull(PaymentGuidePhrases.classify("dónde estoy"))
        assertNull(WhatsAppCallPhrases.parseVideoCall("dónde estoy"))
        assertIs<OutdoorPhrases.Command.SafetyQuery>(OutdoorPhrases.parse("¿es seguro cruzar?"))
        // "vinculá mercado pago a didi" clasifica como pago, no como viaje.
        assertEquals(
            TaskIntent.GUIDE_PAYMENT_LINKING,
            EstelaTaskIntentRouter.classify("vinculá mercado pago a didi")
        )
    }
}
