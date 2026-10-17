package io.github.bijojp.thingspot.data.backup

import io.github.bijojp.thingspot.domain.LabelCode
import io.github.bijojp.thingspot.domain.Limits
import io.github.bijojp.thingspot.domain.cleanName
import io.github.bijojp.thingspot.domain.cleanNotes
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

// A Backup is a plain ZIP (documented in docs/backup-format.md):
//   manifest.json   what and when
//   data.json       Places, Items, Categories and photo records
//   photos/<name>   the photo files
// With a password, the whole ZIP is wrapped by BackupCrypto.

@Serializable
data class BackupManifest(
    val format: String = FORMAT_NAME,
    val formatVersion: Int = FORMAT_VERSION,
    val appVersion: String,
    val createdAt: Long,
    val places: Int,
    val items: Int,
    val categories: Int,
    val photos: Int,
)

@Serializable
data class BackupData(
    val places: List<BackupPlace>,
    val items: List<BackupItem>,
    val categories: List<BackupCategory>,
    val photos: List<BackupPhoto>,
    val labels: List<BackupLabel> = emptyList(),
)

@Serializable
data class BackupPlace(
    val id: String,
    val name: String,
    val icon: String,
    val parentId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class BackupItem(
    val id: String,
    val name: String,
    val placeId: String,
    val categoryId: String? = null,
    val notes: String = "",
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class BackupCategory(
    val id: String,
    val name: String,
    val color: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class BackupPhoto(
    val id: String,
    val itemId: String? = null,
    val placeId: String? = null,
    val fileName: String,
    val createdAt: Long,
)

@Serializable
data class BackupLabel(
    val code: String,
    val placeId: String,
    val createdAt: Long,
)

const val FORMAT_NAME = "thingspot-backup"
const val FORMAT_VERSION = 1

/** Why a file can't be restored. */
enum class BackupProblem { NotABackup, NewerVersion, Damaged }

class BackupException(val problem: BackupProblem, message: String) : Exception(message)

/** A Backup that has been read and checked, ready to restore. */
class BackupContents(val manifest: BackupManifest, val data: BackupData, val zip: File)

object BackupFormat {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    /** The data.json bytes; stable for the same data, so it doubles as a change fingerprint. */
    fun encodeData(data: BackupData): ByteArray = json.encodeToString(BackupData.serializer(), data.sorted()).toByteArray()

    fun write(output: OutputStream, manifest: BackupManifest, data: BackupData, photoFile: (String) -> File?) {
        ZipOutputStream(output.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(json.encodeToString(BackupManifest.serializer(), manifest).toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(DATA))
            zip.write(encodeData(data))
            zip.closeEntry()
            for (photo in data.photos.map { it.fileName }.distinct()) {
                val file = photoFile(photo)?.takeIf { it.isFile } ?: continue
                zip.putNextEntry(ZipEntry(PHOTOS + photo))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /** Reads and checks a plain (decrypted) Backup ZIP, repairing what can safely be repaired. */
    fun read(zipFile: File): BackupContents {
        val zip = try {
            ZipFile(zipFile)
        } catch (e: java.util.zip.ZipException) {
            throw BackupException(BackupProblem.NotABackup, "Not a ZIP file: ${e.message}")
        }
        zip.use {
            val manifestEntry = it.getEntry(MANIFEST) ?: throw BackupException(BackupProblem.NotABackup, "No manifest")
            val manifest = decode(BackupManifest.serializer(), it.getInputStream(manifestEntry))
            if (manifest.format != FORMAT_NAME) throw BackupException(BackupProblem.NotABackup, "Unknown format ${manifest.format}")
            if (manifest.formatVersion > FORMAT_VERSION) {
                throw BackupException(BackupProblem.NewerVersion, "Format ${manifest.formatVersion} is newer than $FORMAT_VERSION")
            }
            val dataEntry = it.getEntry(DATA) ?: throw BackupException(BackupProblem.Damaged, "No data")
            val data = decode(BackupData.serializer(), it.getInputStream(dataEntry))
            val photosInZip = it.entries().asSequence().map { e -> e.name }
                .filter { name -> name.startsWith(PHOTOS) }
                .map { name -> name.removePrefix(PHOTOS) }
                .toSet()
            return BackupContents(manifest, checked(data, photosInZip), zipFile)
        }
    }

    /** Copies one photo out of a Backup ZIP. */
    fun copyPhoto(zipFile: File, fileName: String, target: File) {
        require(isSafeFileName(fileName)) { "Unsafe photo name" }
        ZipFile(zipFile).use { zip ->
            val entry = zip.getEntry(PHOTOS + fileName) ?: return
            zip.getInputStream(entry).use { input -> target.outputStream().use { input.copyTo(it) } }
        }
    }

    /**
     * Makes a Backup safe to load: unique IDs, cleaned names, Places whose parent is missing or
     * that form a loop become top-level, unknown Categories are dropped from Items, and photos
     * without an owner or a file are skipped. An Item without a Place means the file is damaged.
     */
    internal fun checked(data: BackupData, photosInZip: Set<String>): BackupData {
        val categories = data.categories.distinctBy { it.id }
            .map { it.copy(name = cleanName(it.name, Limits.CATEGORY_NAME) ?: UNNAMED) }
        val categoryIds = categories.map { it.id }.toSet()

        val rawPlaces = data.places.distinctBy { it.id }.associateBy { it.id }
        val places = rawPlaces.values.map { place ->
            val parentOk = place.parentId != null && place.parentId in rawPlaces && !loops(place.id, rawPlaces)
            place.copy(
                name = cleanName(place.name, Limits.PLACE_NAME) ?: UNNAMED,
                icon = place.icon.ifBlank { "📦" },
                parentId = if (parentOk) place.parentId else null,
            )
        }
        val placeIds = places.map { it.id }.toSet()

        val items = data.items.distinctBy { it.id }.map { item ->
            if (item.placeId !in placeIds) throw BackupException(BackupProblem.Damaged, "Item ${item.id} has no Place")
            item.copy(
                name = cleanName(item.name, Limits.ITEM_NAME) ?: UNNAMED,
                categoryId = item.categoryId?.takeIf { it in categoryIds },
                notes = cleanNotes(item.notes),
            )
        }
        val itemIds = items.map { it.id }.toSet()

        val photos = data.photos.distinctBy { it.id }.filter { photo ->
            val ownerOk = (photo.itemId != null && photo.itemId in itemIds) != (photo.placeId != null && photo.placeId in placeIds)
            ownerOk && isSafeFileName(photo.fileName) && photo.fileName in photosInZip
        }
        val labels = data.labels.distinctBy { it.code }.filter { LabelCode.parse(it.code)?.value == it.code && it.placeId in placeIds }
        return BackupData(places, items, categories, photos, labels)
    }

    /** Whether following parents from [placeId] ever comes back to it. */
    private fun loops(placeId: String, places: Map<String, BackupPlace>): Boolean {
        val seen = HashSet<String>()
        var current = places[placeId]?.parentId
        while (current != null) {
            if (current == placeId || !seen.add(current)) return true
            current = places[current]?.parentId
        }
        return false
    }

    /** Photo names are generated UUIDs; anything with a path in it is refused (no zip-slip). */
    fun isSafeFileName(name: String): Boolean = name.matches(SAFE_NAME)

    private fun <T> decode(serializer: kotlinx.serialization.KSerializer<T>, input: InputStream): T = try {
        input.use { json.decodeFromString(serializer, it.readBytes().decodeToString()) }
    } catch (e: kotlinx.serialization.SerializationException) {
        throw BackupException(BackupProblem.Damaged, "Unreadable JSON: ${e.message}")
    } catch (e: IllegalArgumentException) {
        throw BackupException(BackupProblem.Damaged, "Unreadable JSON: ${e.message}")
    }

    private fun BackupData.sorted() = BackupData(
        places = places.sortedBy { it.id },
        items = items.sortedBy { it.id },
        categories = categories.sortedBy { it.id },
        photos = photos.sortedBy { it.id },
        labels = labels.sortedBy { it.code },
    )

    private const val MANIFEST = "manifest.json"
    private const val DATA = "data.json"
    private const val PHOTOS = "photos/"
    private const val UNNAMED = "Unnamed"
    private val SAFE_NAME = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
}
