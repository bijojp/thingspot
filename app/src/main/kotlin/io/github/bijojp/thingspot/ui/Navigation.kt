package io.github.bijojp.thingspot.ui

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object SearchRoute : NavKey

/** A Place's contents, or the top-level Places when [placeId] is null. */
@Serializable
data class PlaceRoute(val placeId: String? = null) : NavKey

@Serializable
data object ItemsRoute : NavKey

@Serializable
data object SettingsRoute : NavKey

/** Add an Item (optionally already in [placeId]), or edit the Item [itemId]. */
@Serializable
data class ItemEditorRoute(val itemId: String? = null, val placeId: String? = null) : NavKey

enum class Tab(val root: NavKey) {
    Search(SearchRoute),
    Places(PlaceRoute()),
    Items(ItemsRoute),
    Settings(SettingsRoute),
}

/** Search is always at the bottom of the back stack, so Back from any tab returns to it. */
class Navigator(private val backStack: NavBackStack<NavKey>) {
    fun open(route: NavKey) {
        backStack.add(route)
    }

    fun back() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    fun switchTo(tab: Tab) {
        while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        if (tab != Tab.Search) backStack.add(tab.root)
    }

    val selectedTab: Tab
        get() = when (backStack.lastOrNull { it !is ItemEditorRoute }) {
            is PlaceRoute -> Tab.Places
            ItemsRoute -> Tab.Items
            SettingsRoute -> Tab.Settings
            else -> Tab.Search
        }
}
