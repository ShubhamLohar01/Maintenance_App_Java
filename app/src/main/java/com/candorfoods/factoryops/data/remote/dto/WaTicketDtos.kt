package com.candorfoods.factoryops.data.remote.dto

import com.candorfoods.factoryops.domain.model.tickets.RaisedTicket
import com.candorfoods.factoryops.domain.model.tickets.TicketRecipient
import com.candorfoods.factoryops.domain.model.tickets.TicketStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ─── GET /wa-tickets and /wa-tickets/{id} ────────────────────────────────────
// Timestamps arrive as ISO-8601 already converted to IST by the backend; they
// are carried through as strings and only parsed for display.

@Serializable
data class WaReceiveDto(
    @SerialName("id")                val id: Int,
    @SerialName("name")              val name: String? = null,
    @SerialName("ack_status")        val ackStatus: String = "pending",
    @SerialName("done_by")           val doneBy: String? = null,
    @SerialName("done_at")           val doneAt: String? = null,
    @SerialName("corrective_action") val correctiveAction: String? = null,
    @SerialName("received_at")       val receivedAt: String? = null,
)

@Serializable
data class WaTicketDto(
    @SerialName("id")          val id: Int,
    @SerialName("ticket_no")   val ticketNo: String,
    @SerialName("module")      val module: String? = null,
    @SerialName("asset")       val asset: String? = null,
    @SerialName("area")        val area: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("photo_url")   val photoUrl: String? = null,
    @SerialName("status")      val status: String = "open",
    @SerialName("assigned_to") val assignedTo: String? = null,
    @SerialName("raised_by")   val raisedBy: String? = null,
    @SerialName("created_at")  val createdAt: String? = null,
    @SerialName("closed_at")   val closedAt: String? = null,
    // Only present on the detail endpoint; the list omits it.
    @SerialName("recipients")  val recipients: List<WaReceiveDto> = emptyList(),
)

// ─── POST /wa-tickets/{id}/resolve ───────────────────────────────────────────

@Serializable
data class WaTicketResolveRequestDto(
    @SerialName("corrective_action") val correctiveAction: String,
)

// ─── mappers ─────────────────────────────────────────────────────────────────

fun WaReceiveDto.toDomain() = TicketRecipient(
    id = id,
    name = name,
    ackStatus = ackStatus,
    doneBy = doneBy,
    doneAt = doneAt,
    correctiveAction = correctiveAction,
    receivedAt = receivedAt,
)

fun WaTicketDto.toDomain() = RaisedTicket(
    id = id,
    ticketNo = ticketNo,
    asset = asset,
    area = area,
    description = description,
    photoUrl = photoUrl,
    status = TicketStatus.of(status),
    assignedTo = assignedTo,
    raisedBy = raisedBy,
    createdAt = createdAt,
    closedAt = closedAt,
    recipients = recipients.map { it.toDomain() },
)
