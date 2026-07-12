package com.example.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateTimeFormatter {
    private val threadLocalFormatter = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat {
            return SimpleDateFormat("MMM dd, yyyy  •  hh:mm a", Locale.getDefault())
        }
    }

    fun format(timestampMs: Long): String {
        val sdf = threadLocalFormatter.get() ?: return ""
        return sdf.format(Date(timestampMs))
    }
}
