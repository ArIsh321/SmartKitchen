package com.arise.smartkitchenai.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arise.smartkitchenai.KitchenApp
import com.arise.smartkitchenai.ui.expiry.ExpiryViewModel
import com.arise.smartkitchenai.ui.insights.InsightsViewModel
import com.arise.smartkitchenai.ui.inventory.InventoryViewModel
import com.arise.smartkitchenai.ui.manager.ManagerSalesViewModel
import com.arise.smartkitchenai.ui.manager.PurchasingViewModel
import com.arise.smartkitchenai.ui.owner.OverviewViewModel
import com.arise.smartkitchenai.ui.owner.ProfileViewModel
import com.arise.smartkitchenai.ui.owner.ReportsViewModel
import com.arise.smartkitchenai.ui.staff.StaffViewModel
import com.arise.smartkitchenai.ui.waste.WasteViewModel

/**
 * Manager/Staff ViewModels work in this device's branch; Owner ViewModels use the Owner's
 * branch filter (one branch or all).
 */
object AppViewModelFactory {
    val Factory: ViewModelProvider.Factory = viewModelFactory {
        // Manager
        initializer { app().run { InventoryViewModel(repository, settings.deviceBranchId) } }
        initializer { app().run { WasteViewModel(repository, settings.deviceBranchId) } }
        initializer { app().run { ExpiryViewModel(repository, settings.deviceBranchId) } }
        initializer { app().run { PurchasingViewModel(repository, settings.deviceBranchId) } }
        initializer { app().run { ManagerSalesViewModel(repository, settings.deviceBranchId) } }
        // Kitchen staff
        initializer { app().run { StaffViewModel(repository, settings.deviceBranchId) } }
        // Owner
        initializer { app().run { OverviewViewModel(repository, kitchenAi, settings) } }
        initializer { app().run { ReportsViewModel(repository, settings) } }
        initializer { app().run { InsightsViewModel(repository, kitchenAi, settings.ownerBranchFilter) } }
        initializer { app().run { ProfileViewModel(repository, settings, roleManager) } }
    }
}

private fun CreationExtras.app(): KitchenApp = this[APPLICATION_KEY] as KitchenApp
