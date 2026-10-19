package com.candorfoods.factoryops

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import com.candorfoods.factoryops.notification.FcmTokenRegistrar
import com.candorfoods.factoryops.sync.BreakdownPollWorker
import com.candorfoods.factoryops.sync.FlagCheckerWorker
import com.candorfoods.factoryops.sync.PmSchedulerWorker
import com.candorfoods.factoryops.sync.SyncWorker
import javax.inject.Inject

@HiltAndroidApp
class FactoryOpsApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var fcmTokenRegistrar: FcmTokenRegistrar

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        val workManager = WorkManager.getInstance(this)
        SyncWorker.schedulePeriodic(workManager)
        FlagCheckerWorker.schedulePeriodic(workManager)
        BreakdownPollWorker.enqueueImmediately(this)
        PmSchedulerWorker.schedulePeriodic(workManager)
        PmSchedulerWorker.enqueueImmediately(this)
        // Refresh this device's push token on every launch (covers an already-signed-in
        // user whose FCM token didn't rotate since last run). No-op if signed out.
        fcmTokenRegistrar.registerCurrentToken()
    }
}
