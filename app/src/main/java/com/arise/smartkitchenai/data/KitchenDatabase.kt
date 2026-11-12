package com.arise.smartkitchenai.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Ingredient::class, WasteEntry::class, UsageEntry::class, KnownProduct::class],
    version = 2,
    exportSchema = false,
)
abstract class KitchenDatabase : RoomDatabase() {
    abstract fun kitchenDao(): KitchenDao
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
