package io.github.bijojp.thingspot.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import io.github.bijojp.thingspot.domain.InventoryRepository

@Database(
    entities = [PlaceEntity::class, ItemEntity::class, CategoryEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class ThingSpotDatabase : RoomDatabase() {
    abstract fun inventoryDao(): InventoryDao
}

/** The app's single entry point into stored data. */
object ThingSpotData {
    fun openInventory(context: Context): InventoryRepository {
        val database = Room.databaseBuilder(context.applicationContext, ThingSpotDatabase::class.java, "thingspot.db")
            .build()
        return RoomInventoryRepository(database)
    }
}
