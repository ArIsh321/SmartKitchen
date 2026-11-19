package com.arise.smartkitchenai.ui.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arise.smartkitchenai.ai.AiState
import com.arise.smartkitchenai.ui.AppViewModelFactory
import com.arise.smartkitchenai.ui.components.AiTipCard
import com.arise.smartkitchenai.ui.components.EmptyState
import com.arise.smartkitchenai.ui.components.Stat
import com.arise.smartkitchenai.ui.components.SummaryCard
import com.arise.smartkitchenai.util.asMoney
import com.arise.smartkitchenai.util.asQuantity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(viewModel: InsightsViewModel = viewModel(factory = AppViewModelFactory.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Insights · ${state.branchName}") }) }) { padding ->
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
                        Stat("Order now", state.orderNowCount.toString(), highlight = state.orderNowCount > 0),
                        Stat("Order soon", state.orderSoonCount.toString()),
                        Stat("Est. reorder cost", state.reorderCost.asMoney()),
                    )
                )
            }

            item { AiSection(aiState, onGetRecommendations = viewModel::getRecommendations) }

            item { MoneyLostSection(state) }

            item {
                Text(
                    "Reorder forecast",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    "Based on average daily usage over the last ${DemandForecaster.WINDOW_DAYS} days. " +
                        "Suggested orders cover ${DemandForecaster.COVER_DAYS} days plus a safety buffer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.forecasts.isEmpty()) {
                item { EmptyState("No ingredients yet", "Add ingredients and record their usage to see predictions.") }
            }
            items(state.forecasts, key = { it.ingredient.id }) { ForecastRow(it) }
        }
    }
}

@Composable
private fun AiSection(aiState: AiState, onGetRecommendations: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("AI recommendations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            when (aiState) {
                AiState.NotConfigured -> Text(
                    "AI is not set up yet. Add google-services.json to the app folder and rebuild.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                AiState.Empty -> Text(
                    "Analyse your stock, usage, waste, expiry dates and sales to find ways to save money.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                AiState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Analysing your kitchen…")
                }
                is AiState.Ready -> aiState.tips.forEachIndexed { index, tip ->
                    AiTipCard(tip, index, modifier = Modifier.fillMaxWidth())
                }
                is AiState.Error -> Text(
                    aiState.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (aiState != AiState.NotConfigured) {
                Button(onClick = onGetRecommendations, enabled = aiState != AiState.Loading) {
                    Text(if (aiState is AiState.Ready) "Refresh recommendations" else "Get AI recommendations")
                }
                Text(
                    "Only stock, usage, waste and sales numbers are sent to Gemini.",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun MoneyLostSection(state: InsightsUiState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Where money is lost (last 30 days)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row {
                Column(Modifier.weight(1f)) {
                    Text("Waste", style = MaterialTheme.typography.labelMedium)
                    Text(state.wasteCost30Days.asMoney(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
                }
                Column(Modifier.weight(1f)) {
                    Text("Expired, not written off", style = MaterialTheme.typography.labelMedium)
                    Text(
                        state.expiredStockValue.asMoney(),
                        style = MaterialTheme.typography.titleLarge,
                        color = if (state.expiredStockValue > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            if (state.topLosses.isEmpty()) {
                Text(
                    "No waste recorded in the last 30 days.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            HorizontalDivider()
            Text("Top ingredients", style = MaterialTheme.typography.titleSmall)
            val maxLoss = state.topLosses.maxOf { it.cost }.coerceAtLeast(0.01)
            state.topLosses.forEach { loss ->
                LossBar(
                    label = "${loss.name} — ${loss.quantity.asQuantity()} ${loss.unit}",
                    value = loss.cost,
                    fraction = (loss.cost / maxLoss).toFloat(),
                )
            }

            HorizontalDivider()
            Text("By reason", style = MaterialTheme.typography.titleSmall)
            val total = state.wasteCost30Days.coerceAtLeast(0.01)
            state.lossByReason.forEach { (reason, cost) ->
                LossBar(
                    label = "${reason.label} — ${(cost / total * 100).toInt()}%",
                    value = cost,
                    fraction = (cost / total).toFloat(),
                )
            }
        }
    }
}

@Composable
private fun LossBar(label: String, value: Double, fraction: Float) {
    Column {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(value.asMoney(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}

@Composable
private fun ForecastRow(forecast: Forecast) {
    val ing = forecast.ingredient
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    ing.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                UrgencyChip(forecast.urgency)
            }
            val details = buildList {
                add("Stock ${ing.quantity.asQuantity()} ${ing.unit}")
                forecast.avgDailyUse?.takeIf { it > 0 }?.let { add("uses ${it.asQuantity()} ${ing.unit}/day") }
                forecast.daysLeft?.let { add("≈ ${it.asQuantity()} days left") }
            }.joinToString(" • ")
            Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when {
                forecast.urgency == Urgency.NOT_ENOUGH_DATA -> Text(
                    "Record usage for at least ${DemandForecaster.MIN_HISTORY_DAYS} days to get a prediction.",
                    style = MaterialTheme.typography.bodySmall,
                )
                forecast.suggestedOrder > 0 -> Text(
                    "Suggested order: ${forecast.suggestedOrder.asQuantity()} ${ing.unit} (${forecast.orderCost.asMoney()})",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun UrgencyChip(urgency: Urgency) {
    val color = when (urgency) {
        Urgency.ORDER_NOW -> MaterialTheme.colorScheme.error
        Urgency.ORDER_SOON -> MaterialTheme.colorScheme.tertiary
        Urgency.OK -> MaterialTheme.colorScheme.primary
        Urgency.NO_RECENT_USE, Urgency.NOT_ENOUGH_DATA -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    AssistChip(
        onClick = {},
        label = { Text(urgency.label) },
        colors = AssistChipDefaults.assistChipColors(labelColor = color),
        border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = color.copy(alpha = 0.5f)),
    )
}
