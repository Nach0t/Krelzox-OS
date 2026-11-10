package dev.nexium.launcher.ui.performance

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

/** Locale-aware formatting for system metrics. Pure (no Android), unit tested. */
object PerformanceFormat {

    fun percent(value: Float, locale: Locale): String =
        NumberFormat.getPercentInstance(locale).apply { maximumFractionDigits = 0 }.format(value / 100.0)

    fun temperature(celsius: Float, locale: Locale): String = "${decimal(celsius, 1, locale)} °C"

    /** Short temperature for the compact panel: whole degrees. */
    fun temperatureShort(celsius: Float, locale: Locale): String = "${decimal(celsius, 0, locale)} °C"

    /** Decimal megabytes (1 MB = 1 000 000 bytes, like Android Settings), with grouping. */
    fun megabytes(bytes: Long, locale: Locale): String =
        "${NumberFormat.getIntegerInstance(locale).format((bytes / 1_000_000.0).roundToInt())} MB"

    /** Decimal size with one decimal from 1 GB up, like Android Settings. */
    fun size(bytes: Long, locale: Locale): String = when {
        bytes >= 1_000_000_000L -> "${decimal(bytes / 1e9f, 1, locale)} GB"
        bytes >= 1_000_000L -> "${decimal(bytes / 1e6f, 0, locale)} MB"
        else -> "${decimal(bytes / 1e3f, 0, locale)} KB"
    }

    /** Measured throughput. */
    fun rate(bytesPerSecond: Long, locale: Locale): String = when {
        bytesPerSecond >= 1_000_000L -> "${decimal(bytesPerSecond / 1e6f, 1, locale)} MB/s"
        bytesPerSecond >= 1_000L -> "${decimal(bytesPerSecond / 1e3f, 0, locale)} KB/s"
        else -> "$bytesPerSecond B/s"
    }

    fun frequency(mhz: Int, locale: Locale): String =
        if (mhz >= 1000) "${decimal(mhz / 1000f, 1, locale)} GHz" else "$mhz MHz"

    fun load(values: List<Float>, locale: Locale): String = values.joinToString(" · ") { decimal(it, 2, locale) }

    /** Uptime as "2 d 4 h", "3 h 37 min" or "12 min". */
    fun duration(ms: Long): String {
        val minutes = ms / 60_000
        val hours = minutes / 60
        val days = hours / 24
        return when {
            days > 0 -> "$days d ${hours % 24} h"
            hours > 0 -> "$hours h ${minutes % 60} min"
            else -> "$minutes min"
        }
    }

    fun decimal(value: Float, digits: Int, locale: Locale): String =
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = digits
            maximumFractionDigits = digits
        }.format(value.toDouble())
}
