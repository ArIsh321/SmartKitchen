package com.arise.smartkitchenai.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class KitchenDao {

    // ---- Ingredients ----

    @Query("SELECT * FROM ingredients ORDER BY name COLLATE NOCASE")
    abstract fun observeIngredients(): Flow<List<Ingredient>>

    @Query("SELECT * FROM ingredients WHERE id = :id")
    abstract suspend fun getIngredient(id: Long): Ingredient?

    @Query("SELECT * FROM ingredients WHERE barcode = :barcode LIMIT 1")
    abstract suspend fun findByBarcode(barcode: String): Ingredient?

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

    @Query("SELECT * FROM waste_entries ORDER BY epochDay DESC, id DESC")
    abstract fun observeWaste(): Flow<List<WasteEntry>>

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
            )
        )
        upsertIngredient(current.copy(quantity = (current.quantity - quantity).coerceAtLeast(0.0)))
    }

    // ---- Usage ----

    @Query("SELECT * FROM usage_entries ORDER BY epochDay")
    abstract fun observeUsage(): Flow<List<UsageEntry>>

    @Insert
    abstract suspend fun insertUsage(entry: UsageEntry)

    /** Logs usage and deducts it from stock atomically. */
    @Transaction
    open suspend fun recordUsage(ingredientId: Long, quantity: Double, epochDay: Long) {
        val current = getIngredient(ingredientId) ?: return
        insertUsage(UsageEntry(ingredientId = ingredientId, quantity = quantity, epochDay = epochDay))
        upsertIngredient(current.copy(quantity = (current.quantity - quantity).coerceAtLeast(0.0)))
    }
}
