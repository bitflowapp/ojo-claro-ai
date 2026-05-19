package com.ojoclaro.android.agent.core

import com.ojoclaro.android.agent.AgentSlotName
import java.text.Normalizer
import java.util.Locale

/**
 * Filtro especializado en verbos de acción irreversible o costosa.
 *
 * Complementa a [com.ojoclaro.android.privacy.PrivacyGuard], que ya bloquea
 * contenido sensible (tarjetas, contraseñas, códigos). Esta capa atiende
 * un riesgo distinto: que el agente prepare y dispare —sin que el usuario
 * se entere bien— una acción del estilo Enviar / Pagar / Borrar / Comprar /
 * Transferir / Eliminar.
 *
 * Filosofía:
 *  - NO bloqueamos. El usuario puede legítimamente querer mandar un mensaje
 *    que diga "voy a pagar el café". Si bloqueáramos, hacemos a la app
 *    insoportable.
 *  - SÍ elevamos a [AgentRiskLevel.HIGH] y forzamos `requiresConfirmation = true`
 *    cuando detectamos un verbo peligroso en el contenido del mensaje, el
 *    contacto destino, el destino de navegación o el comando crudo.
 *  - Sumamos un texto explícito al confirmationPrompt: el usuario tiene que
 *    OÍR que su acción incluye una palabra de alto impacto antes de aceptar.
 *
 * Diseñado puro: sin Android, sin coroutines, sin estado interno persistente.
 */
object DangerousActionGuard {

    sealed class Verdict {
        /** No hay verbos peligrosos detectados. La acción pasa sin cambios. */
        data object Safe : Verdict()

        /**
         * Hay verbo(s) peligroso(s). Se eleva el riesgo y se exige confirmación.
         * [matchedVerbs] son los términos crudos que dispararon la alerta, en
         * el mismo orden en que aparecen en el texto. Útiles para componer
         * un confirmationPrompt natural.
         */
        data class Elevated(
            val matchedVerbs: List<String>,
            val reason: String
        ) : Verdict()
    }

    /**
     * Revisa una acción ya construida.
     *
     * Se inspeccionan, en orden:
     *  1. [AgentSlotName.MESSAGE_TEXT] — contenido que el agente "enviaría".
     *  2. [AgentSlotName.RAW_COMMAND] — lo que el usuario dijo.
     *  3. [AgentSlotName.DESTINATION] y [AgentSlotName.LOCATION_ALIAS] — por si
     *     el destino "borrar cuenta" se cuela como alias.
     *  4. [AgentSlotName.CONTACT_NAME] — defensivo, por si el TTS confunde
     *     "Pago" con un nombre.
     */
    fun review(action: AgentAction): Verdict {
        val payloads = collectPayloadsToScan(action)
        if (payloads.isEmpty()) return Verdict.Safe

        val hits = linkedSetOf<String>()
        var matchedFinancial = false
        var matchedDestructive = false
        var matchedTransactional = false

        for (payload in payloads) {
            val normalized = normalize(payload)
            if (normalized.isBlank()) continue

            financialVerbs.forEach { verb ->
                if (containsVerb(normalized, verb)) {
                    hits += verb
                    matchedFinancial = true
                }
            }
            destructiveVerbs.forEach { verb ->
                if (containsVerb(normalized, verb)) {
                    hits += verb
                    matchedDestructive = true
                }
            }
            transactionalVerbs.forEach { verb ->
                if (containsVerb(normalized, verb)) {
                    hits += verb
                    matchedTransactional = true
                }
            }
        }

        if (hits.isEmpty()) return Verdict.Safe

        val reason = when {
            matchedDestructive -> "dangerous_destructive_verb"
            matchedFinancial -> "dangerous_financial_verb"
            matchedTransactional -> "dangerous_transactional_verb"
            else -> "dangerous_verb"
        }
        return Verdict.Elevated(matchedVerbs = hits.toList(), reason = reason)
    }

    /**
     * Sube el riesgo de una [AgentAction] y le agrega un confirmationPrompt
     * que menciona explícitamente los verbos peligrosos detectados.
     *
     * El confirmationPrompt es lo que el usuario va a oír por TTS antes de
     * confirmar. Que diga "esto incluye la palabra pagar" es deliberado:
     * el objetivo es hacer la acción visible.
     */
    fun apply(action: AgentAction, elevated: Verdict.Elevated): AgentAction {
        val verbsHuman = elevated.matchedVerbs.joinToString(separator = ", ")
        val basePrompt = action.confirmationPrompt
            ?: "Para continuar, decí: confirmar."
        val warning = "Atención: esta acción incluye '$verbsHuman'. " +
            "No la voy a ejecutar sin que confirmes."
        val combined = if (basePrompt.startsWith(warning)) {
            basePrompt
        } else {
            "$warning $basePrompt"
        }
        val elevatedRisk = if (action.risk.ordinal < AgentRiskLevel.HIGH.ordinal) {
            AgentRiskLevel.HIGH
        } else {
            action.risk
        }
        return action.copy(
            risk = elevatedRisk,
            requiresConfirmation = true,
            confirmationPrompt = combined
        )
    }

    private fun collectPayloadsToScan(action: AgentAction): List<String> {
        val scannableSlots = listOf(
            AgentSlotName.MESSAGE_TEXT,
            AgentSlotName.RAW_COMMAND,
            AgentSlotName.DESTINATION,
            AgentSlotName.LOCATION_ALIAS,
            AgentSlotName.CONTACT_NAME
        )
        return scannableSlots
            .mapNotNull { name -> action.slots[name]?.takeIf { it.isNotBlank() } }
    }

    private fun containsVerb(normalizedHaystack: String, verb: String): Boolean {
        // Buscamos como palabra: con boundary suave. No usamos \b porque las
        // formas verbales en español pueden caer en bordes raros. En lugar de
        // eso, exigimos que el match esté rodeado por inicio/fin o por un
        // carácter no alfabético.
        val idx = normalizedHaystack.indexOf(verb)
        if (idx < 0) return false
        val before = if (idx == 0) ' ' else normalizedHaystack[idx - 1]
        val afterIdx = idx + verb.length
        val after = if (afterIdx >= normalizedHaystack.length) ' ' else normalizedHaystack[afterIdx]
        return !before.isLetter() && !after.isLetter()
    }

    private fun normalize(text: String): String {
        val lower = text.lowercase(Locale("es", "AR"))
        return Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replace(diacriticRegex, "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private val diacriticRegex = Regex("\\p{Mn}+")

    // Verbos financieros: cualquier mención dispara HIGH.
    // Ya normalizados: sin acentos, en minúsculas.
    private val financialVerbs: Set<String> = setOf(
        "pagar", "paga", "paga.", "pague", "pagale", "pagame", "abonar", "abona",
        "transferir", "transfiera", "transferi", "transferile",
        "depositar", "deposita", "depositame",
        "girar", "gira", "gírame",
        "comprar", "compra", "comprame", "comprale"
    )

    // Verbos destructivos: borrar, eliminar, cancelar suscripción/cuenta, cerrar
    // cuenta. Los términos sueltos "cancelar"/"cerrar" SOLO disparan si vienen
    // con un sustantivo sensible cerca; para no inflar el matcher, los
    // mantenemos como términos compuestos.
    private val destructiveVerbs: Set<String> = setOf(
        "borrar", "borra", "borrame", "borrale", "borralo", "borrala",
        "eliminar", "elimina", "eliminame", "eliminale",
        "desinstalar", "desinstala",
        "cancelar suscripcion", "cancelame la suscripcion",
        "cerrar cuenta", "cerra cuenta", "cerrame la cuenta",
        "formatear", "formatea",
        "vaciar", "vacia"
    )

    // Verbos transaccionales: enviar como acción de comunicación irrevocable.
    // OJO: "enviar" puro es muy común en mensajes legítimos ("envío el archivo
    // mañana"). Para evitar falsos positivos masivos, exigimos formas
    // imperativas o reflejos típicos de pedido de acción ("enviá", "enviame",
    // "mandá ahora"). No incluimos "enviar" infinitivo solo.
    private val transactionalVerbs: Set<String> = setOf(
        "envia ya", "envialo ya", "envialo ahora",
        "manda ya", "mandalo ya", "mandalo ahora",
        "manda urgente", "manda urgentemente",
        "envia urgente", "envia urgentemente"
    )
}
