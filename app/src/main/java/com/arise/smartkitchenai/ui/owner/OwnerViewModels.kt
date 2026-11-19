package com.arise.smartkitchenai.ui.owner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arise.smartkitchenai.ai.AiState
import com.arise.smartkitchenai.ai.AiTipsController
import com.arise.smartkitchenai.ai.KitchenAi
import com.arise.smartkitchenai.auth.Role
import com.arise.smartkitchenai.auth.RoleManager
import com.arise.smartkitchenai.data.Branch
import com.arise.smartkitchenai.data.DailySales
import com.arise.smartkitchenai.data.KitchenRepository
import com.arise.smartkitchenai.data.WasteReason
import com.arise.smartkitchenai.settings.AppSettings
import com.arise.smartkitchenai.util.Dates
import com.arise.smartkitchenai.util.Period
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Branch picker state shared by the Owner's screens. */
data class BranchSelection(
    val branches: List<Branch> = emptyList(),
    /** null = all branches. */
    val selected: Long? = null,
) {
    val selectedName: String
        get() = if (selected == null) "All branches" else branches.firstOrNull { it.id == selected }?.name ?: "Branch"
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun ViewModel.ownerDataFor(repository: KitchenRepository, settings: AppSettings): StateFlow<OwnerData> =
    settings.ownerBranchFilter
        .flatMapLatest { repository.ownerData(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OwnerData())

private fun ViewModel.branchSelection(repository: KitchenRepository, settings: AppSettings): StateFlow<BranchSelection> =
    combine(repository.branches, settings.ownerBranchFilter) { branches, filter -> BranchSelection(branches, filter) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, BranchSelection())

// ---------------------------------------------------------------- Overview

data class OverviewUiState(
    val ownerName: String = "",
    val restaurantName: String = "",
    val period: Period = Period.THIS_MONTH,
    val current: FinanceSummary = FinanceSummary(),
    val previous: FinanceSummary = FinanceSummary(),
    val chartTitle: String = "",
    val revenueBars: List<ChartPoint> = emptyList(),
    val todaysSales: Double? = null,
)

class OverviewViewModel(
    private val repository: KitchenRepository,
    kitchenAi: KitchenAi,
    private val settings: AppSettings,
) : ViewModel() {

    private val data = ownerDataFor(repository, settings)

    // All branches' sales, independent of the Owner's filter, for pre-filling the sales dialog.
    private val allSales = repository.sales(null).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Saved sales for one branch on one day, or null if none. */
    fun existingSales(branchId: Long, epochDay: Long): Double? =
        allSales.value.firstOrNull { it.branchId == branchId && it.epochDay == epochDay }?.amount
    val branches: StateFlow<BranchSelection> = branchSelection(repository, settings)
    private val period = MutableStateFlow(Period.THIS_MONTH)

    val uiState: StateFlow<OverviewUiState> =
        combine(data, period, settings.ownerName, settings.restaurantName) { d, p, owner, restaurant ->
            val today = Dates.todayEpochDay()
            val range = p.range(today)
            val (title, bars) = d.revenueBars(range)
            OverviewUiState(
                ownerName = owner,
                restaurantName = restaurant,
                period = p,
                current = d.summary(range),
                previous = d.summary(p.previousRange(today)),
                chartTitle = title,
                revenueBars = bars,
                todaysSales = d.sales.filter { it.epochDay == today }.takeIf { it.isNotEmpty() }?.sumOf { it.amount },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverviewUiState())

    private val ai = AiTipsController(kitchenAi, viewModelScope, settings.ownerBranchFilter)
    val aiState: StateFlow<AiState> = ai.state

    /** Branch to pre-select when the Owner enters sales. */
    val defaultSalesBranch: Long get() = settings.ownerBranchFilter.value ?: settings.deviceBranchId.value

    fun setPeriod(value: Period) { period.value = value }
    fun setBranch(branchId: Long?) = settings.setOwnerBranchFilter(branchId)
    fun generateAi() = ai.generate(data.value, branches.value.selectedName)

    fun saveSales(branchId: Long, epochDay: Long, amount: Double) {
        viewModelScope.launch { repository.saveSales(branchId, epochDay, amount) }
    }
}

// ---------------------------------------------------------------- Reports

data class SalesRow(val sales: DailySales, val branchName: String)

data class ReportsUiState(
    val period: Period = Period.THIS_MONTH,
    val current: FinanceSummary = FinanceSummary(),
    val previous: FinanceSummary = FinanceSummary(),
    val stockValue: Double = 0.0,
    val expiredStockValue: Double = 0.0,
    val topLosses: List<IngredientLoss> = emptyList(),
    val lossByReason: List<Pair<WasteReason, Double>> = emptyList(),
    val revenueTrend: List<ChartPoint> = emptyList(),
    val wasteTrend: List<ChartPoint> = emptyList(),
    val salesRows: List<SalesRow> = emptyList(),
)

class ReportsViewModel(
    private val repository: KitchenRepository,
    private val settings: AppSettings,
) : ViewModel() {

    private val data = ownerDataFor(repository, settings)

    // All branches' sales, independent of the Owner's filter, for pre-filling the sales dialog.
    private val allSales = repository.sales(null).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Saved sales for one branch on one day, or null if none. */
    fun existingSales(branchId: Long, epochDay: Long): Double? =
        allSales.value.firstOrNull { it.branchId == branchId && it.epochDay == epochDay }?.amount
    val branches: StateFlow<BranchSelection> = branchSelection(repository, settings)
    private val period = MutableStateFlow(Period.THIS_MONTH)

    val uiState: StateFlow<ReportsUiState> =
        combine(data, period, repository.branches) { d, p, branchList ->
            val today = Dates.todayEpochDay()
            val range = p.range(today)
            val trend = d.weeklyTrend(today)
            val names = branchList.associate { it.id to it.name }
            ReportsUiState(
                period = p,
                current = d.summary(range),
                previous = d.summary(p.previousRange(today)),
                stockValue = d.stockValue,
                expiredStockValue = d.expiredStockValue(today),
                topLosses = d.topLosses(range),
                lossByReason = d.lossByReason(range),
                // Revenue and waste are on very different scales, so they get separate charts.
                revenueTrend = trend.map { ChartPoint(it.label, listOf(it.values[0])) },
                wasteTrend = trend.map { ChartPoint(it.label, listOf(it.values[1])) },
                salesRows = d.sales.filter { it.epochDay in range }.map { SalesRow(it, names[it.branchId] ?: "Branch") },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportsUiState())

    val defaultSalesBranch: Long get() = settings.ownerBranchFilter.value ?: settings.deviceBranchId.value

    fun setPeriod(value: Period) { period.value = value }
    fun setBranch(branchId: Long?) = settings.setOwnerBranchFilter(branchId)

    fun saveSales(branchId: Long, epochDay: Long, amount: Double) {
        viewModelScope.launch { repository.saveSales(branchId, epochDay, amount) }
    }

    fun deleteSales(sales: DailySales) {
        viewModelScope.launch { repository.deleteSales(sales) }
    }
}

// ---------------------------------------------------------------- Profile

data class ProfileUiState(
    val restaurantName: String = "",
    val ownerName: String = "",
    val branches: List<Branch> = emptyList(),
    val deviceBranchId: Long = 0,
)

class ProfileViewModel(
    private val repository: KitchenRepository,
    private val settings: AppSettings,
    private val roleManager: RoleManager,
) : ViewModel() {

    val uiState: StateFlow<ProfileUiState> =
        combine(settings.restaurantName, settings.ownerName, repository.branches, settings.deviceBranchId) { r, o, b, d ->
            ProfileUiState(r, o, b, d)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    fun setRestaurantName(name: String) = settings.setRestaurantName(name.trim())
    fun setOwnerName(name: String) = settings.setOwnerName(name.trim())
    fun setDeviceBranch(branchId: Long) = settings.setDeviceBranch(branchId)

    fun addBranch(name: String) {
        viewModelScope.launch { repository.addBranch(name.trim()) }
    }

    fun renameBranch(branch: Branch, name: String) {
        viewModelScope.launch { repository.renameBranch(branch, name.trim()) }
    }

    /** @return an error message, or null if the PIN was changed. */
    fun changePin(role: Role, pin: String): String? = when {
        pin.length != RoleManager.PIN_LENGTH || !pin.all(Char::isDigit) -> "PIN must be ${RoleManager.PIN_LENGTH} digits."
        roleManager.isPinUsedByOtherRole(role, pin) -> "This PIN is already used for another role."
        else -> {
            roleManager.setPin(role, pin)
            null
        }
    }

    fun lock() = roleManager.logout()
}
