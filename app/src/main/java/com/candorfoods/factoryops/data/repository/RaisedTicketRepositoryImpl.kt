package com.candorfoods.factoryops.data.repository

import com.candorfoods.factoryops.data.local.AuthPreferences
import com.candorfoods.factoryops.data.remote.api.WaTicketsApi
import com.candorfoods.factoryops.data.remote.dto.WaTicketResolveRequestDto
import com.candorfoods.factoryops.data.remote.dto.toDomain
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.tickets.RaisedTicket
import com.candorfoods.factoryops.domain.repository.RaisedTicketRepository
import com.candorfoods.factoryops.domain.repository.TicketActionResult
import com.candorfoods.factoryops.domain.repository.TicketScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RaisedTicketRepositoryImpl @Inject constructor(
    private val api: WaTicketsApi,
    private val authPreferences: AuthPreferences,
) : RaisedTicketRepository {

    private suspend fun bearerToken() = "Bearer ${authPreferences.getToken().orEmpty()}"

    override suspend fun list(scope: TicketScope): Result<List<RaisedTicket>> = try {
        Result.Success(api.list(bearerToken(), scope.backendValue).map { it.toDomain() })
    } catch (e: Exception) {
        Result.Error(e.detailOrNull() ?: e.message ?: "Failed to load tickets", e)
    }

    override suspend fun get(id: Int): Result<RaisedTicket> = try {
        Result.Success(api.get(bearerToken(), id).toDomain())
    } catch (e: Exception) {
        Result.Error(e.detailOrNull() ?: e.message ?: "Failed to load ticket", e)
    }

    override suspend fun acknowledge(id: Int): TicketActionResult =
        runAction("Failed to acknowledge") { api.acknowledge(bearerToken(), id).toDomain() }

    override suspend fun resolve(id: Int, correctiveAction: String): TicketActionResult =
        runAction("Failed to resolve") {
            api.resolve(bearerToken(), id, WaTicketResolveRequestDto(correctiveAction)).toDomain()
        }

    /**
     * Runs a write and separates the one outcome the UI must treat differently: a
     * 409 means someone else took or closed the ticket, so the screen should show
     * who and refresh rather than offering a retry that can never succeed.
     */
    private suspend fun runAction(
        fallback: String,
        block: suspend () -> RaisedTicket,
    ): TicketActionResult = try {
        TicketActionResult.Success(block())
    } catch (e: HttpException) {
        val detail = e.detailOrNull()
        if (e.code() == HTTP_CONFLICT) TicketActionResult.Conflict(detail ?: "Ticket already taken")
        else TicketActionResult.Failure(detail ?: e.message ?: fallback)
    } catch (e: Exception) {
        TicketActionResult.Failure(e.message ?: fallback)
    }

    private companion object {
        const val HTTP_CONFLICT = 409

        /** Lenient: an error body is diagnostic, never worth a second crash. */
        val ERROR_JSON = Json { ignoreUnknownKeys = true; isLenient = true }

        /**
         * FastAPI puts the human-readable reason in `{"detail": "..."}`. Without
         * this the user would see Retrofit's "HTTP 409 Conflict" instead of
         * "already acknowledged by Vipul ware".
         */
        fun Exception.detailOrNull(): String? {
            val body = (this as? HttpException)?.response()?.errorBody()?.string()
            if (body.isNullOrBlank()) return null
            return runCatching {
                ERROR_JSON.parseToJsonElement(body).jsonObject["detail"]?.jsonPrimitive?.content
            }.getOrNull()?.takeIf { it.isNotBlank() }
        }
    }
}
