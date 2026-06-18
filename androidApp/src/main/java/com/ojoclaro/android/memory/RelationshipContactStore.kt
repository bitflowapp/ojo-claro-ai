package com.ojoclaro.android.memory

import android.content.Context
import android.content.SharedPreferences

/**
 * Un contacto confiable vinculado a una RELACIÓN (clave canónica). Guarda el
 * número para poder construir el deep link, pero los logs lo redactan: nunca el
 * número completo.
 */
data class RelationshipContact(
    val key: String,
    val label: String,
    val phoneE164: String,
    val source: String
) {
    val phoneDigits: String get() = phoneE164.filter(Char::isDigit)
    val phoneLen: Int get() = phoneDigits.length
    val phoneEnding: String get() = phoneDigits.takeLast(4)

    /** La clave canónica ES el grupo de alias ("pareja" cubre novia/pareja/…). */
    val aliasGroup: String get() = key

    /** Log SIN número: sólo longitudes/flags. */
    fun redactedForLog(): String =
        "key=$key labelLen=${label.length} phoneLen=$phoneLen source=$source"

    /**
     * Defensivo: el `toString()` por defecto de un data class imprimiría
     * `phoneE164` completo (fuga de PII si esto cae en un log/crash). Se
     * sobrescribe para exponer SÓLO metadata segura — nunca el número completo;
     * a lo sumo los últimos 4, que ya se usan como identificador redactado.
     */
    override fun toString(): String =
        "RelationshipContact(canonicalKey=$key, aliasGroup=$aliasGroup, " +
            "labelLen=${label.length}, phoneLen=$phoneLen, phoneEnding=$phoneEnding, source=$source)"
}

/**
 * Almacén LOCAL y privado de contactos por relación ("pareja" → contacto).
 *
 * Seguridad/privacidad:
 *  - SharedPreferences privadas del propio paquete (no repo, no docs, no red).
 *  - El número se guarda sólo para resolver el deep link; jamás se loguea.
 *  - Validado con [SafeContactMemory.normalizePhoneNumber].
 *
 * Testeable inyectando un [SharedPreferences] (FakeSharedPreferences).
 */
class RelationshipContactStore(private val prefs: SharedPreferences) {

    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    /** Vincula (o re-vincula) una relación a un contacto. null si el número no es válido. */
    fun link(
        key: String,
        label: String,
        phoneE164: String,
        source: String = "voice_link"
    ): RelationshipContact? {
        val k = key.trim().lowercase()
        if (k.isBlank()) return null
        val sane = SafeContactMemory.normalizePhoneNumber(phoneE164) ?: return null
        val cleanLabel = label.replace(Regex("\\s+"), " ").trim().take(MAX_LABEL).ifBlank { k }
        val cleanSource = source.take(MAX_SOURCE)
        prefs.edit()
            .putString(field(k, FIELD_LABEL), cleanLabel)
            .putString(field(k, FIELD_PHONE), sane)
            .putString(field(k, FIELD_SOURCE), cleanSource)
            .apply()
        return RelationshipContact(k, cleanLabel, sane, cleanSource)
    }

    fun resolve(key: String): RelationshipContact? {
        val k = key.trim().lowercase()
        val phone = prefs.getString(field(k, FIELD_PHONE), null) ?: return null
        val label = prefs.getString(field(k, FIELD_LABEL), null) ?: k
        val source = prefs.getString(field(k, FIELD_SOURCE), null) ?: "unknown"
        return RelationshipContact(k, label, phone, source)
    }

    fun isLinked(key: String): Boolean = resolve(key) != null

    fun forget(key: String) {
        val k = key.trim().lowercase()
        prefs.edit()
            .remove(field(k, FIELD_LABEL))
            .remove(field(k, FIELD_PHONE))
            .remove(field(k, FIELD_SOURCE))
            .apply()
    }

    fun clearAll() {
        val edit = prefs.edit()
        prefs.all.keys.filter { it.startsWith(PREFIX) }.forEach(edit::remove)
        edit.apply()
    }

    private fun field(key: String, field: String) = "$PREFIX$key.$field"

    companion object {
        const val PREFS_NAME = "ojo_claro_relationship_contacts"
        private const val PREFIX = "rel."
        private const val FIELD_LABEL = "label"
        private const val FIELD_PHONE = "phone"
        private const val FIELD_SOURCE = "source"
        private const val MAX_LABEL = 60
        private const val MAX_SOURCE = 24
    }
}
