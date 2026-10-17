package io.github.bijojp.thingspot.ui.places

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bijojp.thingspot.R
import io.github.bijojp.thingspot.domain.PlaceId
import io.github.bijojp.thingspot.ui.InventoryViewModel
import io.github.bijojp.thingspot.ui.ItemEditorRoute
import io.github.bijojp.thingspot.ui.Navigator
import io.github.bijojp.thingspot.ui.PlaceRoute
import io.github.bijojp.thingspot.ui.components.DeletePlaceDialog
import io.github.bijojp.thingspot.ui.components.EmptyHint
import io.github.bijojp.thingspot.ui.components.ItemRow
import io.github.bijojp.thingspot.ui.components.PhotoBanner
import io.github.bijojp.thingspot.ui.components.PlaceEditorDialog
import io.github.bijojp.thingspot.ui.components.PlacePickerDialog
import io.github.bijojp.thingspot.ui.components.PlaceRow
import io.github.bijojp.thingspot.ui.components.SectionHeader
import io.github.bijojp.thingspot.ui.components.TabScaffold
import io.github.bijojp.thingspot.ui.search.FirstRunCard
import io.github.bijojp.thingspot.ui.search.starterPlaces

private enum class PlaceDialog { None, AddInside, Edit, Move, Delete }

/** What's inside a Place, or the top-level Places when [placeId] is null (the Places tab). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceScreen(placeId: PlaceId?, viewModel: InventoryViewModel, nav: Navigator) {
    val inventory = viewModel.inventory.collectAsStateWithLifecycle().value ?: return
    val place = placeId?.let(inventory::place)
    if (placeId != null && place == null) {
        // Deleted (here or elsewhere): there is nothing left to show.
        LaunchedEffect(Unit) { nav.back() }
        return
    }
    var dialog by rememberSaveable { mutableStateOf(PlaceDialog.None) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val children = inventory.children(placeId)
    val items = placeId?.let(inventory::itemsIn).orEmpty()
    val starter = starterPlaces()

    TabScaffold(
        nav = nav,
        topBar = {
            TopAppBar(
                title = {
                    if (place == null) {
                        Text(stringResource(R.string.places_title))
                    } else {
                        Column {
                            Text("${place.icon} ${place.name}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val parents = inventory.path(place.id).dropLast(1)
                            if (parents.isNotEmpty()) {
                                Text(
                                    parents.joinToString(" → ") { it.name },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    if (place != null) {
                        IconButton(onClick = nav::back) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    }
                },
                actions = {
                    if (place != null) {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.place_action_edit)) },
                                onClick = { menuOpen = false; dialog = PlaceDialog.Edit },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.place_action_move)) },
                                onClick = { menuOpen = false; dialog = PlaceDialog.Move },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.place_action_delete), color = MaterialTheme.colorScheme.error) },
                                onClick = { menuOpen = false; dialog = PlaceDialog.Delete },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (place == null) {
                if (inventory.places.isNotEmpty()) {
                    ExtendedFloatingActionButton(
                        text = { Text(stringResource(R.string.action_add_place)) },
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        onClick = { dialog = PlaceDialog.AddInside },
                    )
                }
            } else {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ExtendedFloatingActionButton(
                        text = { Text(stringResource(R.string.action_add_place_inside)) },
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        onClick = { dialog = PlaceDialog.AddInside },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    ExtendedFloatingActionButton(
                        text = { Text(stringResource(R.string.action_add_item)) },
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        onClick = { nav.open(ItemEditorRoute(placeId = place.id.value)) },
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            val photo = place?.photo
            if (photo != null) {
                item(key = "photo") { PhotoBanner(photo, Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
            }
            if (place == null && children.isEmpty()) {
                item(key = "first-run") {
                    FirstRunCard(onStarter = { viewModel.addStarterPlaces(starter) }, onOwnPlace = { dialog = PlaceDialog.AddInside })
                }
            }
            if (children.isNotEmpty()) {
                if (place != null) item(key = "places-header") { SectionHeader(stringResource(R.string.place_section_places)) }
                items(children, key = { "place:${it.id.value}" }) { child ->
                    PlaceRow(child, inventory, onClick = { nav.open(PlaceRoute(child.id.value)) })
                }
            }
            if (place != null) {
                item(key = "items-header") { SectionHeader(stringResource(R.string.place_section_items)) }
                if (items.isEmpty()) {
                    item(key = "no-items") { EmptyHint(stringResource(R.string.place_no_items)) }
                }
                items(items, key = { "item:${it.id.value}" }) { item ->
                    ItemRow(item, inventory, onClick = { nav.open(ItemEditorRoute(itemId = item.id.value)) }, showPath = false)
                }
            }
            item(key = "fab-space") { Spacer(Modifier.height(if (place == null) 88.dp else 152.dp)) }
        }
    }

    when (dialog) {
        PlaceDialog.None -> Unit
        PlaceDialog.AddInside -> PlaceEditorDialog(
            initial = null,
            viewModel = viewModel,
            onDismiss = { dialog = PlaceDialog.None },
            onSave = { name, icon, photo ->
                viewModel.addPlace(name, icon, parentId = place?.id, photo = photo)
                dialog = PlaceDialog.None
            },
        )
        PlaceDialog.Edit -> if (place != null) {
            PlaceEditorDialog(
                initial = place,
                viewModel = viewModel,
                onDismiss = { dialog = PlaceDialog.None },
                onSave = { name, icon, photo ->
                    viewModel.editPlace(place.id, name, icon, photo)
                    dialog = PlaceDialog.None
                },
            )
        }
        PlaceDialog.Move -> if (place != null) {
            val blocked = inventory.descendants(place.id) + place.id
            PlacePickerDialog(
                inventory = inventory,
                title = stringResource(R.string.place_move_title, place.name),
                selected = place.parentId,
                onDismiss = { dialog = PlaceDialog.None },
                onPick = { newParent ->
                    if (newParent != place.parentId) viewModel.movePlace(place.id, newParent)
                    dialog = PlaceDialog.None
                },
                allowTopLevel = true,
                selectable = { it !in blocked },
            )
        }
        PlaceDialog.Delete -> place?.let { inventory.deletionOf(it.id) }?.let { deletion ->
            DeletePlaceDialog(
                inventory = inventory,
                deletion = deletion,
                onDismiss = { dialog = PlaceDialog.None },
                onConfirm = { destination ->
                    viewModel.deletePlace(deletion.place.id, destination)
                    dialog = PlaceDialog.None
                },
            )
        }
    }
}
