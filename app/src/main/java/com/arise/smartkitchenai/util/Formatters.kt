package com.arise.smartkitchenai.util

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Epoch-day helpers that work on minSdk 24 without java.time desugaring. */
object Dates {
    private const val DAY_MS = 86_400_000L

    fun todayEpochDay(): Long {
        val now = System.currentTimeMillis()
        return Math.floorDiv(now + TimeZone.getDefault().getOffset(now), DAY_MS)
    }

    /** Material3 DatePicker works with UTC-midnight millis. */
    fun epochDayToUtcMillis(epochDay: Long): Long = epochDay * DAY_MS
    fun utcMillisToEpochDay(millis: Long): Long = Math.floorDiv(millis, DAY_MS)

    fun format(epochDay: Long): String =
        SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(epochDayToUtcMillis(epochDay)))
}

fun Double.asMoney(): String = NumberFormat.getCurrencyInstance().format(this)

fun Double.asQuantity(): String =
    if (this % 1.0 == 0.0) toLong().toString()
    else String.format(Locale.getDefault(), "%.2f", this).trimEnd('0').trimEnd('.', ',')

/** Lenient parse that accepts both "1.5" and "1,5". */
fun String.toQuantityOrNull(): Double? = trim().replace(',', '.').toDoubleOrNull()
