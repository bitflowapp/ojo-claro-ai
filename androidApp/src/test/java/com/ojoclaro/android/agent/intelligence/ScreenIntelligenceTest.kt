package com.ojoclaro.android.agent.intelligence

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertIs

/**
 * V2.2 — cableado de inteligencia de pantalla al runtime. PURO.
 *
 * Cubre el parser (qué frases reclama y cuáles deja pasar), el narrador
 * (percepción y "a quién le mando") y la resolución de contacto con los alias
 * autorizados (incluyendo el fallback por número de Marco Luna, SOLO abrir).
 */
class ScreenIntelligenceTest {

    // ---- parser: percepción ----

    @Test
    fun parsesWhoIsVisible() {
        assertEquals(ScreenIntent.WhoIsVisible, ScreenIntelligencePhrases.parse("¿qué personas aparecen?"))
        assertEquals(ScreenIntent.WhoIsVisible, ScreenIntelligencePhrases.parse("quiénes aparecen"))
        assertEquals(ScreenIntent.WhoIsVisible, ScreenIntelligencePhrases.parse("qué contactos hay"))
    }

    @Test
    fun parsesWhichChat() {
        assertEquals(ScreenIntent.WhichChat, ScreenIntelligencePhrases.parse("¿qué chat estoy viendo?"))
        assertEquals(ScreenIntent.WhichChat, ScreenIntelligencePhrases.parse("en qué chat estoy"))
    }

    @Test
    fun parsesWhoAmISending() {
        assertEquals(
            ScreenIntent.WhoAmISending,
            ScreenIntelligencePhrases.parse("¿a quién le estoy por mandar esto?")
        )
        assertEquals(ScreenIntent.WhoAmISending, ScreenIntelligencePhrases.parse("a quién le mando esto"))
    }

    // ---- parser: abrir chat ----

    @Test
    fun parsesOpenChatPlainName() {
        val intent = ScreenIntelligencePhrases.parse("abrí el chat de Marco")
        assertIs<ScreenIntent.OpenChat>(intent)
        assertEquals("marco", intent.rawName)
        assertEquals(TargetApp.UNKNOWN, intent.app)
    }

    @Test
    fun parsesOpenChatWithWhatsAppApp() {
        val intent = ScreenIntelligencePhrases.parse("abrí el chat de Marco Luna en WhatsApp")
        assertIs<ScreenIntent.OpenChat>(intent)
        assertEquals("marco luna", intent.rawName)
        assertEquals(TargetApp.WHATSAPP, intent.app)
    }

    @Test
    fun parsesOpenChatWithSpokenWhatsAppAlias() {
        // QA fuzz MEDIUM #2: el alias hablado ("wsp"/"guasap") debe reconocerse como
        // WhatsApp y salir del nombre (antes quedaba "ana prueba en wsp").
        val intent = ScreenIntelligencePhrases.parse("encontrá el chat de Ana Prueba en wsp")
        assertIs<ScreenIntent.OpenChat>(intent)
        assertEquals("ana prueba", intent.rawName)
        assertEquals(TargetApp.WHATSAPP, intent.app)
    }

    @Test
    fun parsesSearchByPersonWhenWhatsAppAppAnchorsThePhrase() {
        // QA fuzz MEDIUM #2: "buscá a X en <whatsapp-alias>" (sin la palabra "chat")
        // entra al MISMO flujo seguro de abrir-chat; el ancla de app desambigua.
        listOf(
            "buscá a Ana Prueba en guasap",
            "porfa buscá a Ana Prueba en wasa"
        ).forEach { phrase ->
            val intent = ScreenIntelligencePhrases.parse(phrase)
            assertIs<ScreenIntent.OpenChat>(intent)
            assertEquals(TargetApp.WHATSAPP, intent.app)
            assertEquals("ana prueba", intent.rawName)
        }
    }

    @Test
    fun searchWithoutWhatsAppAnchorDoesNotFalseMatch() {
        // Sin ancla de app NO debe robar la frase (evita "buscá mis llaves").
        assertNull(ScreenIntelligencePhrases.parse("buscá mis llaves en casa"))
        assertNull(ScreenIntelligencePhrases.parse("buscá un restaurante"))
    }

    @Test
    fun parsesOpenChatWithInstagramApp() {
        val intent = ScreenIntelligencePhrases.parse("abrí el chat de Sofi en Instagram")
        assertIs<ScreenIntent.OpenChat>(intent)
        assertEquals("sofi", intent.rawName)
        assertEquals(TargetApp.INSTAGRAM, intent.app)
    }

    @Test
    fun parsesOpenConversationSynonym() {
        val intent = ScreenIntelligencePhrases.parse("abrime la conversación con Juan")
        assertIs<ScreenIntent.OpenChat>(intent)
        assertEquals("juan", intent.rawName)
    }

    // "buscá/buscar/encontrá el chat de X" deben entrar al MISMO OpenChat seguro que
    // "abrí el chat de X" (antes caían a no_local_match → LLM).
    @Test
    fun parsesBuscarYEncontrarChatLikeAbrir() {
        listOf(
            "buscá el chat de Sofi",
            "busca el chat de Sofi",
            "buscar el chat de Sofi",
            "buscá la conversación de Sofi",
            "busca la conversación de Sofi",
            "buscar la conversación de Sofi",
            "encontrá el chat de Sofi",
            "encontra el chat de Sofi",
            "encontrar el chat de Sofi"
        ).forEach { phrase ->
            val intent = ScreenIntelligencePhrases.parse(phrase)
            assertIs<ScreenIntent.OpenChat>(intent, "debería ser OpenChat: \"$phrase\"")
            assertEquals("sofi", intent.rawName, "nombre extraído de: \"$phrase\"")
            assertEquals(TargetApp.UNKNOWN, intent.app)
        }
        // ancla de seguridad: sin "chat/conversación" NO es OpenChat ("buscá mis llaves").
        assertNull(ScreenIntelligencePhrases.parse("buscá mis llaves"))
        assertNull(ScreenIntelligencePhrases.parse("encontrá la salida"))
    }

    // ---- parser: lo que NO debe reclamar ----

    @Test
    fun doesNotClaimCompose() {
        // "mandale a X que…" es de WhatsAppSmartComposeParser, no de acá.
        assertNull(ScreenIntelligencePhrases.parse("mandale a Marco que estoy llegando"))
    }

    @Test
    fun doesNotClaimOpenApp() {
        // "abrí WhatsApp" abre la app, no un chat: no debe matchear.
        assertNull(ScreenIntelligencePhrases.parse("abrí WhatsApp"))
        assertNull(ScreenIntelligencePhrases.parse("abrime Instagram"))
    }

    @Test
    fun doesNotClaimScreenQueryPhrases() {
        // Estas son de ScreenQueryPhrases (ruta existente): no las robamos.
        assertNull(ScreenIntelligencePhrases.parse("qué aparece en pantalla"))
        assertNull(ScreenIntelligencePhrases.parse("qué estoy viendo"))
        assertNull(ScreenIntelligencePhrases.parse("dónde estoy"))
    }

    @Test
    fun doesNotClaimOpenChatWithoutName() {
        assertNull(ScreenIntelligencePhrases.parse("abrí el chat"))
    }

    // ---- narrador ----

    @Test
    fun whoAmISendingWithNoPendingIsSafe() {
        val spoken = ScreenIntelligenceNarrator.whoAmISending(null)
        assertTrue(spoken.contains("no tengo", ignoreCase = true))
    }

    @Test
    fun whoAmISendingNamesRecipientButPromisesNoSend() {
        val spoken = ScreenIntelligenceNarrator.whoAmISending("Marco Luna")
        assertTrue(spoken.contains("Marco Luna"))
        assertTrue(spoken.contains("no voy a enviar", ignoreCase = true))
    }

    @Test
    fun whoIsVisibleListsContacts() {
        val model = ScreenReasoner.reason(
            RawScreen(
                "com.whatsapp",
                listOf(
                    ReasonerNode(text = "Marco Luna", isClickable = true),
                    ReasonerNode(text = "Sofía", isClickable = true),
                    ReasonerNode(text = "Juan", isClickable = true),
                )
            )
        )
        val spoken = ScreenIntelligenceNarrator.whoIsVisible(model)
        assertTrue(spoken.contains("Marco Luna"))
        assertTrue(spoken.contains("Sofía"))
    }

    @Test
    fun whichChatOutsideConversationIsHonest() {
        val model = ScreenReasoner.reason(
            RawScreen(
                "com.whatsapp",
                listOf(
                    ReasonerNode(text = "Marco Luna", isClickable = true),
                    ReasonerNode(text = "Sofía", isClickable = true),
                    ReasonerNode(text = "Juan", isClickable = true),
                )
            )
        )
        val spoken = ScreenIntelligenceNarrator.whichChat(model)
        assertTrue(spoken.contains("No estás dentro de un chat", ignoreCase = true))
    }

    // ---- resolución con alias autorizados ----

    @Test
    fun marcoResolvesToPhoneFallbackWhenNotVisible() {
        // El número de fallback ya no se hardcodea en producción (PII removida);
        // acá se prueba el MECANISMO con un alias sintético local.
        val testAliases = listOf(
            ContactAlias(
                alias = "marco",
                canonicalName = "Marco Luna",
                app = TargetApp.WHATSAPP,
                phoneFallback = "+5491150000000",
            )
        )
        val resolution = ContactResolver.resolve(
            query = "marco",
            app = TargetApp.WHATSAPP,
            candidates = emptyList(),
            aliases = testAliases,
        )
        assertIs<ContactResolution.Resolved>(resolution)
        assertEquals(ResolutionSource.PHONE_FALLBACK, resolution.match.source)
        assertEquals("Marco Luna", resolution.match.name)
        assertEquals("+5491150000000", resolution.match.phoneFallback)
    }

    @Test
    fun visibleCandidateWinsOverPhoneFallback() {
        // Si Marco está VISIBLE, se resuelve por la pantalla (no por número).
        val resolution = ContactResolver.resolve(
            query = "marco",
            app = TargetApp.WHATSAPP,
            candidates = listOf(
                ContactCandidate("Marco Luna", index = 0, app = TargetApp.WHATSAPP, kind = CandidateKind.CHAT)
            ),
            aliases = ScreenIntelligenceAliases.WHATSAPP,
        )
        assertIs<ContactResolution.Resolved>(resolution)
        assertTrue(
            resolution.match.source == ResolutionSource.EXACT_VISIBLE ||
                resolution.match.source == ResolutionSource.PARTIAL_VISIBLE
        )
        assertNull(resolution.match.phoneFallback)
    }

    @Test
    fun ambiguousWhenTwoDistinctStrongCandidates() {
        val resolution = ContactResolver.resolve(
            query = "marco",
            app = TargetApp.WHATSAPP,
            candidates = listOf(
                ContactCandidate("Marco Luna", index = 0, app = TargetApp.WHATSAPP, kind = CandidateKind.CHAT),
                ContactCandidate("Marco Pérez", index = 1, app = TargetApp.WHATSAPP, kind = CandidateKind.CHAT),
            ),
            aliases = ScreenIntelligenceAliases.WHATSAPP,
        )
        assertIs<ContactResolution.Ambiguous>(resolution)
    }

    // ---- ActiveAppResolver (fuente estable de app activa) ----

    private fun igNode() = ReasonerNode(text = "Mensaje", isEditable = true)
    private fun waNode() = ReasonerNode(text = "WhatsApp", isHeading = true)

    @Test
    fun rawPackageInstagramWins() {
        val r = ActiveAppResolver.resolve("com.instagram.android", false, false, emptyList())
        assertEquals(ActiveApp.INSTAGRAM, r.app)
        assertEquals(ActiveAppSource.RAW_PACKAGE, r.source)
    }

    @Test
    fun rawPackageWhatsAppWins() {
        val r = ActiveAppResolver.resolve("com.whatsapp", false, false, emptyList())
        assertEquals(ActiveApp.WHATSAPP, r.app)
        assertEquals(ActiveAppSource.RAW_PACKAGE, r.source)
    }

    @Test
    fun rawPackageWhatsAppBusinessDetected() {
        val r = ActiveAppResolver.resolve("com.whatsapp.w4b", false, false, emptyList())
        assertEquals(ActiveApp.WHATSAPP_BUSINESS, r.app)
    }

    @Test
    fun unreliablePackagePlusInstagramMarkersGivesInstagram() {
        // rawPackage = overlay de Estela (poco confiable) pero marcadores IG.
        val r = ActiveAppResolver.resolve("com.ojoclaro.android", false, true, listOf(igNode()))
        assertEquals(ActiveApp.INSTAGRAM, r.app)
        assertEquals(ActiveAppSource.IG_MARKERS, r.source)
    }

    @Test
    fun nullPackagePlusExternalWhatsAppGivesWhatsApp() {
        val r = ActiveAppResolver.resolve(null, true, false, listOf(waNode()))
        assertEquals(ActiveApp.WHATSAPP, r.app)
        assertEquals(ActiveAppSource.EXTERNAL_APP, r.source)
    }

    @Test
    fun unreliablePackagePlusWhatsAppNodeMarkersGivesWhatsApp() {
        val r = ActiveAppResolver.resolve("", false, false, listOf(waNode()))
        assertEquals(ActiveApp.WHATSAPP, r.app)
        assertEquals(ActiveAppSource.WA_NODE_MARKERS, r.source)
    }

    @Test
    fun concreteOtherAppIsNotForcedToInstagramEvenWithStaleMarkers() {
        // Aunque haya marcadores IG (estado viejo), si el snapshot dice claramente
        // otra app concreta (ajustes), NO forzamos Instagram.
        val r = ActiveAppResolver.resolve("com.android.settings", false, true, emptyList())
        assertEquals(ActiveApp.OTHER, r.app)
        assertEquals(ActiveAppSource.RAW_PACKAGE_OTHER, r.source)
    }

    @Test
    fun conflictRawWhatsAppVsInstagramNodesPrefersRawPackage() {
        // Regla documentada: gana rawPackage (dueño de la ventana) sobre nodos.
        val r = ActiveAppResolver.resolve("com.whatsapp", false, true, listOf(igNode()))
        assertEquals(ActiveApp.WHATSAPP, r.app)
        assertEquals(ActiveAppSource.RAW_PACKAGE, r.source)
    }

    @Test
    fun noSignalsGivesOther() {
        val r = ActiveAppResolver.resolve(null, false, false, emptyList())
        assertEquals(ActiveApp.OTHER, r.app)
        assertEquals(ActiveAppSource.NONE, r.source)
    }

    @Test
    fun overrideFlowsThroughScreenReasoner() {
        // currentScreenModel pasa el activeApp resuelto como override: aunque el
        // packageName sea desconocido, el modelo refleja INSTAGRAM.
        val model = ScreenReasoner.reason(
            RawScreen(packageName = null, nodes = listOf(ReasonerNode(text = "Hola"))),
            activeAppOverride = ActiveApp.INSTAGRAM,
        )
        assertEquals(ActiveApp.INSTAGRAM, model.activeApp)
    }
}
