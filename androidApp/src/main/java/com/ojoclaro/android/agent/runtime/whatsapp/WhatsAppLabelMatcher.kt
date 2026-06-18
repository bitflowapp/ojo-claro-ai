package com.ojoclaro.android.agent.runtime.whatsapp

import com.ojoclaro.android.outdoor.removeSpanishAccents

/**
 * Blind Safety — comparación de label (nombre ESPERADO del contacto vs TÍTULO
 * visible de la cabecera) para verificar destino cuando NO hay señal de
 * últimos-4 (contacto agendado: la cabecera muestra un nombre, no un número).
 *
 * Devuelve:
 *  - true  : coincidencia FUERTE — igualdad normalizada (mismo conjunto de tokens
 *            de palabra completa). Un SUBconjunto NO alcanza (fail-closed).
 *  - false : hay nombres comparables que NO coinciden → MISMATCH (bloquear).
 *  - null  : no hay señal de nombre comparable (título numérico/vacío) →
 *            UNVERIFIED; el verificador lo trata como fail-closed.
 *
 * CRÍTICO: el match es por TOKEN de palabra COMPLETA, NO substring. Un substring
 * laxo ("ana" ⊂ "susana", "leo" ⊂ "leonardo", "marco" ⊂ "marcos") podría
 * VERIFICAR el chat equivocado de un contacto agendado y escribir el borrador
 * ahí — exactamente lo que estos fixes deben impedir para una persona no vidente.
 *
 * PURO: sin Android, sin estado, sin IO.
 */
object WhatsAppLabelMatcher {

    private const val MIN_TOKEN = 3

    fun matches(expectedLabel: String?, visibleTitle: String?): Boolean? {
        val a = normalize(expectedLabel)
        val b = normalize(visibleTitle)
        if (a.isBlank() || b.isBlank()) return null
        // Título puramente numérico → no es un nombre: deja decidir a los últimos-4.
        if (b.all { it.isDigit() || it == ' ' }) return null
        if (a == b) return true
        val at = a.split(' ').filter { it.length >= MIN_TOKEN }.toSet()
        val bt = b.split(' ').filter { it.length >= MIN_TOKEN }.toSet()
        if (at.isEmpty() || bt.isEmpty()) return false
        // FAIL-CLOSED (#1): sólo VERIFICA si los conjuntos de tokens significativos
        // son IGUALES. Un subconjunto en CUALQUIER dirección NO verifica: "Ana
        // García" esperado vs cabecera "Ana" (otro contacto, menos específico) →
        // MISMATCH; igual "Ana" vs "Ana García". El match laxo bidireccional previo
        // (aInB || bInA) podía VERIFICAR el chat equivocado y escribir el borrador
        // ahí para una persona no vidente. Cuando sólo hay nombre (sin últimos-4),
        // la igualdad es la única señal fuerte aceptable.
        return at == bt
    }

    private fun normalize(s: String?): String =
        (s ?: "").lowercase()
            .removeSpanishAccents()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
