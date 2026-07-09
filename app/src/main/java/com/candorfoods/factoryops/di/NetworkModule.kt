package com.candorfoods.factoryops.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.candorfoods.factoryops.BuildConfig
import com.candorfoods.factoryops.data.remote.TokenAuthenticator
import com.candorfoods.factoryops.data.remote.TunnelColdStartRetryInterceptor
import com.candorfoods.factoryops.data.remote.api.AssetScheduleApi
import com.candorfoods.factoryops.data.remote.api.AuthApi
import com.candorfoods.factoryops.data.remote.api.BreakdownApi
import com.candorfoods.factoryops.data.remote.api.ChecklistApi
import com.candorfoods.factoryops.data.remote.api.EnergyApi
import com.candorfoods.factoryops.data.remote.api.FloorReadingApi
import com.candorfoods.factoryops.data.remote.api.MachineApi
import com.candorfoods.factoryops.data.remote.api.MachineTransferApi
import com.candorfoods.factoryops.data.remote.api.MtMachineApi
import com.candorfoods.factoryops.data.remote.api.MtUserApi
import com.candorfoods.factoryops.data.remote.api.PmSyncApi
import com.candorfoods.factoryops.data.remote.mock.MockAuthApi
import com.candorfoods.factoryops.data.remote.mock.MockBreakdownApi
import com.candorfoods.factoryops.data.remote.mock.MockChecklistApi
import com.candorfoods.factoryops.data.remote.mock.MockEnergyApi
import com.candorfoods.factoryops.data.remote.mock.MockFloorReadingApi
import com.candorfoods.factoryops.data.remote.mock.MockMachineApi
import com.candorfoods.factoryops.data.remote.mock.MockMachineTransferApi
import com.candorfoods.factoryops.data.remote.mock.MockMtMachineApi
import com.candorfoods.factoryops.data.remote.mock.MockMtUserApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideRetrofit(authenticator: TokenAuthenticator): Retrofit {
        val client = OkHttpClient.Builder()
            // A cold cloud backend (Render free tier sleeps) can take 30-50s to wake;
            // the default 10s read timeout gives up before it answers, so the machine
            // list / any first call fails with "(timeout)". Give a single attempt enough
            // time to wait out a cold start (the retry interceptor then covers 502-504
            // while it warms). Applies to every request.
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            // On a 401 (expired / backend-invalidated token) clear the session so the next
            // app launch routes to Login instead of looping on 401s.
            .authenticator(authenticator)
            // Outermost: re-issues the whole call (incl. logging) when the dev tunnel
            // is waking from idle (502/503/504 or connect/read timeout, then works).
            .addInterceptor(TunnelColdStartRetryInterceptor())
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                        else HttpLoggingInterceptor.Level.NONE
            })
            .build()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi =
        if (BuildConfig.USE_MOCK_AUTH) MockAuthApi()
        else retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideMachineApi(retrofit: Retrofit): MachineApi =
        if (BuildConfig.USE_MOCK_API) MockMachineApi()
        else retrofit.create(MachineApi::class.java)

    @Provides
    @Singleton
    fun provideEnergyApi(retrofit: Retrofit): EnergyApi =
        if (BuildConfig.USE_MOCK_API) MockEnergyApi()
        else retrofit.create(EnergyApi::class.java)

    @Provides
    @Singleton
    fun provideBreakdownApi(retrofit: Retrofit): BreakdownApi =
        if (BuildConfig.USE_MOCK_API) MockBreakdownApi()
        else retrofit.create(BreakdownApi::class.java)

    @Provides
    @Singleton
    fun provideMtMachineApi(retrofit: Retrofit): MtMachineApi =
        if (BuildConfig.USE_MOCK_API) MockMtMachineApi()
        else retrofit.create(MtMachineApi::class.java)

    @Provides
    @Singleton
    fun provideMtUserApi(retrofit: Retrofit): MtUserApi =
        if (BuildConfig.USE_MOCK_API) MockMtUserApi()
        else retrofit.create(MtUserApi::class.java)

    @Provides
    @Singleton
    fun provideChecklistApi(retrofit: Retrofit): ChecklistApi =
        if (BuildConfig.USE_MOCK_API) MockChecklistApi()
        else retrofit.create(ChecklistApi::class.java)

    @Provides
    @Singleton
    fun provideMachineTransferApi(retrofit: Retrofit): MachineTransferApi =
        if (BuildConfig.USE_MOCK_API) MockMachineTransferApi()
        else retrofit.create(MachineTransferApi::class.java)

    @Provides
    @Singleton
    fun provideFloorReadingApi(retrofit: Retrofit): FloorReadingApi =
        if (BuildConfig.USE_MOCK_API) MockFloorReadingApi()
        else retrofit.create(FloorReadingApi::class.java)

    // Real backend only — no mock (feature relies on server-generated kWh rows).
    @Provides
    @Singleton
    fun provideAssetScheduleApi(retrofit: Retrofit): AssetScheduleApi =
        retrofit.create(AssetScheduleApi::class.java)

    // Real backend only — PM plan + work-order sync (mt_pm_plan / mt_pm_work_order).
    @Provides
    @Singleton
    fun providePmSyncApi(retrofit: Retrofit): PmSyncApi =
        retrofit.create(PmSyncApi::class.java)
}
