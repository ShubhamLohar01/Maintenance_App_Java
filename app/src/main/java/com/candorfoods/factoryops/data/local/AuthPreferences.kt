package com.candorfoods.factoryops.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import com.candorfoods.factoryops.util.Constants
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        Constants.AUTH_PREFS,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveToken(token: String) = prefs.edit().putString(Constants.KEY_JWT_TOKEN, token).apply()
    fun getToken(): String? = prefs.getString(Constants.KEY_JWT_TOKEN, null)
    fun saveUserId(userId: String) = prefs.edit().putString(Constants.KEY_USER_ID, userId).apply()
    fun getUserId(): String? = prefs.getString(Constants.KEY_USER_ID, null)
    fun savePlantId(plantId: String) = prefs.edit().putString(Constants.KEY_PLANT_ID, plantId).apply()
    fun getPlantId(): String? = prefs.getString(Constants.KEY_PLANT_ID, null)
    fun clear() = prefs.edit().clear().apply()
    fun isLoggedIn(): Boolean = getToken() != null

    fun saveBreakdownLastSeenAt(ts: Long) =
        prefs.edit().putLong(Constants.KEY_BREAKDOWN_LAST_SEEN, ts).apply()
    fun getBreakdownLastSeenAt(): Long =
        prefs.getLong(Constants.KEY_BREAKDOWN_LAST_SEEN, 0L)
}
