package io.github.bijojp.thingspot.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import io.github.bijojp.thingspot.data.CategoryEntity
import io.github.bijojp.thingspot.data.ItemEntity
import io.github.bijojp.thingspot.data.PhotoEntity
import io.github.bijojp.thingspot.data.PhotoStore
import io.github.bijojp.thingspot.data.PlaceEntity
import io.github.bijojp.thingspot.data.Snapshot
import io.github.bijojp.thingspot.data.ThingSpotDatabase
import io.github.bijojp.thingspot.data.toInventory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.crypto.spec.SecretKeySpec

/** What the Backups section of Settings shows. */
data class BackupStatus(
    /** Where Backups go, e.g. "Documents/ThingSpot", or null when backups are off. */
    val folderName: String?,
    /** A folder was chosen but Android no longer lets ThingSpot write there. */
    val folderLost: Boolean,
    val lastBackupAt: Long?,
    val lastAttemptFailed: Boolean,
    val passwordSet: Boolean,
) {
    val needsAttention: Boolean get() = folderName == null || folderLost || lastAttemptFailed
}

enum class BackupOutcome { Written, Unchanged, Empty, NotSetUp, Failed }

sealed interface OpenResult {
    data object NeedsPassword : OpenResult

    data object WrongPassword : OpenResult

    data class Unusable(val problem: BackupProblem) : OpenResult

    data class Ready(val contents: BackupContents) : OpenResult
}

/**
 * Backups and Export (ADR 0001): the only way data leaves the phone, and only where the user
 * puts it. Automatic Backups go to a folder the user chose, once a day when something changed,
 * keeping the last [KEEP]. Restoring replaces everything after saving a safety copy.
 */
class Backups internal constructor(
    context: Context,
    private val db: ThingSpotDatabase,
    private val photos: PhotoStore,
    private val appVersion: String,
) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val prefs = appContext.getSharedPreferences("backups", Context.MODE_PRIVATE)
    private val dao = db.inventoryDao()
    private val lock = Mutex()

    private val mutableStatus = MutableStateFlow(readStatus())
    val status: StateFlow<BackupStatus> = mutableStatus.asStateFlow()

    // --- Folder

    /** Remembers the folder the user picked; backups go into a "ThingSpot" folder inside it. */
    suspend fun chooseFolder(tree: Uri) = withContext(Dispatchers.IO) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        resolver.takePersistableUriPermission(tree, flags)
        val previous = prefs.getString(FOLDER, null)?.let(Uri::parse)
        if (previous != null && previous != tree) runCatching { resolver.releasePersistableUriPermission(previous, flags) }
        val root = DocumentFile.fromTreeUri(appContext, tree)
        prefs.edit {
            putString(FOLDER, tree.toString())
            val picked = root?.name ?: tree.lastPathSegment.orEmpty()
            putString(FOLDER_NAME, if (picked == FOLDER_INSIDE) picked else "$picked/$FOLDER_INSIDE")
            remove(FINGERPRINT)
            putBoolean(LAST_FAILED, false)
        }
        refresh()
    }

    // --- Password

    suspend fun setPassword(password: CharArray) = withContext(Dispatchers.Default) {
        val key = BackupCrypto.newPasswordKey(password)
        prefs.edit {
            putString(KEY, key.key.encoded.encode())
            putString(SALT, key.salt.encode())
            putInt(ITERATIONS, key.iterations)
            remove(FINGERPRINT)
        }
        refresh()
    }

    fun removePassword() {
        prefs.edit {
            remove(KEY)
            remove(SALT)
            remove(ITERATIONS)
            remove(FINGERPRINT)
        }
        refresh()
    }

    private fun passwordKey(): BackupCrypto.PasswordKey? {
        val key = prefs.getString(KEY, null) ?: return null
        val salt = prefs.getString(SALT, null) ?: return null
        return BackupCrypto.PasswordKey(SecretKeySpec(key.decode(), "AES"), salt.decode(), prefs.getInt(ITERATIONS, BackupCrypto.ITERATIONS))
    }

    // --- Backing up

    /**
     * Writes a Backup to the chosen folder. With [onlyIfChanged] (the daily job), nothing is
     * written when the data matches the last Backup, or when there's nothing recorded at all,
     * so an emptied app can never push real Backups out of the folder.
     */
    suspend fun backUp(onlyIfChanged: Boolean): BackupOutcome = lock.withLock {
        withContext(Dispatchers.IO) {
            val directory = backupDirectory() ?: return@withContext BackupOutcome.NotSetUp.also { refresh() }
            val snapshot = dao.snapshot()
            if (onlyIfChanged && snapshot.places.isEmpty()) return@withContext BackupOutcome.Empty
            val data = snapshot.toBackupData()
            val fingerprint = fingerprint(data)
            if (onlyIfChanged && fingerprint == prefs.getString(FINGERPRINT, null)) return@withContext BackupOutcome.Unchanged

            val outcome = runCatching {
                val file = directory.createFile(MIME, "$BACKUP_PREFIX${timestamp()}$EXTENSION") ?: error("Couldn't create file")
                requireNotNull(resolver.openOutputStream(file.uri)) { "Couldn't open file" }.use { write(it, data) }
                prune(directory)
            }
            prefs.edit {
                putBoolean(LAST_FAILED, outcome.isFailure)
                if (outcome.isSuccess) {
                    putLong(LAST_BACKUP, System.currentTimeMillis())
                    putString(FINGERPRINT, fingerprint)
                }
            }
            refresh()
            if (outcome.isSuccess) BackupOutcome.Written else BackupOutcome.Failed
        }
    }

    /** A fresh Backup in the share cache, for sending to another phone or a computer. */
    suspend fun shareableBackup(): File = withContext(Dispatchers.IO) {
        val file = File(shareDirectory(), "$BACKUP_PREFIX${timestamp()}$EXTENSION")
        file.outputStream().use { write(it, dao.snapshot().toBackupData()) }
        file
    }

    /** The spreadsheet Export, in the share cache. */
    suspend fun spreadsheet(): File = withContext(Dispatchers.IO) {
        val file = File(shareDirectory(), "thingspot-items-${timestamp()}.csv")
        file.writeText(InventoryCsv.write(dao.snapshot().toInventory()))
        file
    }

    private fun write(output: OutputStream, data: BackupData) {
        val manifest = BackupManifest(
            appVersion = appVersion,
            createdAt = System.currentTimeMillis(),
            places = data.places.size,
            items = data.items.size,
            categories = data.categories.size,
            photos = data.photos.size,
        )
        val key = passwordKey()
        if (key == null) {
            BackupFormat.write(output, manifest, data, photos::file)
            return
        }
        val plain = File.createTempFile("backup", ".zip", appContext.cacheDir)
        try {
            plain.outputStream().use { BackupFormat.write(it, manifest, data, photos::file) }
            plain.inputStream().use { BackupCrypto.encrypt(it, output, key) }
        } finally {
            plain.delete()
        }
    }

    private fun fingerprint(data: BackupData): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(BackupFormat.encodeData(data))
        digest.update(if (passwordKey() == null) PLAIN_MARK else ENCRYPTED_MARK)
        return digest.digest().encode()
    }

    private fun backupDirectory(): DocumentFile? {
        val tree = prefs.getString(FOLDER, null)?.let(Uri::parse) ?: return null
        if (!canWrite(tree)) return null
        val root = DocumentFile.fromTreeUri(appContext, tree) ?: return null
        if (root.name == FOLDER_INSIDE) return root
        return root.findFile(FOLDER_INSIDE)?.takeIf { it.isDirectory } ?: root.createDirectory(FOLDER_INSIDE)
    }

    private fun prune(directory: DocumentFile) {
        directory.listFiles()
            .filter { it.name.orEmpty().let { name -> name.startsWith(BACKUP_PREFIX) && name.endsWith(EXTENSION) } }
            .sortedByDescending { it.name }
            .drop(KEEP)
            .forEach { it.delete() }
    }

    private fun shareDirectory(): File = File(appContext.cacheDir, "share").apply {
        deleteRecursively()
        mkdirs()
    }

    // --- Restoring

    /** Reads a Backup the user picked. Encrypted ones need [password]. Nothing changes yet. */
    suspend fun open(source: Uri, password: CharArray?): OpenResult = withContext(Dispatchers.IO) {
        val directory = File(appContext.cacheDir, "restore").apply {
            deleteRecursively()
            mkdirs()
        }
        val incoming = File(directory, "incoming")
        val copied = runCatching {
            requireNotNull(resolver.openInputStream(source)).use { input -> incoming.outputStream().use { input.copyTo(it) } }
        }
        if (copied.isFailure) return@withContext OpenResult.Unusable(BackupProblem.NotABackup)

        val head = ByteArray(16).let { buffer -> buffer.copyOf(incoming.inputStream().use { it.read(buffer).coerceAtLeast(0) }) }
        val zip = if (BackupCrypto.isEncrypted(head)) {
            if (password == null) return@withContext OpenResult.NeedsPassword
            val plain = File(directory, "backup.zip")
            try {
                incoming.inputStream().use { input -> plain.outputStream().use { BackupCrypto.decrypt(input, it, password) } }
            } catch (_: BackupCrypto.WrongPassword) {
                return@withContext OpenResult.WrongPassword
            } catch (_: Exception) {
                return@withContext OpenResult.Unusable(BackupProblem.Damaged)
            }
            plain
        } else {
            incoming
        }
        try {
            OpenResult.Ready(BackupFormat.read(zip))
        } catch (e: BackupException) {
            OpenResult.Unusable(e.problem)
        }
    }

    /** Replaces everything with [contents], after saving a safety copy of the current data. */
    suspend fun restore(contents: BackupContents) = lock.withLock {
        withContext(Dispatchers.IO) {
            saveSafetyCopy()
            val data = contents.data
            for (name in data.photos.map { it.fileName }.distinct()) {
                val target = photos.file(name)
                if (!target.exists()) {
                    target.parentFile?.mkdirs()
                    BackupFormat.copyPhoto(contents.zip, name, target)
                }
            }
            db.withTransaction {
                dao.clearPhotos()
                dao.clearItems()
                dao.clearPlaces()
                dao.clearCategories()
                data.categories.forEach { dao.insertCategory(CategoryEntity(it.id, it.name, it.color, it.createdAt, it.updatedAt)) }
                parentsFirst(data.places).forEach {
                    dao.insertPlace(PlaceEntity(it.id, it.name, it.icon, it.parentId, it.createdAt, it.updatedAt))
                }
                data.items.forEach {
                    dao.insertItem(ItemEntity(it.id, it.name, it.placeId, it.categoryId, it.notes, it.createdAt, it.updatedAt))
                }
                data.photos.forEach { dao.insertPhoto(PhotoEntity(it.id, it.itemId, it.placeId, it.fileName, it.createdAt)) }
            }
            File(appContext.cacheDir, "restore").deleteRecursively()
            prefs.edit { remove(FINGERPRINT) }
        }
    }

    /** Before a restore replaces anything: a Backup of what's there now, in the folder or app storage. */
    private suspend fun saveSafetyCopy() {
        val snapshot = dao.snapshot()
        if (snapshot.places.isEmpty()) return
        val data = snapshot.toBackupData()
        val name = "$SAFETY_PREFIX${timestamp()}$EXTENSION"
        val directory = backupDirectory()
        if (directory != null) {
            val file = directory.createFile(MIME, name)
            val output = file?.let { resolver.openOutputStream(it.uri) }
            if (output != null) {
                output.use { write(it, data) }
                return
            }
        }
        val local = File(appContext.filesDir, "safety").apply { mkdirs() }
        File(local, name).outputStream().use { write(it, data) }
        local.listFiles()?.sortedByDescending { it.name }?.drop(SAFETY_KEEP)?.forEach { it.delete() }
    }

    private fun parentsFirst(places: List<BackupPlace>): List<BackupPlace> {
        val ordered = ArrayList<BackupPlace>(places.size)
        val placed = HashSet<String>()
        var remaining = places
        while (remaining.isNotEmpty()) {
            val (ready, waiting) = remaining.partition { it.parentId == null || it.parentId in placed }
            check(ready.isNotEmpty()) { "Places form a loop" }
            ordered += ready
            ready.mapTo(placed) { it.id }
            remaining = waiting
        }
        return ordered
    }

    // --- Status

    private fun canWrite(tree: Uri) = resolver.persistedUriPermissions.any { it.uri == tree && it.isWritePermission }

    private fun readStatus(): BackupStatus {
        val tree = prefs.getString(FOLDER, null)?.let(Uri::parse)
        val reachable = tree != null && canWrite(tree)
        return BackupStatus(
            folderName = if (tree != null) prefs.getString(FOLDER_NAME, null) ?: tree.toString() else null,
            folderLost = tree != null && !reachable,
            lastBackupAt = prefs.getLong(LAST_BACKUP, 0L).takeIf { it > 0 },
            lastAttemptFailed = prefs.getBoolean(LAST_FAILED, false),
            passwordSet = prefs.contains(KEY),
        )
    }

    private fun refresh() {
        mutableStatus.value = readStatus()
    }

    private companion object {
        const val KEEP = 7
        const val SAFETY_KEEP = 3
        const val FOLDER_INSIDE = "ThingSpot"
        const val BACKUP_PREFIX = "thingspot-backup-"
        const val SAFETY_PREFIX = "thingspot-before-restore-"
        const val EXTENSION = ".thingspot"
        const val MIME = "application/octet-stream"
        val PLAIN_MARK = byteArrayOf(0)
        val ENCRYPTED_MARK = byteArrayOf(1)
        val TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")

        const val FOLDER = "folder"
        const val FOLDER_NAME = "folder_name"
        const val LAST_BACKUP = "last_backup"
        const val LAST_FAILED = "last_failed"
        const val FINGERPRINT = "fingerprint"
        const val KEY = "key"
        const val SALT = "salt"
        const val ITERATIONS = "iterations"

        fun timestamp(): String = LocalDateTime.now().format(TIMESTAMP)

        fun ByteArray.encode(): String = Base64.encodeToString(this, Base64.NO_WRAP)

        fun String.decode(): ByteArray = Base64.decode(this, Base64.NO_WRAP)
    }
}

private fun Snapshot.toBackupData() = BackupData(
    places = places.map { BackupPlace(it.id, it.name, it.icon, it.parentId, it.createdAt, it.updatedAt) },
    items = items.map { BackupItem(it.id, it.name, it.placeId, it.categoryId, it.notes, it.createdAt, it.updatedAt) },
    categories = categories.map { BackupCategory(it.id, it.name, it.color, it.createdAt, it.updatedAt) },
    photos = photos.map { BackupPhoto(it.id, it.itemId, it.placeId, it.fileName, it.createdAt) },
)
