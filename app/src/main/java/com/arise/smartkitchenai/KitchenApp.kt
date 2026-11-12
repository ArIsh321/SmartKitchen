package com.arise.smartkitchenai

import android.app.Application
import androidx.room.Room
import com.arise.smartkitchenai.ai.KitchenAi
import com.arise.smartkitchenai.data.KitchenDatabase
import com.arise.smartkitchenai.data.KitchenRepository
import com.arise.smartkitchenai.data.MIGRATION_1_2
import com.arise.smartkitchenai.data.ProductLookup

/** Simple manual DI container for the app. */
class KitchenApp : Application() {

    private val database: KitchenDatabase by lazy {
        Room.databaseBuilder(this, KitchenDatabase::class.java, "smart_kitchen.db")
            .addMigrations(MIGRATION_1_2)
            .build()
    }

    val repository: KitchenRepository by lazy {
        KitchenRepository(database.kitchenDao(), ProductLookup())
    }

    val kitchenAi: KitchenAi by lazy { KitchenAi(this) }

    override fun onCreate() {
        super.onCreate()
        // Firebase auto-initializes only when google-services.json is present.
        if (kitchenAi.isConfigured) installAppCheck()
    }
}
