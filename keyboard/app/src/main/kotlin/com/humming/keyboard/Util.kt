package com.humming.keyboard

import android.content.Context

internal fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
