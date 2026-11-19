package com.arise.smartkitchenai

import android.app.Application
import androidx.room.Room
import com.arise.smartkitchenai.ai.KitchenAi
import com.arise.smartkitchenai.auth.RoleManager
import com.arise.smartkitchenai.data.CREATE_DEFAULT_BRANCH
import com.arise.smartkitchenai.data.KitchenDatabase
import com.arise.smartkitchenai.data.KitchenRepository
import com.arise.smartkitchenai.data.MIGRATION_1_2
import com.arise.smartkitchenai.data.MIGRATION_2_3
import com.arise.smartkitchenai.data.MIGRATION_3_4
import com.arise.smartkitchenai.data.ProductLookup
import com.arise.smartkitchenai.settings.AppSettings

/** Simple manual DI container for the app. */
class KitchenApp : Application() {

    private val database: KitchenDatabase by lazy {
        Room.databaseBuilder(this, KitchenDatabase::class.java, "smart_kitchen.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .addCallback(CREATE_DEFAULT_BRANCH)
            .build()
    }

    val repository: KitchenRepository by lazy {
        KitchenRepository(database.kitchenDao(), ProductLookup())
    }

    val kitchenAi: KitchenAi by lazy { KitchenAi(this) }

    val roleManager: RoleManager by lazy { RoleManager(this) }

    val settings: AppSettings by lazy { AppSettings(this) }

    override fun onCreate() {
        super.onCreate()
        // Firebase auto-initializes only when google-services.json is present.
        if (kitchenAi.isConfigured) installAppCheck()
    }
}
