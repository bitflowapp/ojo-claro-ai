package com.ojoclaro.android.agent.core.llm

import com.ojoclaro.android.agent.AgentIntent
import com.ojoclaro.android.agent.IntentInterpreter
import com.ojoclaro.android.agent.ParsedAgentIntent

/**
 * Compone un parser local (determinista) con un LLM fallback (opcional).
 *
 * Flujo:
 *  1. El parser local intenta entender.
 *  2. Si devuelve UNKNOWN o confidence < umbral, se intenta el LLM.
 *  3. El LLM propone un LlmIntentCandidate que se evalúa con LlmFallbackPolicy.
 *  4. Si la policy lo acepta, ese es el resultado final.
 *  5. Si la policy lo rechaza o el LLM no devuelve nada, queda el UNKNOWN local.
 *
 * No hay llamada de red dentro de esta clase. La inyección permite tests con un
 * fake interpreter sin tocar internet.
 */
class CompositeIntentInterpreter(
    private val localParser: IntentInterpreter,
    private val llmInterpreter: AgentLlmInterpreter,
    private val fallbackPolicy: LlmFallbackPolicy = LlmFallbackPolicy(),
    private val llmEnabledProvider: () -> Boolean = { false },
    private val localConfidenceThreshold: Float = DEFAULT_LOCAL_THRESHOLD
) {

    suspend fun interpret(
        rawText: String,
        context: AgentLlmContext
    ): CompositeIntentResult {
        val localParsed = localParser.parse(rawText)
        val shouldEscalate = localParsed.intent == AgentIntent.UNKNOWN ||
            localParsed.confidence < localConfidenceThreshold

        if (!shouldEscalate) {
            return CompositeIntentResult(parsed = localParsed, source = CompositeIntentSource.LOCAL)
        }

        if (!llmEnabledProvider()) {
            return CompositeIntentResult(parsed = localParsed, source = CompositeIntentSource.LOCAL_UNKNOWN_NO_LLM)
        }

        if (context.screenIsSensitive) {
            // Defensa en profundidad: no consultamos al LLM si estamos en zona caliente.
            return CompositeIntentResult(parsed = localParsed, source = CompositeIntentSource.LOCAL_UNKNOWN_HOT_ZONE)
        }

        val candidate = try {
            llmInterpreter.proposeCandidate(rawText, context)
        } catch (_: Throwable) {
            null
        } ?: return CompositeIntentResult(parsed = localParsed, source = CompositeIntentSource.LOCAL_UNKNOWN_LLM_QUIET)

        val evaluation = fallbackPolicy.evaluate(
            candidate = candidate,
            rawText = rawText,
            screenIsHotZone = context.screenIsSensitive,
            llmEnabled = true
        )

        return when (evaluation) {
            is LlmFallbackResult.Accepted -> CompositeIntentResult(
                parsed = evaluation.parsed,
                source = CompositeIntentSource.LLM_FALLBACK,
                llmCandidate = candidate
            )
            is LlmFallbackResult.RejectedBySafety -> CompositeIntentResult(
                parsed = localParsed,
                source = CompositeIntentSource.LLM_REJECTED,
                llmCandidate = candidate,
                llmRejectionReason = evaluation.reason
            )
            is LlmFallbackResult.NotAvailable -> CompositeIntentResult(
                parsed = localParsed,
                source = CompositeIntentSource.LLM_UNAVAILABLE,
                llmRejectionReason = evaluation.reason
            )
        }
    }

    companion object {
        const val DEFAULT_LOCAL_THRESHOLD = 0.5f
    }
}

enum class CompositeIntentSource {
    LOCAL,
    LOCAL_UNKNOWN_NO_LLM,
    LOCAL_UNKNOWN_HOT_ZONE,
    LOCAL_UNKNOWN_LLM_QUIET,
    LLM_FALLBACK,
    LLM_REJECTED,
    LLM_UNAVAILABLE
}

data class CompositeIntentResult(
    val parsed: ParsedAgentIntent,
    val source: CompositeIntentSource,
    val llmCandidate: LlmIntentCandidate? = null,
    val llmRejectionReason: String? = null
) {
    val isLlmAccepted: Boolean
        get() = source == CompositeIntentSource.LLM_FALLBACK
}
