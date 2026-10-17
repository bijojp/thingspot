package io.github.bijojp.thingspot.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.bijojp.thingspot.R
import io.github.bijojp.thingspot.domain.Inventory
import io.github.bijojp.thingspot.domain.ItemsGoTo
import io.github.bijojp.thingspot.domain.Limits
import io.github.bijojp.thingspot.domain.Place
import io.github.bijojp.thingspot.domain.PlaceDeletion
import io.github.bijojp.thingspot.domain.PlaceId
import io.github.bijojp.thingspot.domain.SearchResult
import io.github.bijojp.thingspot.domain.TreeRow
import io.github.bijojp.thingspot.domain.cleanName
import io.github.bijojp.thingspot.ui.InventoryViewModel

/** Name, icon and optional photo for a new Place ([initial] null) or an existing one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlaceEditorDialog(
    initial: Place?,
    viewModel: InventoryViewModel,
    onDismiss: () -> Unit,
    onSave: (name: String, icon: String, photo: String?) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var icon by rememberSaveable { mutableStateOf(initial?.icon ?: DEFAULT_PLACE_ICON) }
    var photo by rememberSaveable { mutableStateOf(initial?.photo) }
    val clean = cleanName(name, Limits.PLACE_NAME)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.place_new else R.string.place_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(Limits.PLACE_NAME) },
                    label = { Text(stringResource(R.string.field_name)) },
                    placeholder = { Text(stringResource(R.string.place_name_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.field_icon), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PlaceIcons.forEach { option ->
                        val selected = option == icon
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                                .clickable { icon = option },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(option, fontSize = 22.sp)
                        }
                    }
                }
                Text(stringResource(R.string.field_photo_optional), style = MaterialTheme.typography.labelLarge)
                PhotoField(photo, viewModel, onChange = { photo = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { clean?.let { onSave(it, icon, photo) } }, enabled = clean != null) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * Full-screen chooser for a Place, shown as the tree (or a flat filtered list while typing).
 * [selectable] decides which Places can be picked; [allowTopLevel] adds a "Top level" choice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacePickerDialog(
    inventory: Inventory,
    title: String,
    selected: PlaceId?,
    onDismiss: () -> Unit,
    onPick: (PlaceId?) -> Unit,
    allowTopLevel: Boolean = false,
    selectable: (PlaceId) -> Boolean = { true },
) {
    var filter by rememberSaveable { mutableStateOf("") }
    val rows = remember(inventory, filter) {
        if (filter.isBlank()) {
            inventory.tree()
        } else {
            inventory.search(filter)
                .filterIsInstance<SearchResult.PlaceMatch>()
                .map { TreeRow(it.place, depth = 0) }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column {
                TopAppBar(
                    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_cancel))
                        }
                    },
                )
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    placeholder = { Text(stringResource(R.string.picker_filter_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
                LazyColumn(Modifier.fillMaxSize()) {
                    if (allowTopLevel && filter.isBlank()) {
                        item(key = "top") {
                            PickerRow(
                                icon = "⤒",
                                name = stringResource(R.string.picker_top_level),
                                supporting = stringResource(R.string.picker_top_level_hint),
                                depth = 0,
                                isSelected = selected == null,
                                enabled = true,
                                onClick = { onPick(null) },
                            )
                        }
                    }
                    items(rows, key = { it.place.id.value }) { row ->
                        val parents = if (filter.isBlank()) null else inventory.path(row.place.id).dropLast(1).joinToString(" → ") { it.name }
                        PickerRow(
                            icon = row.place.icon,
                            name = row.place.name,
                            supporting = parents?.takeIf { it.isNotEmpty() },
                            depth = row.depth,
                            isSelected = row.place.id == selected,
                            enabled = selectable(row.place.id),
                            onClick = { onPick(row.place.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerRow(
    icon: String,
    name: String,
    supporting: String?,
    depth: Int,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.38f
    ListItem(
        headlineContent = {
            Text(
                name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
        },
        supportingContent = supporting?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        leadingContent = { PlaceIcon(icon) },
        colors = if (isSelected) {
            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            ListItemDefaults.colors()
        },
        modifier = Modifier
            .padding(start = (depth * 20).dp)
            .clickable(enabled = enabled, onClick = onClick),
    )
}

/** Confirms deleting a Place and says where its contents go; asks for a destination when needed. */
@Composable
fun DeletePlaceDialog(inventory: Inventory, deletion: PlaceDeletion, onDismiss: () -> Unit, onConfirm: (PlaceId?) -> Unit) {
    var choice by rememberSaveable { mutableStateOf<String?>(null) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val chosen = choice?.let(::PlaceId)
    val parentName = deletion.place.parentId?.let(inventory::place)?.name

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.place_delete_title, deletion.place.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val places = deletion.childPlaces.size
                if (places > 0) {
                    Text(
                        if (parentName != null) {
                            pluralStringResource(R.plurals.place_delete_places_to_parent, places, places, parentName)
                        } else {
                            pluralStringResource(R.plurals.place_delete_places_to_top, places, places)
                        },
                    )
                }
                val items = deletion.items.size
                when (val goTo = deletion.itemsGoTo) {
                    ItemsGoTo.Nowhere -> if (places == 0) Text(stringResource(R.string.place_delete_empty))
                    is ItemsGoTo.Parent -> Text(pluralStringResource(R.plurals.place_delete_items_to_parent, items, items, parentName.orEmpty()))
                    is ItemsGoTo.UserChoice -> {
                        Text(pluralStringResource(R.plurals.place_delete_items_choose, items, items))
                        OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(chosen?.let(inventory::pathLabel) ?: stringResource(R.string.action_choose_place))
                        }
                        if (picking) {
                            PlacePickerDialog(
                                inventory = inventory,
                                title = stringResource(R.string.place_delete_pick_title),
                                selected = chosen,
                                onDismiss = { picking = false },
                                onPick = { picked ->
                                    choice = picked?.value
                                    picking = false
                                },
                                selectable = { it in goTo.candidates },
                            )
                        }
                    }
                    ItemsGoTo.NoPlaceLeft -> Text(pluralStringResource(R.plurals.place_delete_blocked, items, items))
                }
                val labels = deletion.labels.size
                if (labels > 0) Text(pluralStringResource(R.plurals.place_delete_labels, labels, labels))
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(chosen) }, enabled = deletion.isReady(chosen)) {
                Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
