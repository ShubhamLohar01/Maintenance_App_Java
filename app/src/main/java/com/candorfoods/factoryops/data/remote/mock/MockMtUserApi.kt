package com.candorfoods.factoryops.data.remote.mock

import com.candorfoods.factoryops.data.remote.api.MtUserApi
import com.candorfoods.factoryops.data.remote.dto.MtUserDto
import com.candorfoods.factoryops.data.remote.dto.MtUserWriteDto
import kotlinx.coroutines.delay

/** In-memory mt_users for USE_MOCK_API builds. Mirrors the backend's lowercase-username
 *  and duplicate-username behaviour so the Manage Users flow can be exercised offline. */
class MockMtUserApi : MtUserApi {

    private val users = mutableListOf(
        MtUserDto(1, "E-001", "Shubham Lohar", "both", "9000000001", "shubham@candorfoods.in", "HEAD", "shubhamlohar"),
        MtUserDto(2, "E-002", "Abhishek Ghorpade", "both", "9000000002", null, "HEAD", "abhishekghorpade"),
        MtUserDto(3, "E-003", "Ravi Patil", "A-185", "9000000003", null, "TECHNICIAN", "ravipatil"),
    )
    private var nextId = 4

    override suspend fun list(token: String): List<MtUserDto> {
        delay(300)
        return users.sortedBy { it.name.lowercase() }
    }

    override suspend fun add(token: String, dto: MtUserWriteDto): MtUserDto {
        delay(300)
        val uname = dto.username.trim().lowercase()
        if (users.any { it.username == uname }) error("username already exists")
        val created = MtUserDto(
            id = nextId++,
            empId = dto.empId, name = dto.name, location = dto.location,
            contactNo = dto.contactNo, emailId = dto.emailId, role = dto.role,
            username = uname,
        )
        users.add(created)
        return created
    }

    override suspend fun update(token: String, id: Int, dto: MtUserWriteDto): MtUserDto {
        delay(300)
        val uname = dto.username.trim().lowercase()
        if (users.any { it.username == uname && it.id != id }) error("username already exists")
        val idx = users.indexOfFirst { it.id == id }
        if (idx < 0) error("User not found")
        val updated = MtUserDto(
            id = id,
            empId = dto.empId, name = dto.name, location = dto.location,
            contactNo = dto.contactNo, emailId = dto.emailId, role = dto.role,
            username = uname,
        )
        users[idx] = updated
        return updated
    }

    override suspend fun delete(token: String, id: Int): MtUserDto {
        delay(300)
        val idx = users.indexOfFirst { it.id == id }
        if (idx < 0) error("User not found")
        return users.removeAt(idx)
    }
}
