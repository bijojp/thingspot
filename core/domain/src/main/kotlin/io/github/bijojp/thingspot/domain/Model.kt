package io.github.bijojp.thingspot.domain

// The things a user records. Vocabulary is defined in CONTEXT.md; keep these names in step with it.

@JvmInline
value class PlaceId(val value: String)

@JvmInline
value class ItemId(val value: String)

@JvmInline
value class CategoryId(val value: String)

/** Somewhere things are kept. A Place with no parent is a top-level Place. */
data class Place(
    val id: PlaceId,
    val name: String,
    val icon: String,
    val parentId: PlaceId?,
    val createdAt: Long,
    val updatedAt: Long,
)

/** One physical object, or one bundle kept together, always in exactly one Place. */
data class Item(
    val id: ItemId,
    val name: String,
    val placeId: PlaceId,
    val categoryId: CategoryId?,
    val notes: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/** An optional, coloured grouping of Items by kind. [color] is ARGB, e.g. 0xFF5B9CF5. */
data class Category(
    val id: CategoryId,
    val name: String,
    val color: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

/** What the user fills in when adding or editing an Item. */
data class ItemFields(
    val name: String,
    val placeId: PlaceId,
    val categoryId: CategoryId?,
    val notes: String,
)

object Limits {
    const val PLACE_NAME = 60
    const val ITEM_NAME = 100
    const val CATEGORY_NAME = 40
    const val NOTES = 500
}
