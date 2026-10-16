package io.github.bijojp.thingspot.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bijojp.thingspot.BuildConfig
import io.github.bijojp.thingspot.R
import io.github.bijojp.thingspot.StoreProGate
import io.github.bijojp.thingspot.domain.CategoryId
import io.github.bijojp.thingspot.ui.InventoryViewModel
import io.github.bijojp.thingspot.ui.Navigator
import io.github.bijojp.thingspot.ui.components.CategoryDot
import io.github.bijojp.thingspot.ui.components.CategoryEditorDialog
import io.github.bijojp.thingspot.ui.components.SectionHeader
import io.github.bijojp.thingspot.ui.components.TabScaffold

/** Categories to manage, and what the app promises. Backups will live here too. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: InventoryViewModel, nav: Navigator) {
    val inventory = viewModel.inventory.collectAsStateWithLifecycle().value ?: return
    val proUnlocked by StoreProGate.isUnlocked.collectAsStateWithLifecycle()
    var adding by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    val editingCategory = editing?.let { inventory.category(CategoryId(it)) }

    TabScaffold(
        nav = nav,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item(key = "categories") { SectionHeader(stringResource(R.string.settings_categories)) }
            items(inventory.categories, key = { it.id.value }) { category ->
                val count = inventory.itemsInCategory(category.id).size
                ListItem(
                    headlineContent = { Text(category.name) },
                    supportingContent = { Text(pluralStringResource(R.plurals.item_count, count, count)) },
                    leadingContent = { CategoryDot(category) },
                    modifier = Modifier.clickable { editing = category.id.value },
                )
            }
            item(key = "add-category") {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.category_new), color = MaterialTheme.colorScheme.primary) },
                    leadingContent = { Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { adding = true },
                )
            }

            item(key = "about") { SectionHeader(stringResource(R.string.settings_about)) }
            item(key = "version") {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME)) },
                    supportingContent = {
                        Text(
                            stringResource(
                                R.string.build_status,
                                BuildConfig.FLAVOR,
                                stringResource(if (proUnlocked) R.string.pro_unlocked else R.string.pro_locked),
                            ),
                        )
                    },
                )
            }
            item(key = "privacy") {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_privacy_title)) },
                    supportingContent = { Text(stringResource(R.string.settings_privacy_body)) },
                )
            }
            item(key = "licence") {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_licence_title)) },
                    supportingContent = { Text(stringResource(R.string.settings_licence_body)) },
                )
            }
        }
    }

    if (adding) {
        CategoryEditorDialog(
            initial = null,
            itemCount = 0,
            onDismiss = { adding = false },
            onSave = { name, color ->
                viewModel.addCategory(name, color)
                adding = false
            },
        )
    }
    if (editingCategory != null) {
        CategoryEditorDialog(
            initial = editingCategory,
            itemCount = inventory.itemsInCategory(editingCategory.id).size,
            onDismiss = { editing = null },
            onSave = { name, color ->
                viewModel.editCategory(editingCategory.id, name, color)
                editing = null
            },
            onDelete = {
                viewModel.deleteCategory(editingCategory.id)
                editing = null
            },
        )
    }
}
