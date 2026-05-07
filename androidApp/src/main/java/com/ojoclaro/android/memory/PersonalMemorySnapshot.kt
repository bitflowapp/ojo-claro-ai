package com.ojoclaro.android.memory

data class PersonalMemorySnapshot(
    val memories: List<PersonalAgentMemory> = emptyList()
) {
    val contacts: List<PersonalAgentMemory> get() = memories.filter { it.type == PersonalMemoryType.CONTACT }
    val safePhones: List<PersonalAgentMemory> get() = memories.filter { it.type == PersonalMemoryType.SAFE_PHONE }
    val places: List<PersonalAgentMemory> get() = memories.filter { it.type == PersonalMemoryType.PLACE }
    val routines: List<PersonalAgentMemory> get() = memories.filter { it.type == PersonalMemoryType.ROUTINE }
    val preferences: List<PersonalAgentMemory> get() = memories.filter { it.type == PersonalMemoryType.PREFERENCE }
    val pendingTasks: List<PersonalAgentMemory> get() = memories.filter { it.type == PersonalMemoryType.PENDING_TASK }
    val messageStyles: List<PersonalAgentMemory> get() = memories.filter { it.type == PersonalMemoryType.MESSAGE_STYLE }

    fun summary(maxItems: Int = 8): String =
        memories
            .filter { it.userApproved && !it.isSensitive && it.canBeSpoken }
            .take(maxItems.coerceAtLeast(0))
            .joinToString(" | ") { safeSummary(it) }

    fun labelsFor(type: PersonalMemoryType): List<String> =
        memories.filter { it.type == type }.map { it.label }

    private fun safeSummary(memory: PersonalAgentMemory): String = when (memory.type) {
        PersonalMemoryType.CONTACT -> "Contacto ${memory.label}."
        PersonalMemoryType.SAFE_PHONE -> "Numero seguro de ${memory.label}."
        PersonalMemoryType.PLACE -> "Lugar ${memory.label}."
        PersonalMemoryType.ROUTINE -> "Rutina: ${memory.value}."
        PersonalMemoryType.PREFERENCE -> "Preferencia: ${memory.value}."
        PersonalMemoryType.PENDING_TASK -> "Pendiente: ${memory.value}."
        PersonalMemoryType.MESSAGE_STYLE -> "Estilo de mensaje: ${memory.value}."
    }
}

