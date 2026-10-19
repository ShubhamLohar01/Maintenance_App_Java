package com.candorfoods.factoryops.data.repository

import com.candorfoods.factoryops.data.local.AuthPreferences
import com.candorfoods.factoryops.data.remote.api.W202ReadingsApi
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.w202.W202MeterKind
import com.candorfoods.factoryops.domain.model.w202.W202MeterReading
import com.candorfoods.factoryops.domain.model.w202.W202Prefill
import com.candorfoods.factoryops.domain.model.w202.W202_PLANT
import com.candorfoods.factoryops.domain.repository.W202ReadingsRepository
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
 * JSON boundary for [W202ReadingsApi]. Builds the snake_case body the backend expects (all
 * four meter fields sent every save — null when blank on screen — so a serializer never
 * silently omits one) and parses stored rows back into [W202MeterReading]. Bearer token per
 * call.
 *
 * Never sent: `kwh_consumption` and `kvah_consumption` (computed server-side for both
 * meters), and `power_factor` for SOLAR (derived from the two consumptions). Main's power
 * factor IS sent, because there it is the technician's entry.
 */
@Singleton
class W202ReadingsRepositoryImpl @Inject constructor(
    private val api: W202ReadingsApi,
    private val authPreferences: AuthPreferences,
) : W202ReadingsRepository {

    override suspend fun submit(reading: W202MeterReading): Result<W202MeterReading> = try {
        val stored = api.submit(bearer(), reading.toBody())
        Result.Success(stored.toReading(reading.kind))
    } catch (e: Exception) {
        // The 400s here carry the only explanation the technician will get — in particular
        // a carried-forward opening that exceeds the Final just typed, which the form
        // cannot detect because that opening exists only server-side.
        Result.Error(friendlyBackendError(e, "Failed to save reading"), e)
    }

    override suspend fun list(
        kind: W202MeterKind,
        from: String?,
        to: String?,
    ): Result<List<W202MeterReading>> = try {
        Result.Success(api.list(bearer(), kind.slug, W202_PLANT, from, to).map { it.toReading(kind) })
    } catch (e: Exception) {
        Result.Error(friendlyBackendError(e, "Failed to load history"), e)
    }

    override suspend fun prefill(kind: W202MeterKind, date: String): Result<W202Prefill> = try {
        val o = api.prefill(bearer(), kind.slug, W202_PLANT, date)
        val openings = (o["openings"] as? JsonObject)
        Result.Success(
            W202Prefill(
                readingDate = o.str("reading_date").ifBlank { date },
                sourceDate = o.strOrNull("source_date"),
                initialKwh = openings.num("initial_kwh"),
                initialKvah = openings.num("initial_kvah"),
                powerFactor = o.num("power_factor"),
                alreadySaved = (o["already_saved"] as? JsonPrimitive)?.booleanOrNull ?: false,
            )
        )
    } catch (e: Exception) {
        Result.Error(friendlyBackendError(e, "Failed to load previous reading"), e)
    }

    override suspend fun delete(kind: W202MeterKind, date: String): Result<Unit> = try {
        api.delete(bearer(), date, kind.slug, W202_PLANT)
        Result.Success(Unit)
    } catch (e: Exception) {
        // 403 is the expected answer for a non-HEAD caller, and this sentence beats
        // whatever the server says; every other status defers to the backend's detail.
        Result.Error(
            friendlyBackendError(
                e,
                fallback = "Failed to delete reading",
                forbidden = "Only a Head can delete a day's reading",
            ),
            e,
        )
    }

    private fun bearer() = "Bearer ${authPreferences.getToken().orEmpty()}"

    // ── JSON mapping ─────────────────────────────────────────────────────────────

    /** All four meter fields always sent (JsonNull when absent) so a partial day is explicit. */
    private fun W202MeterReading.toBody(): JsonObject = buildJsonObject {
        put("kind", JsonPrimitive(kind.slug))
        put("plant", JsonPrimitive(plant.ifBlank { W202_PLANT }))
        put("reading_date", JsonPrimitive(readingDate))
        put("initial_kwh", initialKwh.toJson())
        put("final_kwh", finalKwh.toJson())
        put("initial_kvah", initialKvah.toJson())
        put("final_kvah", finalKvah.toJson())
        // Solar's PF is derived server-side — sending one would be ignored at best.
        if (kind.powerFactorEditable) put("power_factor", powerFactor.toJson())
        val r = remark?.trim().orEmpty()
        put("remark", if (r.isEmpty()) JsonNull else JsonPrimitive(r))
    }

    private fun JsonObject.toReading(kind: W202MeterKind): W202MeterReading = W202MeterReading(
        id = (this["id"] as? JsonPrimitive)?.intOrNull,
        // Trust the row's own kind when the backend echoes it; fall back to the one asked for.
        kind = strOrNull("kind")?.let { W202MeterKind.fromSlug(it) } ?: kind,
        plant = str("plant").ifBlank { W202_PLANT },
        readingDate = str("reading_date"),
        initialKwh = num("initial_kwh"), finalKwh = num("final_kwh"),
        kwhConsumption = num("kwh_consumption"),
        initialKvah = num("initial_kvah"), finalKvah = num("final_kvah"),
        kvahConsumption = num("kvah_consumption"),
        powerFactor = num("power_factor"),
        remark = strOrNull("remark"),
    )

    private fun Double?.toJson(): JsonElement = this?.let { JsonPrimitive(it) } ?: JsonNull
    private fun JsonObject?.num(key: String): Double? = (this?.get(key) as? JsonPrimitive)?.doubleOrNull
    private fun JsonObject.str(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
    private fun JsonObject.strOrNull(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
}
