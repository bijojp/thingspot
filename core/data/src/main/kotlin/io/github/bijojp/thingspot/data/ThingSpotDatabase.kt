package io.github.bijojp.thingspot.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import io.github.bijojp.thingspot.data.backup.Backups
import io.github.bijojp.thingspot.domain.InventoryRepository

@Database(
    entities = [PlaceEntity::class, ItemEntity::class, CategoryEntity::class, PhotoEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2), // adds photos
    ],
)
abstract class ThingSpotDatabase : RoomDatabase() {
    abstract fun inventoryDao(): InventoryDao
}

/** Everything ThingSpot keeps on the device, sharing one database. */
class ThingSpotData private constructor(context: Context, appVersion: String) {
    private val database = Room.databaseBuilder(context.applicationContext, ThingSpotDatabase::class.java, "thingspot.db")
        .build()

    val inventory: InventoryRepository = RoomInventoryRepository(database)
    val photos = PhotoStore(context)
    val backups = Backups(context, database, photos, appVersion)

    companion object {
        /** The app's single entry point into stored data; call once. */
        fun open(context: Context, appVersion: String) = ThingSpotData(context, appVersion)
    }
}
