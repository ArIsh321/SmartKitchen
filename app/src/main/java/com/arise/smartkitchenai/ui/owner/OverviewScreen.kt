package com.arise.smartkitchenai.ui.owner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arise.smartkitchenai.ai.AiState
import com.arise.smartkitchenai.ui.AppViewModelFactory
import com.arise.smartkitchenai.ui.components.AiTipCard
import com.arise.smartkitchenai.ui.components.BarChart
import com.arise.smartkitchenai.ui.components.BrandHeader
import com.arise.smartkitchenai.ui.components.ChartColor
import com.arise.smartkitchenai.ui.components.KpiCard
import com.arise.smartkitchenai.ui.components.PickerChip
import com.arise.smartkitchenai.ui.components.SalesDialog
import com.arise.smartkitchenai.ui.theme.BadTerracotta
import com.arise.smartkitchenai.ui.theme.GoodGreen
import com.arise.smartkitchenai.util.Dates
import com.arise.smartkitchenai.util.Period
import com.arise.smartkitchenai.util.asMoney
import com.arise.smartkitchenai.util.asMoneyWhole
import com.arise.smartkitchenai.util.percentChange
import java.util.Locale

@Composable
fun OverviewScreen(
    onOpenInsights: () -> Unit,
    onOpenProfile: () -> Unit,
    viewModel: OverviewViewModel = viewModel(factory = AppViewModelFactory.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val branches by viewModel.branches.collectAsStateWithLifecycle()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()
    var showSalesDialog by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { BrandHeader(state.ownerName, onAvatarClick = onOpenProfile) }

        item {
            Column {
                Text("Owner Dashboard", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text(
                    "${state.restaurantName} at a glance",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            OwnerPickers(
                branches = branches,
                period = state.period,
                onBranch = viewModel::setBranch,
                onPeriod = viewModel::setPeriod,
            )
        }

        item {
            Card(shape = RoundedCornerShape(20.dp)) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (branches.selected == null) "Today's sales · all branches" else "Today's sales",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            state.todaysSales?.asMoney() ?: "Not entered yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    FilledTonalButton(onClick = { showSalesDialog = true }) {
                        Text(if (state.todaysSales == null) "Enter sales" else "Edit")
                    }
                }
            }
        }

        item { KpiRow(state) }

        item { RevenueChartCard(state) }

        item { AiRecommendationsCard(aiState, onGenerate = viewModel::generateAi, onOpenInsights = onOpenInsights) }
    }

    if (showSalesDialog) {
        SalesDialog(
            initialDay = Dates.todayEpochDay(),
            // Pre-filled per branch via existingAmount, never with the all-branches total.
            initialAmount = null,
            branches = branches.branches,
            initialBranchId = viewModel.defaultSalesBranch,
            existingAmount = viewModel::existingSales,
            onDismiss = { showSalesDialog = false },
            onSave = { branch, day, amount ->
                viewModel.saveSales(branch, day, amount)
                showSalesDialog = false
            },
        )
    }
}

/** Branch + period pickers, shared with Reports. */
@Composable
fun OwnerPickers(
    branches: BranchSelection,
    period: Period,
    onBranch: (Long?) -> Unit,
    onPeriod: (Period) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PickerChip(
            leading = "🏪",
            selectedLabel = branches.selectedName,
            options = listOf<Long?>(null) + branches.branches.map { it.id },
            optionLabel = { id -> if (id == null) "All branches" else branches.branches.first { it.id == id }.name },
            onSelect = onBranch,
            modifier = Modifier.weight(1.15f),
        )
        PickerChip(
            leading = "📅",
            selectedLabel = period.label,
            options = Period.entries,
            optionLabel = { it.label },
            onSelect = onPeriod,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun KpiRow(state: OverviewUiState) {
    val cur = state.current
    val prev = state.previous
    val foodCostChange = if (cur.foodCostPercent != null && prev.foodCostPercent != null) {
        cur.foodCostPercent!! - prev.foodCostPercent!!
    } else null

    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        KpiCard(
            emoji = "📊",
            emojiBackground = MaterialTheme.colorScheme.primaryContainer,
            label = "Revenue",
            value = cur.revenue.asMoneyWhole(),
            change = percentChange(cur.revenue, prev.revenue),
            higherIsBetter = true,
            comparisonLabel = state.period.comparisonLabel,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        KpiCard(
            emoji = "🥧",
            emojiBackground = MaterialTheme.colorScheme.tertiaryContainer,
            label = "Food Cost",
            value = cur.foodCostPercent?.let { String.format(Locale.getDefault(), "%.1f%%", it) } ?: "—",
            change = foodCostChange,
            higherIsBetter = false,
            comparisonLabel = state.period.comparisonLabel,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        KpiCard(
            emoji = "🍃",
            emojiBackground = MaterialTheme.colorScheme.primaryContainer,
            label = "Waste Cost",
            value = cur.wasteCost.asMoneyWhole(),
            change = percentChange(cur.wasteCost, prev.wasteCost),
            higherIsBetter = false,
            comparisonLabel = state.period.comparisonLabel,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

@Composable
private fun RevenueChartCard(state: OverviewUiState) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    state.chartTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(state.current.revenue.asMoneyWhole(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    percentChange(state.current.revenue, state.previous.revenue)?.let { change ->
                        Text(
                            (if (change >= 0) "↑ +" else "↓ ") + String.format(Locale.getDefault(), "%.1f%%", change),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (change >= 0) GoodGreen else BadTerracotta,
                        )
                        Text(state.period.comparisonLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (state.revenueBars.all { it.values.first() == 0.0 }) {
                Text(
                    "No sales entered for this period yet. Tap \"Enter sales\" to record each day's total.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                BarChart(points = state.revenueBars, color = ChartColor.GREEN, formatValue = { it.asMoney() })
            }
        }
    }
}

@Composable
private fun AiRecommendationsCard(aiState: AiState, onGenerate: () -> Unit, onOpenInsights: () -> Unit) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("AI Recommendations", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onOpenInsights) { Text("View all ›") }
            }
            when (aiState) {
                AiState.NotConfigured -> Text("AI is not set up yet (Firebase).", style = MaterialTheme.typography.bodyMedium)
                AiState.Empty -> {
                    Text(
                        "Get money-saving tips based on your stock, waste, expiry dates and sales.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = onGenerate) { Text("Generate recommendations") }
                }
                AiState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Analysing your kitchen…")
                }
                is AiState.Error -> {
                    Text(aiState.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onGenerate) { Text("Try again") }
                }
                is AiState.Ready -> Row(
                    Modifier.height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    aiState.tips.take(2).forEachIndexed { index, tip ->
                        AiTipCard(
                            tip = tip,
                            index = index,
                            actionLabel = if (index == 0) "View details" else "See insights",
                            onAction = onOpenInsights,
                            maxDetailLines = 5,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}
