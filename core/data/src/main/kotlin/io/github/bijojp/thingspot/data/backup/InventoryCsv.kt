package io.github.bijojp.thingspot.data.backup

import io.github.bijojp.thingspot.domain.Inventory
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The spreadsheet Export: one row per Item, with where it is. One-way; it can't be restored. */
object InventoryCsv {
    private val dates = DateTimeFormatter.ISO_LOCAL_DATE

    fun write(inventory: Inventory, zone: ZoneId = ZoneId.systemDefault()): String = buildString {
        // The byte-order mark makes Excel read the file as UTF-8 (accents, emoji).
        append('﻿')
        appendRow(listOf("Item", "Where", "Category", "Notes", "Added"))
        for (item in inventory.items) {
            appendRow(
                listOf(
                    item.name,
                    inventory.path(item.placeId).joinToString(" > ") { it.name },
                    item.categoryId?.let(inventory::category)?.name.orEmpty(),
                    item.notes,
                    dates.format(Instant.ofEpochMilli(item.createdAt).atZone(zone)),
                ),
            )
        }
    }

    private fun StringBuilder.appendRow(cells: List<String>) {
        cells.joinTo(this, separator = ",") { escape(it) }
        append("\r\n")
    }

    /** RFC 4180 quoting, plus a leading apostrophe so spreadsheets never run a cell as a formula. */
    internal fun escape(cell: String): String {
        val safe = if (cell.isNotEmpty() && cell[0] in "=+-@") "'$cell" else cell
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + safe.replace("\"", "\"\"") + "\""
        } else {
            safe
        }
    }
}
