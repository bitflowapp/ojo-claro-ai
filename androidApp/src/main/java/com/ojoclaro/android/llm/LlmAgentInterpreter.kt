package com.ojoclaro.android.llm

interface LlmAgentInterpreter {
    suspend fun interpret(request: LlmAgentRequest): LlmAgentResponse
}

