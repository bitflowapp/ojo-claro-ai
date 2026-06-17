package com.ojoclaro.android.voice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * WhatsApp Fluency — muletillas / frase incompleta. Lo crítico: NUNCA mutilar
 * contenido real ("este sábado", "te voy a ver mañana").
 */
class IncompleteUtteranceClassifierTest {

    @Test
    fun stripsLeadingAndTrailingInterjections() {
        assertEquals("estoy llegando", IncompleteUtteranceClassifier.stripFillers("ehh estoy llegando"))
        assertEquals("estoy llegando", IncompleteUtteranceClassifier.stripFillers("estoy llegando eh"))
        assertEquals("estoy llegando", IncompleteUtteranceClassifier.stripFillers("mmm estoy llegando ehh"))
        assertEquals("estoy llegando", IncompleteUtteranceClassifier.stripFillers("o sea estoy llegando"))
    }

    @Test
    fun keepsRealContentUntouched() {
        assertEquals("estoy llegando", IncompleteUtteranceClassifier.stripFillers("estoy llegando"))
        // "este" NO es muletilla acá: es contenido real.
        assertEquals("este sábado voy", IncompleteUtteranceClassifier.stripFillers("este sábado voy"))
        // "a ver" NO se quita (solo "o sea").
        assertEquals("te voy a ver mañana", IncompleteUtteranceClassifier.stripFillers("te voy a ver mañana"))
        assertEquals("comprá pan y leche", IncompleteUtteranceClassifier.stripFillers("comprá pan y leche"))
    }

    @Test
    fun detectsFillerOnly() {
        assertTrue(IncompleteUtteranceClassifier.isFillerOnly("ehh"))
        assertTrue(IncompleteUtteranceClassifier.isFillerOnly("ehh mmm"))
        assertTrue(IncompleteUtteranceClassifier.isFillerOnly("o sea"))
        assertFalse(IncompleteUtteranceClassifier.isFillerOnly("estoy llegando"))
        assertFalse(IncompleteUtteranceClassifier.isFillerOnly(""))
    }

    @Test
    fun detectsDanglingPhrase() {
        assertTrue(IncompleteUtteranceClassifier.looksIncomplete("comprá pan y"))
        assertTrue(IncompleteUtteranceClassifier.looksIncomplete("voy para"))
        assertTrue(IncompleteUtteranceClassifier.looksIncomplete("ehh")) // solo muletilla
        assertFalse(IncompleteUtteranceClassifier.looksIncomplete("estoy llegando"))
        assertFalse(IncompleteUtteranceClassifier.looksIncomplete("comprá pan y leche"))
    }
}
