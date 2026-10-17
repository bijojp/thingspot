package io.github.bijojp.thingspot.ui.labels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bijojp.thingspot.R
import io.github.bijojp.thingspot.StoreProGate
import io.github.bijojp.thingspot.domain.Inventory
import io.github.bijojp.thingspot.domain.LabelCode
import io.github.bijojp.thingspot.domain.Place
import io.github.bijojp.thingspot.domain.PlaceId
import io.github.bijojp.thingspot.ui.InventoryViewModel
import io.github.bijojp.thingspot.ui.components.PlaceEditorDialog
import io.github.bijojp.thingspot.ui.components.PlacePickerDialog

private enum class BlankStep { Choose, PickExisting, PickParent, NamePlace }

/** A scanned Label that isn't linked yet: make a new Place for it, or link an existing one. */
@Composable
fun BlankLabelDialog(code: LabelCode, inventory: Inventory, viewModel: InventoryViewModel, onLinked: (PlaceId) -> Unit, onDismiss: () -> Unit) {
    var step by rememberSaveable { mutableStateOf(BlankStep.Choose) }
    var parent by rememberSaveable { mutableStateOf<String?>(null) }

    when (step) {
        BlankStep.Choose -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.label_blank_title, code.display)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.label_blank_body))
                    FilledTonalButton(
                        onClick = { step = if (inventory.places.isEmpty()) BlankStep.NamePlace else BlankStep.PickParent },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.label_blank_new_place)) }
                    if (inventory.places.isNotEmpty()) {
                        OutlinedButton(onClick = { step = BlankStep.PickExisting }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.label_blank_existing))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        )
        BlankStep.PickExisting -> PlacePickerDialog(
            inventory = inventory,
            title = stringResource(R.string.label_link_title),
            selected = null,
            onDismiss = { step = BlankStep.Choose },
            onPick = { picked ->
                if (picked != null) {
                    viewModel.linkLabel(code, picked)
                    onLinked(picked)
                }
            },
        )
        BlankStep.PickParent -> PlacePickerDialog(
            inventory = inventory,
            title = stringResource(R.string.label_new_place_parent),
            selected = null,
            onDismiss = { step = BlankStep.Choose },
            onPick = { picked ->
                parent = picked?.value
                step = BlankStep.NamePlace
            },
            allowTopLevel = true,
        )
        BlankStep.NamePlace -> PlaceEditorDialog(
            initial = null,
            viewModel = viewModel,
            onDismiss = { step = BlankStep.Choose },
            onSave = { name, icon, photo ->
                viewModel.addPlace(name, icon, parent?.let(::PlaceId), photo) { id ->
                    viewModel.linkLabel(code, id)
                    onLinked(id)
                }
            },
        )
    }
}

/** How many Labels to print, for [place] or blank; codes are made (and linked) before printing. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrintLabelsDialog(place: Place?, viewModel: InventoryViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val options = if (place == null) listOf(3, 6, 21, 42) else listOf(1, 2, 3, 4)
    var count by rememberSaveable { mutableIntStateOf(options.first()) }
    val jobName = stringResource(R.string.label_print_job)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (place == null) stringResource(R.string.label_print_blank_title) else stringResource(R.string.label_print_title, place.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(if (place == null) R.string.label_print_blank_body else R.string.label_print_body))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { option ->
                        FilterChip(selected = option == count, onClick = { count = option }, label = { Text(option.toString()) })
                    }
                }
                Text(stringResource(R.string.label_print_paper))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.prepareLabels(place?.id, count) { codes ->
                    printLabels(context, jobName, codes.map { PrintableLabel(it, place?.name) })
                }
                onDismiss()
            }) { Text(stringResource(R.string.label_print_action)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * Runs Pro-only actions (QR Labels) when Pro is unlocked, and otherwise explains Pro.
 * Always unlocked in the F-Droid build (ADR 0005).
 */
@Composable
fun rememberRequirePro(): (action: () -> Unit) -> Unit {
    val unlocked by StoreProGate.isUnlocked.collectAsStateWithLifecycle()
    var explaining by rememberSaveable { mutableStateOf(false) }
    if (explaining) {
        AlertDialog(
            onDismissRequest = { explaining = false },
            title = { Text(stringResource(R.string.pro_title)) },
            text = { Text(stringResource(R.string.pro_body)) },
            confirmButton = { TextButton(onClick = { explaining = false }) { Text(stringResource(R.string.action_ok)) } },
        )
    }
    return { action -> if (unlocked) action() else explaining = true }
}
