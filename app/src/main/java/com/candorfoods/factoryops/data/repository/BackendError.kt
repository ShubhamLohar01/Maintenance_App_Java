package com.candorfoods.factoryops.data.repository

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.HttpException

/**
 * Turning a failed call into text a technician can act on.
 *
 * Without this, a Retrofit [HttpException]'s `message` is just the status line, so a
 * backend that carefully said
 *
 *     KWH: final reading (800.0) is less than initial reading (900.0)
 *
 * reaches the screen as "HTTP 400 Bad Request". That matters most for the W-202 main
 * meter: a blank Initial is carried forward from the previous day server-side, so when the
 * carried opening exceeds the Final just typed, the 400 is the *only* way the technician
 * can learn what went wrong — the on-screen Initial is empty, so the form's own
 * "Final ≥ Initial" check cannot see it.
 *
 * Same shape as the private helper in MtUserRepositoryImpl, which predates this file.
 */
private val errorJson = Json { ignoreUnknownKeys = true }

@Serializable
private data class ApiError(val detail: String? = null)

/**
 * FastAPI's `{"detail": "..."}` from an error response, if present and a plain string.
 *
 * Call this at most once per exception: [okhttp3.ResponseBody.string] consumes the buffer,
 * and a second read comes back empty.
 */
private fun backendDetail(e: HttpException): String? = try {
    e.response()?.errorBody()?.string()
        ?.takeIf { it.isNotBlank() }
        ?.let { errorJson.decodeFromString<ApiError>(it).detail }
        ?.takeIf { it.isNotBlank() }
} catch (_: Exception) {
    null   // non-string detail (e.g. a 422 list) or unparseable body -> fall back
}

/**
 * User-facing text for a failed call: the backend's own `detail` when it sent one,
 * otherwise [fallback].
 *
 * @param forbidden used instead of the backend's text on a 403, for endpoints where the
 *   app has a better sentence than the server does (e.g. "Only a Head can delete a day's
 *   reading"). Leave null to let the backend speak for itself.
 */
internal fun friendlyBackendError(
    e: Exception,
    fallback: String,
    forbidden: String? = null,
): String = when (e) {
    is HttpException ->
        if (e.code() == 403 && forbidden != null) forbidden
        else backendDetail(e) ?: e.message ?: fallback
    else -> e.message ?: fallback
}
