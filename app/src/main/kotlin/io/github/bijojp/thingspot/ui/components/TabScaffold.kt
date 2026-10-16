package io.github.bijojp.thingspot.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import io.github.bijojp.thingspot.R
import io.github.bijojp.thingspot.ui.LocalSnackbarHostState
import io.github.bijojp.thingspot.ui.Navigator
import io.github.bijojp.thingspot.ui.Tab

/** The frame for the four main tabs: bottom navigation, snackbar, optional top bar and button. */
@Composable
fun TabScaffold(
    nav: Navigator,
    topBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = topBar,
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = nav.selectedTab == tab,
                        onClick = { nav.switchTo(tab) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.label)) },
                    )
                }
            }
        },
        floatingActionButton = floatingActionButton,
        snackbarHost = { SnackbarHost(LocalSnackbarHostState.current) },
        content = content,
    )
}

private val Tab.icon: ImageVector
    get() = when (this) {
        Tab.Search -> Icons.Filled.Search
        Tab.Places -> Icons.Filled.Home
        Tab.Items -> Icons.AutoMirrored.Filled.List
        Tab.Settings -> Icons.Filled.Settings
    }

private val Tab.label: Int
    get() = when (this) {
        Tab.Search -> R.string.tab_search
        Tab.Places -> R.string.tab_places
        Tab.Items -> R.string.tab_items
        Tab.Settings -> R.string.tab_settings
    }
