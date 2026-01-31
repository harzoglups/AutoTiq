package com.cussou.autotiq.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class LocationWorkScheduler(private val context: Context) {

    fun scheduleLocationChecks(intervalSeconds: Int) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()

        // WorkManager minimum interval for PeriodicWork is 15 minutes (900 seconds)
        // For shorter intervals, we use OneTimeWork with repeat
        if (intervalSeconds < 900) {
            // Use OneTimeWorkRequest for short intervals
            // The worker will reschedule itself after execution using scheduleNextCheck()
            // Note: We cannot use setExpedited() with setInitialDelay() together
            // For subsequent runs (with delay), we use regular work requests
            // For the initial run (no delay), we can use expedited if desired
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

    /**
     * Schedules an immediate expedited check (no delay).
     * Used when tracking is first enabled to get an immediate location check.
     */
    fun scheduleImmediateCheck() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<LocationCheckWorker>()
            .setConstraints(constraints)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            LocationCheckWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    fun cancelLocationChecks() {
        WorkManager.getInstance(context).cancelUniqueWork(LocationCheckWorker.WORK_NAME)
    }
}
