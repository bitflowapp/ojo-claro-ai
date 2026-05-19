package com.ojoclaro.android.onboarding

import kotlin.test.Test
import kotlin.test.assertTrue

class OnboardingStateTest {

    @Test
    fun primerUsoExplicaAlphaConfirmacionYAyuda() {
        val joined = OnboardingStep.entries.joinToString(" ") { it.spoken }

        assertTrue(joined.contains("Estela", ignoreCase = true))
        assertTrue(joined.contains("voz", ignoreCase = true))
        assertTrue(joined.contains("visión", ignoreCase = true))
        assertTrue(joined.contains("confirmación", ignoreCase = true))
        assertTrue(joined.contains("alpha experimental", ignoreCase = true))
        assertTrue(joined.contains("qué podés hacer", ignoreCase = true))
    }
}
