package io.github.bijojp.thingspot.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalResources
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import io.github.bijojp.thingspot.domain.ItemId
import io.github.bijojp.thingspot.domain.PlaceId
import io.github.bijojp.thingspot.ui.components.LocalPhotoFiles
import io.github.bijojp.thingspot.ui.items.ItemEditorScreen
import io.github.bijojp.thingspot.ui.items.ItemsScreen
import io.github.bijojp.thingspot.ui.places.PlaceScreen
import io.github.bijojp.thingspot.ui.search.SearchScreen
import io.github.bijojp.thingspot.ui.settings.SettingsScreen

val LocalSnackbarHostState = staticCompositionLocalOf { SnackbarHostState() }

@Composable
fun ThingSpotApp(viewModel: InventoryViewModel) {
    val backStack = rememberNavBackStack(SearchRoute)
    val nav = remember(backStack) { Navigator(backStack) }
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(viewModel, resources) {
        viewModel.messages.collect { message ->
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(resources.getString(message))
        }
    }

    CompositionLocalProvider(
        LocalSnackbarHostState provides snackbar,
        LocalPhotoFiles provides viewModel::photoFile,
    ) {
        NavDisplay(
            backStack = backStack,
            onBack = nav::back,
            entryProvider = entryProvider {
                entry<SearchRoute> { SearchScreen(viewModel, nav) }
                entry<PlaceRoute> { route -> PlaceScreen(route.placeId?.let(::PlaceId), viewModel, nav) }
                entry<ItemsRoute> { ItemsScreen(viewModel, nav) }
                entry<SettingsRoute> { SettingsScreen(viewModel, nav) }
                entry<ItemEditorRoute> { route ->
                    ItemEditorScreen(route.itemId?.let(::ItemId), route.placeId?.let(::PlaceId), viewModel, nav)
                }
            },
        )
    }
}
