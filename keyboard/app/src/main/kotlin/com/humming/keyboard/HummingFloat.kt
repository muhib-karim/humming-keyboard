/*
 * Copyright 2026 Humming contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.humming.keyboard

/** Humming: bounds for the floating keyboard card, in dp from the bottom-left of the input window. */
object HummingFloat {
    const val MAX_X = 200
    const val MAX_Y = 600

    fun clampX(value: Float): Int = value.toInt().coerceIn(0, MAX_X)
    fun clampY(value: Float): Int = value.toInt().coerceIn(0, MAX_Y)
    fun clampX(value: Int): Int = value.coerceIn(0, MAX_X)
    fun clampY(value: Int): Int = value.coerceIn(0, MAX_Y)
}
