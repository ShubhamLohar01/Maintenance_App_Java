package com.candorfoods.factoryops.domain.model

data class User(
    val id: String,
    val name: String,
    val username: String,
    val role: UserRole,
    val plantId: String,
    val jwtToken: String,
    val tokenExpiresAt: Long
)
