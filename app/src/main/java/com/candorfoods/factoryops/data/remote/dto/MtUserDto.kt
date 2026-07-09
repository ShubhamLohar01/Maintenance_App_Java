package com.candorfoods.factoryops.data.remote.dto

import com.candorfoods.factoryops.domain.model.MtUser
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Mirrors an mt_users row served by GET /mt-users. ignoreUnknownKeys handles created_at. */
@Serializable
data class MtUserDto(
    @SerialName("id")         val id: Int = 0,
    @SerialName("emp_id")     val empId: String? = null,
    @SerialName("name")       val name: String = "",
    @SerialName("location")   val location: String? = null,
    @SerialName("contact_no") val contactNo: String? = null,
    @SerialName("email_id")   val emailId: String? = null,
    @SerialName("role")       val role: String? = null,
    @SerialName("username")   val username: String = "",
)

fun MtUserDto.toDomain(): MtUser = MtUser(
    id = id,
    empId = empId,
    name = name,
    location = location,
    contactNo = contactNo,
    emailId = emailId,
    role = role,
    username = username,
)

/**
 * Body for POST /mt-users and PUT /mt-users/{id} (same full-row shape for both). `id` is
 * not sent — it's assigned on create and is the path param on update.
 */
@Serializable
data class MtUserWriteDto(
    @SerialName("emp_id")     val empId: String? = null,
    @SerialName("name")       val name: String,
    @SerialName("location")   val location: String? = null,
    @SerialName("contact_no") val contactNo: String? = null,
    @SerialName("email_id")   val emailId: String? = null,
    @SerialName("role")       val role: String? = null,
    @SerialName("username")   val username: String,
)

fun MtUser.toWriteDto(): MtUserWriteDto = MtUserWriteDto(
    empId = empId,
    name = name,
    location = location,
    contactNo = contactNo,
    emailId = emailId,
    role = role,
    username = username,
)
