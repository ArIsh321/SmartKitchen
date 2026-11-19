package com.arise.smartkitchenai.ui.expiry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arise.smartkitchenai.data.Ingredient
import com.arise.smartkitchenai.data.KitchenRepository
import com.arise.smartkitchenai.data.WasteReason
import com.arise.smartkitchenai.data.stockValue
import com.arise.smartkitchenai.util.Dates
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ExpiryGroup(val title: String) {
    EXPIRED("Expired"),
    CRITICAL("Expires within 3 days"),
    SOON("Expires within 7 days"),
    LATER("Later"),
}

data class ExpiryItem(val ingredient: Ingredient, val daysLeft: Long)

data class ExpiryUiState(
    val groups: Map<ExpiryGroup, List<ExpiryItem>> = emptyMap(),
    /** Value of stock that is expired or expires within 3 days. */
    val valueAtRisk: Double = 0.0,
)

/** Manager's expiry tracker for this device's branch. */
@OptIn(ExperimentalCoroutinesApi::class)
class ExpiryViewModel(
    private val repository: KitchenRepository,
    branchId: StateFlow<Long>,
) : ViewModel() {

    val uiState: StateFlow<ExpiryUiState> =
        branchId.flatMapLatest { repository.ingredients(it) }.map { ingredients ->
            val today = Dates.todayEpochDay()
            val items = ingredients
                .filter { it.expiryEpochDay != null && it.quantity > 0 }
                .map { ExpiryItem(it, it.expiryEpochDay!! - today) }
                .sortedBy { it.daysLeft }
            val groups = items.groupBy {
                when {
                    it.daysLeft < 0 -> ExpiryGroup.EXPIRED
                    it.daysLeft <= 3 -> ExpiryGroup.CRITICAL
                    it.daysLeft <= 7 -> ExpiryGroup.SOON
                    else -> ExpiryGroup.LATER
                }
            }.toSortedMap()
            ExpiryUiState(
                groups = groups,
                valueAtRisk = items.filter { it.daysLeft <= 3 }.sumOf { it.ingredient.stockValue },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExpiryUiState())

    /** Writes off the remaining stock of an expired ingredient as waste. */
    fun writeOff(ingredient: Ingredient) {
        viewModelScope.launch {
            repository.recordWaste(
                ingredientId = ingredient.id,
                quantity = ingredient.quantity,
                reason = WasteReason.EXPIRED,
                epochDay = Dates.todayEpochDay(),
                note = "Written off from expiry tracker",
            )
        }
    }
}
