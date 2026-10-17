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

/**
 * A photo of an Item or a Place (exactly one of [itemId] / [placeId] is set). Kept in its own
 * table so several photos per Item can come later without a migration; v1 shows one.
 * Rows go when their owner goes; unreferenced files are swept by [PhotoStore].
 */
@Entity(
    tableName = "photos",
    foreignKeys = [
        ForeignKey(entity = ItemEntity::class, parentColumns = ["id"], childColumns = ["item_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = PlaceEntity::class, parentColumns = ["id"], childColumns = ["place_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("item_id"), Index("place_id")],
)
data class PhotoEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "item_id") val itemId: String?,
    @ColumnInfo(name = "place_id") val placeId: String?,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** A Label linked to a Place. Blank Labels have no row; deleting the Place makes its Labels blank. */
@Entity(
    tableName = "labels",
    foreignKeys = [
        ForeignKey(entity = PlaceEntity::class, parentColumns = ["id"], childColumns = ["place_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("place_id")],
)
data class LabelEntity(
    @PrimaryKey val code: String,
    @ColumnInfo(name = "place_id") val placeId: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
