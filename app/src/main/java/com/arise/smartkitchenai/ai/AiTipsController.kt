package com.arise.smartkitchenai.ai

import com.arise.smartkitchenai.ui.insights.DemandForecaster
import com.arise.smartkitchenai.ui.owner.OwnerData
import com.arise.smartkitchenai.util.Dates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AiState {
    data object NotConfigured : AiState
    /** Configured but nothing generated yet for this branch selection. */
    data object Empty : AiState
    data object Loading : AiState
    data class Ready(val tips: List<AiTip>, val generatedAtMillis: Long) : AiState
    data class Error(val message: String) : AiState
}

/** Shared by the Overview and Insights screens: cached tips for the selected branch + regenerate. */
class AiTipsController(
    private val ai: KitchenAi,
    private val scope: CoroutineScope,
    private val branchFilter: StateFlow<Long?>,
) {
    private val loading = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    val state: StateFlow<AiState> =
        combine(ai.cache, branchFilter, loading, error) { cache, branch, isLoading, message ->
            val cached = cache[KitchenAi.cacheKey(branch)]
            when {
                !ai.isConfigured -> AiState.NotConfigured
                isLoading -> AiState.Loading
                message != null -> AiState.Error(message)
                cached != null -> AiState.Ready(cached.tips, cached.generatedAtMillis)
                else -> AiState.Empty
            }
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), AiState.Empty)

    fun generate(data: OwnerData, branchName: String) {
        if (!ai.isConfigured || loading.value) return
        if (data.ingredients.isEmpty()) {
            error.value = "Add some ingredients first so there is data to analyse."
            return
        }
        val branch = branchFilter.value
        error.value = null
        loading.value = true
        scope.launch {
            try {
                val today = Dates.todayEpochDay()
                val usageById = data.usage.groupBy { it.ingredientId }
                val forecasts = data.ingredients.map { DemandForecaster.forecast(it, usageById[it.id].orEmpty(), today) }
                val tips = ai.generate(KitchenAi.cacheKey(branch), KitchenSummary.build(data, forecasts, today, branchName))
                if (tips.isEmpty()) error.value = "No recommendations returned. Try again."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error.value = "Couldn't get recommendations: ${e.message ?: e.javaClass.simpleName}\n" +
                    "Check your internet connection and that the App Check debug token is registered in Firebase."
            } finally {
                loading.value = false
            }
        }
    }
}
