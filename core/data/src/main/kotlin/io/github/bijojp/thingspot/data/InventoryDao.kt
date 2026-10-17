package io.github.bijojp.thingspot.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
abstract class InventoryDao {
    @Query("SELECT * FROM places")
    abstract suspend fun places(): List<PlaceEntity>

    @Query("SELECT * FROM items")
    abstract suspend fun items(): List<ItemEntity>

    @Query("SELECT * FROM categories")
    abstract suspend fun categories(): List<CategoryEntity>

    @Query("SELECT * FROM photos")
    abstract suspend fun photos(): List<PhotoEntity>

    /** Everything at one consistent moment. */
    @Transaction
    open suspend fun snapshot(): Snapshot = Snapshot(places(), items(), categories(), photos())

    @Query("SELECT * FROM places WHERE id = :id")
    abstract suspend fun place(id: String): PlaceEntity?

    @Query("SELECT * FROM items WHERE id = :id")
    abstract suspend fun item(id: String): ItemEntity?

    @Query("SELECT * FROM categories WHERE id = :id")
    abstract suspend fun category(id: String): CategoryEntity?

    @Insert
    abstract suspend fun insertPlace(place: PlaceEntity)

    @Insert
    abstract suspend fun insertItem(item: ItemEntity)

    @Insert
    abstract suspend fun insertCategory(category: CategoryEntity)

    @Update
    abstract suspend fun updatePlace(place: PlaceEntity)

    @Update
    abstract suspend fun updateItem(item: ItemEntity)

    @Update
    abstract suspend fun updateCategory(category: CategoryEntity)

    @Query("UPDATE places SET parent_id = :newParentId, updated_at = :now WHERE parent_id = :oldParentId")
    abstract suspend fun moveChildPlaces(oldParentId: String, newParentId: String?, now: Long)

    @Query("UPDATE items SET place_id = :to, updated_at = :now WHERE place_id = :from")
    abstract suspend fun moveAllItems(from: String, to: String, now: Long)

    @Query("DELETE FROM places WHERE id = :id")
    abstract suspend fun deletePlace(id: String)

    @Query("DELETE FROM items WHERE id = :id")
    abstract suspend fun deleteItem(id: String)

    @Query("DELETE FROM categories WHERE id = :id")
    abstract suspend fun deleteCategory(id: String)

    @Query("SELECT file_name FROM photos WHERE item_id = :itemId")
    abstract suspend fun itemPhotoFiles(itemId: String): List<String>

    @Query("SELECT file_name FROM photos WHERE place_id = :placeId")
    abstract suspend fun placePhotoFiles(placeId: String): List<String>

    @Query("DELETE FROM photos WHERE item_id = :itemId")
    abstract suspend fun deleteItemPhotos(itemId: String)

    @Query("DELETE FROM photos WHERE place_id = :placeId")
    abstract suspend fun deletePlacePhotos(placeId: String)

    @Insert
    abstract suspend fun insertPhoto(photo: PhotoEntity)
}

data class Snapshot(
    val places: List<PlaceEntity>,
    val items: List<ItemEntity>,
    val categories: List<CategoryEntity>,
    val photos: List<PhotoEntity>,
)
