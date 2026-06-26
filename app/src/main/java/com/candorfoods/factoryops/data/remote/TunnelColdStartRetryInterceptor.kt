package com.candorfoods.factoryops.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Retries transient failures that occur while the dev tunnel (or the backend it
 * forwards to) is waking from idle: the first request after a pause can return
 * 502/503/504 or time out, then succeed on a retry. Bounded attempts with linear
 * backoff.
 *
 * Only gateway errors (502–504) and connection-level failures (IOException) are
 * retried — these indicate the request did not reach a live backend. The app's
 * writes tolerate a retry anyway: the daily-kWh upsert is idempotent on
 * (machine_id, reading_date), and run start/stop are stored locally first and
 * re-synced by SyncWorker.
 */
class TunnelColdStartRetryInterceptor(
    private val maxAttempts: Int = 3,
    private val backoffMillis: Long = 1500L,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var attempt = 0
        while (true) {
            attempt++
            try {
                val response = chain.proceed(request)
                if (response.code in 502..504 && attempt < maxAttempts) {
                    response.close()
                    sleep(backoffMillis * attempt)
                    continue
                }
                return response
            } catch (e: IOException) {
                if (attempt >= maxAttempts) throw e
                sleep(backoffMillis * attempt)
            }
        }
    }

    private fun sleep(millis: Long) {
        try {
            Thread.sleep(millis)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Retry interrupted", e)
        }
    }
}
