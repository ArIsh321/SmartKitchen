package com.arise.smartkitchenai.ai

import com.arise.smartkitchenai.data.Ingredient
import com.arise.smartkitchenai.data.stockValue
import com.arise.smartkitchenai.ui.insights.DemandForecaster
import com.arise.smartkitchenai.ui.insights.Forecast
import com.arise.smartkitchenai.ui.owner.OwnerData
import com.arise.smartkitchenai.ui.owner.summary
import com.arise.smartkitchenai.util.DayRange
import com.arise.smartkitchenai.util.Dates
import com.arise.smartkitchenai.util.asMoney
import com.arise.smartkitchenai.util.asQuantity

/** Builds the numbers-only text summary of the kitchen that is sent to Gemini. */
object KitchenSummary {

    fun build(data: OwnerData, forecasts: List<Forecast>, today: Long, branchName: String): String {
        val last30 = DayRange(today - 29, today)
        val finance = data.summary(last30)
        val waste30 = data.waste.filter { it.epochDay in last30 }
        val wasteByIngredient = waste30.groupBy { it.ingredientId }
        val forecastById = forecasts.associateBy { it.ingredient.id }

        // Keep the prompt small: most costly waste first, then highest stock value.
        val relevant = data.ingredients
            .sortedWith(
                compareByDescending<Ingredient> { ing -> wasteByIngredient[ing.id].orEmpty().sumOf { it.costLoss } }
                    .thenByDescending { it.stockValue }
            )
            .take(60)

        return buildString {
            appendLine("Restaurant kitchen data for: $branchName. Today: ${Dates.format(today)}.")
            appendLine()
            appendLine("LAST 30 DAYS:")
            appendLine("- Revenue: ${finance.revenue.asMoney()} (sales recorded on ${finance.salesDaysRecorded} days)")
            appendLine("- Food used in cooking: ${finance.foodUsedCost.asMoney()}")
            appendLine("- Waste: ${finance.wasteCost.asMoney()}")
            finance.foodCostPercent?.let { appendLine("- Food cost (used + wasted) as % of revenue: ${it.asQuantity()}%") }
            appendLine("- Purchases: ${finance.purchases.asMoney()}")
            appendLine()
            appendLine("INGREDIENTS (stock, cost, expiry, usage, waste in last 30 days):")
            relevant.forEach { ing ->
                val f = forecastById[ing.id]
                val waste = wasteByIngredient[ing.id].orEmpty()
                val parts = mutableListOf(
                    "stock ${ing.quantity.asQuantity()} ${ing.unit}",
                    "cost ${ing.unitCost.asMoney()}/${ing.unit}",
                    "min level ${ing.minStock.asQuantity()}",
                )
                ing.expiryEpochDay?.let { parts += "expires in ${it - today} days" }
                val avg = f?.avgDailyUse
                when {
                    avg == null -> parts += "usage: not enough data"
                    avg == 0.0 -> parts += "not used in last ${DemandForecaster.WINDOW_DAYS} days"
                    else -> {
                        parts += "avg use ${avg.asQuantity()} ${ing.unit}/day"
                        f.daysLeft?.let { parts += "about ${it.asQuantity()} days of stock left" }
                    }
                }
                if (waste.isNotEmpty()) {
                    val topReason = waste.groupBy { it.reason }.maxBy { (_, l) -> l.sumOf { it.costLoss } }.key
                    parts += "wasted ${waste.sumOf { it.quantity }.asQuantity()} ${ing.unit} " +
                        "(${waste.sumOf { it.costLoss }.asMoney()}), mostly ${topReason.label}"
                }
                val category = if (ing.category.isNotBlank()) " [${ing.category}]" else ""
                appendLine("- ${ing.name}$category: ${parts.joinToString(", ")}")
            }
            appendLine()
            appendLine("WASTE BY REASON (last 30 days):")
            waste30.groupBy { it.reason }
                .map { (reason, list) -> reason.label to list.sumOf { it.costLoss } }
                .sortedByDescending { it.second }
                .forEach { (reason, cost) -> appendLine("- $reason: ${cost.asMoney()}") }
            appendLine()
            appendLine("Total stock value: ${data.ingredients.sumOf { it.stockValue }.asMoney()}")
        }
    }
}
