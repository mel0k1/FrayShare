package com.frayshare.app.core.util

import java.util.Locale

object Format {

    fun bytes(value: Long): String {
        if (value <= 0) return "0 B"
        if (value < 1024) return "$value B"
        val units = arrayOf("KB", "MB", "GB", "TB", "PB")
        var size = value.toDouble()
        var index = -1
        while (size >= 1024.0 && index < units.size - 1) {
            size /= 1024.0
            index += 1
        }
        return if (size >= 100.0) {
            String.format(Locale.US, "%.0f %s", size, units[index])
        } else {
            String.format(Locale.US, "%.1f %s", size, units[index])
        }
    }

    fun plural(count: Int, one: String, few: String, many: String): String {
        val mod10 = count % 10
        val mod100 = count % 100
        return when {
            mod10 == 1 && mod100 != 11 -> one
            mod10 in 2..4 && mod100 !in 12..14 -> few
            else -> many
        }
    }

    fun percent(done: Long, total: Long): Float {
        if (total <= 0) return 0f
        return (done.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }
}
