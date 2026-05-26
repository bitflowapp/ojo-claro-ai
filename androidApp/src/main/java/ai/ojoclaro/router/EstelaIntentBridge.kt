package ai.ojoclaro.router

import ai.ojoclaro.adapter.LlmIntentAdapter
import ai.ojoclaro.adapter.LlmIntentAdapterResult

/**
 * Thin bridge for Estela JSON output.
 *
 * It intentionally does not parse templates or call [IntentRouter] directly:
 * every LLM response must pass through [LlmIntentAdapter], which owns
 * malformed JSON, safety-level, confidence, slot_fill, and template handling.
 */
class EstelaIntentBridge(
    private val adapter: LlmIntentAdapter
) {

    fun process(rawJson: String): EstelaDispatch =
        EstelaDispatch(result = adapter.adapt(rawJson))
}

data class EstelaDispatch(
    val result: LlmIntentAdapterResult
)
