package com.candorfoods.factoryops.data.repository

import com.candorfoods.factoryops.data.local.AuthPreferences
import com.candorfoods.factoryops.data.remote.api.A185ReadingsApi
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.a185.A185_PLANT
import com.candorfoods.factoryops.domain.model.a185.A185Prefill
import com.candorfoods.factoryops.domain.model.a185.A185Reading
import com.candorfoods.factoryops.domain.repository.A185ReadingsRepository
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * JSON boundary for [A185ReadingsApi]. Builds the snake_case body the backend expects
 * (all six meter fields sent every save — null when blank on screen — so a serializer
 * never silently omits one; consumptions and PF are NOT sent, the backend computes them)
 * and parses stored rows back into [A185Reading]. Bearer token per call.
 */
@Singleton
class A185ReadingsRepositoryImpl @Inject constructor(
    private val api: A185ReadingsApi,
    private val authPreferences: AuthPreferences,
) : A185ReadingsRepository {

    override suspend fun submit(reading: A185Reading): Result<A185Reading> = try {
        val stored = api.submit(bearer(), reading.toBody())
        Result.Success(stored.toReading())
    } catch (e: Exception) {
        // Surface the backend's own 400 text — "HTTP 400 Bad Request" tells the
        // technician nothing about which section was rejected or why.
        Result.Error(friendlyBackendError(e, "Failed to save readings"), e)
    }

    override suspend fun list(from: String?, to: String?): Result<List<A185Reading>> = try {
        Result.Success(api.list(bearer(), A185_PLANT, from, to).map { it.toReading() })
    } catch (e: Exception) {
        Result.Error(friendlyBackendError(e, "Failed to load history"), e)
    }

    override suspend fun prefill(date: String): Result<A185Prefill> = try {
        val o = api.prefill(bearer(), A185_PLANT, date)
        val openings = (o["openings"] as? JsonObject)
        Result.Success(
            A185Prefill(
                readingDate = o.str("reading_date").ifBlank { date },
                sourceDate = o.strOrNull("source_date"),
                htPowerFactor = (o["ht_power_factor"] as? JsonPrimitive)?.doubleOrNull ?: 0.99,
                htInitial = openings.num("ht_initial"),
                coldInitial = openings.num("cold_initial"),
                uvInitial = openings.num("uv_initial"),
                alreadySaved = (o["already_saved"] as? JsonPrimitive)?.booleanOrNull ?: false,
            )
        )
    } catch (e: Exception) {
        Result.Error(friendlyBackendError(e, "Failed to load previous readings"), e)
    }

    override suspend fun delete(date: String): Result<Unit> = try {
        api.delete(bearer(), date, A185_PLANT)
        Result.Success(Unit)
    } catch (e: Exception) {
        // 403 is the expected answer for a non-HEAD caller, and this sentence beats
        // whatever the server says; every other status defers to the backend's detail.
        Result.Error(
            friendlyBackendError(
                e,
                fallback = "Failed to delete readings",
                forbidden = "Only a Head can delete a day's readings",
            ),
            e,
        )
    }

    private fun bearer() = "Bearer ${authPreferences.getToken().orEmpty()}"

    // ── JSON mapping ─────────────────────────────────────────────────────────────

    /** All six meter fields always sent (JsonNull when absent) so a partial day is explicit. */
    private fun A185Reading.toBody(): JsonObject = buildJsonObject {
        put("plant", JsonPrimitive(plant.ifBlank { A185_PLANT }))
        put("reading_date", JsonPrimitive(readingDate))
        put("ht_initial", htInitial.toJson())
        put("ht_final", htFinal.toJson())
        put("cold_initial", coldInitial.toJson())
        put("cold_final", coldFinal.toJson())
        put("uv_initial", uvInitial.toJson())
        put("uv_final", uvFinal.toJson())
        val r = remark?.trim().orEmpty()
        put("remark", if (r.isEmpty()) JsonNull else JsonPrimitive(r))
    }

    private fun JsonObject.toReading(): A185Reading = A185Reading(
        id = (this["id"] as? JsonPrimitive)?.intOrNull,
        plant = str("plant").ifBlank { A185_PLANT },
        readingDate = str("reading_date"),
        htInitial = num("ht_initial"), htFinal = num("ht_final"),
        htPowerFactor = num("ht_power_factor"), htConsumption = num("ht_consumption"),
        coldInitial = num("cold_initial"), coldFinal = num("cold_final"),
        coldConsumption = num("cold_consumption"),
        uvInitial = num("uv_initial"), uvFinal = num("uv_final"),
        uvConsumption = num("uv_consumption"),
        remark = strOrNull("remark"),
    )

    private fun Double?.toJson(): JsonElement = this?.let { JsonPrimitive(it) } ?: JsonNull
    private fun JsonObject?.num(key: String): Double? = (this?.get(key) as? JsonPrimitive)?.doubleOrNull
    private fun JsonObject.str(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
    private fun JsonObject.strOrNull(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
}
