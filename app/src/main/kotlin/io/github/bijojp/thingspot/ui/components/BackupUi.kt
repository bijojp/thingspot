package io.github.bijojp.thingspot.ui.components

import android.content.Intent
import android.provider.DocumentsContract
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bijojp.thingspot.R
import io.github.bijojp.thingspot.data.backup.BackupStatus
import io.github.bijojp.thingspot.ui.BackupsViewModel
import io.github.bijojp.thingspot.ui.RestoreState

private const val MIN_PASSWORD = 8

/** Where the folder picker opens first: the phone's Documents folder. */
private val DOCUMENTS = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Documents")

/** Opens the system folder picker; the chosen folder turns backups on. */
@Composable
fun rememberBackupFolderPicker(backups: BackupsViewModel): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) backups.chooseFolder(tree)
    }
    return { launcher.launch(DOCUMENTS) }
}

/** Opens the system file picker to restore from a Backup file. */
@Composable
fun rememberBackupFilePicker(backups: BackupsViewModel): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { file ->
        if (file != null) backups.openBackup(file)
    }
    return { launcher.launch(arrayOf("*/*")) }
}

/** "5 minutes ago", "Yesterday", or "Not yet". */
@Composable
fun lastBackupLabel(status: BackupStatus): String = status.lastBackupAt?.let {
    DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
} ?: stringResource(R.string.backup_last_never)

/** Shown on Search while there's something to lose and backups aren't working. */
@Composable
fun BackupBanner(status: BackupStatus, onSetUp: () -> Unit, onBackUpNow: () -> Unit) {
    val broken = status.folderName != null && !status.folderLost && status.lastAttemptFailed
    ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(if (status.folderName == null) R.string.banner_off_title else R.string.banner_failed_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(
                    when {
                        status.folderName == null -> R.string.banner_off_body
                        status.folderLost -> R.string.backup_folder_lost
                        else -> R.string.banner_failed_body
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            FilledTonalButton(onClick = if (broken) onBackUpNow else onSetUp) {
                Text(stringResource(if (broken) R.string.backup_now else R.string.backup_choose_folder))
            }
        }
    }
}

/** App-wide: the share sheet for Backups/Exports, and every restore dialog. */
@Composable
fun BackupEffects(backups: BackupsViewModel) {
    val context = LocalContext.current
    LaunchedEffect(backups) {
        backups.shares.collect { request ->
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", request.file)
            val send = Intent(Intent.ACTION_SEND)
                .setType(request.mimeType)
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(send, null))
        }
    }
    val state by backups.restore.collectAsStateWithLifecycle()
    when (val current = state) {
        RestoreState.Idle -> Unit
        RestoreState.Working -> AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text(stringResource(R.string.restore_working)) },
            text = { LinearProgressIndicator(Modifier.fillMaxWidth()) },
        )
        is RestoreState.NeedsPassword -> PasswordPrompt(
            wrongAttempt = current.wrongAttempt,
            onDismiss = backups::cancelRestore,
            onSubmit = { backups.openBackup(current.source, it) },
        )
        is RestoreState.Confirm -> {
            val manifest = current.contents.manifest
            val data = current.contents.data
            val created = DateUtils.formatDateTime(
                context,
                manifest.createdAt,
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_YEAR,
            )
            AlertDialog(
                onDismissRequest = backups::cancelRestore,
                title = { Text(stringResource(R.string.restore_confirm_title)) },
                text = {
                    Text(
                        stringResource(
                            R.string.restore_confirm_body,
                            created,
                            pluralStringResource(R.plurals.count_places, data.places.size, data.places.size),
                            pluralStringResource(R.plurals.item_count, data.items.size, data.items.size),
                            pluralStringResource(R.plurals.count_photos, data.photos.size, data.photos.size),
                        ),
                    )
                },
                confirmButton = {
                    TextButton(onClick = backups::confirmRestore) {
                        Text(stringResource(R.string.restore_action), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = { TextButton(onClick = backups::cancelRestore) { Text(stringResource(R.string.action_cancel)) } },
            )
        }
    }
}

@Composable
private fun PasswordPrompt(wrongAttempt: Boolean, onDismiss: () -> Unit, onSubmit: (CharArray) -> Unit) {
    var password by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.restore_password_title)) },
        text = {
            PasswordField(
                value = password,
                onValueChange = { password = it },
                label = stringResource(R.string.password_field),
                error = if (wrongAttempt) stringResource(R.string.restore_password_wrong) else null,
            )
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(password.toCharArray()) }, enabled = password.isNotEmpty()) {
                Text(stringResource(R.string.action_open))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Turn the backup password on, change it, or turn it off. */
@Composable
fun BackupPasswordDialog(passwordSet: Boolean, onDismiss: () -> Unit, onSet: (CharArray) -> Unit, onRemove: () -> Unit) {
    var choosing by rememberSaveable { mutableStateOf(!passwordSet) }
    if (!choosing) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.backup_password_title)) },
            text = { Text(stringResource(R.string.backup_password_on)) },
            confirmButton = {
                Row {
                    TextButton(onClick = onRemove) { Text(stringResource(R.string.password_turn_off), color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { choosing = true }) { Text(stringResource(R.string.password_change)) }
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        )
        return
    }

    var password by rememberSaveable { mutableStateOf("") }
    var repeat by rememberSaveable { mutableStateOf("") }
    var understood by rememberSaveable { mutableStateOf(false) }
    val tooShort = password.isNotEmpty() && password.length < MIN_PASSWORD
    val mismatch = repeat.isNotEmpty() && repeat != password
    val ready = password.length >= MIN_PASSWORD && repeat == password && understood

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (passwordSet) R.string.password_change_title else R.string.password_set_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PasswordField(password, { password = it }, stringResource(R.string.password_field), if (tooShort) stringResource(R.string.password_short) else null)
                PasswordField(repeat, { repeat = it }, stringResource(R.string.password_repeat_field), if (mismatch) stringResource(R.string.password_mismatch) else null)
                Text(stringResource(R.string.password_warning), style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = understood, onCheckedChange = { understood = it })
                    Text(stringResource(R.string.password_understood))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSet(password.toCharArray()) }, enabled = ready) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun PasswordField(value: String, onValueChange: (String) -> Unit, label: String, error: String?) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}
