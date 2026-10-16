package io.github.bijojp.thingspot.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class InventoryTest {
    private var clock = 0L

    private fun place(id: String, parent: String? = null, name: String = id) =
        Place(PlaceId(id), name, "📦", parent?.let(::PlaceId), ++clock, clock)

    private fun item(id: String, place: String, name: String = id, category: String? = null, notes: String = "") =
        Item(ItemId(id), name, PlaceId(place), category?.let(::CategoryId), notes, ++clock, clock)

    private fun category(id: String, name: String = id) = Category(CategoryId(id), name, 0xFF5B9CF5, ++clock, clock)

    private val home = place("home", name = "Home")
    private val garage = place("garage", parent = "home", name = "Garage")
    private val toolbox = place("toolbox", parent = "garage", name = "Toolbox")
    private val kitchen = place("kitchen", parent = "home", name = "Kitchen")
    private val office = place("office", name = "Office")

    private fun inventory(vararg items: Item, categories: List<Category> = emptyList()) =
        Inventory(listOf(home, garage, toolbox, kitchen, office), items.toList(), categories)

    private fun names(results: List<SearchResult>) = results.map {
        when (it) {
            is SearchResult.ItemMatch -> it.item.name
            is SearchResult.PlaceMatch -> it.place.name
        }
    }

    @Test
    fun `path runs from the top level down`() {
        assertEquals(listOf("Home", "Garage", "Toolbox"), inventory().path(toolbox.id).map { it.name })
    }

    @Test
    fun `a Place can't move inside itself or its sub-places`() {
        val inventory = inventory()
        assertFalse(inventory.canMove(garage.id, garage.id))
        assertFalse(inventory.canMove(garage.id, toolbox.id))
        assertTrue(inventory.canMove(garage.id, office.id))
        assertTrue(inventory.canMove(toolbox.id, null))
    }

    @Test
    fun `item counts include sub-places`() {
        val inventory = inventory(item("drill", "toolbox"), item("ladder", "garage"), item("pan", "kitchen"))
        assertEquals(3, inventory.itemCountWithin(home.id))
        assertEquals(2, inventory.itemCountWithin(garage.id))
        assertEquals(1, inventory.itemsIn(garage.id).size)
    }

    @Test
    fun `tree lists Places depth first, alphabetically, with depth`() {
        val rows = inventory().tree().map { "${"-".repeat(it.depth)}${it.place.name}" }
        assertEquals(listOf("Home", "-Garage", "--Toolbox", "-Kitchen", "Office"), rows)
    }

    @Test
    fun `deleting a nested Place moves its contents up to the parent`() {
        val deletion = inventory(item("ladder", "garage")).deletionOf(garage.id)!!
        assertEquals(listOf(toolbox), deletion.childPlaces)
        assertEquals(ItemsGoTo.Parent(home.id), deletion.itemsGoTo)
        assertEquals(home.id, deletion.itemDestination(choice = null))
    }

    @Test
    fun `deleting a top-level Place with Items lets the user choose any other Place`() {
        val deletion = inventory(item("lamp", "home")).deletionOf(home.id)!!
        val goTo = deletion.itemsGoTo as ItemsGoTo.UserChoice
        assertFalse(home.id in goTo.candidates)
        assertTrue(garage.id in goTo.candidates)
        assertFalse(deletion.isReady(choice = null))
        assertTrue(deletion.isReady(choice = office.id))
        assertEquals(office.id, deletion.itemDestination(office.id))
        assertThrows(IllegalArgumentException::class.java) { deletion.itemDestination(home.id) }
    }

    @Test
    fun `deleting the only Place while it holds Items is blocked`() {
        val only = place("only")
        val deletion = Inventory(listOf(only), listOf(item("x", "only")), emptyList()).deletionOf(only.id)!!
        assertEquals(ItemsGoTo.NoPlaceLeft, deletion.itemsGoTo)
        assertFalse(deletion.isReady(choice = null))
    }

    @Test
    fun `deleting an empty Place needs no destination`() {
        val deletion = inventory().deletionOf(office.id)!!
        assertEquals(ItemsGoTo.Nowhere, deletion.itemsGoTo)
        assertNull(deletion.itemDestination(choice = null))
    }

    @Test
    fun `search ignores case and accents`() {
        val inventory = inventory(item("receipts", "office", name = "Café receipts"))
        assertEquals(listOf("Café receipts"), names(inventory.search("CAFE")))
    }

    @Test
    fun `search ranks name matches above notes matches`() {
        val inventory = inventory(
            item("cable", "office", name = "Phone cable"),
            item("box", "garage", name = "Spare box", notes = "has a phone charger"),
        )
        assertEquals(listOf("Phone cable", "Spare box"), names(inventory.search("phone")))
    }

    @Test
    fun `search words can match the Places an Item sits in`() {
        val inventory = inventory(item("drill", "toolbox", name = "Drill"), item("drill2", "office", name = "Drill bits"))
        assertEquals(listOf("Drill"), names(inventory.search("garage drill")))
    }

    @Test
    fun `search words can match the Category`() {
        val inventory = inventory(
            item("passport", "office", name = "Passport", category = "docs"),
            categories = listOf(category("docs", name = "Documents")),
        )
        assertEquals(listOf("Passport"), names(inventory.search("documents")))
    }

    @Test
    fun `search finds Places too, Items first on a tie`() {
        val inventory = inventory(item("toolbox-key", "office", name = "Toolbox"))
        assertEquals(listOf("Toolbox", "Toolbox"), names(inventory.search("toolbox")))
        assertTrue(inventory.search("toolbox").first() is SearchResult.ItemMatch)
    }

    @Test
    fun `blank search returns nothing`() {
        assertTrue(inventory(item("a", "home")).search("   ").isEmpty())
    }

    @Test
    fun `recent items are newest first`() {
        val inventory = inventory(item("old", "home"), item("new", "home"))
        assertEquals(listOf("new", "old"), inventory.recentItems(10).map { it.name })
    }

    @Test
    fun `names are trimmed, collapsed and never blank`() {
        assertEquals("Kitchen shelf", cleanName("  Kitchen   shelf ", Limits.PLACE_NAME))
        assertNull(cleanName("   ", Limits.PLACE_NAME))
        assertEquals(3, cleanName("abcdef", 3)!!.length)
    }
}
