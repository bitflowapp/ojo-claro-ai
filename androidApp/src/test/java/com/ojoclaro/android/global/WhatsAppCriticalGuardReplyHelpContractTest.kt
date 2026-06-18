package com.ojoclaro.android.global

import com.ojoclaro.android.agent.runtime.conversation.SafeLlmFallbackPolicy
import com.ojoclaro.android.agent.runtime.conversation.SafeLlmPhrases
import com.ojoclaro.android.agent.runtime.conversation.SafeLlmRoute
import com.ojoclaro.android.agent.runtime.conversation.SafeLlmSignals
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppCriticalGuard
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppReplyPhrases
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Fix pre-merge PR #3 — "qué le respondo" debe llegar a SUGGEST_REPLY_ONLY y no
 * quedar atrapado por el guard crítico pre-LLM.
 *
 * El guard usa el marcador 'respond', que también dispara con la pregunta de
 * ayuda. La corrección agrega una excepción explícita por `isReplyHelp` ANTES de
 * bloquear, sin relajar imperativos ni contenido privado.
 */
class WhatsAppCriticalGuardReplyHelpContractTest {

    private val service: String =
        File("src/main/java/com/ojoclaro/android/global/GlobalAssistantService.kt").readText()

    private val guard: String = service
        .substringAfter("private fun handleWhatsAppCriticalGuardBeforeLlm")
        .substringBefore("private suspend fun handleSafeLlmFallback")

    @Test
    fun guardExemptsReplyHelpBeforeBlocking() {
        val exemptIdx = guard.indexOf("SafeLlmPhrases.isReplyHelp(text)) return false")
        val blockIdx = guard.indexOf("handler=whatsapp_critical_guard blocked_llm=true")
        assertTrue(exemptIdx >= 0, "el guard debe eximir reply-help")
        assertTrue(blockIdx >= 0, "el guard debe seguir bloqueando lo crítico")
        assertTrue(exemptIdx < blockIdx, "la excepción reply-help debe correr ANTES del bloqueo del guard")
    }

    @Test
    fun replyHelpIsCriticalByMarkerButExemptedAndSuggestOnly() {
        // Sin la excepción, "qué le respondo" caería en el guard (isCritical por 'respond')...
        assertTrue(WhatsAppCriticalGuard.isCritical("qué le respondo"), "dispara el guard crítico")
        // ...pero es reply-help → exenta y sigue al fallback seguro.
        listOf("qué le respondo", "qué le contesto").forEach {
            assertTrue(SafeLlmPhrases.isReplyHelp(it), "'$it' es reply-help (exenta del guard)")
        }
        // Con WhatsApp activo, la política la deriva a sugerir-sin-enviar.
        assertEquals(
            SafeLlmRoute.SUGGEST_REPLY_ONLY,
            SafeLlmFallbackPolicy.decide(
                SafeLlmSignals(
                    conversational = false, whatsAppActive = true, namesWhatsApp = false,
                    looksDangerous = true, looksLikeMessageContent = false,
                    looksLikeReplyHelp = true, wantsChatContent = false, looksLikeSafeQuestion = false
                )
            )
        )
    }

    @Test
    fun imperativeReplyIsNotExemptedAndNeverReachesFreeLlm() {
        // "respondéle que ya voy" NO es reply-help → NO se exime del guard.
        assertFalse(SafeLlmPhrases.isReplyHelp("respondéle que ya voy"))
        // Y es CRÍTICA (marcador 'respond'): si llegara al guard, se bloquea. Por
        // eso, aun sin pasar por el handler de reply, jamás cae a conversación libre.
        assertTrue(WhatsAppCriticalGuard.isCritical("respondéle que ya voy"))
        // En la práctica la atrapa ANTES el handler de reply (forma canónica del
        // pipeline) → borrador + doble confirmación, nunca LLM.
        assertNotNull(
            WhatsAppReplyPhrases.extractReply("respondele que ya voy"),
            "es un comando de respuesta → ruta segura de borrador, no LLM"
        )
    }

    @Test
    fun conceptualTransferQuestionIsExemptedButImperativesStayBlocked() {
        val safeQuestionIdx = guard.indexOf("SafeLlmPhrases.isSafeQuestion(text)) return false")
        val blockIdx = guard.indexOf("handler=whatsapp_critical_guard blocked_llm=true")
        assertTrue(safeQuestionIdx in 0 until blockIdx, "concept questions must be exempted before blocking")

        assertTrue(SafeLlmPhrases.isSafeQuestion("qué es una transferencia"))
        assertTrue(SafeLlmPhrases.isSafeQuestion("cómo funciona una transferencia"))
        assertFalse(SafeLlmPhrases.isSafeQuestion("transferile plata"))
        assertFalse(SafeLlmPhrases.isSafeQuestion("pagale"))
    }

    @Test
    fun privateContentAndDangerousAreNotExempted() {
        // "leelo y respondé según el chat": NO reply-help → no exento; pide contenido
        // privado → BLOCK_PRIVATE_CONTEXT, nunca LLM libre.
        listOf("leelo y respondé según el chat", "según el chat respondé").forEach {
            assertFalse(SafeLlmPhrases.isReplyHelp(it), "'$it' no es reply-help")
        }
        assertTrue(SafeLlmPhrases.wantsChatContent("leelo y respondé según el chat"))
        // "tocá enviar": peligroso, no reply-help → refusal explícito.
        assertFalse(SafeLlmPhrases.isReplyHelp("tocá enviar"))
        assertTrue(WhatsAppCriticalGuard.isCritical("tocá enviar"))
    }

    @Test
    fun replyCommandRunsBeforeCriticalGuardAndFallbackInDispatch() {
        val replyIdx = service.indexOf("if (handleWhatsAppReplyCommand(text)) return")
        val guardIdx = service.indexOf("if (handleWhatsAppCriticalGuardBeforeLlm(text)) return")
        val fallbackIdx = service.indexOf("if (handleSafeLlmFallback(text)) return")
        assertTrue(replyIdx in 1 until guardIdx, "el handler de reply corre antes del guard")
        assertTrue(guardIdx in 1 until fallbackIdx, "el guard corre antes del fallback LLM")
    }
}
