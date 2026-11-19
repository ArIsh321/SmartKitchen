package com.arise.smartkitchenai.ui.owner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arise.smartkitchenai.data.DailySales
import com.arise.smartkitchenai.ui.AppViewModelFactory
import com.arise.smartkitchenai.ui.components.BarChart
import com.arise.smartkitchenai.ui.components.ChartColor
import com.arise.smartkitchenai.ui.components.LossBar
import com.arise.smartkitchenai.ui.components.SalesDialog
import com.arise.smartkitchenai.ui.theme.BadTerracotta
import com.arise.smartkitchenai.ui.theme.GoodGreen
import com.arise.smartkitchenai.util.Dates
import com.arise.smartkitchenai.util.asMoney
import com.arise.smartkitchenai.util.asQuantity
import com.arise.smartkitchenai.util.percentChange
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(viewModel: ReportsViewModel = viewModel(factory = AppViewModelFactory.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val branches by viewModel.branches.collectAsStateWithLifecycle()
    // null = closed; DailySales with amount -1 = new entry.
    var editing by remember { mutableStateOf<DailySales?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text("Reports") }) }) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { OwnerPickers(branches, state.period, viewModel::setBranch, viewModel::setPeriod) }

            item { SummaryTable(state) }

            item {
                ChartCard("Weekly revenue", "Last 8 weeks") {
                    BarChart(state.revenueTrend, ChartColor.GREEN, formatValue = { it.asMoney() }, showValueLabels = false)
                }
            }
            item {
                ChartCard("Weekly waste cost", "Last 8 weeks") {
                    BarChart(state.wasteTrend, ChartColor.TERRACOTTA, formatValue = { it.asMoney() }, showValueLabels = false)
                }
            }

            item { MoneyLostCard(state) }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Daily sales", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    FilledTonalButton(onClick = {
                        editing = DailySales(viewModel.defaultSalesBranch, Dates.todayEpochDay(), -1.0)
                    }) { Text("Add sales") }
                }
            }
            if (state.salesRows.isEmpty()) {
                item {
                    Text(
                        "No sales recorded in this period.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.salesRows, key = { "${it.sales.branchId}-${it.sales.epochDay}" }) { row ->
                Card(onClick = { editing = row.sales }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                            Text(Dates.format(row.sales.epochDay), style = MaterialTheme.typography.titleSmall)
                            Text(row.branchName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(row.sales.amount.asMoney(), fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = { viewModel.deleteSales(row.sales) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete sales entry")
                        }
                    }
                }
            }
        }
    }

    editing?.let { sales ->
        SalesDialog(
            initialDay = sales.epochDay,
            initialAmount = sales.amount.takeIf { it >= 0 },
            branches = branches.branches,
            initialBranchId = sales.branchId,
            // New entries: show what's already saved for the chosen branch/day.
            existingAmount = if (sales.amount < 0) viewModel::existingSales else null,
            onDismiss = { editing = null },
            onSave = { branch, day, amount ->
                // Moving an entry to another day/branch: remove the old one first.
                if (sales.amount >= 0 && (branch != sales.branchId || day != sales.epochDay)) viewModel.deleteSales(sales)
                viewModel.saveSales(branch, day, amount)
                editing = null
            },
        )
    }
}

@Composable
private fun SummaryTable(state: ReportsUiState) {
    val c = state.current
    val p = state.previous
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Summary · ${state.period.label}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            SummaryRow("Revenue", c.revenue.asMoney(), percentChange(c.revenue, p.revenue), higherIsBetter = true)
            SummaryRow("Food cost (used + wasted)", c.foodCost.asMoney(), percentChange(c.foodCost, p.foodCost), higherIsBetter = false)
            SummaryRow("Food cost % of revenue", c.foodCostPercent?.let { pct(it) } ?: "— (enter sales)", null, false)
            SummaryRow("Purchases", c.purchases.asMoney(), percentChange(c.purchases, p.purchases), higherIsBetter = false)
            SummaryRow("Waste cost", c.wasteCost.asMoney(), percentChange(c.wasteCost, p.wasteCost), higherIsBetter = false)
            SummaryRow("Waste % of purchases", c.wastePercentOfPurchases?.let { pct(it) } ?: "—", null, false)
            HorizontalDivider()
            SummaryRow("Stock value now", state.stockValue.asMoney(), null, false)
            SummaryRow("Expired, not written off", state.expiredStockValue.asMoney(), null, false)
            Text(
                "Changes are ${state.period.comparisonLabel}. Sales recorded on ${c.salesDaysRecorded} day(s) in this period.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun pct(value: Double) = String.format(Locale.getDefault(), "%.1f%%", value)

@Composable
private fun SummaryRow(label: String, value: String, change: Double?, higherIsBetter: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            change?.let {
                val good = if (higherIsBetter) it >= 0 else it <= 0
                Text(
                    (if (it >= 0) "↑ +" else "↓ ") + pct(it),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (good) GoodGreen else BadTerracotta,
                )
            }
        }
    }
}

@Composable
private fun ChartCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun MoneyLostCard(state: ReportsUiState) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Where money is lost · ${state.period.label}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (state.topLosses.isEmpty()) {
                Text("No waste recorded in this period.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            Text("Top ingredients", style = MaterialTheme.typography.titleSmall)
            val maxLoss = state.topLosses.maxOf { it.cost }.coerceAtLeast(0.01)
            state.topLosses.forEach {
                LossBar("${it.name} — ${it.quantity.asQuantity()} ${it.unit}", it.cost.asMoney(), (it.cost / maxLoss).toFloat())
            }
            HorizontalDivider()
            Text("By reason", style = MaterialTheme.typography.titleSmall)
            val total = state.current.wasteCost.coerceAtLeast(0.01)
            state.lossByReason.forEach { (reason, cost) ->
                LossBar("${reason.label} — ${(cost / total * 100).toInt()}%", cost.asMoney(), (cost / total).toFloat())
            }
        }
    }
}
