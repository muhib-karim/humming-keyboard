package com.humming.keyboard

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Humming (M5): human time labels for clipboard items and the clean-up setting. Pure: ClipTimeTest. */
object ClipTime {
    fun ago(thenMs: Long, nowMs: Long, locale: Locale = Locale.getDefault()): String {
        val s = ((nowMs - thenMs) / 1000).coerceAtLeast(0)
        return when {
            s < 60 -> "Just now"
            s < 3600 -> "${s / 60} min ago"
            s < 86_400 -> "${s / 3600} h ago"
            s < 2 * 86_400 -> "Yesterday"
            s < 7 * 86_400 -> "${s / 86_400} days ago"
            else -> SimpleDateFormat("d MMM", locale).format(Date(thenMs))
        }
    }

    /** Minutes as "45 min", "3 h", "2 days", "1 week". */
    fun duration(minutes: Int): String = when {
        minutes < 60 -> "$minutes min"
        minutes < 1440 -> if (minutes % 60 == 0) "${minutes / 60} h" else "${minutes / 60} h ${minutes % 60} min"
        minutes == 10_080 -> "1 week"
        minutes % 1440 == 0 -> if (minutes == 1440) "1 day" else "${minutes / 1440} days"
        else -> "${minutes / 1440} d ${(minutes % 1440) / 60} h"
    }
}
