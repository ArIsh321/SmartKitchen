package com.arise.smartkitchenai.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** A purchase order with its lines. */
data class OrderWithItems(
    @Embedded val order: PurchaseOrder,
    @Relation(parentColumn = "id", entityColumn = "orderId") val items: List<PurchaseOrderItem>,
) {
    val total: Double get() = items.sumOf { it.lineTotal }
}

/** What actually arrived for one order line when receiving it. */
data class ReceivedLine(val ingredientId: Long, val quantity: Double, val unitCost: Double, val expiryEpochDay: Long?)

/**
 * Queries taking `branchId: Long?` return one branch's data, or all branches when null.
 */
@Dao
abstract class KitchenDao {

    // ---- Branches ----

    @Query("SELECT * FROM branches ORDER BY id")
    abstract fun observeBranches(): Flow<List<Branch>>

    @Insert
    abstract suspend fun insertBranch(branch: Branch): Long

    @Update
    abstract suspend fun updateBranch(branch: Branch)

    // ---- Ingredients ----

    @Query("SELECT * FROM ingredients WHERE (:branchId IS NULL OR branchId = :branchId) ORDER BY name COLLATE NOCASE")
    abstract fun observeIngredients(branchId: Long?): Flow<List<Ingredient>>

    @Query("SELECT * FROM ingredients WHERE id = :id")
    abstract suspend fun getIngredient(id: Long): Ingredient?

    @Query("SELECT * FROM ingredients WHERE barcode = :barcode AND branchId = :branchId LIMIT 1")
    abstract suspend fun findByBarcode(barcode: String, branchId: Long): Ingredient?

    @Upsert
    abstract suspend fun upsertIngredient(ingredient: Ingredient)

    @Delete
    abstract suspend fun deleteIngredient(ingredient: Ingredient)

    /** Saves the ingredient and, if it has a barcode, remembers the product for future scans. */
    @Transaction
    open suspend fun saveIngredient(ingredient: Ingredient) {
        upsertIngredient(ingredient)
        val barcode = ingredient.barcode
        if (!barcode.isNullOrBlank()) {
            upsertKnownProduct(
                KnownProduct(barcode, ingredient.name, ingredient.category, ingredient.unit, ingredient.unitCost)
            )
        }
    }

    // ---- Known products (barcode memory) ----

    @Query("SELECT * FROM known_products WHERE barcode = :barcode")
    abstract suspend fun getKnownProduct(barcode: String): KnownProduct?

    @Upsert
    abstract suspend fun upsertKnownProduct(product: KnownProduct)

    // ---- Waste ----

    @Query("SELECT * FROM waste_entries WHERE (:branchId IS NULL OR branchId = :branchId) ORDER BY epochDay DESC, id DESC")
    abstract fun observeWaste(branchId: Long?): Flow<List<WasteEntry>>

    @Insert
    abstract suspend fun insertWaste(entry: WasteEntry)

    @Delete
    abstract suspend fun deleteWaste(entry: WasteEntry)

    /** Logs waste and deducts it from stock atomically, using the latest stored quantity. */
    @Transaction
    open suspend fun recordWaste(
        ingredientId: Long,
        quantity: Double,
        reason: WasteReason,
        epochDay: Long,
        note: String,
    ) {
        val current = getIngredient(ingredientId) ?: return
        insertWaste(
            WasteEntry(
                ingredientId = current.id,
                ingredientName = current.name,
                quantity = quantity,
                unit = current.unit,
                reason = reason,
                costLoss = quantity * current.unitCost,
                epochDay = epochDay,
                note = note,
                branchId = current.branchId,
            )
        )
        upsertIngredient(current.copy(quantity = (current.quantity - quantity).coerceAtLeast(0.0)))
    }

    // ---- Usage ----

    @Query("SELECT * FROM usage_entries WHERE (:branchId IS NULL OR branchId = :branchId) ORDER BY epochDay")
    abstract fun observeUsage(branchId: Long?): Flow<List<UsageEntry>>

    @Insert
    abstract suspend fun insertUsage(entry: UsageEntry)

    /** Logs usage (with today's cost) and deducts it from stock atomically. */
    @Transaction
    open suspend fun recordUsage(ingredientId: Long, quantity: Double, epochDay: Long) {
        val current = getIngredient(ingredientId) ?: return
        insertUsage(
            UsageEntry(
                ingredientId = ingredientId,
                quantity = quantity,
                epochDay = epochDay,
                branchId = current.branchId,
                unitCost = current.unitCost,
            )
        )
        upsertIngredient(current.copy(quantity = (current.quantity - quantity).coerceAtLeast(0.0)))
    }

    // ---- Daily sales ----

    @Query("SELECT * FROM daily_sales WHERE (:branchId IS NULL OR branchId = :branchId) ORDER BY epochDay DESC")
    abstract fun observeSales(branchId: Long?): Flow<List<DailySales>>

    @Upsert
    abstract suspend fun upsertSales(sales: DailySales)

    @Delete
    abstract suspend fun deleteSales(sales: DailySales)

    // ---- Purchases ----

    @Query("SELECT * FROM purchases WHERE (:branchId IS NULL OR branchId = :branchId) ORDER BY epochDay DESC, id DESC")
    abstract fun observePurchases(branchId: Long?): Flow<List<Purchase>>

    @Insert
    abstract suspend fun insertPurchase(purchase: Purchase)

    /**
     * Records a delivery: adds stock, updates the cost per unit, and keeps the earliest expiry
     * date if old stock is still on hand (so older stock isn't hidden from the expiry tracker).
     */
    @Transaction
    open suspend fun receiveDelivery(
        ingredientId: Long,
        quantity: Double,
        unitCost: Double,
        expiryEpochDay: Long?,
        epochDay: Long,
        supplier: String,
    ) {
        val current = getIngredient(ingredientId) ?: return
        insertPurchase(
            Purchase(
                branchId = current.branchId,
                ingredientId = current.id,
                ingredientName = current.name,
                quantity = quantity,
                unit = current.unit,
                unitCost = unitCost,
                totalCost = quantity * unitCost,
                epochDay = epochDay,
                supplier = supplier,
            )
        )
        val keptExpiry = when {
            expiryEpochDay == null -> current.expiryEpochDay
            current.quantity > 0 && current.expiryEpochDay != null -> minOf(current.expiryEpochDay, expiryEpochDay)
            else -> expiryEpochDay
        }
        upsertIngredient(
            current.copy(quantity = current.quantity + quantity, unitCost = unitCost, expiryEpochDay = keptExpiry)
        )
    }

    // ---- Purchase orders ----

    @Transaction
    @Query("SELECT * FROM purchase_orders WHERE branchId = :branchId ORDER BY createdDay DESC, id DESC")
    abstract fun observeOrders(branchId: Long): Flow<List<OrderWithItems>>

    @Upsert
    abstract suspend fun upsertOrder(order: PurchaseOrder): Long

    @Insert
    abstract suspend fun insertOrderItems(items: List<PurchaseOrderItem>)

    @Query("DELETE FROM purchase_order_items WHERE orderId = :orderId")
    abstract suspend fun deleteOrderItems(orderId: Long)

    @Query("DELETE FROM purchase_orders WHERE id = :orderId")
    abstract suspend fun deleteOrderRow(orderId: Long)

    @Query("SELECT * FROM purchase_orders WHERE id = :orderId")
    abstract suspend fun getOrder(orderId: Long): PurchaseOrder?

    /** Creates or replaces an order and its lines. */
    @Transaction
    open suspend fun saveOrder(order: PurchaseOrder, items: List<PurchaseOrderItem>) {
        // @Upsert returns -1 when it updated an existing row.
        val id = upsertOrder(order).takeIf { it > 0 } ?: order.id
        deleteOrderItems(id)
        insertOrderItems(items.map { it.copy(id = 0, orderId = id) })
    }

    @Transaction
    open suspend fun deleteOrder(orderId: Long) {
        deleteOrderItems(orderId)
        deleteOrderRow(orderId)
    }

    @Query("UPDATE purchase_orders SET status = 'SENT', sentDay = :day WHERE id = :orderId")
    abstract suspend fun markSent(orderId: Long, day: Long)

    /** Receives an order: adds each line to stock, records the purchases, and closes the order. */
    @Transaction
    open suspend fun receiveOrder(orderId: Long, lines: List<ReceivedLine>, day: Long) {
        val order = getOrder(orderId) ?: return
        lines.filter { it.quantity > 0 }.forEach {
            receiveDelivery(it.ingredientId, it.quantity, it.unitCost, it.expiryEpochDay, day, order.supplier)
        }
        upsertOrder(order.copy(status = OrderStatus.RECEIVED, receivedDay = day))
    }
}
