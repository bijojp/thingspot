package io.github.bijojp.thingspot.domain

sealed interface SearchResult {
    data class ItemMatch(val item: Item) : SearchResult

    data class PlaceMatch(val place: Place) : SearchResult
}

/**
 * Search is the product: every word the user types must match somewhere, case and accents are
 * ignored, and a word can match an Item's name, Category, notes or the Places it sits in
 * ("garage drill"). Name matches rank highest; ties go to Items, then alphabetical order.
 */
internal class InventorySearch(private val inventory: Inventory) {
    private val foldedPaths = HashMap<PlaceId, String>()

    fun run(query: String): List<SearchResult> {
        val words = fold(query).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()

        val scored = ArrayList<Scored>()
        for (item in inventory.items) {
            val others = listOf(
                Field(item.categoryId?.let(inventory::category)?.name?.let(::fold).orEmpty(), CATEGORY),
                Field(foldedPath(item.placeId), PLACE_PATH),
                Field(fold(item.notes), NOTES),
            )
            score(words, fold(item.name), others)
                ?.let { scored += Scored(SearchResult.ItemMatch(item), it, item.name, isItem = true) }
        }
        for (place in inventory.places) {
            val parents = place.parentId?.let(::foldedPath).orEmpty()
            score(words, fold(place.name), listOf(Field(parents, PLACE_PATH)))
                ?.let { scored += Scored(SearchResult.PlaceMatch(place), it, place.name, isItem = false) }
        }
        return scored
            .sortedWith(
                compareByDescending<Scored> { it.score }
                    .thenByDescending { it.isItem }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
            )
            .map { it.result }
    }

    private fun foldedPath(placeId: PlaceId): String =
        foldedPaths.getOrPut(placeId) { inventory.path(placeId).joinToString(" ") { fold(it.name) } }

    /** Total score, or null when some word matches nothing. */
    private fun score(words: List<String>, name: String, others: List<Field>): Int? {
        var total = 0
        for (word in words) {
            val best = maxOf(nameScore(name, word), others.maxOf { if (word in it.text) it.weight else 0 })
            if (best == 0) return null
            total += best
        }
        return total
    }

    private fun nameScore(name: String, word: String): Int = when {
        name == word -> 50
        name.startsWith(word) -> 40
        name.split(' ').any { it.startsWith(word) } -> 30
        word in name -> 20
        else -> 0
    }

    private class Field(val text: String, val weight: Int)

    private class Scored(val result: SearchResult, val score: Int, val name: String, val isItem: Boolean)

    private companion object {
        const val CATEGORY = 8
        const val PLACE_PATH = 6
        const val NOTES = 4
    }
}
