package io.github.bijojp.thingspot.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// IDs are random UUIDs and every row carries created/updated times, so user-controlled sync
// can be added later without a migration (ADR 0001).

@Entity(
    tableName = "places",
    foreignKeys = [
        // RESTRICT: a Place with Places inside can't vanish; deletion moves them up first.
        ForeignKey(
            entity = PlaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["parent_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("parent_id")],
)
data class PlaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val icon: String,
    @ColumnInfo(name = "parent_id") val parentId: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "items",
    foreignKeys = [
        // RESTRICT: the database itself refuses to leave an Item without a Place.
        ForeignKey(
            entity = PlaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["place_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("place_id"), Index("category_id")],
)
data class ItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "place_id") val placeId: String,
    @ColumnInfo(name = "category_id") val categoryId: String?,
    val notes: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** ARGB, e.g. 0xFF5B9CF5. */
    val color: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
