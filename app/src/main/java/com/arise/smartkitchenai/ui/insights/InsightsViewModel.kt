package com.arise.smartkitchenai.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arise.smartkitchenai.ai.AiState
import com.arise.smartkitchenai.ai.AiTipsController
import com.arise.smartkitchenai.ai.KitchenAi
import com.arise.smartkitchenai.data.KitchenRepository
import com.arise.smartkitchenai.data.WasteReason
import com.arise.smartkitchenai.ui.owner.IngredientLoss
import com.arise.smartkitchenai.ui.owner.OwnerData
import com.arise.smartkitchenai.ui.owner.expiredStockValue
import com.arise.smartkitchenai.ui.owner.lossByReason
import com.arise.smartkitchenai.ui.owner.ownerData
import com.arise.smartkitchenai.ui.owner.summary
import com.arise.smartkitchenai.ui.owner.topLosses
import com.arise.smartkitchenai.util.DayRange
import com.arise.smartkitchenai.util.Dates
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

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
    val branchName: String = "",
)

/** Owner's Insights tab, for the branch selected on the Owner's screens (or all branches). */
@OptIn(ExperimentalCoroutinesApi::class)
class InsightsViewModel(
    repository: KitchenRepository,
    kitchenAi: KitchenAi,
    branchFilter: StateFlow<Long?>,
) : ViewModel() {

    private val data: StateFlow<OwnerData> = branchFilter
        .flatMapLatest { repository.ownerData(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OwnerData())

    private val branchName: StateFlow<String> =
        combine(repository.branches, branchFilter) { branches, filter ->
            if (filter == null) "All branches" else branches.firstOrNull { it.id == filter }?.name ?: "Branch"
        }.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val uiState: StateFlow<InsightsUiState> =
        combine(data, branchName) { d, name ->
            val today = Dates.todayEpochDay()
            val last30 = DayRange(today - 29, today)
            val usageByIngredient = d.usage.groupBy { it.ingredientId }
            val forecasts = d.ingredients
                .map { DemandForecaster.forecast(it, usageByIngredient[it.id].orEmpty(), today) }
                .sortedWith(compareBy<Forecast> { it.urgency.ordinal }.thenBy { it.daysLeft ?: Double.MAX_VALUE })
            InsightsUiState(
                forecasts = forecasts,
                orderNowCount = forecasts.count { it.urgency == Urgency.ORDER_NOW },
                orderSoonCount = forecasts.count { it.urgency == Urgency.ORDER_SOON },
                reorderCost = forecasts.filter { it.urgency == Urgency.ORDER_NOW || it.urgency == Urgency.ORDER_SOON }
                    .sumOf { it.orderCost },
                wasteCost30Days = d.summary(last30).wasteCost,
                topLosses = d.topLosses(last30),
                lossByReason = d.lossByReason(last30),
                expiredStockValue = d.expiredStockValue(today),
                branchName = name,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsUiState())

    private val ai = AiTipsController(kitchenAi, viewModelScope, branchFilter)
    val aiState: StateFlow<AiState> = ai.state

    fun getRecommendations() = ai.generate(data.value, branchName.value)
}
