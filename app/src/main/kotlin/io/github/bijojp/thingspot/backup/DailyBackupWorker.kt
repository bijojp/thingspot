package io.github.bijojp.thingspot.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.bijojp.thingspot.ThingSpotApplication
import io.github.bijojp.thingspot.data.backup.BackupOutcome
import java.util.concurrent.TimeUnit

/** Once a day: a Backup to the chosen folder, if anything changed since the last one. */
class DailyBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val backups = (applicationContext as ThingSpotApplication).container.backups
        return when (backups.backUp(onlyIfChanged = true)) {
            BackupOutcome.Failed -> Result.retry()
            else -> Result.success()
        }
    }

    companion object {
        private const val NAME = "daily-backup"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DailyBackupWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
