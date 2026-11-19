package com.arise.smartkitchenai.ui.staff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arise.smartkitchenai.data.Ingredient
import com.arise.smartkitchenai.data.KitchenRepository
import com.arise.smartkitchenai.data.WasteReason
import com.arise.smartkitchenai.util.Dates
import com.arise.smartkitchenai.util.asQuantity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A usage or waste entry shown in "Recent Activity". No prices. */
data class Activity(
    val key: String,
    val wasted: Boolean,
    val name: String,
    val quantity: Double,
    val unit: String,
    val epochDay: Long,
    val reason: WasteReason? = null,
)

data class StaffUiState(
    val ingredients: List<Ingredient> = emptyList(),
    /** Most-used ingredients in the last 30 days (falls back to alphabetical). */
    val quickSelect: List<Ingredient> = emptyList(),
    val recent: List<Activity> = emptyList(),
)

/** Kitchen staff: quick usage/waste entry for this device's branch. Exposes no prices. */
@OptIn(ExperimentalCoroutinesApi::class)
class StaffViewModel(
    private val repository: KitchenRepository,
    private val branchId: StateFlow<Long>,
) : ViewModel() {

    val uiState: StateFlow<StaffUiState> =
        branchId.flatMapLatest { branch ->
            combine(repository.ingredients(branch), repository.usageEntries(branch), repository.wasteEntries(branch)) { ingredients, usage, waste ->
                val today = Dates.todayEpochDay()
                val byId = ingredients.associateBy { it.id }

                val useCounts = (usage.filter { it.epochDay > today - 30 }.map { it.ingredientId } +
                    waste.filter { it.epochDay > today - 30 }.mapNotNull { it.ingredientId })
                    .groupingBy { it }.eachCount()
                val quickSelect = ingredients
                    .sortedWith(compareByDescending<Ingredient> { useCounts[it.id] ?: 0 }.thenBy { it.name.lowercase() })
                    .take(QUICK_SELECT_COUNT)

                val usedActivity = usage.mapNotNull { u ->
                    val ing = byId[u.ingredientId] ?: return@mapNotNull null
                    Activity("u${u.id}", wasted = false, ing.name, u.quantity, ing.unit, u.epochDay) to u.id
                }
                val wasteActivity = waste.map { w ->
                    Activity("w${w.id}", wasted = true, w.ingredientName, w.quantity, w.unit, w.epochDay, w.reason) to w.id
                }
                // Newest day first; within a day, higher ids were entered later.
                val recent = (usedActivity + wasteActivity)
                    .sortedWith(compareByDescending<Pair<Activity, Long>> { it.first.epochDay }.thenByDescending { it.second })
                    .take(RECENT_COUNT)
                    .map { it.first }

                StaffUiState(ingredients = ingredients, quickSelect = quickSelect, recent = recent)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StaffUiState())

    private val _messages = Channel<String>(Channel.BUFFERED)
    /** One-off confirmation/error messages for a snackbar. */
    val messages: Flow<String> = _messages.receiveAsFlow()

    private val _scanned = MutableStateFlow<Ingredient?>(null)
    /** Ingredient found by barcode scan, to select it. */
    val scanned: StateFlow<Ingredient?> = _scanned.asStateFlow()

    /** [quantity] is already converted to the ingredient's own unit. */
    fun recordUsed(ingredient: Ingredient, quantity: Double) {
        viewModelScope.launch {
            repository.recordUsage(ingredient.id, quantity, Dates.todayEpochDay())
            _messages.send("Saved: ${quantity.asQuantity()} ${ingredient.unit} ${ingredient.name} used")
        }
    }

    fun recordWasted(ingredient: Ingredient, quantity: Double, reason: WasteReason) {
        viewModelScope.launch {
            repository.recordWaste(ingredient.id, quantity, reason, Dates.todayEpochDay(), note = "Logged by kitchen staff")
            _messages.send("Saved: ${quantity.asQuantity()} ${ingredient.unit} ${ingredient.name} wasted (${reason.label})")
        }
    }

    fun onBarcodeScanned(barcode: String) {
        viewModelScope.launch {
            val ingredient = repository.findIngredientByBarcode(barcode, branchId.value)
            if (ingredient == null) {
                _messages.send("This product isn't in the inventory yet. Ask the manager to add it.")
            } else {
                _scanned.value = ingredient
            }
        }
    }

    fun consumeScanned() {
        _scanned.value = null
    }

    private companion object {
        const val QUICK_SELECT_COUNT = 4
        const val RECENT_COUNT = 30
    }
}
