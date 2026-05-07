package com.ojoclaro.android.memory

/**
 * Contrato de almacenamiento local de memoria segura.
 *
 * Reglas duras:
 *  - save() NO debe persistir nada que PrivacyGuard.canStoreMemory() rechace.
 *    El llamador no se puede confiar en el booleano: si la memoria es sensible o
 *    no aprobada por el usuario, save() simplemente la descarta.
 *  - listAllSafeSummaries() NUNCA expone contenido sensible.
 *  - findRelevant() solo trabaja sobre memoria activa y no sensible.
 */
interface MemoryStore {

    fun save(memory: UserMemory)

    fun getByType(type: MemoryType): List<UserMemory>

    fun findRelevant(query: String): List<UserMemory>

    fun delete(id: String)

    fun clearAll()

    fun listAllSafeSummaries(): List<String>
}
