package com.candorfoods.factoryops.presentation.spareparts.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.candorfoods.factoryops.domain.model.spareparts.SparePartPick
import com.candorfoods.factoryops.domain.model.spareparts.SparePartPickerCatalog
import com.candorfoods.factoryops.presentation.common.components.PrimaryButton
import com.candorfoods.factoryops.presentation.common.components.PrimaryButtonSize
import com.candorfoods.factoryops.presentation.common.components.PrimaryButtonStyle
import com.candorfoods.factoryops.presentation.common.theme.FactoryTheme

/**
 * Pick a spare part off the shelf and say how many were used. Shared by the PM
 * execute screen and the breakdown ticket screen — the two places a technician
 * closes a job — so both log spares against real stock in exactly the same way.
 *
 * There is no free-text path: every line has to name a row in mt_spareparts,
 * because submitting is what decrements it. A part that isn't in the master yet is
 * added through [onAddNewPart], which opens the existing add-part form.
 *
 * The list is already warehouse-scoped by the server; this only groups it, putting
 * the parts fitted to the machine being worked on first. Quantities shown are the
 * last synced figures — the server re-checks at submit, so the worst a stale cache
 * can do is produce a clear "not enough stock" error there.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SparePartPickerSheet(
    catalog: SparePartPickerCatalog,
    isLoading: Boolean,
    onConfirm: (pick: SparePartPick, quantity: Double, notes: String?) -> Unit,
    onDismiss: () -> Unit,
    onAddNewPart: (() -> Unit)? = null,
) {
    var selected by remember { mutableStateOf<SparePartPick?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        val current = selected
        if (current == null) {
            PartList(
                catalog = catalog,
                isLoading = isLoading,
                onPick = { selected = it },
                onAddNewPart = onAddNewPart,
            )
        } else {
            QuantityForm(
                pick = current,
                onBack = { selected = null },
                onConfirm = { qty, notes -> onConfirm(current, qty, notes) },
            )
        }
    }
}

@Composable
private fun PartList(
    catalog: SparePartPickerCatalog,
    isLoading: Boolean,
    onPick: (SparePartPick) -> Unit,
    onAddNewPart: (() -> Unit)?,
) {
    var query by remember { mutableStateOf("") }
    val matching = catalog.items.filter { it.matches(query) }
    val (thisMachine, others) = matching.partition { it.forThisMachine }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Add spare used", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search parts") },
            placeholder = { Text("part or machine name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        when {
            isLoading && catalog.items.isEmpty() -> Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                horizontalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }

            // An error outranks emptiness: with a failed fetch the empty list is
            // just an empty cache, and claiming nothing is registered would send
            // the technician chasing the wrong problem.
            catalog.error != null -> ErrorNote(catalog.error, catalog.items.isNotEmpty())

            catalog.items.isEmpty() -> EmptyNote(
                "No spare parts are registered for ${catalog.label} yet."
            )

            matching.isEmpty() -> EmptyNote("No part matches \"$query\".")

            else -> LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (thisMachine.isNotEmpty()) {
                    item { GroupHeader("THIS MACHINE") }
                    items(thisMachine, key = { it.partId }) { PartRow(it, onPick) }
                }
                if (others.isNotEmpty()) {
                    item {
                        GroupHeader(
                            if (thisMachine.isEmpty()) "PARTS IN ${catalog.label}"
                            else "OTHER PARTS IN ${catalog.label}"
                        )
                    }
                    items(others, key = { it.partId }) { PartRow(it, onPick) }
                }
            }
        }

        if (onAddNewPart != null) {
            TextButton(onClick = onAddNewPart) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Part not listed? Add it to stock")
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun GroupHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = FactoryTheme.colors.onSurfaceMuted,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun ErrorNote(text: String, hasCachedRows: Boolean) {
    Column(
        modifier = Modifier.padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            if (hasCachedRows) "Showing the last synced list below — quantities may be out of date."
            else "Nothing is cached on this device yet, so there is nothing to show.",
            style = MaterialTheme.typography.bodySmall,
            color = FactoryTheme.colors.onSurfaceMuted,
        )
    }
}

@Composable
private fun EmptyNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = FactoryTheme.colors.onSurfaceMuted,
        modifier = Modifier.padding(vertical = 16.dp),
    )
}

@Composable
private fun PartRow(pick: SparePartPick, onPick: (SparePartPick) -> Unit) {
    // An out-of-stock part is shown rather than hidden: seeing "0 on hand" tells the
    // technician the part exists and needs restocking, which an absent row does not.
    val enabled = pick.inStock
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onPick(pick) }
            .alpha(if (enabled) 1f else 0.45f)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(pick.partName, fontWeight = FontWeight.SemiBold)
            Text(
                pick.machineName.orEmpty().ifBlank { "Unassigned" },
                style = MaterialTheme.typography.bodySmall,
                color = FactoryTheme.colors.onSurfaceMuted,
            )
        }
        if (pick.lowStock && enabled) {
            Box(
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.errorContainer,
                        RoundedCornerShape(percent = 50),
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    "LOW", fontSize = 10.sp, fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Spacer(Modifier.width(8.dp))
        }
        Text(
            if (enabled) "${pick.quantity} ${pick.unit.orEmpty()}".trim() else "out of stock",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun QuantityForm(
    pick: SparePartPick,
    onBack: () -> Unit,
    onConfirm: (Double, String?) -> Unit,
) {
    var qty by remember { mutableStateOf("1") }
    var notes by remember { mutableStateOf("") }

    // Whole numbers only: mt_spareparts.quantity is an integer column, and the
    // server rejects a fractional line rather than rounding it.
    val parsed = qty.toIntOrNull()
    val valid = parsed != null && parsed > 0 && parsed <= pick.quantity

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back to parts")
            }
            Column {
                Text(pick.partName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "${pick.quantity} ${pick.unit.orEmpty()} on hand".trim(),
                    style = MaterialTheme.typography.bodySmall,
                    color = FactoryTheme.colors.onSurfaceMuted,
                )
            }
        }

        OutlinedTextField(
            value = qty,
            onValueChange = { qty = it.filter(Char::isDigit).take(4) },
            label = { Text("Quantity used") },
            suffix = { Text(pick.unit.orEmpty()) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            isError = qty.isNotBlank() && !valid,
            supportingText = {
                if (parsed != null && parsed > pick.quantity) {
                    Text("Only ${pick.quantity} on hand")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes (optional)") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )

        PrimaryButton(
            text = "Add",
            onClick = { onConfirm(parsed!!.toDouble(), notes.takeIf { it.isNotBlank() }) },
            enabled = valid,
            style = PrimaryButtonStyle.Success,
            size = PrimaryButtonSize.Primary,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
    }
}
