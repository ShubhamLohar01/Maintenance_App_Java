package com.candorfoods.factoryops.domain.repository

import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.tickets.RaisedTicket

/** Which slice of the queue to fetch. */
enum class TicketScope(val backendValue: String) {
    LIVE("live"),       // open + acknowledged, oldest first
    HISTORY("history"), // resolved, newest first
}

/**
 * Outcome of acknowledging or resolving.
 *
 * [Conflict] is modelled separately from [Failure] because it is not really an
 * error: two technicians opened the same ticket and one tapped first. The UI
 * must say who has it and refresh, not offer a retry — retrying would fail
 * identically forever.
 */
sealed class TicketActionResult {
    data class Success(val ticket: RaisedTicket) : TicketActionResult()
    data class Conflict(val message: String) : TicketActionResult()
    data class Failure(val message: String) : TicketActionResult()
}

/** WhatsApp-raised complaints, backed by the backend /wa-tickets API. */
interface RaisedTicketRepository {

    suspend fun list(scope: TicketScope): Result<List<RaisedTicket>>

    /** One ticket including its acknowledgment trail (`recipients`). */
    suspend fun get(id: Int): Result<RaisedTicket>

    /** Take the ticket; assigns it to the calling technician. */
    suspend fun acknowledge(id: Int): TicketActionResult

    /** Close it with a note of what was done. Blank notes are rejected server-side. */
    suspend fun resolve(id: Int, correctiveAction: String): TicketActionResult
}
