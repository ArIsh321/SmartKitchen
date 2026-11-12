package com.arise.smartkitchenai.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * An ingredient held in stock. Dates are stored as epoch days (days since 1970-01-01)
 * so they are timezone-independent and easy to compare.
 */
@Entity(tableName = "ingredients", indices = [Index("expiryEpochDay"), Index("barcode")])
data class Ingredient(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String,
    val unit: String,
    val quantity: Double,
    val unitCost: Double,
    val minStock: Double,
    val expiryEpochDay: Long? = null,
    val barcode: String? = null,
)

val Ingredient.isLowStock: Boolean get() = quantity <= minStock
val Ingredient.stockValue: Double get() = quantity * unitCost

enum class WasteReason(val label: String) {
    SPOILED("Spoiled"),
    EXPIRED("Expired"),
    OVER_PREPARED("Over-prepared"),
    PLATE_WASTE("Plate waste"),
    DAMAGED("Damaged / dropped"),
    OTHER("Other"),
}

/**
 * A waste record. Ingredient name, unit and cost are snapshotted so the history
 * stays accurate even if the ingredient is later edited or deleted.
 */
@Entity(tableName = "waste_entries", indices = [Index("epochDay")])
data class WasteEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ingredientId: Long?,
    val ingredientName: String,
    val quantity: Double,
    val unit: String,
    val reason: WasteReason,
    val costLoss: Double,
    val epochDay: Long,
    val note: String = "",
)

/** Quantity of an ingredient used in cooking on a given day. Source data for demand prediction. */
@Entity(tableName = "usage_entries", indices = [Index("ingredientId", "epochDay")])
data class UsageEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ingredientId: Long,
    val quantity: Double,
    val epochDay: Long,
)

/**
 * Product details remembered per barcode, so a scanned barcode fills the form next time
 * even if the ingredient was deleted or the product isn't in Open Food Facts.
 */
@Entity(tableName = "known_products")
data class KnownProduct(
    @PrimaryKey val barcode: String,
    val name: String,
    val category: String,
    val unit: String,
    val unitCost: Double,
)
