package com.candorfoods.factoryops.data.remote.mock

import com.candorfoods.factoryops.data.remote.api.AuthApi
import com.candorfoods.factoryops.data.remote.dto.LoginRequestDto
import com.candorfoods.factoryops.data.remote.dto.LoginResponseDto
import kotlinx.coroutines.delay

class MockAuthApi : AuthApi {
    // Phase 2A dev-only seed. NOT for production — production seed will use anonymized fixtures.
    private data class SeedUser(
        val password: String, val userId: String, val name: String,
        val role: String, val plantId: String
    )

    // Mirrors the backend mt_users table (real usernames, all pass123 for now).
    // plant_id is a location string ("185-Koparkhairnae", "W-202-Koparkhairne", "both").
    private val validUsers = mapOf(
        "shubhammhatre"    to SeedUser("pass123", "1", "Shubham Mhatre",    "OPERATOR",   "185-Koparkhairnae"),
        "sachinthorat"     to SeedUser("pass123", "2", "Sachin Thorat",     "OPERATOR",   "185-Koparkhairnae"),
        "amaryadav"        to SeedUser("pass123", "3", "Amar Yadav",        "OPERATOR",   "W-202-Koparkhairne"),
        "anilyadav"        to SeedUser("pass123", "4", "Anil Yadav",        "TECHNICIAN", "185-Koparkhairnae"),
        "manishkumar"      to SeedUser("pass123", "5", "Manish Kumar",      "TECHNICIAN", "W-202-Koparkhairne"),
        "abhishekghorpade" to SeedUser("pass123", "6", "Abhishek Ghorpade", "HEAD",       "both"),
        "shubhamlohar"     to SeedUser("pass123", "7", "Shubham Lohar",     "HEAD",       "both")
    )

    override suspend fun login(request: LoginRequestDto): LoginResponseDto {
        delay(600)
        // Tolerate Android keyboard auto-cap on usernames ("Shubhamlohar" → "shubhamlohar").
        val normalized = request.username.trim().lowercase()
        val user = validUsers[normalized]
            ?: throw Exception("Invalid username or password")
        if (user.password != request.password) throw Exception("Invalid username or password")
        return LoginResponseDto(
            // Backend's dev-bypass token (see backend/app/config.py + auth.py).
            // Lets post-login calls reach the real backend while login itself stays mocked.
            token = "dev-bypass-token",
            userId = user.userId,
            name = user.name,
            role = user.role,
            plantId = user.plantId
        )
    }
}
