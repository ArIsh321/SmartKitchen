package com.arise.smartkitchenai.ui.insights

import com.arise.smartkitchenai.data.Ingredient
import com.arise.smartkitchenai.data.UsageEntry
import kotlin.math.ceil
import kotlin.math.max

enum class Urgency(val label: String) {
    ORDER_NOW("Order now"),
    ORDER_SOON("Order soon"),
    OK("OK"),
    NO_RECENT_USE("No recent use"),
    NOT_ENOUGH_DATA("Not enough data yet"),
}

data class Forecast(
    val ingredient: Ingredient,
    val avgDailyUse: Double?,
    val daysLeft: Double?,
    val suggestedOrder: Double,
    val orderCost: Double,
    val urgency: Urgency,
)

/** Simple, explainable demand forecast from recorded usage. */
object DemandForecaster {
    const val WINDOW_DAYS = 14      // look back this far for average use
    const val MIN_HISTORY_DAYS = 3  // need at least this much history to predict
    const val COVER_DAYS = 7        // order enough to last this long
    const val SAFETY_DAYS = 2       // extra buffer on top

    fun forecast(ingredient: Ingredient, usage: List<UsageEntry>, today: Long): Forecast {
        val firstDay = usage.minOfOrNull { it.epochDay }
        val historyDays = firstDay?.let { (today - it + 1).coerceAtMost(WINDOW_DAYS.toLong()) } ?: 0
        if (historyDays < MIN_HISTORY_DAYS) {
            return Forecast(ingredient, null, null, 0.0, 0.0, Urgency.NOT_ENOUGH_DATA)
        }

        val used = usage.filter { it.epochDay in (today - WINDOW_DAYS + 1)..today }.sumOf { it.quantity }
        val avg = used / historyDays
        if (avg <= 0.0) {
            // Not used recently: only top up to the minimum level, if below it.
            val order = roundUp(max(0.0, ingredient.minStock - ingredient.quantity))
            return Forecast(ingredient, 0.0, null, order, order * ingredient.unitCost, Urgency.NO_RECENT_USE)
        }

        val daysLeft = ingredient.quantity / avg
        val target = avg * COVER_DAYS + max(ingredient.minStock, avg * SAFETY_DAYS)
        val order = roundUp(max(0.0, target - ingredient.quantity))
        val urgency = when {
            daysLeft <= 2 -> Urgency.ORDER_NOW
            daysLeft <= COVER_DAYS -> Urgency.ORDER_SOON
            else -> Urgency.OK
        }
        return Forecast(ingredient, avg, daysLeft, order, order * ingredient.unitCost, urgency)
    }

    private fun roundUp(value: Double): Double = ceil(value * 10) / 10
}
