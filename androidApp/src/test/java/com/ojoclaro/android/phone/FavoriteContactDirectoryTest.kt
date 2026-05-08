package com.ojoclaro.android.phone

import com.ojoclaro.android.memory.MemoryStore
import com.ojoclaro.android.memory.MemoryType
import com.ojoclaro.android.memory.UserMemory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FavoriteContactDirectoryTest {

    @Test
    fun demoContactoSeResuelvePorAliasSinLeerAgenda() {
        val favorite = FavoriteContactDirectory.demo().resolveName("contacto demo")

        assertEquals("Contacto demo", favorite?.displayName)
        assertTrue(favorite?.aliases.orEmpty().any { it.equals("contacto", ignoreCase = true) })
        assertNull(favorite?.phoneE164)
    }

    @Test
    fun resolverUsaFavoritoManualConNumeroSeguro() {
        val resolver = MemoryContactResolver(
            memoryStore = EmptyMemoryStore,
            favoriteContactDirectory = FavoriteContactDirectory(
                listOf(
                    FavoriteContact(
                        displayName = "Contacto principal",
                        aliases = listOf("contacto principal", "persona principal"),
                        phoneE164 = "+5492991234567",
                        preferredChannel = PreferredContactChannel.WHATSAPP
                    )
                )
            )
        )

        val result = resolver.resolve("contacto principal")

        val resolved = assertIs<ContactResolutionResult.Resolved>(result)
        assertEquals("Contacto principal", resolved.candidate.displayName)
        assertEquals("+5492991234567", resolved.candidate.phoneE164)
        assertEquals(ContactSource.FAVORITE_DEMO, resolved.candidate.source)
    }

    @Test
    fun demoSinNumeroNoInventaTelefono() {
        val result = MemoryContactResolver(memoryStore = EmptyMemoryStore).resolve("contacto")

        assertIs<ContactResolutionResult.NotFound>(result)
    }

    private object EmptyMemoryStore : MemoryStore {
        override fun save(memory: UserMemory) = Unit
        override fun getByType(type: MemoryType): List<UserMemory> = emptyList()
        override fun findRelevant(query: String): List<UserMemory> = emptyList()
        override fun delete(id: String) = Unit
        override fun clearAll() = Unit
        override fun listAllSafeSummaries(): List<String> = emptyList()
    }
}
