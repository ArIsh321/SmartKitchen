package com.arise.smartkitchenai.ui.waste

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arise.smartkitchenai.data.Ingredient
import com.arise.smartkitchenai.data.KitchenRepository
import com.arise.smartkitchenai.data.WasteEntry
import com.arise.smartkitchenai.data.WasteReason
import com.arise.smartkitchenai.util.Dates
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WasteUiState(
    val entries: List<WasteEntry> = emptyList(),
    val ingredients: List<Ingredient> = emptyList(),
    val last7DaysCost: Double = 0.0,
    val last30DaysCost: Double = 0.0,
    val topReason: WasteReason? = null,
)

/** Manager's waste log for this device's branch. */
@OptIn(ExperimentalCoroutinesApi::class)
class WasteViewModel(
    private val repository: KitchenRepository,
    branchId: StateFlow<Long>,
) : ViewModel() {

    val uiState: StateFlow<WasteUiState> =
        branchId.flatMapLatest { branch ->
            combine(repository.wasteEntries(branch), repository.ingredients(branch)) { e, i -> e to i }
        }.map { (entries, ingredients) ->
            val today = Dates.todayEpochDay()
            val last30 = entries.filter { it.epochDay > today - 30 }
            WasteUiState(
                entries = entries,
                ingredients = ingredients,
                last7DaysCost = entries.filter { it.epochDay > today - 7 }.sumOf { it.costLoss },
                last30DaysCost = last30.sumOf { it.costLoss },
                topReason = last30.groupBy { it.reason }
                    .maxByOrNull { (_, list) -> list.sumOf { it.costLoss } }?.key,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WasteUiState())

    fun record(ingredientId: Long, quantity: Double, reason: WasteReason, epochDay: Long, note: String) {
        viewModelScope.launch { repository.recordWaste(ingredientId, quantity, reason, epochDay, note) }
    }

    fun delete(entry: WasteEntry) {
        viewModelScope.launch { repository.deleteWaste(entry) }
    }
}
