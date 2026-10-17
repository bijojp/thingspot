package io.github.bijojp.thingspot.domain

/**
 * An immutable snapshot of everything the user has recorded, with the queries and rules that
 * need the whole picture: paths, counts, where a Place may move, what a deletion does, and
 * search. A new snapshot is built whenever stored data changes.
 */
class Inventory(
    places: Collection<Place>,
    items: Collection<Item>,
    categories: Collection<Category>,
) {
    val places: List<Place> = places.sortedWith(byName { it.name })
    val items: List<Item> = items.sortedWith(byName { it.name })
    val categories: List<Category> = categories.sortedWith(byName { it.name })

    private val placesById = this.places.associateBy { it.id }
    private val itemsById = this.items.associateBy { it.id }
    private val categoriesById = this.categories.associateBy { it.id }
    private val childrenByParent = this.places.groupBy { it.parentId }
    private val itemsByPlace = this.items.groupBy { it.placeId }
    private val itemsByCategory = this.items.groupBy { it.categoryId }

    fun place(id: PlaceId): Place? = placesById[id]

    fun item(id: ItemId): Item? = itemsById[id]

    fun category(id: CategoryId): Category? = categoriesById[id]

    /** Places directly inside [parentId], or the top-level Places when it is null. */
    fun children(parentId: PlaceId?): List<Place> = childrenByParent[parentId].orEmpty()

    /** Items kept directly in [placeId], not in its sub-places. */
    fun itemsIn(placeId: PlaceId): List<Item> = itemsByPlace[placeId].orEmpty()

    /** How many Items are in [placeId] or anywhere inside it. */
    fun itemCountWithin(placeId: PlaceId): Int =
        itemsIn(placeId).size + descendants(placeId).sumOf { itemsIn(it).size }

    fun itemsInCategory(categoryId: CategoryId): List<Item> = itemsByCategory[categoryId].orEmpty()

    /** The chain of Places from the top level down to [placeId], inclusive. */
    fun path(placeId: PlaceId): List<Place> {
        val path = ArrayDeque<Place>()
        val seen = HashSet<PlaceId>()
        var current = placesById[placeId]
        while (current != null && seen.add(current.id)) {
            path.addFirst(current)
            current = current.parentId?.let(placesById::get)
        }
        return path.toList()
    }

    /** Every Place nested anywhere inside [placeId], not including it. */
    fun descendants(placeId: PlaceId): Set<PlaceId> {
        val found = LinkedHashSet<PlaceId>()
        val queue = ArrayDeque(children(placeId))
        while (queue.isNotEmpty()) {
            val next = queue.removeFirst()
            if (found.add(next.id)) queue.addAll(children(next.id))
        }
        return found
    }

    /** A Place can move anywhere except inside itself or its own sub-places. */
    fun canMove(placeId: PlaceId, newParentId: PlaceId?): Boolean = when {
        placeId !in placesById -> false
        newParentId == null -> true
        newParentId !in placesById -> false
        else -> newParentId != placeId && newParentId !in descendants(placeId)
    }

    /** Every Place, depth first, with how deeply it is nested. */
    fun tree(): List<TreeRow> {
        val rows = ArrayList<TreeRow>(places.size)
        val seen = HashSet<PlaceId>()
        fun visit(parentId: PlaceId?, depth: Int) {
            for (place in children(parentId)) {
                if (!seen.add(place.id)) continue
                rows += TreeRow(place, depth)
                visit(place.id, depth + 1)
            }
        }
        visit(null, 0)
        return rows
    }

    /** Every photo file something still points at; any other photo file is safe to delete. */
    fun referencedPhotos(): Set<String> = (places.mapNotNull { it.photo } + items.mapNotNull { it.photo }).toSet()

    fun recentItems(limit: Int): List<Item> = items.sortedByDescending { it.createdAt }.take(limit)

    /** What deleting [placeId] would do, or null if there is no such Place. */
    fun deletionOf(placeId: PlaceId): PlaceDeletion? {
        val place = place(placeId) ?: return null
        val items = itemsIn(placeId)
        val itemsGoTo = when {
            items.isEmpty() -> ItemsGoTo.Nowhere
            place.parentId != null -> ItemsGoTo.Parent(place.parentId)
            else -> places.map { it.id }.filter { it != placeId }
                .let { if (it.isEmpty()) ItemsGoTo.NoPlaceLeft else ItemsGoTo.UserChoice(it) }
        }
        return PlaceDeletion(place, children(placeId), items, itemsGoTo)
    }

    /** Items and Places matching every word of [query], best matches first. */
    fun search(query: String): List<SearchResult> = InventorySearch(this).run(query)
}

data class TreeRow(val place: Place, val depth: Int)

private fun <T> byName(name: (T) -> String): Comparator<T> = compareBy(String.CASE_INSENSITIVE_ORDER, name)
