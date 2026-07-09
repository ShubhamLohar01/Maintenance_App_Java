package com.candorfoods.factoryops.domain.model

/**
 * A row from the `mt_users` directory (HEAD-only CRUD). `id` is 0 for a not-yet-saved
 * user; the backend assigns it. `role` is a free-text column but the UI constrains it to
 * the [UserRole] names. There is no password — new users log in with the shared fixed
 * password.
 */
data class MtUser(
    val id: Int = 0,
    val empId: String? = null,
    val name: String,
    val location: String? = null,
    val contactNo: String? = null,
    val emailId: String? = null,
    val role: String? = null,
    val username: String,
)
