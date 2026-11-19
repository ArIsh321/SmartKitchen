package com.arise.smartkitchenai.settings

import android.content.Context
import androidx.core.content.edit
import com.arise.smartkitchenai.data.DEFAULT_BRANCH_ID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Device-level settings managed by the Owner in Profile. */
class AppSettings(context: Context) {

    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private val _restaurantName = MutableStateFlow(prefs.getString(KEY_RESTAURANT, null) ?: "My Restaurant")
    val restaurantName: StateFlow<String> = _restaurantName.asStateFlow()

    private val _ownerName = MutableStateFlow(prefs.getString(KEY_OWNER, null) ?: "Owner")
    val ownerName: StateFlow<String> = _ownerName.asStateFlow()

    private val _deviceBranchId = MutableStateFlow(prefs.getLong(KEY_DEVICE_BRANCH, DEFAULT_BRANCH_ID))
    /** The branch Manager and Kitchen staff work in on this device. */
    val deviceBranchId: StateFlow<Long> = _deviceBranchId.asStateFlow()

    private val _ownerBranchFilter = MutableStateFlow(
        prefs.getLong(KEY_OWNER_FILTER, ALL_BRANCHES).takeIf { it != ALL_BRANCHES }
    )
    /** Branch shown on the Owner's screens; null = all branches. */
    val ownerBranchFilter: StateFlow<Long?> = _ownerBranchFilter.asStateFlow()

    fun setRestaurantName(name: String) {
        _restaurantName.value = name
        prefs.edit { putString(KEY_RESTAURANT, name) }
    }

    fun setOwnerName(name: String) {
        _ownerName.value = name
        prefs.edit { putString(KEY_OWNER, name) }
    }

    fun setDeviceBranch(branchId: Long) {
        _deviceBranchId.value = branchId
        prefs.edit { putLong(KEY_DEVICE_BRANCH, branchId) }
    }

    fun setOwnerBranchFilter(branchId: Long?) {
        _ownerBranchFilter.value = branchId
        prefs.edit { putLong(KEY_OWNER_FILTER, branchId ?: ALL_BRANCHES) }
    }

    private companion object {
        const val KEY_RESTAURANT = "restaurant_name"
        const val KEY_OWNER = "owner_name"
        const val KEY_DEVICE_BRANCH = "device_branch"
        const val KEY_OWNER_FILTER = "owner_branch_filter"
        const val ALL_BRANCHES = -1L
    }
}
