package com.arise.smartkitchenai.ui.expiry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arise.smartkitchenai.data.Ingredient
import com.arise.smartkitchenai.data.stockValue
import com.arise.smartkitchenai.ui.AppViewModelFactory
import com.arise.smartkitchenai.ui.components.EmptyState
import com.arise.smartkitchenai.ui.components.Stat
import com.arise.smartkitchenai.ui.components.SummaryCard
import com.arise.smartkitchenai.util.Dates
import com.arise.smartkitchenai.util.asMoney
import com.arise.smartkitchenai.util.asQuantity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpiryScreen(viewModel: ExpiryViewModel = viewModel(factory = AppViewModelFactory.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmWriteOff by remember { mutableStateOf<Ingredient?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text("Expiry tracker") }) }) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SummaryCard(
                    listOf(
                        Stat("Expired", (state.groups[ExpiryGroup.EXPIRED]?.size ?: 0).toString(),
                            highlight = state.groups.containsKey(ExpiryGroup.EXPIRED)),
                        Stat("≤ 3 days", (state.groups[ExpiryGroup.CRITICAL]?.size ?: 0).toString()),
                        Stat("Value at risk", state.valueAtRisk.asMoney(), highlight = state.valueAtRisk > 0),
                    )
                )
            }
            if (state.groups.isEmpty()) {
                item { EmptyState("Nothing to track", "Set expiry dates on ingredients in the Inventory tab.") }
            }
            state.groups.forEach { (group, items) ->
                item(key = "header-${group.name}") {
                    Text(
                        group.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = groupColor(group),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(items, key = { it.ingredient.id }) { item ->
                    ExpiryRow(item, group, onWriteOff = { confirmWriteOff = item.ingredient })
                }
            }
        }
    }

    confirmWriteOff?.let { ingredient ->
        AlertDialog(
            onDismissRequest = { confirmWriteOff = null },
            title = { Text("Write off ${ingredient.name}?") },
            text = {
                Text(
                    "Logs ${ingredient.quantity.asQuantity()} ${ingredient.unit} " +
                        "(${ingredient.stockValue.asMoney()}) as expired waste and sets stock to 0."
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.writeOff(ingredient); confirmWriteOff = null }) {
                    Text("Write off", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmWriteOff = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun groupColor(group: ExpiryGroup): Color = when (group) {
    ExpiryGroup.EXPIRED, ExpiryGroup.CRITICAL -> MaterialTheme.colorScheme.error
    ExpiryGroup.SOON -> MaterialTheme.colorScheme.tertiary
    ExpiryGroup.LATER -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun ExpiryRow(item: ExpiryItem, group: ExpiryGroup, onWriteOff: () -> Unit) {
    val ingredient = item.ingredient
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (group == ExpiryGroup.EXPIRED) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        } else CardDefaults.cardColors(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(ingredient.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${ingredient.quantity.asQuantity()} ${ingredient.unit} • ${ingredient.stockValue.asMoney()}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "${daysLabel(item.daysLeft)} (${Dates.format(ingredient.expiryEpochDay!!)})",
                    style = MaterialTheme.typography.bodySmall,
                    color = groupColor(group),
                )
            }
            if (group == ExpiryGroup.EXPIRED) {
                OutlinedButton(onClick = onWriteOff) { Text("Write off") }
            }
        }
    }
}

private fun daysLabel(daysLeft: Long): String = when {
    daysLeft < -1 -> "Expired ${-daysLeft} days ago"
    daysLeft == -1L -> "Expired yesterday"
    daysLeft == 0L -> "Expires today"
    daysLeft == 1L -> "Expires tomorrow"
    else -> "Expires in $daysLeft days"
}
