package io.github.bijojp.thingspot.domain

import kotlinx.coroutines.flow.Flow

/**
 * Where the inventory is stored. Every change keeps the rules in [Inventory] and
 * [PlaceDeletion] true; implementations apply each change atomically.
 */
interface InventoryRepository {
    /** The latest snapshot, re-emitted after every change. */
    val inventory: Flow<Inventory>

    suspend fun addPlace(name: String, icon: String, parentId: PlaceId?, photo: String? = null): PlaceId

    suspend fun editPlace(id: PlaceId, name: String, icon: String, photo: String?)

    /** Fails if [newParentId] is the Place itself or one of its sub-places. */
    suspend fun movePlace(id: PlaceId, newParentId: PlaceId?)

    /** [itemDestination] is the user's choice, needed only when [PlaceDeletion.itemsGoTo] asks for one. */
    suspend fun deletePlace(id: PlaceId, itemDestination: PlaceId?)

    suspend fun addItem(fields: ItemFields): ItemId

    suspend fun editItem(id: ItemId, fields: ItemFields)

    suspend fun deleteItem(id: ItemId)

    suspend fun addCategory(name: String, color: Long): CategoryId

    suspend fun editCategory(id: CategoryId, name: String, color: Long)

    /** Items in the Category keep everything else; they just lose the Category. */
    suspend fun deleteCategory(id: CategoryId)

    /** Sticks [code] on [placeId]. A Label already on another Place moves here. */
    suspend fun linkLabel(code: LabelCode, placeId: PlaceId)

    /** Makes the Label blank again. */
    suspend fun unlinkLabel(code: LabelCode)
}
