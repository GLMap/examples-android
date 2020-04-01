package com.getyourmap.androidDemo

import java.util.*

internal object NumberFormatter {
    private var locale: Locale = Locale.getDefault()
    private fun Double.toNiceString(): String {
        if (this.isNaN()) {
            return "---"
        }
        if (this < 10) {
            return String.format(locale, "%.2f", this)
        } else if (this < 100) {
            return String.format(locale, "%.1f", this)
        }
        return String.format(locale, "%.0f", this)
    }

    fun formatSize(value: Long): String {
        val sizeInMB = value.toDouble() / (1000 * 1000)
        return "${sizeInMB.toNiceString()} MB"
    }
}