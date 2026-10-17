package io.github.bijojp.thingspot.data.backup

import io.github.bijojp.thingspot.domain.Category
import io.github.bijojp.thingspot.domain.CategoryId
import io.github.bijojp.thingspot.domain.Inventory
import io.github.bijojp.thingspot.domain.Item
import io.github.bijojp.thingspot.domain.ItemId
import io.github.bijojp.thingspot.domain.Place
import io.github.bijojp.thingspot.domain.PlaceId
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.ZoneOffset
import kotlin.random.Random

class BackupTest {
    @get:Rule
    val temp = TemporaryFolder()

    // Fewer PBKDF2 rounds keep the tests fast; the format records the count either way.
    private fun key(password: String) = BackupCrypto.newPasswordKey(password.toCharArray(), iterations = 1_000)

    private fun encrypt(plain: ByteArray, password: String): ByteArray =
        ByteArrayOutputStream().also { BackupCrypto.encrypt(ByteArrayInputStream(plain), it, key(password)) }.toByteArray()

    private fun decrypt(sealed: ByteArray, password: String): ByteArray =
        ByteArrayOutputStream().also { BackupCrypto.decrypt(ByteArrayInputStream(sealed), it, password.toCharArray()) }.toByteArray()

    @Test
    fun `encryption round-trips small, empty and multi-chunk data`() {
        for (size in listOf(0, 10, (1 shl 20), (1 shl 20) + 1, 3 * (1 shl 20) + 7)) {
            val plain = Random(size).nextBytes(size)
            val sealed = encrypt(plain, "correct horse")
            assertTrue(BackupCrypto.isEncrypted(sealed))
            assertArrayEquals("size $size", plain, decrypt(sealed, "correct horse"))
        }
    }

    @Test
    fun `wrong password is refused`() {
        val sealed = encrypt("secret".toByteArray(), "right")
        assertThrows(BackupCrypto.WrongPassword::class.java) { decrypt(sealed, "wrong") }
    }

    @Test
    fun `tampering and truncation are detected`() {
        val plain = Random(1).nextBytes(2 * (1 shl 20) + 5)
        val sealed = encrypt(plain, "pw")
        val flipped = sealed.copyOf().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 1).toByte() }
        assertThrows(BackupCrypto.WrongPassword::class.java) { decrypt(flipped, "pw") }
        // Drop the final chunk: the new last chunk wasn't sealed as "last", so it must fail.
        val lastChunk = 4 + 5 + 16
        assertThrows(BackupCrypto.WrongPassword::class.java) { decrypt(sealed.copyOf(sealed.size - lastChunk), "pw") }
    }

    // Lists are in ID order, the order a backup is written in.
    private val data = BackupData(
        places = listOf(
            BackupPlace("box", "Box", "📦", "home", 2, 2),
            BackupPlace("home", "Home", "🏠", null, 1, 1),
        ),
        items = listOf(BackupItem("passport", "Passport", "box", "docs", "blue cover", 3, 3)),
        categories = listOf(BackupCategory("docs", "Documents", 0xFF5B9CF5, 4, 4)),
        photos = listOf(BackupPhoto("p1", itemId = "passport", fileName = "a1.jpg", createdAt = 5)),
    )

    @Test
    fun `a backup ZIP round-trips data and photos`() {
        val photo = temp.newFile("a1.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val zip = temp.newFile("backup.zip")
        val manifest = BackupManifest(appVersion = "test", createdAt = 9, places = 2, items = 1, categories = 1, photos = 1)
        zip.outputStream().use { BackupFormat.write(it, manifest, data) { name -> if (name == "a1.jpg") photo else null } }

        val contents = BackupFormat.read(zip)
        assertEquals(manifest, contents.manifest)
        assertEquals(data, contents.data)
        val copied = temp.newFile("copied.jpg")
        BackupFormat.copyPhoto(zip, "a1.jpg", copied)
        assertArrayEquals(byteArrayOf(1, 2, 3), copied.readBytes())
    }

    @Test
    fun `the data fingerprint is stable regardless of order`() {
        val shuffled = data.copy(places = data.places.reversed())
        assertArrayEquals(BackupFormat.encodeData(data), BackupFormat.encodeData(shuffled))
    }

    @Test
    fun `checking repairs loops and missing parents, and drops bad photos`() {
        val messy = data.copy(
            places = listOf(
                BackupPlace("a", "A", "", "b", 1, 1),
                BackupPlace("b", "  ", "📦", "a", 1, 1),
                BackupPlace("c", "C", "📦", "gone", 1, 1),
            ),
            items = listOf(BackupItem("i", "Item", "a", categoryId = "unknown", createdAt = 1, updatedAt = 1)),
            categories = emptyList(),
            photos = listOf(
                BackupPhoto("p1", itemId = "i", fileName = "ok.jpg", createdAt = 1),
                BackupPhoto("p2", itemId = "i", fileName = "../evil.jpg", createdAt = 1),
                BackupPhoto("p3", placeId = "a", fileName = "missing.jpg", createdAt = 1),
            ),
        )
        val checked = BackupFormat.checked(messy, photosInZip = setOf("ok.jpg", "../evil.jpg"))
        assertTrue(checked.places.all { it.parentId == null })
        assertEquals("Unnamed", checked.places.first { it.id == "b" }.name)
        assertEquals("📦", checked.places.first { it.id == "a" }.icon)
        assertNull(checked.items.single().categoryId)
        assertEquals(listOf("p1"), checked.photos.map { it.id })
    }

    @Test
    fun `an Item without a Place means the backup is damaged`() {
        val broken = data.copy(items = listOf(BackupItem("i", "Item", "nowhere", createdAt = 1, updatedAt = 1)))
        val error = assertThrows(BackupException::class.java) { BackupFormat.checked(broken, emptySet()) }
        assertEquals(BackupProblem.Damaged, error.problem)
    }

    @Test
    fun `a newer backup format is refused`() {
        val zip = temp.newFile("future.zip")
        val manifest = BackupManifest(formatVersion = FORMAT_VERSION + 1, appVersion = "x", createdAt = 1, places = 0, items = 0, categories = 0, photos = 0)
        zip.outputStream().use { BackupFormat.write(it, manifest, BackupData(emptyList(), emptyList(), emptyList(), emptyList())) { null } }
        val error = assertThrows(BackupException::class.java) { BackupFormat.read(zip) }
        assertEquals(BackupProblem.NewerVersion, error.problem)
    }

    @Test
    fun `a file that isn't a ZIP is not a backup`() {
        val file = temp.newFile("notes.txt").apply { writeText("hello") }
        assertEquals(BackupProblem.NotABackup, assertThrows(BackupException::class.java) { BackupFormat.read(file) }.problem)
    }

    @Test
    fun `photo names with paths are unsafe`() {
        assertTrue(BackupFormat.isSafeFileName("3f2a-1.jpg"))
        assertFalse(BackupFormat.isSafeFileName("../x.jpg"))
        assertFalse(BackupFormat.isSafeFileName("photos/x.jpg"))
        assertFalse(BackupFormat.isSafeFileName(".hidden"))
    }

    @Test
    fun `CSV export quotes, neutralises formulas and shows the path`() {
        val inventory = Inventory(
            places = listOf(Place(PlaceId("h"), "Home", "🏠", null, 0, 0), Place(PlaceId("d"), "Desk", "🪑", PlaceId("h"), 0, 0)),
            items = listOf(Item(ItemId("i"), "Cable, USB-C", PlaceId("d"), CategoryId("c"), "=SUM(1)", 0, 0)),
            categories = listOf(Category(CategoryId("c"), "Tech", 0, 0, 0)),
        )
        val lines = InventoryCsv.write(inventory, ZoneOffset.UTC).removePrefix("﻿").split("\r\n")
        assertEquals("Item,Where,Category,Notes,Added", lines[0])
        assertEquals("\"Cable, USB-C\",Home > Desk,Tech,'=SUM(1),1970-01-01", lines[1])
    }
}
