package com.arise.smartkitchenai.ui.owner

import com.arise.smartkitchenai.data.DailySales
import com.arise.smartkitchenai.data.Ingredient
import com.arise.smartkitchenai.data.KitchenRepository
import com.arise.smartkitchenai.data.Purchase
import com.arise.smartkitchenai.data.UsageEntry
import com.arise.smartkitchenai.data.WasteEntry
import com.arise.smartkitchenai.data.WasteReason
import com.arise.smartkitchenai.data.cost
import com.arise.smartkitchenai.data.stockValue
import com.arise.smartkitchenai.util.DayRange
import com.arise.smartkitchenai.util.Dates
import com.arise.smartkitchenai.util.WEEKDAY_SHORT
import com.arise.smartkitchenai.util.dayOfWeek
import com.arise.smartkitchenai.util.startOfWeek
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Everything the Owner's screens need for one branch (or all branches). */
data class OwnerData(
    val ingredients: List<Ingredient> = emptyList(),
    val usage: List<UsageEntry> = emptyList(),
    val waste: List<WasteEntry> = emptyList(),
    val sales: List<DailySales> = emptyList(),
    val purchases: List<Purchase> = emptyList(),
)

fun KitchenRepository.ownerData(branchId: Long?): Flow<OwnerData> =
    combine(
        ingredients(branchId), usageEntries(branchId), wasteEntries(branchId), sales(branchId), purchases(branchId),
    ) { ingredients, usage, waste, sales, purchases -> OwnerData(ingredients, usage, waste, sales, purchases) }

/** Money figures for a date range. */
data class FinanceSummary(
    val revenue: Double = 0.0,
    val foodUsedCost: Double = 0.0,
    val wasteCost: Double = 0.0,
    val purchases: Double = 0.0,
    val salesDaysRecorded: Int = 0,
) {
    /** Food cost = ingredients used in cooking + ingredients wasted. */
    val foodCost: Double get() = foodUsedCost + wasteCost
    val foodCostPercent: Double? get() = if (revenue > 0) foodCost / revenue * 100 else null
    val wastePercentOfPurchases: Double? get() = if (purchases > 0) wasteCost / purchases * 100 else null
}

fun OwnerData.summary(range: DayRange): FinanceSummary {
    val salesInRange = sales.filter { it.epochDay in range }
    return FinanceSummary(
        revenue = salesInRange.sumOf { it.amount },
        foodUsedCost = usage.filter { it.epochDay in range }.sumOf { it.cost },
        wasteCost = waste.filter { it.epochDay in range }.sumOf { it.costLoss },
        purchases = purchases.filter { it.epochDay in range }.sumOf { it.totalCost },
        salesDaysRecorded = salesInRange.map { it.epochDay }.distinct().size,
    )
}

val OwnerData.stockValue: Double get() = ingredients.sumOf { it.stockValue }

fun OwnerData.expiredStockValue(today: Long): Double = ingredients
    .filter { it.expiryEpochDay != null && it.expiryEpochDay < today && it.quantity > 0 }
    .sumOf { it.stockValue }

/** Money lost on one ingredient (grouped by saved name so deleted ingredients still count). */
data class IngredientLoss(val name: String, val quantity: Double, val unit: String, val cost: Double)

fun OwnerData.topLosses(range: DayRange, limit: Int = 5): List<IngredientLoss> =
    waste.filter { it.epochDay in range }
        .groupBy { it.ingredientName to it.unit }
        .map { (key, list) -> IngredientLoss(key.first, list.sumOf { it.quantity }, key.second, list.sumOf { it.costLoss }) }
        .sortedByDescending { it.cost }
        .take(limit)

fun OwnerData.lossByReason(range: DayRange): List<Pair<WasteReason, Double>> =
    waste.filter { it.epochDay in range }
        .groupBy { it.reason }
        .map { (reason, list) -> reason to list.sumOf { it.costLoss } }
        .sortedByDescending { it.second }

/** One bar (or group of bars) in a chart. */
data class ChartPoint(val label: String, val values: List<Double>)

/**
 * Revenue bars for a range: one bar per day for a week or less, otherwise one bar per week.
 * @return title for the chart and the bars.
 */
fun OwnerData.revenueBars(range: DayRange): Pair<String, List<ChartPoint>> {
    val byDay = sales.filter { it.epochDay in range }.groupBy { it.epochDay }.mapValues { (_, l) -> l.sumOf { it.amount } }
    return if (range.length <= 7) {
        // Always show a full Mon–Sun week so the chart keeps its shape.
        val weekStart = startOfWeek(range.start)
        "Daily revenue" to (0 until 7).map { i ->
            val day = weekStart + i
            ChartPoint(WEEKDAY_SHORT[dayOfWeek(day)], listOf(byDay[day] ?: 0.0))
        }
    } else {
        val weeks = generateSequence(startOfWeek(range.start)) { it + 7 }.takeWhile { it <= range.end }.toList()
        "Weekly revenue" to weeks.map { weekStart ->
            ChartPoint(Dates.format(weekStart).substringBeforeLast(' '), listOf((weekStart until weekStart + 7).sumOf { byDay[it] ?: 0.0 }))
        }
    }
}

/** Revenue and waste cost per week for the last [weeks] weeks (oldest first). */
fun OwnerData.weeklyTrend(today: Long, weeks: Int = 8): List<ChartPoint> {
    val thisWeek = startOfWeek(today)
    return (weeks - 1 downTo 0).map { back ->
        val start = thisWeek - back * 7
        val range = DayRange(start, start + 6)
        ChartPoint(
            label = Dates.format(start).substringBeforeLast(' '),
            values = listOf(
                sales.filter { it.epochDay in range }.sumOf { it.amount },
                waste.filter { it.epochDay in range }.sumOf { it.costLoss },
            ),
        )
    }
}
