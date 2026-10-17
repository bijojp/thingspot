package io.github.bijojp.thingspot.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LabelTest {
    private val home = Place(PlaceId("home"), "Home", "🏠", null, 0, 0)
    private val box = Place(PlaceId("box"), "Box", "📦", PlaceId("home"), 0, 0)

    @Test
    fun `codes avoid look-alike characters and round-trip through the QR content`() {
        repeat(200) {
            val code = LabelCode.random(Random(it))
            assertTrue(code.value.none { c -> c in "01ILO" })
            assertEquals(code, LabelCode.fromQr(code.qrContent))
        }
    }

    @Test
    fun `the QR content stays in compact alphanumeric mode`() {
        val content = LabelCode("K7Q3MX9P").qrContent
        assertEquals("THINGSPOT:L:K7Q3MX9P", content)
        assertTrue(content.all { it.isUpperCase() || it.isDigit() || it == ':' })
    }

    @Test
    fun `typed codes are forgiving, other QR codes are not Labels`() {
        assertEquals(LabelCode("K7Q3MX9P"), LabelCode.parse("k7q3-mx9p"))
        assertEquals("K7Q3-MX9P", LabelCode("K7Q3MX9P").display)
        assertNull(LabelCode.parse("K7Q3MX9"))
        assertNull(LabelCode.parse("K7Q3MX9O"))
        assertNull(LabelCode.fromQr("https://example.com"))
    }

    @Test
    fun `a scanned Label finds its Place, and a Place can have several`() {
        val front = Label(LabelCode("AAAA2222"), box.id, 1)
        val side = Label(LabelCode("BBBB3333"), box.id, 2)
        val inventory = Inventory(listOf(home, box), emptyList(), emptyList(), listOf(side, front))
        assertEquals(box, inventory.placeFor(front.code))
        assertNull(inventory.placeFor(LabelCode("CCCC4444")))
        assertEquals(listOf(front, side), inventory.labelsOn(box.id))
    }

    @Test
    fun `deleting a Place says which Labels become blank`() {
        val label = Label(LabelCode("AAAA2222"), box.id, 1)
        val inventory = Inventory(listOf(home, box), emptyList(), emptyList(), listOf(label))
        assertEquals(listOf(label), inventory.deletionOf(box.id)!!.labels)
    }
}
