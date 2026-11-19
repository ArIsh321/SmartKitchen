package com.arise.smartkitchenai.ui.manager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arise.smartkitchenai.data.DailySales
import com.arise.smartkitchenai.data.Ingredient
import com.arise.smartkitchenai.data.KitchenRepository
import com.arise.smartkitchenai.data.OrderStatus
import com.arise.smartkitchenai.data.OrderWithItems
import com.arise.smartkitchenai.data.Purchase
import com.arise.smartkitchenai.data.PurchaseOrder
import com.arise.smartkitchenai.data.PurchaseOrderItem
import com.arise.smartkitchenai.data.ReceivedLine
import com.arise.smartkitchenai.data.UsageEntry
import kotlinx.coroutines.flow.MutableStateFlow
import com.arise.smartkitchenai.ui.insights.DemandForecaster
import com.arise.smartkitchenai.ui.insights.Forecast
import com.arise.smartkitchenai.ui.insights.Urgency
import com.arise.smartkitchenai.util.Dates
import com.arise.smartkitchenai.util.asMoney
import com.arise.smartkitchenai.util.asQuantity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ---------------------------------------------------------------- Purchasing

enum class OrderSort(val label: String) {
    RECENT("Most recent"),
    OLDEST("Oldest"),
    TOTAL("Highest total"),
    STATUS("Status"),
}

/** An editable order line (used when creating/editing and receiving orders). */
data class OrderLine(val ingredient: Ingredient, val quantity: Double, val unitCost: Double)

data class PurchasingUiState(
    val branchName: String = "",
    val ingredients: List<Ingredient> = emptyList(),
    /** Items to order now/soon (or top up to minimum), most urgent first. */
    val reorder: List<Forecast> = emptyList(),
    /** Orders after the status filter and sort are applied. */
    val orders: List<OrderWithItems> = emptyList(),
    val totalOrders: Int = 0,
    val statusFilter: OrderStatus? = null,
    val sort: OrderSort = OrderSort.RECENT,
    /** Last supplier each ingredient was bought from, to pre-fill new orders. */
    val lastSupplier: Map<Long, String> = emptyMap(),
)

/** Manager's purchasing for this device's branch. */
@OptIn(ExperimentalCoroutinesApi::class)
class PurchasingViewModel(
    private val repository: KitchenRepository,
    private val branchId: StateFlow<Long>,
) : ViewModel() {

    private val statusFilter = MutableStateFlow<OrderStatus?>(null)
    private val sort = MutableStateFlow(OrderSort.RECENT)

    private val branchData = branchId.flatMapLatest { branch ->
        combine(
            repository.ingredients(branch), repository.usageEntries(branch), repository.purchases(branch),
            repository.orders(branch), repository.branches,
        ) { ingredients, usage, purchases, orders, branches ->
            BranchData(ingredients, usage, purchases, orders, branches.firstOrNull { it.id == branch }?.name ?: "Branch")
        }
    }

    private data class BranchData(
        val ingredients: List<Ingredient>,
        val usage: List<UsageEntry>,
        val purchases: List<Purchase>,
        val orders: List<OrderWithItems>,
        val branchName: String,
    )

    val uiState: StateFlow<PurchasingUiState> =
        combine(branchData, statusFilter, sort) { d, filter, sortBy ->
            val today = Dates.todayEpochDay()
            val usageById = d.usage.groupBy { it.ingredientId }
            val reorder = d.ingredients
                .map { DemandForecaster.forecast(it, usageById[it.id].orEmpty(), today) }
                .filter { it.suggestedOrder > 0 && it.urgency in ORDERABLE }
                .sortedWith(compareBy<Forecast> { it.urgency.ordinal }.thenBy { it.daysLeft ?: Double.MAX_VALUE })
            val filtered = d.orders.filter { filter == null || it.order.status == filter }
            PurchasingUiState(
                branchName = d.branchName,
                ingredients = d.ingredients,
                reorder = reorder,
                orders = when (sortBy) {
                    OrderSort.RECENT -> filtered // already newest first
                    OrderSort.OLDEST -> filtered.reversed()
                    OrderSort.TOTAL -> filtered.sortedByDescending { it.total }
                    OrderSort.STATUS -> filtered.sortedBy { it.order.status.ordinal }
                },
                totalOrders = d.orders.size,
                statusFilter = filter,
                sort = sortBy,
                // Purchases are newest first, so the first one seen per ingredient is the latest.
                lastSupplier = d.purchases.filter { it.ingredientId != null && it.supplier.isNotBlank() }
                    .distinctBy { it.ingredientId }
                    .associate { it.ingredientId!! to it.supplier },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PurchasingUiState())

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    fun setStatusFilter(status: OrderStatus?) { statusFilter.value = status }
    fun setSort(value: OrderSort) { sort.value = value }

    /** Lines for a new order pre-filled from the reorder suggestions. */
    fun suggestedLines(): List<OrderLine> = uiState.value.reorder.map {
        OrderLine(it.ingredient, it.suggestedOrder, it.ingredient.unitCost)
    }

    /** Creates ([existing] null) or updates a draft order. */
    fun saveOrder(existing: PurchaseOrder?, supplier: String, supplierNote: String, lines: List<OrderLine>) {
        viewModelScope.launch {
            val order = (existing ?: PurchaseOrder(
                branchId = branchId.value,
                supplier = "",
                supplierNote = "",
                status = OrderStatus.DRAFT,
                createdDay = Dates.todayEpochDay(),
            )).copy(supplier = supplier.trim(), supplierNote = supplierNote.trim())
            repository.saveOrder(
                order,
                lines.map {
                    PurchaseOrderItem(
                        orderId = order.id,
                        ingredientId = it.ingredient.id,
                        ingredientName = it.ingredient.name,
                        unit = it.ingredient.unit,
                        quantity = it.quantity,
                        unitCost = it.unitCost,
                    )
                },
            )
            _messages.send(if (existing == null) "Draft order created" else "Order updated")
        }
    }

    fun deleteOrder(order: PurchaseOrder) {
        viewModelScope.launch { repository.deleteOrder(order.id) }
    }

    fun markSent(order: PurchaseOrder) {
        viewModelScope.launch { repository.markOrderSent(order.id, Dates.todayEpochDay()) }
    }

    fun receiveOrder(order: PurchaseOrder, lines: List<ReceivedLine>, day: Long) {
        viewModelScope.launch {
            repository.receiveOrder(order.id, lines, day)
            _messages.send("Order from ${order.supplier} received — stock updated")
        }
    }

    /** Single delivery without an order (kept from the previous version). */
    fun receive(ingredient: Ingredient, quantity: Double, unitCost: Double, expiry: Long?, epochDay: Long, supplier: String) {
        viewModelScope.launch {
            repository.receiveDelivery(ingredient.id, quantity, unitCost, expiry, epochDay, supplier.trim())
            _messages.send("Received ${quantity.asQuantity()} ${ingredient.unit} ${ingredient.name}")
        }
    }

    /** Finds a scanned product in this branch's inventory; null if it isn't there. */
    suspend fun findByBarcode(barcode: String): Ingredient? = repository.findIngredientByBarcode(barcode, branchId.value)

    /** Plain-text purchase order for sharing with the supplier (e.g. WhatsApp, email). */
    fun orderShareText(order: OrderWithItems, restaurantName: String): String = buildString {
        appendLine("Purchase order — $restaurantName (${uiState.value.branchName})")
        appendLine("To: ${order.order.supplier}")
        appendLine("Date: ${Dates.format(Dates.todayEpochDay())}")
        appendLine()
        order.items.forEach { appendLine("• ${it.ingredientName}: ${it.quantity.asQuantity()} ${it.unit}") }
        appendLine()
        append("Estimated total: ${order.total.asMoney()}")
    }

    private companion object {
        val ORDERABLE = setOf(Urgency.ORDER_NOW, Urgency.ORDER_SOON, Urgency.NO_RECENT_USE)
    }
}

// ---------------------------------------------------------------- Sales (manager)

data class ManagerSalesUiState(
    val sales: List<DailySales> = emptyList(),
    val last7Days: Double = 0.0,
    val last30Days: Double = 0.0,
    val todayEntered: Boolean = false,
)

/** Manager's daily sales entry for this device's branch. */
@OptIn(ExperimentalCoroutinesApi::class)
class ManagerSalesViewModel(
    private val repository: KitchenRepository,
    private val branchId: StateFlow<Long>,
) : ViewModel() {

    val uiState: StateFlow<ManagerSalesUiState> =
        branchId.flatMapLatest { repository.sales(it) }.map { sales ->
            val today = Dates.todayEpochDay()
            ManagerSalesUiState(
                sales = sales.filter { it.epochDay > today - 60 },
                last7Days = sales.filter { it.epochDay > today - 7 }.sumOf { it.amount },
                last30Days = sales.filter { it.epochDay > today - 30 }.sumOf { it.amount },
                todayEntered = sales.any { it.epochDay == today },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ManagerSalesUiState())

    val currentBranchId: Long get() = branchId.value

    fun save(epochDay: Long, amount: Double) {
        viewModelScope.launch { repository.saveSales(branchId.value, epochDay, amount) }
    }

    fun delete(sales: DailySales) {
        viewModelScope.launch { repository.deleteSales(sales) }
    }
}
