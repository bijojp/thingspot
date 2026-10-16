package io.github.bijojp.thingspot.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bijojp.thingspot.domain.Category
import io.github.bijojp.thingspot.domain.Inventory
import io.github.bijojp.thingspot.domain.Item
import io.github.bijojp.thingspot.domain.Place
import io.github.bijojp.thingspot.domain.PlaceId

/** "Home → Garage → Toolbox". */
fun Inventory.pathLabel(placeId: PlaceId): String = path(placeId).joinToString(" → ") { it.name }

/** An Item with where it is. */
@Composable
fun ItemRow(item: Item, inventory: Inventory, onClick: () -> Unit, showPath: Boolean = true) {
    val category = item.categoryId?.let(inventory::category)
    val supporting = if (showPath) inventory.pathLabel(item.placeId) else category?.name
    ListItem(
        headlineContent = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = supporting?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        leadingContent = { CategoryDot(category) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** A Place with how many Items are inside it, optionally with the Places it sits in. */
@Composable
fun PlaceRow(place: Place, inventory: Inventory, onClick: () -> Unit, showPath: Boolean = false) {
    val count = inventory.itemCountWithin(place.id)
    val parents = if (showPath) inventory.path(place.id).dropLast(1).joinToString(" → ") { it.name } else ""
    ListItem(
        headlineContent = { Text(place.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = parents.takeIf { it.isNotEmpty() }?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        leadingContent = { PlaceIcon(place.icon) },
        trailingContent = if (count > 0) {
            { Text(count.toString(), style = MaterialTheme.typography.labelLarge) }
        } else {
            null
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
fun PlaceIcon(icon: String, modifier: Modifier = Modifier) {
    Box(modifier.size(40.dp), contentAlignment = Alignment.Center) {
        Text(icon, fontSize = 24.sp)
    }
}

/** A Category's colour, or an empty space of the same size, so rows line up. */
@Composable
fun CategoryDot(category: Category?) {
    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
        if (category != null) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(Color(category.color)))
        }
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
fun EmptyHint(title: String, body: String? = null) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (body != null) {
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
