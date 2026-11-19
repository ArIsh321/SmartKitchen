package com.arise.smartkitchenai.util

import java.util.Calendar
import java.util.TimeZone

/** Inclusive range of epoch days. */
data class DayRange(val start: Long, val end: Long) {
    operator fun contains(day: Long): Boolean = day in start..end
    val length: Long get() = end - start + 1
}

/** Reporting periods for the Owner's dashboard and reports. */
enum class Period(val label: String, val comparisonLabel: String) {
    THIS_WEEK("This week", "vs. last week"),
    THIS_MONTH("This month", "vs. last month"),
    LAST_MONTH("Last month", "vs. month before"),
    LAST_30_DAYS("Last 30 days", "vs. previous 30 days");

    fun range(today: Long): DayRange = when (this) {
        THIS_WEEK -> DayRange(startOfWeek(today), today)
        THIS_MONTH -> DayRange(startOfMonth(today), today)
        LAST_MONTH -> {
            val thisMonth = startOfMonth(today)
            DayRange(startOfMonth(thisMonth - 1), thisMonth - 1)
        }
        LAST_30_DAYS -> DayRange(today - 29, today)
    }

    /** The equivalent earlier period, for "vs." comparisons (same number of days where possible). */
    fun previousRange(today: Long): DayRange {
        val current = range(today)
        return when (this) {
            THIS_WEEK -> DayRange(current.start - 7, current.end - 7)
            THIS_MONTH -> {
                val prevStart = startOfMonth(current.start - 1)
                DayRange(prevStart, minOf(prevStart + (current.end - current.start), current.start - 1))
            }
            LAST_MONTH -> DayRange(startOfMonth(current.start - 1), current.start - 1)
            LAST_30_DAYS -> DayRange(current.start - 30, current.end - 30)
        }
    }
}

/** 0 = Monday … 6 = Sunday. (1970-01-01 was a Thursday.) */
fun dayOfWeek(epochDay: Long): Int = Math.floorMod(epochDay + 3, 7L).toInt()

fun startOfWeek(epochDay: Long): Long = epochDay - dayOfWeek(epochDay)

fun startOfMonth(epochDay: Long): Long {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = Dates.epochDayToUtcMillis(epochDay)
        set(Calendar.DAY_OF_MONTH, 1)
    }
    return Dates.utcMillisToEpochDay(cal.timeInMillis)
}

val WEEKDAY_SHORT = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/** "12.8%" style with sign, or null when there's nothing to compare against. */
fun percentChange(current: Double, previous: Double): Double? =
    if (previous == 0.0) null else (current - previous) / previous * 100

/** Compact money for chart labels: 6100 -> "6.1K". */
fun Double.compact(): String = when {
    this >= 1_000_000 -> String.format(java.util.Locale.getDefault(), "%.1fM", this / 1_000_000)
    this >= 1_000 -> String.format(java.util.Locale.getDefault(), "%.1fK", this / 1_000)
    else -> String.format(java.util.Locale.getDefault(), "%.0f", this)
}
