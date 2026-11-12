package com.arise.smartkitchenai.ui.waste

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arise.smartkitchenai.data.Ingredient
import com.arise.smartkitchenai.data.WasteEntry
import com.arise.smartkitchenai.data.WasteReason
import com.arise.smartkitchenai.ui.AppViewModelFactory
import com.arise.smartkitchenai.ui.components.DateField
import com.arise.smartkitchenai.ui.components.DropdownField
import com.arise.smartkitchenai.ui.components.EmptyState
import com.arise.smartkitchenai.ui.components.NumberField
import com.arise.smartkitchenai.ui.components.Stat
import com.arise.smartkitchenai.ui.components.SummaryCard
import com.arise.smartkitchenai.util.Dates
import com.arise.smartkitchenai.util.asMoney
import com.arise.smartkitchenai.util.asQuantity
import com.arise.smartkitchenai.util.toQuantityOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WasteScreen(viewModel: WasteViewModel = viewModel(factory = AppViewModelFactory.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<WasteEntry?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Food waste") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showDialog = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Log waste") },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 88.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SummaryCard(
                    listOf(
                        Stat("Lost (7 days)", state.last7DaysCost.asMoney(), highlight = state.last7DaysCost > 0),
                        Stat("Lost (30 days)", state.last30DaysCost.asMoney(), highlight = state.last30DaysCost > 0),
                        Stat("Top reason", state.topReason?.label ?: "—"),
                    )
                )
            }
            if (state.entries.isEmpty()) {
                item { EmptyState("No waste logged", "Record spoiled, expired or leftover food to see where money is lost.") }
            }
            items(state.entries, key = { it.id }) { entry ->
                WasteRow(entry, onDelete = { pendingDelete = entry })
            }
        }
    }

    if (showDialog) {
        LogWasteDialog(
            ingredients = state.ingredients,
            onDismiss = { showDialog = false },
            onSave = { id, qty, reason, day, note ->
                viewModel.record(id, qty, reason, day, note)
                showDialog = false
            },
        )
    }

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete waste record?") },
            text = { Text("This removes the record only. Stock that was deducted is not restored.") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(entry); pendingDelete = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun WasteRow(entry: WasteEntry, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${entry.ingredientName} — ${entry.quantity.asQuantity()} ${entry.unit}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    listOf(entry.reason.label, Dates.format(entry.epochDay), entry.note)
                        .filter { it.isNotBlank() }.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(entry.costLoss.asMoney(), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete record") }
        }
    }
}

@Composable
private fun LogWasteDialog(
    ingredients: List<Ingredient>,
    onDismiss: () -> Unit,
    onSave: (ingredientId: Long, quantity: Double, reason: WasteReason, epochDay: Long, note: String) -> Unit,
) {
    var ingredientId by rememberSaveable { mutableStateOf<Long?>(null) }
    var quantity by rememberSaveable { mutableStateOf("") }
    var reason by rememberSaveable { mutableStateOf(WasteReason.SPOILED) }
    var day by rememberSaveable { mutableStateOf(Dates.todayEpochDay()) }
    var note by rememberSaveable { mutableStateOf("") }
    var showErrors by remember { mutableStateOf(false) }

    val ingredient = ingredients.firstOrNull { it.id == ingredientId }
    val q = quantity.toQuantityOrNull()
    val valid = ingredient != null && q != null && q > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log waste") },
        text = {
            if (ingredients.isEmpty()) {
                Text("Add ingredients in the Inventory tab first.")
            } else {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DropdownField(
                        label = "Ingredient",
                        options = ingredients,
                        selected = ingredient,
                        optionLabel = { "${it.name} (${it.quantity.asQuantity()} ${it.unit} in stock)" },
                        onSelect = { ingredientId = it.id },
                        isError = showErrors && ingredient == null,
                    )
                    NumberField(
                        quantity, { quantity = it }, "Quantity wasted",
                        isError = showErrors && (q == null || q <= 0), suffix = ingredient?.unit,
                    )
                    if (ingredient != null && q != null && q > 0) {
                        Text(
                            "Estimated loss: ${(q * ingredient.unitCost).asMoney()}" +
                                if (q > ingredient.quantity) "\nMore than current stock — stock will be set to 0." else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    DropdownField(
                        label = "Reason",
                        options = WasteReason.entries,
                        selected = reason,
                        optionLabel = { it.label },
                        onSelect = { reason = it },
                    )
                    DateField("Date", day, { it?.let { d -> day = d } }, clearable = false)
                    OutlinedTextField(
                        value = note, onValueChange = { note = it }, label = { Text("Note (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            if (ingredients.isNotEmpty()) {
                TextButton(onClick = {
                    if (valid) onSave(ingredient!!.id, q!!, reason, day, note.trim()) else showErrors = true
                }) { Text("Save") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
