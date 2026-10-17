package io.github.bijojp.thingspot

import android.app.Application
import android.content.Context
import io.github.bijojp.thingspot.backup.DailyBackupWorker
import io.github.bijojp.thingspot.data.ThingSpotData

class ThingSpotApplication : Application() {
    val container by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // Keeps the daily job alive across updates; KEEP means an existing schedule isn't reset.
        if (container.backups.status.value.folderName != null) DailyBackupWorker.schedule(this)
    }
}

/** Hand-wired dependencies; small enough that a DI framework would only add ceremony. */
class AppContainer(context: Context) {
    private val data by lazy { ThingSpotData.open(context, BuildConfig.VERSION_NAME) }

    val inventory get() = data.inventory
    val photos get() = data.photos
    val backups get() = data.backups
}
