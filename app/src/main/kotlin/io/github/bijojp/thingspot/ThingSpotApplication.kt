package io.github.bijojp.thingspot

import android.app.Application
import android.content.Context
import io.github.bijojp.thingspot.data.PhotoStore
import io.github.bijojp.thingspot.data.ThingSpotData
import io.github.bijojp.thingspot.domain.InventoryRepository

class ThingSpotApplication : Application() {
    val container by lazy { AppContainer(this) }
}

/** Hand-wired dependencies; small enough that a DI framework would only add ceremony. */
class AppContainer(context: Context) {
    val inventory: InventoryRepository by lazy { ThingSpotData.openInventory(context) }
    val photos: PhotoStore by lazy { PhotoStore(context) }
}
