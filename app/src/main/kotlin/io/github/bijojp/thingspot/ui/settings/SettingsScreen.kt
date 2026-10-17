package io.github.bijojp.thingspot.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bijojp.thingspot.BuildConfig
import io.github.bijojp.thingspot.R
import io.github.bijojp.thingspot.StoreProGate
import io.github.bijojp.thingspot.domain.CategoryId
import io.github.bijojp.thingspot.ui.BackupsViewModel
import io.github.bijojp.thingspot.ui.InventoryViewModel
import io.github.bijojp.thingspot.ui.Navigator
import io.github.bijojp.thingspot.ui.components.BackupPasswordDialog
import io.github.bijojp.thingspot.ui.components.CategoryDot
import io.github.bijojp.thingspot.ui.components.CategoryEditorDialog
import io.github.bijojp.thingspot.ui.components.SectionHeader
import io.github.bijojp.thingspot.ui.components.TabScaffold
import io.github.bijojp.thingspot.ui.components.lastBackupLabel
import io.github.bijojp.thingspot.ui.components.rememberBackupFilePicker
import io.github.bijojp.thingspot.ui.components.rememberBackupFolderPicker

/** Backups first (they protect everything), then Categories, then what the app promises. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: InventoryViewModel, backups: BackupsViewModel, nav: Navigator) {
    val inventory = viewModel.inventory.collectAsStateWithLifecycle().value ?: return
    val proUnlocked by StoreProGate.isUnlocked.collectAsStateWithLifecycle()
    val backupStatus by backups.status.collectAsStateWithLifecycle()
    val pickBackupFolder = rememberBackupFolderPicker(backups)
    val pickBackupFile = rememberBackupFilePicker(backups)
    var editingPassword by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    val editingCategory = editing?.let { inventory.category(CategoryId(it)) }

    TabScaffold(
        nav = nav,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item(key = "backups") { SectionHeader(stringResource(R.string.settings_backups)) }
            if (backupStatus.folderName == null) {
                item(key = "backups-off") {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.backup_off_title), color = MaterialTheme.colorScheme.error) },
                        supportingContent = { Text(stringResource(R.string.backup_off_body)) },
                        modifier = Modifier.clickable(onClick = pickBackupFolder),
                    )
                }
                item(key = "backups-choose") {
                    FilledTonalButton(onClick = pickBackupFolder, modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(stringResource(R.string.backup_choose_folder))
                    }
                }
            } else {
                item(key = "backup-folder") {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.backup_folder_title)) },
                        supportingContent = {
                            if (backupStatus.folderLost) {
                                Text(stringResource(R.string.backup_folder_lost), color = MaterialTheme.colorScheme.error)
                            } else {
                                Text(backupStatus.folderName.orEmpty())
                            }
                        },
                        modifier = Modifier.clickable(onClick = pickBackupFolder),
                    )
                }
                item(key = "backup-last") {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.backup_last_title)) },
                        supportingContent = {
                            if (backupStatus.lastAttemptFailed) {
                                Text(stringResource(R.string.backup_last_failed), color = MaterialTheme.colorScheme.error)
                            } else {
                                Text(lastBackupLabel(backupStatus) + " · " + stringResource(R.string.backup_schedule))
                            }
                        },
                        trailingContent = { TextButton(onClick = { backups.backUpNow() }) { Text(stringResource(R.string.backup_now)) } },
                    )
                }
            }
            item(key = "backup-share") {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.backup_share_title)) },
                    supportingContent = { Text(stringResource(R.string.backup_share_body)) },
                    modifier = Modifier.clickable { backups.shareBackup() },
                )
            }
            item(key = "backup-restore") {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.backup_restore_title)) },
                    supportingContent = { Text(stringResource(R.string.backup_restore_body)) },
                    modifier = Modifier.clickable(onClick = pickBackupFile),
                )
            }
            item(key = "backup-password") {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.backup_password_title)) },
                    supportingContent = {
                        Text(stringResource(if (backupStatus.passwordSet) R.string.backup_password_on else R.string.backup_password_off))
                    },
                    modifier = Modifier.clickable { editingPassword = true },
                )
            }
            item(key = "export") {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.export_title)) },
                    supportingContent = { Text(stringResource(R.string.export_body)) },
                    modifier = Modifier.clickable { backups.exportSpreadsheet() },
                )
            }

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

    if (editingPassword) {
        BackupPasswordDialog(
            passwordSet = backupStatus.passwordSet,
            onDismiss = { editingPassword = false },
            onSet = {
                backups.setPassword(it)
                editingPassword = false
            },
            onRemove = {
                backups.removePassword()
                editingPassword = false
            },
        )
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
