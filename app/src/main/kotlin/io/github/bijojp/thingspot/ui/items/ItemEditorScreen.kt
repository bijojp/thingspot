package io.github.bijojp.thingspot.ui.items

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bijojp.thingspot.R
import io.github.bijojp.thingspot.domain.CategoryId
import io.github.bijojp.thingspot.domain.ItemFields
import io.github.bijojp.thingspot.domain.ItemId
import io.github.bijojp.thingspot.domain.Limits
import io.github.bijojp.thingspot.domain.PlaceId
import io.github.bijojp.thingspot.domain.cleanName
import io.github.bijojp.thingspot.ui.InventoryViewModel
import io.github.bijojp.thingspot.ui.LocalSnackbarHostState
import io.github.bijojp.thingspot.ui.Navigator
import io.github.bijojp.thingspot.ui.components.CategoryEditorDialog
import io.github.bijojp.thingspot.ui.components.PlaceIcon
import io.github.bijojp.thingspot.ui.components.PlacePickerDialog
import io.github.bijojp.thingspot.ui.components.pathLabel

private enum class EditorDialog { None, PickPlace, NewCategory, ConfirmDelete }

/** Add an Item ([itemId] null, optionally starting in [presetPlaceId]) or edit one. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ItemEditorScreen(itemId: ItemId?, presetPlaceId: PlaceId?, viewModel: InventoryViewModel, nav: Navigator) {
    val inventory = viewModel.inventory.collectAsStateWithLifecycle().value ?: return
    val existing = itemId?.let(inventory::item)
    if (itemId != null && existing == null) {
        LaunchedEffect(Unit) { nav.back() }
        return
    }
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var placeValue by rememberSaveable { mutableStateOf(existing?.placeId?.value ?: presetPlaceId?.value) }
    var categoryValue by rememberSaveable { mutableStateOf(existing?.categoryId?.value) }
    var notes by rememberSaveable { mutableStateOf(existing?.notes.orEmpty()) }
    var showErrors by rememberSaveable { mutableStateOf(false) }
    var dialog by rememberSaveable { mutableStateOf(EditorDialog.None) }

    val place = placeValue?.let { inventory.place(PlaceId(it)) }
    val category = categoryValue?.let { inventory.category(CategoryId(it)) }
    val cleanedName = cleanName(name, Limits.ITEM_NAME)

    fun save() {
        if (cleanedName == null || place == null) {
            showErrors = true
            return
        }
        val fields = ItemFields(cleanedName, place.id, category?.id, notes)
        if (existing == null) viewModel.addItem(fields) else viewModel.editItem(existing.id, fields)
        nav.back()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (existing == null) R.string.item_new else R.string.item_edit)) },
                navigationIcon = {
                    IconButton(onClick = nav::back) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_cancel))
                    }
                },
                actions = { TextButton(onClick = { save() }) { Text(stringResource(R.string.action_save)) } },
            )
        },
        snackbarHost = { SnackbarHost(LocalSnackbarHostState.current) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(Limits.ITEM_NAME) },
                label = { Text(stringResource(R.string.field_name)) },
                placeholder = { Text(stringResource(R.string.item_name_hint)) },
                isError = showErrors && cleanedName == null,
                supportingText = if (showErrors && cleanedName == null) {
                    { Text(stringResource(R.string.item_error_name)) }
                } else {
                    null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.field_place), style = MaterialTheme.typography.labelLarge)
                val missingPlace = showErrors && place == null
                OutlinedCard(onClick = { dialog = EditorDialog.PickPlace }, modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = {
                            Text(
                                place?.let { inventory.pathLabel(it.id) } ?: stringResource(R.string.action_choose_place),
                                color = if (missingPlace) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        leadingContent = { PlaceIcon(place?.icon ?: "📍") },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
                if (missingPlace) {
                    Text(
                        stringResource(R.string.item_error_place),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.field_category_optional), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = category == null,
                        onClick = { categoryValue = null },
                        label = { Text(stringResource(R.string.category_none)) },
                    )
                    inventory.categories.forEach { option ->
                        FilterChip(
                            selected = option.id == category?.id,
                            onClick = { categoryValue = option.id.value },
                            label = { Text(option.name) },
                            leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(Color(option.color))) },
                        )
                    }
                    AssistChip(
                        onClick = { dialog = EditorDialog.NewCategory },
                        label = { Text(stringResource(R.string.category_new)) },
                        leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    )
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it.take(Limits.NOTES) },
                label = { Text(stringResource(R.string.field_notes_optional)) },
                placeholder = { Text(stringResource(R.string.item_notes_hint)) },
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            if (existing != null) {
                OutlinedButton(onClick = { dialog = EditorDialog.ConfirmDelete }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.item_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    when (dialog) {
        EditorDialog.None -> Unit
        EditorDialog.PickPlace -> PlacePickerDialog(
            inventory = inventory,
            title = stringResource(R.string.item_pick_place_title),
            selected = place?.id,
            onDismiss = { dialog = EditorDialog.None },
            onPick = { picked ->
                placeValue = picked?.value
                dialog = EditorDialog.None
            },
        )
        EditorDialog.NewCategory -> CategoryEditorDialog(
            initial = null,
            itemCount = 0,
            onDismiss = { dialog = EditorDialog.None },
            onSave = { categoryName, color ->
                viewModel.addCategory(categoryName, color) { categoryValue = it.value }
                dialog = EditorDialog.None
            },
        )
        EditorDialog.ConfirmDelete -> if (existing != null) {
            AlertDialog(
                onDismissRequest = { dialog = EditorDialog.None },
                title = { Text(stringResource(R.string.item_delete_title, existing.name)) },
                confirmButton = {
                    TextButton(onClick = {
                        dialog = EditorDialog.None
                        viewModel.deleteItem(existing.id)
                        nav.back()
                    }) {
                        Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { dialog = EditorDialog.None }) { Text(stringResource(R.string.action_cancel)) }
                },
            )
        }
    }
}
