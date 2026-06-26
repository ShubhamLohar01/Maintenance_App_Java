package com.candorfoods.factoryops

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import com.candorfoods.factoryops.sync.BreakdownPollWorker
import com.candorfoods.factoryops.sync.FlagCheckerWorker
import com.candorfoods.factoryops.sync.PmSchedulerWorker
import com.candorfoods.factoryops.sync.SyncWorker
import javax.inject.Inject

@HiltAndroidApp
class FactoryOpsApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

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
    }
}
