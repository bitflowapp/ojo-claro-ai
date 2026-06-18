package com.ojoclaro.android.logging

import android.util.Log

/**
 * Logger seguro centralizado para diagnóstico SIN exponer datos privados.
 *
 * Regla dura (release Y debug): JAMÁS texto crudo del usuario, STT, pantalla,
 * OCR, WhatsApp, mensajes, contactos, teléfonos, params sensibles, ni material
 * bancario/claves/tokens. Solo METADATOS: longitudes, conteos, categorías, flags
 * y hashes NO reversibles.
 *
 * Uso:
 *   SafeLog.voice("stt_received", "len" to SafeLog.textLen(text))
 *   SafeLog.intent("resolved", "intent" to "READ_SCREEN", "confidence" to 0.91)
 *   SafeLog.whatsapp("route", "route" to "OPEN_CHAT", "safety" to "read_only")
 *   SafeLog.security("blocked", "reason" to "dangerous")
 *   SafeLog.error("backend_failed", t, "latencyMs" to 840)
 *
 * Los VALORES que se pasan deben ser ya seguros (números, booleanos, enums,
 * etiquetas de categoría). Para texto del usuario, convertilo ANTES con los
 * helpers [textLen]/[safeCount]/[shortHash]/[redact]/[safeIntentParams].
 *
 * El cuerpo emisor envuelve android.util.Log; los helpers son PUROS (testeables).
 */
object SafeLog {

    const val VOICE_TAG = "EstelaSafeVoice"
    const val INTENT_TAG = "EstelaSafeIntent"
    const val SCREEN_TAG = "EstelaSafeScreen"
    const val WHATSAPP_TAG = "EstelaSafeWhatsApp"
    const val SECURITY_TAG = "EstelaSafeSecurity"
    const val ERROR_TAG = "EstelaSafeError"

    // ---------------- helpers de sanitización (PUROS) ----------------

    /** Longitud, nunca contenido. */
    fun textLen(text: CharSequence?): Int = text?.length ?: 0

    /** Tamaño de una colección, nunca sus elementos. */
    fun safeCount(items: Collection<*>?): Int = items?.size ?: 0

    /**
     * Token de IGUALDAD grueso y NO reversible: agrupa en 4096 buckets para poder
     * decir "se repitió la misma frase" sin revelar el contenido (colisiones
     * intencionales → imposible reconstruir). NUNCA es el texto ni un hash fino.
     */
    fun shortHash(text: CharSequence?): String {
        if (text.isNullOrEmpty()) return "h:none"
        val bucket = (text.toString().hashCode() and 0x0FFF)
        return "h:" + bucket.toString(16).padStart(3, '0')
    }

    /** Marca de presencia/contenido redactado. Nunca el valor. */
    fun redact(value: CharSequence?): String =
        if (value.isNullOrEmpty()) "[empty]" else "[redacted:len${value.length}]"

    /**
     * Params de intent SIN valores: solo nombres de clave y longitud por valor.
     * Ej: {contact:"Walter", message:"llego"} → "keys=contact,message lens=6,5".
     */
    fun safeIntentParams(params: Map<String, Any?>?): String {
        if (params.isNullOrEmpty()) return "keys= lens="
        val keys = params.keys.joinToString(",")
        val lens = params.values.joinToString(",") { v ->
            when (v) {
                null -> "0"
                is CharSequence -> v.length.toString()
                is Collection<*> -> "c${v.size}"
                else -> v.toString().length.toString()
            }
        }
        return "keys=$keys lens=$lens"
    }

    /**
     * Categoría de app a partir del package. Los nombres de app conocidos son
     * categorías estables; cualquier otro colapsa a "other_app" (sin revelar qué
     * app de terceros usa la persona).
     */
    fun safePackageName(packageName: String?): String {
        val p = packageName?.lowercase().orEmpty()
        return when {
            p.isBlank() -> "none"
            p.contains("whatsapp") -> "whatsapp"
            p.contains("instagram") -> "instagram"
            p.contains("ubercab") || p.contains("uber") -> "uber"
            p.contains("maps") || p.contains("waze") -> "maps"
            p.contains("ojoclaro") -> "self"
            p.contains("systemui") || p == "android" -> "system"
            p.contains("launcher") -> "launcher"
            p.contains("inputmethod") || p.contains(".latin") -> "ime"
            else -> "other_app"
        }
    }

    // ---------------- canales de evento ----------------

    fun voice(event: String, vararg fields: Pair<String, Any?>) = emit(VOICE_TAG, event, fields)
    fun intent(event: String, vararg fields: Pair<String, Any?>) = emit(INTENT_TAG, event, fields)
    fun screen(event: String, vararg fields: Pair<String, Any?>) = emit(SCREEN_TAG, event, fields)
    fun whatsapp(event: String, vararg fields: Pair<String, Any?>) = emit(WHATSAPP_TAG, event, fields)
    fun security(event: String, vararg fields: Pair<String, Any?>) = emit(SECURITY_TAG, event, fields)

    /** Error técnico: clase + metadatos, JAMÁS el payload/mensaje crudo. */
    fun error(event: String, throwable: Throwable? = null, vararg fields: Pair<String, Any?>) {
        val errKind = throwable?.javaClass?.simpleName
        val all = if (errKind != null) fields.toList() + ("errorKind" to errKind) else fields.toList()
        // No pasamos throwable a Log para no volcar el message (puede traer payload).
        Log.w(ERROR_TAG, format(event, all))
    }

    /** Línea pública (para tests): no emite, solo arma la línea segura. */
    fun format(event: String, fields: List<Pair<String, Any?>>): String {
        val safeEvent = event.replace(Regex("\\s+"), "_")
        val tail = fields.joinToString(" ") { (k, v) -> "$k=${fieldValue(v)}" }
        return if (tail.isBlank()) "event=$safeEvent" else "event=$safeEvent $tail"
    }

    private fun emit(tag: String, event: String, fields: Array<out Pair<String, Any?>>) {
        // El contenido ya es metadato seguro en debug Y release; emitimos en ambos.
        Log.i(tag, format(event, fields.toList()))
    }

    // Los valores numéricos/booleanos/enum se imprimen tal cual; una cadena se
    // imprime tal cual (contrato: el caller solo pasa etiquetas seguras). El
    // helper colapsa espacios para que el formato k=v no se rompa.
    private fun fieldValue(value: Any?): String = when (value) {
        null -> "null"
        is Number, is Boolean -> value.toString()
        is Enum<*> -> value.name
        else -> value.toString().replace(Regex("\\s+"), "_")
    }
}
