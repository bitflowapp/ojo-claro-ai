package com.ojoclaro.android.qa

object VoiceTrainingCaseFormatter {
    fun format(case: VoiceRealWorldCase): String = buildString {
        appendLine("spoken: ${case.spokenByUser}")
        appendLine("recognized: ${case.recognizedByAndroid}")
        appendLine("normalized: ${case.normalizedText}")
        appendLine("state: ${case.agentState}")
        appendLine("intent: ${case.expectedIntent}")
        appendLine("slots: ${case.expectedSlots}")
        appendLine("actual: ${case.actualResult}")
        appendLine("expected: ${case.expectedResult}")
        appendLine("notes: ${case.notes}")
    }.trim()
}

