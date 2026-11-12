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

class KitchenRepository(
    private val dao: KitchenDao,
    private val productLookup: ProductLookup,
) {

    val ingredients: Flow<List<Ingredient>> = dao.observeIngredients()
    val wasteEntries: Flow<List<WasteEntry>> = dao.observeWaste()
    val usageEntries: Flow<List<UsageEntry>> = dao.observeUsage()

    suspend fun saveIngredient(ingredient: Ingredient) = dao.saveIngredient(ingredient)

    suspend fun deleteIngredient(ingredient: Ingredient) = dao.deleteIngredient(ingredient)

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

    suspend fun lookupBarcode(barcode: String): BarcodeResult {
        dao.findByBarcode(barcode)?.let { return BarcodeResult.InInventory(it) }
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
