package com.arise.smartkitchenai.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arise.smartkitchenai.ai.KitchenAi
import com.arise.smartkitchenai.data.Ingredient
import com.arise.smartkitchenai.data.KitchenRepository
import com.arise.smartkitchenai.data.UsageEntry
import com.arise.smartkitchenai.data.WasteEntry
import com.arise.smartkitchenai.data.WasteReason
import com.arise.smartkitchenai.data.stockValue
import com.arise.smartkitchenai.util.Dates
import com.arise.smartkitchenai.util.asMoney
import com.arise.smartkitchenai.util.asQuantity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AiState {
    data object NotConfigured : AiState
    data object Idle : AiState
    data object Loading : AiState
    data class Success(val tips: List<String>) : AiState
    data class Error(val message: String) : AiState
}

/** Money lost on one ingredient (or a deleted one, by its saved name) in the last 30 days. */
data class IngredientLoss(val name: String, val quantity: Double, val unit: String, val cost: Double)

data class InsightsUiState(
    val forecasts: List<Forecast> = emptyList(),
    val orderNowCount: Int = 0,
    val orderSoonCount: Int = 0,
    val reorderCost: Double = 0.0,
    // ---- Where money is lost (last 30 days) ----
    val wasteCost30Days: Double = 0.0,
    val topLosses: List<IngredientLoss> = emptyList(),
    val lossByReason: List<Pair<WasteReason, Double>> = emptyList(),
    /** Value of stock that is already expired and not yet written off. */
    val expiredStockValue: Double = 0.0,
)

class InsightsViewModel(
    private val repository: KitchenRepository,
    private val kitchenAi: KitchenAi,
) : ViewModel() {

    private var latest: Snapshot? = null

    private data class Snapshot(
        val ingredients: List<Ingredient>,
        val usage: List<UsageEntry>,
        val waste: List<WasteEntry>,
        val forecasts: List<Forecast>,
        val today: Long,
    )

    val uiState: StateFlow<InsightsUiState> =
        combine(repository.ingredients, repository.usageEntries, repository.wasteEntries) { ingredients, usage, waste ->
            val today = Dates.todayEpochDay()
            val usageByIngredient = usage.groupBy { it.ingredientId }
            val forecasts = ingredients
                .map { DemandForecaster.forecast(it, usageByIngredient[it.id].orEmpty(), today) }
                .sortedWith(compareBy<Forecast> { it.urgency.ordinal }.thenBy { it.daysLeft ?: Double.MAX_VALUE })
            latest = Snapshot(ingredients, usage, waste, forecasts, today)
            val last30 = waste.filter { it.epochDay > today - 30 }
            InsightsUiState(
                forecasts = forecasts,
                orderNowCount = forecasts.count { it.urgency == Urgency.ORDER_NOW },
                orderSoonCount = forecasts.count { it.urgency == Urgency.ORDER_SOON },
                reorderCost = forecasts.filter { it.urgency == Urgency.ORDER_NOW || it.urgency == Urgency.ORDER_SOON }
                    .sumOf { it.orderCost },
                wasteCost30Days = last30.sumOf { it.costLoss },
                // Group by saved name + unit so history of deleted ingredients still counts.
                topLosses = last30.groupBy { it.ingredientName to it.unit }
                    .map { (key, list) -> IngredientLoss(key.first, list.sumOf { it.quantity }, key.second, list.sumOf { it.costLoss }) }
                    .sortedByDescending { it.cost }
                    .take(5),
                lossByReason = last30.groupBy { it.reason }
                    .map { (reason, list) -> reason to list.sumOf { it.costLoss } }
                    .sortedByDescending { it.second },
                expiredStockValue = ingredients
                    .filter { exp -> exp.expiryEpochDay != null && exp.expiryEpochDay < today && exp.quantity > 0 }
                    .sumOf { it.stockValue },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsUiState())

    private val _aiState = MutableStateFlow<AiState>(if (kitchenAi.isConfigured) AiState.Idle else AiState.NotConfigured)
    val aiState: StateFlow<AiState> = _aiState.asStateFlow()

    fun getRecommendations() {
        if (!kitchenAi.isConfigured) {
            _aiState.value = AiState.NotConfigured
            return
        }
        val snapshot = latest
        if (snapshot == null || snapshot.ingredients.isEmpty()) {
            _aiState.value = AiState.Error("Add some ingredients first so there is data to analyse.")
            return
        }
        _aiState.value = AiState.Loading
        viewModelScope.launch {
            _aiState.value = try {
                val tips = kitchenAi.recommendations(buildSummary(snapshot))
                if (tips.isEmpty()) AiState.Error("No recommendations returned. Try again.") else AiState.Success(tips)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AiState.Error(
                    "Couldn't get recommendations: ${e.message ?: e.javaClass.simpleName}\n" +
                        "Check your internet connection and that the App Check debug token is registered in Firebase."
                )
            }
        }
    }

    /** Numbers-only summary of the kitchen sent to Gemini. */
    private fun buildSummary(s: Snapshot): String {
        val last30 = s.waste.filter { it.epochDay > s.today - 30 }
        val wasteByIngredient = last30.groupBy { it.ingredientId }
        val forecastById = s.forecasts.associateBy { it.ingredient.id }

        // Keep the prompt small: most costly waste first, then highest stock value.
        val relevant = s.ingredients
            .sortedWith(
                compareByDescending<Ingredient> { ing -> wasteByIngredient[ing.id].orEmpty().sumOf { it.costLoss } }
                    .thenByDescending { it.stockValue }
            )
            .take(60)

        return buildString {
            appendLine("Restaurant kitchen data. Today: ${Dates.format(s.today)}.")
            appendLine()
            appendLine("INGREDIENTS (stock, cost, expiry, usage, waste in last 30 days):")
            relevant.forEach { ing ->
                val f = forecastById[ing.id]
                val waste = wasteByIngredient[ing.id].orEmpty()
                val parts = mutableListOf(
                    "stock ${ing.quantity.asQuantity()} ${ing.unit}",
                    "cost ${ing.unitCost.asMoney()}/${ing.unit}",
                    "min level ${ing.minStock.asQuantity()}",
                )
                ing.expiryEpochDay?.let { parts += "expires in ${it - s.today} days" }
                when {
                    f?.avgDailyUse == null -> parts += "usage: not enough data"
                    f.avgDailyUse == 0.0 -> parts += "not used in last ${DemandForecaster.WINDOW_DAYS} days"
                    else -> {
                        parts += "avg use ${f.avgDailyUse.asQuantity()} ${ing.unit}/day"
                        f.daysLeft?.let { parts += "about ${it.asQuantity()} days of stock left" }
                    }
                }
                if (waste.isNotEmpty()) {
                    val topReason = waste.groupBy { it.reason }.maxBy { (_, l) -> l.sumOf { it.costLoss } }.key
                    parts += "wasted ${waste.sumOf { it.quantity }.asQuantity()} ${ing.unit} " +
                        "(${waste.sumOf { it.costLoss }.asMoney()}), mostly ${topReason.label}"
                }
                appendLine("- ${ing.name}${if (ing.category.isNotBlank()) " [${ing.category}]" else ""}: ${parts.joinToString(", ")}")
            }
            appendLine()
            appendLine("WASTE SUMMARY (last 30 days): total ${last30.sumOf { it.costLoss }.asMoney()}")
            last30.groupBy { it.reason }
                .map { (reason, list) -> reason.label to list.sumOf { it.costLoss } }
                .sortedByDescending { it.second }
                .forEach { (reason, cost) -> appendLine("- $reason: ${cost.asMoney()}") }
            appendLine()
            appendLine("Total stock value: ${s.ingredients.sumOf { it.stockValue }.asMoney()}")
        }
    }
}
