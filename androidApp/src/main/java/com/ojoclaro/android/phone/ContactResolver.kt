package com.ojoclaro.android.phone

import com.ojoclaro.android.memory.MemoryPolicy
import com.ojoclaro.android.memory.MemoryStore
import com.ojoclaro.android.memory.MemoryType
import com.ojoclaro.android.memory.SafeContactMemory

/**
 * Resolución de contactos sin tocar la libreta del sistema.
 *
 * Reglas duras:
 *  - Esta capa NO pide READ_CONTACTS por sí sola. Eso es trabajo de un futuro
 *    SystemContactResolver con su propio PermissionGate.
 *  - Hoy resolvemos solo lo que el usuario guardó en memoria local
 *    (TRUSTED_CONTACT, EMERGENCY_CONTACT) o lo que dictó como número directo.
 *  - Si no hay match, devolvemos NotFound para que el agente pida un número
 *    o explique cómo activar contactos en una fase futura.
 *  - Nunca devolvemos un número sin haberlo validado por PhoneActionExecutor.
 */
data class ContactCandidate(
    val displayName: String,
    val phoneE164: String,
    val source: ContactSource,
    val memoryId: String? = null
)

enum class ContactSource {
    LOCAL_MEMORY,
    FAVORITE_DEMO,
    EMERGENCY_DEFAULT,
    USER_DICTATED_NUMBER
}

sealed class ContactResolutionResult {
    data class Resolved(val candidate: ContactCandidate) : ContactResolutionResult()

    data class MultipleMatches(val candidates: List<ContactCandidate>) : ContactResolutionResult()

    data object NotFound : ContactResolutionResult()

    /**
     * Reservado para una futura SystemContactResolver. La impl actual
     * NO devuelve este resultado porque no pide READ_CONTACTS.
     */
    data object NeedsContactsPermission : ContactResolutionResult()
}

interface ContactResolver {
    fun resolve(query: String): ContactResolutionResult
}

/**
 * Implementación local-first. Solo mira memoria del usuario y números dictados
 * directamente. No requiere permiso de contactos.
 *
 * Heurística:
 *  1. Si el query es un número de teléfono válido (sanitizado), devolverlo
 *     como candidato directo.
 *  2. Si el query coincide con "emergencias" / "911" / similares, devolver
 *     el número de emergencia por defecto.
 *  3. Buscar en memoria por TRUSTED_CONTACT / EMERGENCY_CONTACT con alias
 *     exacto (normalizado, sin acentos).
 *  4. Si hay un único candidato con número, Resolved.
 *  5. Si hay varios, MultipleMatches para que el agente pregunte.
 *  6. Si no hay nada, NotFound.
 */
class MemoryContactResolver(
    private val memoryStore: MemoryStore?,
    private val favoriteContactDirectory: FavoriteContactDirectory = FavoriteContactDirectory.demo(),
    private val emergencyAliases: Set<String> = DEFAULT_EMERGENCY_ALIASES,
    private val emergencyNumber: String = PhoneActionExecutor.DEFAULT_EMERGENCY_NUMBER
) : ContactResolver {

    override fun resolve(query: String): ContactResolutionResult {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return ContactResolutionResult.NotFound

        // 1) Número dictado directo: "1123456789" / "11 2345 6789" / "+5491123456789".
        PhoneActionExecutor.sanitizePhoneNumber(cleanQuery)?.let { e164 ->
            return ContactResolutionResult.Resolved(
                ContactCandidate(
                    displayName = e164,
                    phoneE164 = e164,
                    source = ContactSource.USER_DICTATED_NUMBER
                )
            )
        }

        val normalizedQuery = MemoryPolicy.normalize(cleanQuery)
        val wantsStoredEmergencyContact = normalizedQuery.contains("contacto de emergencia")

        // Favoritos de demo/manuales. No leen la agenda del telefono.
        // Si no tienen numero, seguimos buscando en memoria y luego devolvemos NotFound.
        favoriteContactDirectory.resolveName(cleanQuery)?.let { favorite ->
            favorite.phoneE164
                ?.let(PhoneActionExecutor::sanitizePhoneNumber)
                ?.let { phone ->
                    return ContactResolutionResult.Resolved(
                        ContactCandidate(
                            displayName = favorite.displayName,
                            phoneE164 = phone,
                            source = ContactSource.FAVORITE_DEMO
                        )
                    )
                }
        }

        // 2) Emergencias.
        if (!wantsStoredEmergencyContact && normalizedQuery in emergencyAliases) {
            return ContactResolutionResult.Resolved(
                ContactCandidate(
                    displayName = "Emergencias",
                    phoneE164 = emergencyNumber,
                    source = ContactSource.EMERGENCY_DEFAULT
                )
            )
        }

        // 3) Memoria local — solo no sensible y aprobada.
        val store = memoryStore ?: return ContactResolutionResult.NotFound
        val candidates = store
            .findRelevant("")
            .asSequence()
            // Hoy MemoryType solo incluye TRUSTED_CONTACT. Cuando se agregue
            // EMERGENCY_CONTACT como tipo separado, sumarlo aquí.
            .filter { it.type == MemoryType.TRUSTED_CONTACT || it.type == MemoryType.EMERGENCY_CONTACT }
            .filter { it.userApproved && !it.isSensitive }
            .filter { memory ->
                val normalizedLabel = MemoryPolicy.normalize(memory.label)
                val normalizedValueAlias = SafeContactMemory.contactText(memory.value)
                    .takeIf { it.isNotBlank() }
                    ?.let(MemoryPolicy::normalize)
                if (wantsStoredEmergencyContact) {
                    memory.type == MemoryType.EMERGENCY_CONTACT
                } else {
                    normalizedLabel == normalizedQuery ||
                        normalizedValueAlias == normalizedQuery
                }
            }
            .mapNotNull { memory ->
                val phone = SafeContactMemory.extractPhoneNumber(memory.value)
                    ?: PhoneActionExecutor.extractSafePhoneNumber("${memory.label} ${memory.value}")
                phone?.let {
                    ContactCandidate(
                        displayName = memory.label,
                        phoneE164 = it,
                        source = ContactSource.LOCAL_MEMORY,
                        memoryId = memory.id
                    )
                }
            }
            .distinctBy { it.phoneE164 }
            .toList()

        return when (candidates.size) {
            0 -> ContactResolutionResult.NotFound
            1 -> ContactResolutionResult.Resolved(candidates.first())
            else -> ContactResolutionResult.MultipleMatches(candidates)
        }
    }

    companion object {
        // Solo aliases comunes en español. NO interpretar el query "abuela" como
        // emergencia: la lista es estricta y el agente debe ofrecer otras opciones.
        val DEFAULT_EMERGENCY_ALIASES: Set<String> = setOf(
            "emergencia",
            "emergencias",
            "911"
        )
    }
}
