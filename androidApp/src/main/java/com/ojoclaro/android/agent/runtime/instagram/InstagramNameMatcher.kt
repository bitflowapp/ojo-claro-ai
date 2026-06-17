package com.ojoclaro.android.agent.runtime.instagram

import java.text.Normalizer
import kotlin.math.abs

/**
 * V1.12.1 — matching conservador de nombres/handles de Instagram.
 *
 * Un solo lugar para la regla (lo usan el matcher de filas del inbox y la
 * verificación de título/subtítulo del thread): el QA real mostró que
 * "so_roomero" dictado como "so roomero" no matcheaba, aunque el handle
 * está visible en el header del chat.
 *
 * Scoring (menor = mejor):
 *  0 = igualdad exacta plegada;
 *  1 = igualdad de HANDLE (sin @, espacios, puntos, guiones ni guiones
 *      bajos: "so roomero" ≡ "so_roomero" ≡ "@so_roomero");
 *  2 = la etiqueta empieza con la consulta ("Sofi" encuentra "Sofia");
 *  3 = prefijo común largo con longitudes parecidas ("sofie" ~ "sofia").
 *
 * El seguro contra falsos positivos NO es el score: es que la etiqueta REAL
 * matcheada se anuncia en voz alta antes de confirmar cualquier acción.
 * Acá jamás se matchea contra contenido de mensajes.
 */
object InstagramNameMatcher {

    const val NO_MATCH = -1

    fun score(candidateLabel: String, query: String): Int {
        val candidate = fold(candidateLabel)
        val target = fold(query)
        if (candidate.isBlank() || target.isBlank()) return NO_MATCH
        if (candidate == target) return 0
        if (handleKey(candidate) == handleKey(target)) return 1
        if (candidate.startsWith(target)) return 2
        if (candidate.commonPrefixWith(target).length >= 4 &&
            abs(candidate.length - target.length) <= 2
        ) {
            return 3
        }
        return NO_MATCH
    }

    fun matches(candidateLabel: String?, query: String): Boolean =
        candidateLabel != null && score(candidateLabel, query) != NO_MATCH

    fun scoreInboxAvatarMetadata(candidateLabel: String?, query: String): Int {
        val handle = extractInboxAvatarHandle(candidateLabel) ?: return NO_MATCH
        return score(handle, query)
    }

    fun matchesInboxAvatarMetadata(candidateLabel: String?, query: String): Boolean =
        scoreInboxAvatarMetadata(candidateLabel, query) != NO_MATCH

    fun extractInboxAvatarHandle(value: String?): String? {
        val folded = value?.let(::fold) ?: return null
        val handle = when {
            folded.startsWith("abrir historia de ") ->
                folded.removePrefix("abrir historia de ").trim()
            folded.startsWith("historia de ") ->
                folded.removePrefix("historia de ").trim()
            else -> folded
        }
        if (handle.isBlank() || handle.contains(",")) return null
        return handle.takeIf { Regex("@?[a-z0-9._-]+").matches(it) }
    }

    /** lower + sin acentos + sin @ inicial + espacios colapsados. */
    private fun fold(value: String): String {
        val lower = value.lowercase().trim().removePrefix("@")
        return Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /** Clave de handle: ignora separadores típicos de usernames. */
    private fun handleKey(folded: String): String =
        folded.replace(Regex("[@ ._-]"), "")

}
