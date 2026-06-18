package com.ojoclaro.android.logging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** SafeLog — helpers de sanitización: jamás emiten el contenido crudo. */
class SafeLogTest {

    @Test
    fun lenAndCountAreMetadataOnly() {
        assertEquals(0, SafeLog.textLen(null))
        assertEquals(6, SafeLog.textLen("Walter"))
        assertEquals(0, SafeLog.safeCount(null))
        assertEquals(2, SafeLog.safeCount(listOf("a", "b")))
    }

    @Test
    fun shortHashGroupsWithoutRevealing() {
        val a = SafeLog.shortHash("mandale a mi novia que ya voy")
        val b = SafeLog.shortHash("mandale a mi novia que ya voy")
        assertEquals(a, b, "mismo texto → mismo token (igualdad)")
        assertTrue(a.startsWith("h:"))
        assertFalse(a.contains("novia"), "el token jamás contiene el texto")
        assertFalse(a.contains("mandale"))
        assertEquals("h:none", SafeLog.shortHash(""))
        assertEquals("h:none", SafeLog.shortHash(null))
    }

    @Test
    fun redactNeverLeaksValue() {
        assertEquals("[empty]", SafeLog.redact(null))
        assertEquals("[empty]", SafeLog.redact(""))
        val r = SafeLog.redact("llego en 10")
        assertTrue(r.startsWith("[redacted"))
        assertFalse(r.contains("llego"))
    }

    @Test
    fun safeIntentParamsEmitsKeysAndLensNotValues() {
        val out = SafeLog.safeIntentParams(linkedMapOf("contact" to "Walter", "message" to "llego en 10"))
        assertEquals("keys=contact,message lens=6,11", out)
        assertFalse(out.contains("Walter"))
        assertFalse(out.contains("llego"))
        assertEquals("keys= lens=", SafeLog.safeIntentParams(null))
        assertEquals("keys= lens=", SafeLog.safeIntentParams(emptyMap()))
    }

    @Test
    fun safePackageNameCollapsesThirdPartyApps() {
        assertEquals("whatsapp", SafeLog.safePackageName("com.whatsapp"))
        assertEquals("whatsapp", SafeLog.safePackageName("com.whatsapp.w4b"))
        assertEquals("instagram", SafeLog.safePackageName("com.instagram.android"))
        assertEquals("self", SafeLog.safePackageName("com.ojoclaro.android"))
        assertEquals("system", SafeLog.safePackageName("com.android.systemui"))
        assertEquals("other_app", SafeLog.safePackageName("com.some.private.bank.app"))
        assertEquals("none", SafeLog.safePackageName(null))
    }

    @Test
    fun formatProducesSafeKeyValueLineAndCollapsesValues() {
        assertEquals(
            "event=stt_received len=34",
            SafeLog.format("stt received", listOf("len" to 34))
        )
        assertEquals(
            "event=intent_resolved intent=READ_SCREEN confidence=0.91",
            SafeLog.format("intent_resolved", listOf("intent" to "READ_SCREEN", "confidence" to 0.91))
        )
        // un valor con espacios se colapsa (no rompe el formato k=v)
        assertEquals(
            "event=x note=a_b_c",
            SafeLog.format("x", listOf("note" to "a b c"))
        )
    }
}
