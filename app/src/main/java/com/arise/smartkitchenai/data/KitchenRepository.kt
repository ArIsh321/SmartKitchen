package com.arise.smartkitchenai.data

import kotlinx.coroutines.flow.Flow
import org.json.JSONException
import java.io.IOException

/** Result of scanning a barcode, in the order the sources are checked. */
sealed interface BarcodeResult {
    val barcode: String

    /** The barcode already belongs to an ingredient in stock. */
    data class InInventory(val ingredient: Ingredient) : BarcodeResult {
        override val barcode: String get() = ingredient.barcode.orEmpty()
    }

    /** Details found in saved products or Open Food Facts. [unitCost] is only known for saved products. */
    data class Found(
        override val barcode: String,
        val name: String,
        val category: String,
        val unit: String,
        val unitCost: Double?,
        val source: String,
    ) : BarcodeResult

    data class NotFound(override val barcode: String) : BarcodeResult

    data class LookupFailed(override val barcode: String) : BarcodeResult
}

/** Functions taking `branchId: Long?` return one branch's data, or all branches when null. */
class KitchenRepository(
    private val dao: KitchenDao,
    private val productLookup: ProductLookup,
) {

    // ---- Branches ----

    val branches: Flow<List<Branch>> = dao.observeBranches()

    suspend fun addBranch(name: String): Long = dao.insertBranch(Branch(name = name))

    suspend fun renameBranch(branch: Branch, name: String) = dao.updateBranch(branch.copy(name = name))

    // ---- Data streams ----

    fun ingredients(branchId: Long?): Flow<List<Ingredient>> = dao.observeIngredients(branchId)
    fun wasteEntries(branchId: Long?): Flow<List<WasteEntry>> = dao.observeWaste(branchId)
    fun usageEntries(branchId: Long?): Flow<List<UsageEntry>> = dao.observeUsage(branchId)
    fun sales(branchId: Long?): Flow<List<DailySales>> = dao.observeSales(branchId)
    fun purchases(branchId: Long?): Flow<List<Purchase>> = dao.observePurchases(branchId)

    // ---- Ingredients ----

    suspend fun saveIngredient(ingredient: Ingredient) = dao.saveIngredient(ingredient)

    suspend fun deleteIngredient(ingredient: Ingredient) = dao.deleteIngredient(ingredient)

    // ---- Waste / usage ----

    suspend fun recordWaste(
        ingredientId: Long,
        quantity: Double,
        reason: WasteReason,
        epochDay: Long,
        note: String = "",
    ) = dao.recordWaste(ingredientId, quantity, reason, epochDay, note)

    suspend fun deleteWaste(entry: WasteEntry) = dao.deleteWaste(entry)

    suspend fun recordUsage(ingredientId: Long, quantity: Double, epochDay: Long) =
        dao.recordUsage(ingredientId, quantity, epochDay)

    // ---- Sales ----

    suspend fun saveSales(branchId: Long, epochDay: Long, amount: Double) =
        dao.upsertSales(DailySales(branchId, epochDay, amount))

    suspend fun deleteSales(sales: DailySales) = dao.deleteSales(sales)

    // ---- Purchasing ----

    suspend fun receiveDelivery(
        ingredientId: Long,
        quantity: Double,
        unitCost: Double,
        expiryEpochDay: Long?,
        epochDay: Long,
        supplier: String,
    ) = dao.receiveDelivery(ingredientId, quantity, unitCost, expiryEpochDay, epochDay, supplier)

    // ---- Purchase orders ----

    fun orders(branchId: Long): Flow<List<OrderWithItems>> = dao.observeOrders(branchId)

    suspend fun saveOrder(order: PurchaseOrder, items: List<PurchaseOrderItem>) = dao.saveOrder(order, items)

    suspend fun deleteOrder(orderId: Long) = dao.deleteOrder(orderId)

    suspend fun markOrderSent(orderId: Long, day: Long) = dao.markSent(orderId, day)

    suspend fun receiveOrder(orderId: Long, lines: List<ReceivedLine>, day: Long) = dao.receiveOrder(orderId, lines, day)

    // ---- Barcodes ----

    /** Finds an ingredient already in stock at [branchId] (used by staff and purchasing). */
    suspend fun findIngredientByBarcode(barcode: String, branchId: Long): Ingredient? =
        dao.findByBarcode(barcode, branchId)

    suspend fun lookupBarcode(barcode: String, branchId: Long): BarcodeResult {
        dao.findByBarcode(barcode, branchId)?.let { return BarcodeResult.InInventory(it) }
        dao.getKnownProduct(barcode)?.let {
            return BarcodeResult.Found(barcode, it.name, it.category, it.unit, it.unitCost, source = "your saved products")
        }
        return try {
            productLookup.lookup(barcode)
                ?.let { BarcodeResult.Found(barcode, it.name, it.category, it.unit, unitCost = null, source = "Open Food Facts") }
                ?: BarcodeResult.NotFound(barcode)
        } catch (e: IOException) {
            BarcodeResult.LookupFailed(barcode)
        } catch (e: JSONException) {
            BarcodeResult.LookupFailed(barcode)
        }
    }
}
