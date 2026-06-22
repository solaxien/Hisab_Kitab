package com.amg.hisabkitab.ui.common

import java.text.NumberFormat
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val india = Locale("en", "IN")
private val zone = ZoneId.of("Asia/Kolkata")
private val currency = NumberFormat.getCurrencyInstance(india).apply {
    maximumFractionDigits = 2
    minimumFractionDigits = 0
}
private val dateTime = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", india)
private val date = DateTimeFormatter.ofPattern("dd MMM yyyy", india)

fun money(paise: Long): String = currency.format(paise / 100.0)
fun dateTime(timestamp: Long): String =
    Instant.ofEpochMilli(timestamp).atZone(zone).format(dateTime)
fun date(timestamp: Long): String =
    Instant.ofEpochMilli(timestamp).atZone(zone).format(date)

fun parseRupees(value: String): Long? =
    value.trim()
        .toBigDecimalOrNull()
        ?.takeIf { it.signum() >= 0 && it.scale() <= 2 }
        ?.movePointRight(2)
        ?.setScale(0, RoundingMode.UNNECESSARY)
        ?.takeIf { it <= Long.MAX_VALUE.toBigDecimal() }
        ?.toLong()

fun currentDayStart(): Long =
    java.time.LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
