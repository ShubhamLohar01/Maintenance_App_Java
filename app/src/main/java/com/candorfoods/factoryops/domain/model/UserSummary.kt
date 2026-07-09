package com.candorfoods.factoryops.domain.model

/**
 * Lightweight directory entry used by pickers (e.g. assigning a PM plan to a
 * technician). Distinct from [User] (the logged-in principal) — it carries no
 * session/token fields, just enough to display and reference a person.
 */
data class UserSummary(
    val id: String,
    val name: String,
    val role: UserRole,
    val plantId: String,
)
