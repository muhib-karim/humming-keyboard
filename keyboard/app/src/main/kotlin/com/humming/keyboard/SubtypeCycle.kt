/*
 * Copyright 2026 Humming contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.humming.keyboard

/**
 * Humming 1.7.1: which language the language key switches to (SubtypeManager), as a pure rule (SubtypeCycleTest).
 * Next / previous in the user's list, wrapping at the ends. When the active language is no longer in the list (it was
 * just removed), both go to the first language in the list. Null only for an empty list.
 */
object SubtypeCycle {
    fun <T> next(list: List<T>, active: T?): T? = step(list, active, 1)

    fun <T> previous(list: List<T>, active: T?): T? = step(list, active, -1)

    private fun <T> step(list: List<T>, active: T?, by: Int): T? {
        val i = list.indexOf(active)
        return when {
            list.isEmpty() -> null
            i < 0 -> list.first()
            else -> list[(i + by + list.size) % list.size]
        }
    }
}
