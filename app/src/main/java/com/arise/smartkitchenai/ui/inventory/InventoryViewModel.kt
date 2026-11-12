package com.arise.smartkitchenai.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arise.smartkitchenai.data.BarcodeResult
import com.arise.smartkitchenai.data.Ingredient
import com.arise.smartkitchenai.data.KitchenRepository
import com.arise.smartkitchenai.data.isLowStock
import com.arise.smartkitchenai.data.stockValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class InventoryUiState(
    val ingredients: List<Ingredient> = emptyList(),
    val query: String = "",
    val totalValue: Double = 0.0,
    val lowStockCount: Int = 0,
    val itemCount: Int = 0,
)

sealed interface ScanState {
    data object Idle : ScanState
    data object LookingUp : ScanState
    data class Done(val result: BarcodeResult) : ScanState
}

class InventoryViewModel(private val repository: KitchenRepository) : ViewModel() {

    private val query = MutableStateFlow("")

    val uiState: StateFlow<InventoryUiState> =
        combine(repository.ingredients, query) { all, q ->
            val filtered = if (q.isBlank()) all else all.filter {
                it.name.contains(q, ignoreCase = true) || it.category.contains(q, ignoreCase = true)
            }
            InventoryUiState(
                // Low-stock items float to the top so they are noticed first.
                ingredients = filtered.sortedByDescending { it.isLowStock },
                query = q,
                totalValue = all.sumOf { it.stockValue },
                lowStockCount = all.count { it.isLowStock },
                itemCount = all.size,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InventoryUiState())

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun save(ingredient: Ingredient) {
        viewModelScope.launch { repository.saveIngredient(ingredient) }
    }

    fun delete(ingredient: Ingredient) {
        viewModelScope.launch { repository.deleteIngredient(ingredient) }
    }

    fun recordUsage(ingredientId: Long, quantity: Double, epochDay: Long) {
        viewModelScope.launch { repository.recordUsage(ingredientId, quantity, epochDay) }
    }

    fun onBarcodeScanned(barcode: String) {
        _scanState.value = ScanState.LookingUp
        viewModelScope.launch { _scanState.value = ScanState.Done(repository.lookupBarcode(barcode)) }
    }

    /** Call once the UI has applied the scan result. */
    fun consumeScanResult() {
        _scanState.value = ScanState.Idle
    }
}
