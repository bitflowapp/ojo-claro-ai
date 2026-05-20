package com.ojoclaro.android.agent.estela

class EstelaAgentSimulator(
    private val runtime: EstelaAgentRuntime = EstelaAgentRuntime()
) {
    fun simulate(
        input: String,
        context: EstelaAgentContext? = null
    ): EstelaAgentResult {
        context?.let(runtime::replaceContext)
        return runtime.handle(input)
    }

    fun simulateAll(inputs: List<String>): List<EstelaAgentResult> =
        inputs.map { simulate(it) }

    fun snapshot(): EstelaAgentContext =
        runtime.contextSnapshot()
}
