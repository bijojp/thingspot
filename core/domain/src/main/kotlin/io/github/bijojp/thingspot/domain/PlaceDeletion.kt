package io.github.bijojp.thingspot.domain

/**
 * What deleting one Place does. Only that Place is removed: the Places directly inside it move
 * up to its parent (becoming top-level if it had none), its Items must go somewhere, because
 * every Item is always in exactly one Place, and its Labels become blank.
 */
data class PlaceDeletion(
    val place: Place,
    val childPlaces: List<Place>,
    val items: List<Item>,
    val itemsGoTo: ItemsGoTo,
    val labels: List<Label> = emptyList(),
) {
    /** Can the deletion go ahead, given the user's [choice] of destination (if one is needed)? */
    fun isReady(choice: PlaceId?): Boolean = when (itemsGoTo) {
        ItemsGoTo.Nowhere, is ItemsGoTo.Parent -> true
        is ItemsGoTo.UserChoice -> choice in itemsGoTo.candidates
        ItemsGoTo.NoPlaceLeft -> false
    }

    /** Where the Items end up, given the user's [choice]; null when there are no Items to move. */
    fun itemDestination(choice: PlaceId?): PlaceId? = when (itemsGoTo) {
        ItemsGoTo.Nowhere -> null
        is ItemsGoTo.Parent -> itemsGoTo.parentId
        is ItemsGoTo.UserChoice -> {
            require(choice in itemsGoTo.candidates) { "Choose another Place for the Items" }
            choice
        }
        ItemsGoTo.NoPlaceLeft -> throw IllegalStateException("No other Place to move the Items to")
    }
}

sealed interface ItemsGoTo {
    /** The Place holds no Items. */
    data object Nowhere : ItemsGoTo

    /** The Items move up to the deleted Place's parent. */
    data class Parent(val parentId: PlaceId) : ItemsGoTo

    /** A top-level Place: the user picks one of [candidates] for its Items. */
    data class UserChoice(val candidates: List<PlaceId>) : ItemsGoTo

    /** A top-level Place with Items and no other Place to move them to: deletion is blocked. */
    data object NoPlaceLeft : ItemsGoTo
}
