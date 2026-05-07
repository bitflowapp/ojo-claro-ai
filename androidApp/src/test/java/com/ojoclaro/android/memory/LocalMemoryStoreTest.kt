package com.ojoclaro.android.memory

import com.ojoclaro.android.maps.SafeLocationMemory
import com.ojoclaro.android.test.FakeSharedPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalMemoryStoreTest {

    @Test
    fun savesSafeMemory() {
        val store = store()

        store.save(memory("m1", MemoryType.TRUSTED_CONTACT, "Sofi"))

        val memories = store.getByType(MemoryType.TRUSTED_CONTACT)

        assertEquals(1, memories.size)
        assertEquals("Sofi", memories.first().label)
    }

    @Test
    fun listsSafeMemory() {
        val store = store()

        store.save(memory("m1", MemoryType.USER_PREFERENCE, "respuestas cortas"))

        val summaries = store.listAllSafeSummaries()

        assertEquals(1, summaries.size)
        assertTrue(summaries.first().contains("respuestas cortas"))
    }

    @Test
    fun doesNotSaveMemoryWithoutUserApproval() {
        val store = store()

        store.save(
            memory(
                id = "m1",
                type = MemoryType.USER_PREFERENCE,
                value = "respuestas cortas",
                userApproved = false
            )
        )

        assertTrue(store.findRelevant("").isEmpty())
    }

    @Test
    fun doesNotSaveSensitiveMemory() {
        val store = store()

        store.save(
            memory(
                id = "m1",
                type = MemoryType.USER_PREFERENCE,
                value = "mi banco",
                isSensitive = true
            )
        )

        assertTrue(store.findRelevant("").isEmpty())
    }

    @Test
    fun doesNotSavePasswordLikeMemory() {
        val store = store()

        store.save(
            memory(
                id = "m1",
                type = MemoryType.USER_PREFERENCE,
                value = "mi contraseña es 123456"
            )
        )

        assertTrue(store.findRelevant("").isEmpty())
    }

    @Test
    fun doesNotSaveVerificationCodeMemory() {
        val store = store()

        store.save(
            memory(
                id = "m1",
                type = MemoryType.USER_PREFERENCE,
                value = "mi código de verificación es 123456"
            )
        )

        assertTrue(store.findRelevant("").isEmpty())
    }

    @Test
    fun doesNotSaveCardLikeNumberMemory() {
        val store = store()

        store.save(
            memory(
                id = "m1",
                type = MemoryType.USER_PREFERENCE,
                value = "mi tarjeta es 4111 1111 1111 1111"
            )
        )

        assertTrue(store.findRelevant("").isEmpty())
    }

    @Test
    fun getByTypeReturnsOnlyRequestedType() {
        val store = store()

        store.save(memory("m1", MemoryType.TRUSTED_CONTACT, "Sofi"))
        store.save(memory("m2", MemoryType.USER_PREFERENCE, "respuestas cortas"))

        val trustedContacts = store.getByType(MemoryType.TRUSTED_CONTACT)

        assertEquals(1, trustedContacts.size)
        assertEquals("Sofi", trustedContacts.first().label)
    }

    @Test
    fun getByTypeSortsByUpdatedAtDescending() {
        val store = store()

        store.save(
            memory(
                id = "old",
                type = MemoryType.USER_PREFERENCE,
                value = "voz lenta",
                updatedAtMillis = 1L
            )
        )
        store.save(
            memory(
                id = "new",
                type = MemoryType.USER_PREFERENCE,
                value = "respuestas cortas",
                updatedAtMillis = 20L
            )
        )

        val memories = store.getByType(MemoryType.USER_PREFERENCE)

        assertEquals("new", memories.first().id)
        assertEquals("old", memories.last().id)
    }

    @Test
    fun findRelevantReturnsMatchingMemory() {
        val store = store()

        store.save(memory("m1", MemoryType.TRUSTED_CONTACT, "Sofi"))
        store.save(memory("m2", MemoryType.USER_PREFERENCE, "respuestas cortas"))

        val results = store.findRelevant("Sofi")

        assertEquals(1, results.size)
        assertEquals("Sofi", results.first().label)
    }

    @Test
    fun findRelevantIsAccentInsensitive() {
        val store = store()

        store.save(memory("m1", MemoryType.USER_PREFERENCE, "respuestas cortas"))

        val results = store.findRelevant("preferencia respuestas")

        assertEquals(1, results.size)
        assertEquals("respuestas cortas", results.first().value)
    }

    @Test
    fun expiredMemoryIsNotReturned() {
        val store = store(nowMillis = 10_000L)

        store.save(
            memory(
                id = "expired",
                type = MemoryType.USER_PREFERENCE,
                value = "voz lenta",
                expiresAtMillis = 5_000L
            )
        )

        assertTrue(store.findRelevant("").isEmpty())
        assertTrue(store.getByType(MemoryType.USER_PREFERENCE).isEmpty())
    }

    @Test
    fun nonExpiredMemoryIsReturned() {
        val store = store(nowMillis = 10_000L)

        store.save(
            memory(
                id = "active",
                type = MemoryType.USER_PREFERENCE,
                value = "voz lenta",
                expiresAtMillis = 20_000L
            )
        )

        assertEquals(1, store.findRelevant("").size)
    }

    @Test
    fun deletesMemory() {
        val store = store()

        store.save(memory("m1", MemoryType.TRUSTED_CONTACT, "Sofi"))
        store.delete("m1")

        assertTrue(store.getByType(MemoryType.TRUSTED_CONTACT).isEmpty())
    }

    @Test
    fun deletingUnknownMemoryDoesNotCrash() {
        val store = store()

        store.delete("missing-id")

        assertTrue(store.findRelevant("").isEmpty())
    }

    @Test
    fun clearAllWorks() {
        val store = store()

        store.save(memory("m1", MemoryType.TRUSTED_CONTACT, "Sofi"))
        store.save(memory("m2", MemoryType.USER_PREFERENCE, "respuestas cortas"))

        store.clearAll()

        assertTrue(store.findRelevant("").isEmpty())
        assertTrue(store.listAllSafeSummaries().isEmpty())
    }

    @Test
    fun listAllSafeSummariesDoesNotExposeSensitiveMemory() {
        val store = store()

        store.save(memory("m1", MemoryType.USER_PREFERENCE, "respuestas cortas"))
        store.save(
            memory(
                id = "m2",
                type = MemoryType.USER_PREFERENCE,
                value = "mi contraseña es 123456"
            )
        )

        val summaries = store.listAllSafeSummaries()

        assertEquals(1, summaries.size)
        assertTrue(summaries.first().contains("respuestas cortas"))
        assertFalse(summaries.joinToString(" ").contains("123456"))
    }

    @Test
    fun listLocationAliasesDoesNotExposeCoordinates() {
        val store = store()
        store.save(
            memory(
                id = "home",
                type = MemoryType.LOCATION_ALIAS,
                label = "casa",
                value = SafeLocationMemory.value(-38.95, -68.06, 20f)
            )
        )

        val summary = store.listAllSafeSummaries().joinToString(" ")

        assertTrue(summary.contains("casa"))
        assertFalse(summary.contains("-38.95"))
        assertFalse(summary.contains("-68.06"))
    }

    private fun store(nowMillis: Long = 10L): LocalMemoryStore =
        LocalMemoryStore(
            preferences = FakeSharedPreferences(),
            nowMillis = { nowMillis }
        )

    private fun memory(
        id: String,
        type: MemoryType,
        value: String,
        label: String = value,
        createdAtMillis: Long = 1L,
        updatedAtMillis: Long = 1L,
        expiresAtMillis: Long? = null,
        isSensitive: Boolean = false,
        userApproved: Boolean = true
    ): UserMemory =
        UserMemory(
            id = id,
            type = type,
            label = label,
            value = value,
            createdAtMillis = createdAtMillis,
            updatedAtMillis = updatedAtMillis,
            expiresAtMillis = expiresAtMillis,
            isSensitive = isSensitive,
            userApproved = userApproved
        )
}
