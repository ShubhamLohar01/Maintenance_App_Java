package com.candorfoods.factoryops.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val name: String,
    val username: String,
    val role: String,
    val plantId: String,
    val jwtToken: String,
    val tokenExpiresAt: Long
)
