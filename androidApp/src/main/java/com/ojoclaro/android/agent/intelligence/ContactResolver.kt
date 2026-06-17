package com.ojoclaro.android.agent.intelligence

import java.text.Normalizer

/**
 * V2.2 — Resolución SEGURA de una referencia humana ("Marco", "Marco Luna",
 * "Sofi") contra candidatos visibles en pantalla o alias autorizados.
 *
 * Componente PURO (sin Android): recibe candidatos ya extraídos del snapshot
 * por un adapter, y devuelve una decisión explícita. Diseño "pensante con
 * frenos": ante la duda NO elige; pide aclaración o frena.
 *
 * Reglas duras:
 *  - exact match gana.
 *  - normaliza mayúsculas/acentos.
 *  - "Sofi" puede matchear "Sofia"; "Marco" puede matchear "Marco Luna".
 *  - si hay más de un candidato fuerte y distinto → Ambiguous (NUNCA auto-elige).
 *  - número de fallback SOLO para un alias autorizado (ej. Marco Luna).
 *  - jamás resuelve a un botón de acción (enviar/llamar/pagar): eso es Unsafe.
 *  - resolver NO envía nada: solo resuelve a quién. El envío lo decide el planner.
 *
 * NOTA V2.2: código real pero SIN COMPILAR/SIN TESTEAR (disco C: crítico; ver
 * docs/V22_CONTACT_RESOLVER_REPORT.md). Construir y correr tests antes de mergear.
 */
enum class TargetApp { WHATSAPP, INSTAGRAM, UNKNOWN }

enum class CandidateKind { CHAT, CONTACT, BUTTON, INPUT, OTHER }

/** Un candidato visible (chat/contacto). Tipo de entrada LIMPIO. */
data class ContactCandidate(
    val primaryText: String,
    val secondaryText: String? = null,
    val index: Int = 0,
    val app: TargetApp = TargetApp.UNKNOWN,
    val kind: CandidateKind = CandidateKind.CHAT,
)

/** Alias local autorizado: "marco" → "Marco Luna" (+ número fallback opcional). */
data class ContactAlias(
    val alias: String,
    val canonicalName: String,
    val app: TargetApp = TargetApp.UNKNOWN,
    val phoneFallback: String? = null,
)

enum class ResolutionSource { EXACT_VISIBLE, PARTIAL_VISIBLE, ALIAS, PHONE_FALLBACK }

data class ResolvedContact(
    val name: String,
    val app: TargetApp,
    val source: ResolutionSource,
    val candidateIndex: Int? = null,
    val phoneFallback: String? = null,
)

sealed class ContactResolution {
    data class Resolved(val match: ResolvedContact) : ContactResolution()
    data class Ambiguous(val candidates: List<ContactCandidate>) : ContactResolution()
    data class NotFound(val reason: String) : ContactResolution()
    data class Unsafe(val reason: String) : ContactResolution()
}

object ContactResolver {

    // Umbral mínimo de score para considerar un candidato. Por debajo = ignorado.
    private const val MIN_SCORE = 75

    // Etiquetas de ACCIÓN que jamás son un contacto (evita resolver a un botón).
    private val SENSITIVE_LABELS = setOf(
        "enviar", "send", "llamar", "call", "videollamada", "video call",
        "borrar", "delete", "eliminar", "archivar", "pagar", "pay",
        "adjuntar", "attach", "microfono", "camara", "buscar", "search"
    )

    // Palabras que delatan que el "query" es un dato sensible, no un nombre.
    private val SENSITIVE_QUERY = setOf(
        "clave", "contrasena", "password", "cvv", "cbu", "pin", "token", "tarjeta"
    )

    fun resolve(
        query: String,
        app: TargetApp,
        candidates: List<ContactCandidate>,
        aliases: List<ContactAlias> = emptyList(),
    ): ContactResolution {
        val q = normalize(query)
        if (q.isBlank()) return ContactResolution.NotFound("consulta vacía")
        if (SENSITIVE_QUERY.any { q.contains(it) }) {
            return ContactResolution.Unsafe("la consulta parece un dato sensible, no un nombre")
        }

        // Solo chats/contactos de la app pedida (o cualquiera si UNKNOWN), nunca
        // botones/inputs ni etiquetas de acción.
        val pool = candidates.filter { c ->
            (c.kind == CandidateKind.CHAT || c.kind == CandidateKind.CONTACT) &&
                (app == TargetApp.UNKNOWN || c.app == TargetApp.UNKNOWN || c.app == app) &&
                normalize(c.primaryText).let { it.isNotBlank() && it !in SENSITIVE_LABELS }
        }

        val scored = pool
            .map { it to score(q, normalize(it.primaryText)) }
            .filter { it.second >= MIN_SCORE }
            .sortedByDescending { it.second }

        if (scored.isNotEmpty()) {
            val top = scored.first()
            val exact = scored.filter { it.second >= 100 }
            // Match exacto único → resuelto.
            if (exact.size == 1) {
                return ContactResolution.Resolved(
                    ResolvedContact(exact[0].first.primaryText, app, ResolutionSource.EXACT_VISIBLE, exact[0].first.index)
                )
            }
            // Varios exactos, o varios fuertes con nombres DISTINTOS → ambiguo.
            val strong = scored.filter { it.second >= top.second - 5 }
            val distinctNames = strong.map { normalize(it.first.primaryText) }.toSet()
            if (exact.size > 1 || distinctNames.size > 1) {
                return ContactResolution.Ambiguous(strong.map { it.first })
            }
            // Un solo candidato fuerte (parcial) → resuelto parcial.
            return ContactResolution.Resolved(
                ResolvedContact(top.first.primaryText, app, ResolutionSource.PARTIAL_VISIBLE, top.first.index)
            )
        }

        // Sin candidatos visibles: alias autorizados.
        val alias = aliases.firstOrNull { a ->
            (app == TargetApp.UNKNOWN || a.app == TargetApp.UNKNOWN || a.app == app) &&
                (normalize(a.alias) == q || normalize(a.canonicalName) == q ||
                    score(q, normalize(a.canonicalName)) >= MIN_SCORE)
        }
        if (alias != null) {
            // Si el alias trae número fallback, se usa SOLO acá (sin candidato visible).
            return if (alias.phoneFallback != null) {
                ContactResolution.Resolved(
                    ResolvedContact(alias.canonicalName, if (app == TargetApp.UNKNOWN) alias.app else app,
                        ResolutionSource.PHONE_FALLBACK, phoneFallback = alias.phoneFallback)
                )
            } else {
                ContactResolution.Resolved(
                    ResolvedContact(alias.canonicalName, if (app == TargetApp.UNKNOWN) alias.app else app, ResolutionSource.ALIAS)
                )
            }
        }

        return ContactResolution.NotFound("no encontré a \"$query\" en lo visible ni en los alias")
    }

    /**
     * Puntaje 0..100 espejando WhatsAppVisibleChatMatcher + prefijos por token:
     *  - exacto = 100
     *  - query es un token completo del candidato ("marco" en "marco luna") = 92
     *  - query es prefijo (>=4) de un token ("sofi" de "sofia") = 85
     *  - candidato contiene el query (>=4) = 80
     *  - solapamiento de tokens = proporcional (hasta 78)
     */
    private fun score(q: String, candidate: String): Int {
        if (q == candidate) return 100
        val qTokens = q.split(" ").filter { it.isNotBlank() }
        val cTokens = candidate.split(" ").filter { it.isNotBlank() }
        if (qTokens.isEmpty() || cTokens.isEmpty()) return 0

        // query de un solo token: prefijo/igualdad de token.
        if (qTokens.size == 1) {
            val qt = qTokens[0]
            if (cTokens.any { it == qt }) return 92
            if (qt.length >= 4 && cTokens.any { it.startsWith(qt) }) return 85
        }
        // candidato contiene el query como frase.
        if (q.length >= 4 && candidate.contains(q)) return 80
        // solapamiento de tokens.
        val shared = qTokens.count { qt -> cTokens.any { it == qt || (qt.length >= 4 && it.startsWith(qt)) } }
        if (shared > 0) return (50 + 28 * shared / qTokens.size).coerceAtMost(78)
        return 0
    }

    private fun normalize(text: String): String {
        val lower = text.lowercase()
        val stripped = Normalizer.normalize(lower, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        return stripped.replace(Regex("[¿?¡!.,;:@_]"), " ").replace(Regex("\\s+"), " ").trim()
    }
}
