package com.ojoclaro.android.phone

import com.ojoclaro.android.memory.MemoryPolicy

enum class PreferredContactChannel {
    WHATSAPP,
    PHONE
}

data class FavoriteContact(
    val displayName: String,
    val aliases: List<String>,
    val phoneE164: String? = null,
    val preferredChannel: PreferredContactChannel = PreferredContactChannel.WHATSAPP
) {
    fun allNames(): List<String> =
        (listOf(displayName) + aliases)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy(MemoryPolicy::normalize)
}

class FavoriteContactDirectory(
    private val contacts: List<FavoriteContact>
) {
    fun resolveName(query: String): FavoriteContact? {
        val normalizedQuery = MemoryPolicy.normalize(query)
        if (normalizedQuery.isBlank()) return null

        return contacts.firstOrNull { contact ->
            contact.allNames().any { alias -> MemoryPolicy.normalize(alias) == normalizedQuery }
        }
    }

    fun knownDisplayNames(): List<String> =
        contacts.map { it.displayName }
            .filter { it.isNotBlank() }
            .distinctBy(MemoryPolicy::normalize)

    companion object {
        fun demo(): FavoriteContactDirectory =
            FavoriteContactDirectory(DemoFavoriteContacts.contacts)
    }
}

object DemoFavoriteContacts {
    val contacts: List<FavoriteContact> = listOf(
        FavoriteContact(
            displayName = "Contacto demo",
            aliases = listOf("contacto", "contacto demo", "persona"),
            // Sin numero por defecto: no inventamos llamadas ni chats directos.
            phoneE164 = null,
            preferredChannel = PreferredContactChannel.WHATSAPP
        )
    )
}
