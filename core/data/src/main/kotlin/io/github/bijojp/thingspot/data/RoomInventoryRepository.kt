package io.github.bijojp.thingspot.data

import androidx.room.withTransaction
import io.github.bijojp.thingspot.domain.Category
import io.github.bijojp.thingspot.domain.CategoryId
import io.github.bijojp.thingspot.domain.Inventory
import io.github.bijojp.thingspot.domain.InventoryRepository
import io.github.bijojp.thingspot.domain.Item
import io.github.bijojp.thingspot.domain.ItemFields
import io.github.bijojp.thingspot.domain.ItemId
import io.github.bijojp.thingspot.domain.Limits
import io.github.bijojp.thingspot.domain.Place
import io.github.bijojp.thingspot.domain.PlaceId
import io.github.bijojp.thingspot.domain.cleanName
import io.github.bijojp.thingspot.domain.cleanNotes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import java.util.UUID

/** Applies each change in one transaction, after checking it against the domain rules. */
internal class RoomInventoryRepository(
    private val db: ThingSpotDatabase,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : InventoryRepository {
    private val dao = db.inventoryDao()

    override val inventory: Flow<Inventory> =
        db.invalidationTracker.createFlow("places", "items", "categories")
            .conflate()
            .map { dao.snapshot().toInventory() }

    private suspend fun current(): Inventory = dao.snapshot().toInventory()

    override suspend fun addPlace(name: String, icon: String, parentId: PlaceId?): PlaceId = db.withTransaction {
        if (parentId != null) requireNotNull(dao.place(parentId.value)) { "Parent Place not found" }
        val time = now()
        val id = newId()
        dao.insertPlace(PlaceEntity(id, requireName(name, Limits.PLACE_NAME), icon, parentId?.value, time, time))
        PlaceId(id)
    }

    override suspend fun editPlace(id: PlaceId, name: String, icon: String) = db.withTransaction {
        val place = requireNotNull(dao.place(id.value)) { "Place not found" }
        dao.updatePlace(place.copy(name = requireName(name, Limits.PLACE_NAME), icon = icon, updatedAt = now()))
    }

    override suspend fun movePlace(id: PlaceId, newParentId: PlaceId?) = db.withTransaction {
        require(current().canMove(id, newParentId)) { "A Place can't move inside itself or its own sub-places" }
        val place = requireNotNull(dao.place(id.value))
        dao.updatePlace(place.copy(parentId = newParentId?.value, updatedAt = now()))
    }

    override suspend fun deletePlace(id: PlaceId, itemDestination: PlaceId?) = db.withTransaction {
        val deletion = requireNotNull(current().deletionOf(id)) { "Place not found" }
        val destination = deletion.itemDestination(itemDestination)
        val time = now()
        dao.moveChildPlaces(id.value, deletion.place.parentId?.value, time)
        if (destination != null) dao.moveAllItems(id.value, destination.value, time)
        dao.deletePlace(id.value)
    }

    override suspend fun addItem(fields: ItemFields): ItemId = db.withTransaction {
        val time = now()
        val id = newId()
        dao.insertItem(fields.toEntity(id, createdAt = time, updatedAt = time))
        ItemId(id)
    }

    override suspend fun editItem(id: ItemId, fields: ItemFields) = db.withTransaction {
        val item = requireNotNull(dao.item(id.value)) { "Item not found" }
        dao.updateItem(fields.toEntity(item.id, createdAt = item.createdAt, updatedAt = now()))
    }

    override suspend fun deleteItem(id: ItemId) = dao.deleteItem(id.value)

    override suspend fun addCategory(name: String, color: Long): CategoryId {
        val time = now()
        val id = newId()
        dao.insertCategory(CategoryEntity(id, requireName(name, Limits.CATEGORY_NAME), color, time, time))
        return CategoryId(id)
    }

    override suspend fun editCategory(id: CategoryId, name: String, color: Long) = db.withTransaction {
        val category = requireNotNull(dao.category(id.value)) { "Category not found" }
        dao.updateCategory(category.copy(name = requireName(name, Limits.CATEGORY_NAME), color = color, updatedAt = now()))
    }

    override suspend fun deleteCategory(id: CategoryId) = dao.deleteCategory(id.value)

    private suspend fun ItemFields.toEntity(id: String, createdAt: Long, updatedAt: Long): ItemEntity {
        requireNotNull(dao.place(placeId.value)) { "Every Item needs a Place that exists" }
        val category = categoryId?.takeIf { dao.category(it.value) != null }
        return ItemEntity(id, requireName(name, Limits.ITEM_NAME), placeId.value, category?.value, cleanNotes(notes), createdAt, updatedAt)
    }

    private fun requireName(raw: String, maxLength: Int): String =
        requireNotNull(cleanName(raw, maxLength)) { "Name can't be blank" }
}

private fun Snapshot.toInventory() = Inventory(
    places = places.map { Place(PlaceId(it.id), it.name, it.icon, it.parentId?.let(::PlaceId), it.createdAt, it.updatedAt) },
    items = items.map {
        Item(ItemId(it.id), it.name, PlaceId(it.placeId), it.categoryId?.let(::CategoryId), it.notes, it.createdAt, it.updatedAt)
    },
    categories = categories.map { Category(CategoryId(it.id), it.name, it.color, it.createdAt, it.updatedAt) },
)
