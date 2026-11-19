package com.arise.smartkitchenai.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Ingredient::class, WasteEntry::class, UsageEntry::class, KnownProduct::class,
        Branch::class, DailySales::class, Purchase::class,
        PurchaseOrder::class, PurchaseOrderItem::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class KitchenDatabase : RoomDatabase() {
    abstract fun kitchenDao(): KitchenDao
}

/** Creates the first branch on a fresh install (upgrades get it from MIGRATION_2_3). */
val CREATE_DEFAULT_BRANCH = object : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT OR IGNORE INTO `branches` (`id`, `name`) VALUES ($DEFAULT_BRANCH_ID, 'Main Kitchen')")
    }
}

/** v1 -> v2: barcode on ingredients, usage tracking, remembered barcodes. Keeps existing data. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `ingredients` ADD COLUMN `barcode` TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ingredients_barcode` ON `ingredients` (`barcode`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `usage_entries` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`ingredientId` INTEGER NOT NULL, " +
                "`quantity` REAL NOT NULL, " +
                "`epochDay` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_usage_entries_ingredientId_epochDay` " +
                "ON `usage_entries` (`ingredientId`, `epochDay`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `known_products` (" +
                "`barcode` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`category` TEXT NOT NULL, " +
                "`unit` TEXT NOT NULL, " +
                "`unitCost` REAL NOT NULL, " +
                "PRIMARY KEY(`barcode`))"
        )
    }
}

/**
 * v2 -> v3: branches, daily sales, purchases, branch on every record, cost snapshot on usage.
 * All existing data is assigned to the default "Main Kitchen" branch.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `branches` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)"
        )
        db.execSQL("INSERT OR IGNORE INTO `branches` (`id`, `name`) VALUES ($DEFAULT_BRANCH_ID, 'Main Kitchen')")

        db.execSQL("ALTER TABLE `ingredients` ADD COLUMN `branchId` INTEGER NOT NULL DEFAULT 1")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ingredients_branchId` ON `ingredients` (`branchId`)")

        db.execSQL("ALTER TABLE `waste_entries` ADD COLUMN `branchId` INTEGER NOT NULL DEFAULT 1")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_waste_entries_branchId_epochDay` " +
                "ON `waste_entries` (`branchId`, `epochDay`)"
        )

        db.execSQL("ALTER TABLE `usage_entries` ADD COLUMN `branchId` INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE `usage_entries` ADD COLUMN `unitCost` REAL NOT NULL DEFAULT 0")
        // Best estimate for old usage records: the ingredient's current cost.
        db.execSQL(
            "UPDATE `usage_entries` SET `unitCost` = COALESCE(" +
                "(SELECT `unitCost` FROM `ingredients` WHERE `ingredients`.`id` = `usage_entries`.`ingredientId`), 0)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_usage_entries_branchId_epochDay` " +
                "ON `usage_entries` (`branchId`, `epochDay`)"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `daily_sales` (" +
                "`branchId` INTEGER NOT NULL, `epochDay` INTEGER NOT NULL, `amount` REAL NOT NULL, " +
                "PRIMARY KEY(`branchId`, `epochDay`))"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `purchases` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`branchId` INTEGER NOT NULL, " +
                "`ingredientId` INTEGER, " +
                "`ingredientName` TEXT NOT NULL, " +
                "`quantity` REAL NOT NULL, " +
                "`unit` TEXT NOT NULL, " +
                "`unitCost` REAL NOT NULL, " +
                "`totalCost` REAL NOT NULL, " +
                "`epochDay` INTEGER NOT NULL, " +
                "`supplier` TEXT NOT NULL)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_purchases_branchId_epochDay` ON `purchases` (`branchId`, `epochDay`)"
        )
    }
}

/** v3 -> v4: purchase orders and their items. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `purchase_orders` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`branchId` INTEGER NOT NULL, " +
                "`supplier` TEXT NOT NULL, " +
                "`supplierNote` TEXT NOT NULL, " +
                "`status` TEXT NOT NULL, " +
                "`createdDay` INTEGER NOT NULL, " +
                "`sentDay` INTEGER, " +
                "`receivedDay` INTEGER)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_purchase_orders_branchId_status` ON `purchase_orders` (`branchId`, `status`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `purchase_order_items` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`orderId` INTEGER NOT NULL, " +
                "`ingredientId` INTEGER NOT NULL, " +
                "`ingredientName` TEXT NOT NULL, " +
                "`unit` TEXT NOT NULL, " +
                "`quantity` REAL NOT NULL, " +
                "`unitCost` REAL NOT NULL)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_purchase_order_items_orderId` ON `purchase_order_items` (`orderId`)"
        )
    }
}
