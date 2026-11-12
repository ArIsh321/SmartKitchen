package com.arise.smartkitchenai.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arise.smartkitchenai.KitchenApp
import com.arise.smartkitchenai.ui.expiry.ExpiryViewModel
import com.arise.smartkitchenai.ui.inventory.InventoryViewModel
import com.arise.smartkitchenai.ui.waste.WasteViewModel

object AppViewModelFactory {
    val Factory: ViewModelProvider.Factory = viewModelFactory {
        initializer { InventoryViewModel(kitchenApp().repository) }
        initializer { WasteViewModel(kitchenApp().repository) }
        initializer { ExpiryViewModel(kitchenApp().repository) }
    }
}

private fun androidx.lifecycle.viewmodel.CreationExtras.kitchenApp(): KitchenApp =
    this[APPLICATION_KEY] as KitchenApp
