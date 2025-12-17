package com.cussou.autotiq.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class LocationWorkScheduler(private val context: Context) {

    fun scheduleLocationChecks(intervalSeconds: Int) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()

        // WorkManager minimum interval for PeriodicWork is 15 minutes (900 seconds)
        // For shorter intervals (testing), we use OneTimeWork with repeat
        if (intervalSeconds < 900) {
            // Use OneTimeWorkRequest for testing with short intervals
            val workRequest = OneTimeWorkRequestBuilder<LocationCheckWorker>()
                .setConstraints(constraints)
                .setInitialDelay(intervalSeconds.toLong(), TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                LocationCheckWorker.WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        } else {
            // Use PeriodicWorkRequest for intervals >= 15 minutes
            val workRequest = PeriodicWorkRequestBuilder<LocationCheckWorker>(
                intervalSeconds.toLong(),
                TimeUnit.SECONDS
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                LocationCheckWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        }
    }

    fun cancelLocationChecks() {
        WorkManager.getInstance(context).cancelUniqueWork(LocationCheckWorker.WORK_NAME)
    }
}
