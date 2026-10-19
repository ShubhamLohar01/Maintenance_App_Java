package com.candorfoods.factoryops.data.repository

import com.candorfoods.factoryops.data.local.AuthPreferences
import com.candorfoods.factoryops.data.remote.api.UtilitiesApi
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.utilities.FieldKind
import com.candorfoods.factoryops.domain.model.utilities.UTILITY_RATE_KEYS
import com.candorfoods.factoryops.domain.model.utilities.UtilityPrefill
import com.candorfoods.factoryops.domain.model.utilities.UtilityRates
import com.candorfoods.factoryops.domain.model.utilities.UtilityRecord
import com.candorfoods.factoryops.domain.model.utilities.UtilityType
import com.candorfoods.factoryops.domain.repository.UtilitiesRepository
import com.candorfoods.factoryops.domain.usecase.utilities.roundTo
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backs the utility forms via [UtilitiesApi]. This is the JSON boundary: it builds the
 * exact snake_case body the backend expects (numbers rounded to each field's decimals,
 * absent numbers and blank text sent as JSON null) and parses stored rows back into a
 * generic [UtilityRecord]. Token follows the per-call "Bearer <token>" pattern.
 */
@Singleton
class UtilitiesRepositoryImpl @Inject constructor(
    private val api: UtilitiesApi,
    private val authPreferences: AuthPreferences,
) : UtilitiesRepository {

    override suspend fun submit(type: UtilityType, record: UtilityRecord): Result<UtilityRecord> = try {
        val token = "Bearer ${authPreferences.getToken().orEmpty()}"
        val stored = api.submit(token, type.slug, record.toBody(type))
        Result.Success(stored.toRecord(type))
    } catch (e: Exception) {
        Result.Error(e.message ?: "Failed to save reading", e)
    }

    override suspend fun list(type: UtilityType, plant: String?): Result<List<UtilityRecord>> = try {
        val token = "Bearer ${authPreferences.getToken().orEmpty()}"
        Result.Success(api.list(token, type.slug, plant).map { it.toRecord(type) })
    } catch (e: Exception) {
        Result.Error(e.message ?: "Failed to load history", e)
    }

    override suspend fun rates(plant: String): Result<UtilityRates> = try {
        val token = "Bearer ${authPreferences.getToken().orEmpty()}"
        val row = api.rates(token, plant).firstOrNull()
        Result.Success(row?.toRates() ?: UtilityRates(plant, emptyMap()))
    } catch (e: Exception) {
        Result.Error(e.message ?: "Failed to load rates", e)
    }

    override suspend fun setRates(plant: String, rates: Map<String, Double?>): Result<UtilityRates> = try {
        val token = "Bearer ${authPreferences.getToken().orEmpty()}"
        val body = buildJsonObject {
            put("plant", plant)
            // Only non-null rates are sent — a partial update leaves the rest unchanged.
            for ((k, v) in rates) if (v != null) put(k, v)
        }
        Result.Success(api.setRates(token, body).toRates())
    } catch (e: Exception) {
        Result.Error(e.message ?: "Failed to save rates", e)
    }

    override suspend fun prefill(type: UtilityType, plant: String, date: String): Result<UtilityPrefill> = try {
        val token = "Bearer ${authPreferences.getToken().orEmpty()}"
        val obj = api.prefill(token, type.slug, plant, date)
        val openings = (obj["openings"] as? JsonObject)?.entries
            ?.associate { (k, v) -> k to (v as? JsonPrimitive)?.doubleOrNull }
            ?: emptyMap()
        Result.Success(
            UtilityPrefill(
                openings = openings,
                rate = (obj["rate"] as? JsonPrimitive)?.doubleOrNull,
                sourceDate = (obj["source_date"] as? JsonPrimitive)?.contentOrNull,
            )
        )
    } catch (e: Exception) {
        Result.Error(e.message ?: "Failed to load previous reading", e)
    }

    private fun UtilityRecord.toBody(type: UtilityType): JsonObject = buildJsonObject {
        put("plant", plant)
        put("reading_date", readingDate)
        for (f in type.inputs + type.computed) when (f.kind) {
            FieldKind.NUMBER -> {
                val v = numbers[f.key]
                if (v == null) put(f.key, JsonNull) else put(f.key, v.roundTo(f.decimals))
            }
            FieldKind.TEXT -> {
                val t = texts[f.key]?.trim().orEmpty()
                if (t.isEmpty()) put(f.key, JsonNull) else put(f.key, t)
            }
        }
    }

    private fun JsonObject.toRecord(type: UtilityType): UtilityRecord {
        val numbers = mutableMapOf<String, Double?>()
        val texts = mutableMapOf<String, String>()
        for (f in type.inputs + type.computed) {
            val el = this[f.key] as? JsonPrimitive
            when (f.kind) {
                FieldKind.NUMBER -> numbers[f.key] = el?.doubleOrNull
                FieldKind.TEXT -> texts[f.key] = el?.contentOrNull.orEmpty()
            }
        }
        return UtilityRecord(
            plant = (this["plant"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            readingDate = (this["reading_date"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            numbers = numbers,
            texts = texts,
            id = (this["id"] as? JsonPrimitive)?.intOrNull,
        )
    }

    private fun JsonObject.toRates(): UtilityRates = UtilityRates(
        plant = (this["plant"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
        rates = UTILITY_RATE_KEYS.associateWith { (this[it] as? JsonPrimitive)?.doubleOrNull },
        setBy = (this["set_by"] as? JsonPrimitive)?.contentOrNull,
        setAt = (this["set_at"] as? JsonPrimitive)?.contentOrNull,
    )
}
