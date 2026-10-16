package io.github.bijojp.thingspot.ui.items

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bijojp.thingspot.R
import io.github.bijojp.thingspot.domain.CategoryId
import io.github.bijojp.thingspot.ui.InventoryViewModel
import io.github.bijojp.thingspot.ui.ItemEditorRoute
import io.github.bijojp.thingspot.ui.Navigator
import io.github.bijojp.thingspot.ui.components.EmptyHint
import io.github.bijojp.thingspot.ui.components.ItemRow
import io.github.bijojp.thingspot.ui.components.TabScaffold

/** Every Item, A to Z, optionally narrowed to one Category. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemsScreen(viewModel: InventoryViewModel, nav: Navigator) {
    val inventory = viewModel.inventory.collectAsStateWithLifecycle().value ?: return
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    val category = filter?.let { inventory.category(CategoryId(it)) }
    val items = category?.let { inventory.itemsInCategory(it.id) } ?: inventory.items

    TabScaffold(
        nav = nav,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.items_title)) }) },
        floatingActionButton = {
            if (inventory.places.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(R.string.action_add_item)) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    onClick = { nav.open(ItemEditorRoute()) },
                )
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            if (inventory.categories.isNotEmpty()) {
                item(key = "filters") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            FilterChip(
                                selected = category == null,
                                onClick = { filter = null },
                                label = { Text(stringResource(R.string.items_filter_all)) },
                            )
                        }
                        items(inventory.categories, key = { it.id.value }) { option ->
                            FilterChip(
                                selected = option.id == category?.id,
                                onClick = { filter = option.id.value },
                                label = { Text(option.name) },
                                leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(Color(option.color))) },
                            )
                        }
                    }
                }
            }
            if (items.isEmpty()) {
                item(key = "empty") {
                    if (category == null) {
                        EmptyHint(stringResource(R.string.items_empty_title), stringResource(R.string.items_empty_body))
                    } else {
                        EmptyHint(stringResource(R.string.items_empty_category, category.name))
                    }
                }
            }
            items(items, key = { it.id.value }) { item ->
                ItemRow(item, inventory, onClick = { nav.open(ItemEditorRoute(itemId = item.id.value)) })
            }
            item(key = "fab-space") { Spacer(Modifier.height(88.dp)) }
        }
    }
}
