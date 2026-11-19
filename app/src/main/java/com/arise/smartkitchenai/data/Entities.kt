package com.arise.smartkitchenai.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** The branch every pre-existing record was moved into by the v2 -> v3 migration. */
const val DEFAULT_BRANCH_ID = 1L

/** A restaurant branch/location. */
@Entity(tableName = "branches")
data class Branch(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

/**
 * An ingredient held in stock at one branch. Dates are stored as epoch days (days since
 * 1970-01-01) so they are timezone-independent and easy to compare.
 */
@Entity(
    tableName = "ingredients",
    indices = [Index("expiryEpochDay"), Index("barcode"), Index("branchId")],
)
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
    @ColumnInfo(defaultValue = "1") val branchId: Long = DEFAULT_BRANCH_ID,
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
@Entity(tableName = "waste_entries", indices = [Index("epochDay"), Index("branchId", "epochDay")])
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
    @ColumnInfo(defaultValue = "1") val branchId: Long = DEFAULT_BRANCH_ID,
)

/**
 * Quantity of an ingredient used in cooking on a given day. Source data for demand prediction
 * and food cost. [unitCost] is the ingredient's cost at the time of use.
 */
@Entity(
    tableName = "usage_entries",
    indices = [Index("ingredientId", "epochDay"), Index("branchId", "epochDay")],
)
data class UsageEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ingredientId: Long,
    val quantity: Double,
    val epochDay: Long,
    @ColumnInfo(defaultValue = "1") val branchId: Long = DEFAULT_BRANCH_ID,
    @ColumnInfo(defaultValue = "0") val unitCost: Double = 0.0,
)

val UsageEntry.cost: Double get() = quantity * unitCost

/** Total sales (revenue) of one branch on one day, typed in from the till/POS report. */
@Entity(tableName = "daily_sales", primaryKeys = ["branchId", "epochDay"])
data class DailySales(
    val branchId: Long,
    val epochDay: Long,
    val amount: Double,
)

/** A received delivery. Name, unit and cost are snapshotted for reports. */
@Entity(tableName = "purchases", indices = [Index("branchId", "epochDay")])
data class Purchase(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val branchId: Long,
    val ingredientId: Long?,
    val ingredientName: String,
    val quantity: Double,
    val unit: String,
    val unitCost: Double,
    val totalCost: Double,
    val epochDay: Long,
    val supplier: String,
)

enum class OrderStatus(val label: String) {
    DRAFT("Draft"),
    SENT("Sent to supplier"),
    RECEIVED("Received"),
}

/** A purchase order to one supplier. Receiving it adds stock and records [Purchase]s. */
@Entity(tableName = "purchase_orders", indices = [Index("branchId", "status")])
data class PurchaseOrder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val branchId: Long,
    val supplier: String,
    /** Short supplier note, e.g. "Produce • Local". */
    val supplierNote: String,
    val status: OrderStatus,
    val createdDay: Long,
    val sentDay: Long? = null,
    val receivedDay: Long? = null,
)

/** One line of a purchase order. Name/unit are snapshotted. */
@Entity(tableName = "purchase_order_items", indices = [Index("orderId")])
data class PurchaseOrderItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long,
    val ingredientId: Long,
    val ingredientName: String,
    val unit: String,
    val quantity: Double,
    val unitCost: Double,
)

val PurchaseOrderItem.lineTotal: Double get() = quantity * unitCost

/**
 * Product details remembered per barcode, so a scanned barcode fills the form next time
 * even if the ingredient was deleted or the product isn't in Open Food Facts. Shared by all branches.
 */
@Entity(tableName = "known_products")
data class KnownProduct(
    @PrimaryKey val barcode: String,
    val name: String,
    val category: String,
    val unit: String,
    val unitCost: Double,
)
