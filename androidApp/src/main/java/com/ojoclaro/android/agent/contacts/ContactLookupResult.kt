package com.ojoclaro.android.agent.contacts

sealed class ContactLookupResult {
    data class Found(val contact: ContactCandidate) : ContactLookupResult()

    data class MultipleMatches(val candidates: List<ContactCandidate>) : ContactLookupResult() {
        init {
            require(candidates.isNotEmpty()) { "candidates must not be empty" }
        }
    }

    data class NotFound(val query: String) : ContactLookupResult()

    data class PermissionMissing(val permission: String) : ContactLookupResult() {
        init {
            require(permission.isNotBlank()) { "permission must not be blank" }
        }
    }
}
