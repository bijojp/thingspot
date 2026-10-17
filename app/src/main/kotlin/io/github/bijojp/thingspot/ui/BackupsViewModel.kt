package io.github.bijojp.thingspot.ui

import android.net.Uri
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.bijojp.thingspot.R
import io.github.bijojp.thingspot.data.backup.BackupContents
import io.github.bijojp.thingspot.data.backup.BackupOutcome
import io.github.bijojp.thingspot.data.backup.BackupProblem
import io.github.bijojp.thingspot.data.backup.BackupStatus
import io.github.bijojp.thingspot.data.backup.Backups
import io.github.bijojp.thingspot.data.backup.OpenResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.io.File

/** Where a restore is up to; the app shows a dialog for every state but [Idle]. */
sealed interface RestoreState {
    data object Idle : RestoreState

    data object Working : RestoreState

    data class NeedsPassword(val source: Uri, val wrongAttempt: Boolean) : RestoreState

    data class Confirm(val contents: BackupContents) : RestoreState
}

/** A file to hand to the system share sheet. */
data class ShareRequest(val file: File, val mimeType: String)

class BackupsViewModel(
    private val backups: Backups,
    private val scheduleDaily: (enabled: Boolean) -> Unit,
) : ViewModel() {
    val status: StateFlow<BackupStatus> = backups.status

    private val restoreState = MutableStateFlow<RestoreState>(RestoreState.Idle)
    val restore: StateFlow<RestoreState> = restoreState.asStateFlow()

    private val messageChannel = Channel<Int>(Channel.BUFFERED)
    val messages: Flow<Int> = messageChannel.receiveAsFlow()

    private val shareChannel = Channel<ShareRequest>(Channel.BUFFERED)
    val shares: Flow<ShareRequest> = shareChannel.receiveAsFlow()

    /** Turns backups on (or moves them) and makes the first one straight away. */
    fun chooseFolder(tree: Uri) = viewModelScope.launch {
        if (runCatching { backups.chooseFolder(tree) }.onFailure { log(it) }.isFailure) {
            say(R.string.msg_backup_failed)
            return@launch
        }
        scheduleDaily(true)
        say(if (backups.backUp(onlyIfChanged = false) == BackupOutcome.Written) R.string.msg_backups_on else R.string.msg_backup_failed)
    }

    fun backUpNow() = viewModelScope.launch {
        say(
            when (backups.backUp(onlyIfChanged = false)) {
                BackupOutcome.Written -> R.string.msg_backed_up
                BackupOutcome.NotSetUp -> R.string.msg_backup_folder_lost
                else -> R.string.msg_backup_failed
            },
        )
    }

    fun shareBackup() = share("application/octet-stream") { backups.shareableBackup() }

    fun exportSpreadsheet() = share("text/csv") { backups.spreadsheet() }

    private fun share(mimeType: String, make: suspend () -> File) = viewModelScope.launch {
        runCatching { make() }
            .onSuccess { shareChannel.send(ShareRequest(it, mimeType)) }
            .onFailure {
                log(it)
                say(R.string.msg_failed)
            }
    }

    fun setPassword(password: CharArray) = viewModelScope.launch {
        backups.setPassword(password)
        password.fill(' ')
        say(R.string.msg_password_set)
    }

    fun removePassword() = viewModelScope.launch {
        backups.removePassword()
        say(R.string.msg_password_removed)
    }

    /** Reads a Backup the user picked, asking for its password if it has one. */
    fun openBackup(source: Uri, password: CharArray? = null) = viewModelScope.launch {
        restoreState.value = RestoreState.Working
        val result = runCatching { backups.open(source, password) }.getOrElse {
            log(it)
            OpenResult.Unusable(BackupProblem.NotABackup)
        }
        password?.fill(' ')
        restoreState.value = when (result) {
            OpenResult.NeedsPassword -> RestoreState.NeedsPassword(source, wrongAttempt = false)
            OpenResult.WrongPassword -> RestoreState.NeedsPassword(source, wrongAttempt = true)
            is OpenResult.Ready -> RestoreState.Confirm(result.contents)
            is OpenResult.Unusable -> {
                say(
                    when (result.problem) {
                        BackupProblem.NotABackup -> R.string.msg_not_a_backup
                        BackupProblem.NewerVersion -> R.string.msg_backup_newer
                        BackupProblem.Damaged -> R.string.msg_backup_damaged
                    },
                )
                RestoreState.Idle
            }
        }
    }

    fun confirmRestore() {
        val confirm = restoreState.value as? RestoreState.Confirm ?: return
        viewModelScope.launch {
            restoreState.value = RestoreState.Working
            val restored = runCatching { backups.restore(confirm.contents) }.onFailure { log(it) }.isSuccess
            restoreState.value = RestoreState.Idle
            say(if (restored) R.string.msg_restored else R.string.msg_restore_failed)
        }
    }

    fun cancelRestore() {
        restoreState.value = RestoreState.Idle
    }

    private suspend fun say(@StringRes message: Int) = messageChannel.send(message)

    private fun log(error: Throwable) = Log.w("ThingSpot", "Backup action failed", error)
}
