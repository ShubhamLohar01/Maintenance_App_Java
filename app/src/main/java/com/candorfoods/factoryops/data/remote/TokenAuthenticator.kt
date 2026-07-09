package com.candorfoods.factoryops.data.remote

import com.candorfoods.factoryops.data.local.AuthPreferences
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Singleton

/**
 * There is no refresh token, so a 401 on an authenticated request means the session is
 * dead — the token expired, or the backend invalidated it (e.g. a redeploy that rotated
 * the JWT signing key). Clear the stored session so [AuthPreferences.isLoggedIn] flips to
 * false and the next app launch routes to Login instead of looping on 401s. Returns null
 * to stop OkHttp from retrying (we have no fresh credentials to offer).
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val authPreferences: AuthPreferences,
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        // Only react to challenges on requests we actually authenticated.
        if (response.request.header("Authorization") != null) {
            authPreferences.clear()
        }
        return null
    }
}
