package io.github.bijojp.thingspot.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bijojp.thingspot.R
import io.github.bijojp.thingspot.domain.SearchResult
import io.github.bijojp.thingspot.ui.BackupsViewModel
import io.github.bijojp.thingspot.ui.InventoryViewModel
import io.github.bijojp.thingspot.ui.ItemEditorRoute
import io.github.bijojp.thingspot.ui.Navigator
import io.github.bijojp.thingspot.ui.NewPlace
import io.github.bijojp.thingspot.ui.PlaceRoute
import io.github.bijojp.thingspot.ui.components.BackupBanner
import io.github.bijojp.thingspot.ui.components.EmptyHint
import io.github.bijojp.thingspot.ui.components.ItemRow
import io.github.bijojp.thingspot.ui.components.PlaceEditorDialog
import io.github.bijojp.thingspot.ui.components.PlaceRow
import io.github.bijojp.thingspot.ui.components.SectionHeader
import io.github.bijojp.thingspot.ui.components.TabScaffold
import io.github.bijojp.thingspot.ui.components.rememberBackupFilePicker
import io.github.bijojp.thingspot.ui.components.rememberBackupFolderPicker

private const val RECENT_LIMIT = 15

/** The home screen. Search is the product; when the box is empty, recently added Items show. */
@Composable
fun SearchScreen(viewModel: InventoryViewModel, backups: BackupsViewModel, nav: Navigator) {
    val inventory = viewModel.inventory.collectAsStateWithLifecycle().value ?: return
    val backupStatus by backups.status.collectAsStateWithLifecycle()
    val pickBackupFolder = rememberBackupFolderPicker(backups)
    val pickBackupFile = rememberBackupFilePicker(backups)
    var query by rememberSaveable { mutableStateOf("") }
    var addingPlace by rememberSaveable { mutableStateOf(false) }
    val results = remember(inventory, query) { inventory.search(query) }
    val starter = starterPlaces()

    TabScaffold(
        nav = nav,
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
            item(key = "search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_clear))
                            }
                        }
                    },
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            if (backupStatus.needsAttention && inventory.items.isNotEmpty()) {
                item(key = "backup-banner") {
                    BackupBanner(backupStatus, onSetUp = pickBackupFolder, onBackUpNow = { backups.backUpNow() })
                }
            }
            when {
                inventory.places.isEmpty() -> item(key = "first-run") {
                    FirstRunCard(
                        onStarter = { viewModel.addStarterPlaces(starter) },
                        onOwnPlace = { addingPlace = true },
                        onRestore = pickBackupFile,
                    )
                }
                query.isBlank() -> {
                    val recent = inventory.recentItems(RECENT_LIMIT)
                    if (recent.isEmpty()) {
                        item(key = "no-items") {
                            EmptyHint(stringResource(R.string.search_no_items_title), stringResource(R.string.search_no_items_body))
                        }
                    } else {
                        item(key = "recent") { SectionHeader(stringResource(R.string.search_recent)) }
                        items(recent, key = { "item:${it.id.value}" }) { item ->
                            ItemRow(item, inventory, onClick = { nav.open(ItemEditorRoute(itemId = item.id.value)) })
                        }
                    }
                }
                results.isEmpty() -> item(key = "no-match") {
                    EmptyHint(stringResource(R.string.search_no_match, query.trim()))
                }
                else -> items(results, key = { it.key }) { result ->
                    when (result) {
                        is SearchResult.ItemMatch -> ItemRow(
                            result.item,
                            inventory,
                            onClick = { nav.open(ItemEditorRoute(itemId = result.item.id.value)) },
                        )
                        is SearchResult.PlaceMatch -> PlaceRow(
                            result.place,
                            inventory,
                            onClick = { nav.open(PlaceRoute(result.place.id.value)) },
                            showPath = true,
                        )
                    }
                }
            }
            item(key = "fab-space") { Spacer(Modifier.height(88.dp)) }
        }
    }

    if (addingPlace) {
        PlaceEditorDialog(
            initial = null,
            viewModel = viewModel,
            onDismiss = { addingPlace = false },
            onSave = { name, icon, photo ->
                viewModel.addPlace(name, icon, parentId = null, photo = photo)
                addingPlace = false
            },
        )
    }
}

private val SearchResult.key: String
    get() = when (this) {
        is SearchResult.ItemMatch -> "item:${item.id.value}"
        is SearchResult.PlaceMatch -> "place:${place.id.value}"
    }

/** The optional starter set from the plan: Home → Bedroom, Kitchen, Living room, Garage. */
@Composable
fun starterPlaces() = NewPlace(
    stringResource(R.string.starter_home),
    "🏠",
    listOf(
        NewPlace(stringResource(R.string.starter_bedroom), "🛏️"),
        NewPlace(stringResource(R.string.starter_kitchen), "🍽️"),
        NewPlace(stringResource(R.string.starter_living_room), "🛋️"),
        NewPlace(stringResource(R.string.starter_garage), "🚗"),
    ),
)

/** Shown until the first Place exists: Items need somewhere to live. */
@Composable
fun FirstRunCard(onStarter: () -> Unit, onOwnPlace: () -> Unit, onRestore: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.first_run_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.first_run_body), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onStarter, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.first_run_starter))
            }
            Text(
                stringResource(R.string.first_run_starter_detail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onOwnPlace, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.first_run_own))
            }
            TextButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.first_run_restore))
            }
        }
    }
}
