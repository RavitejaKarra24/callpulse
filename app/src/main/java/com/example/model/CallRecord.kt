package com.example.model

import android.provider.CallLog
import com.example.util.DateTimeFormatter

data class CallRecord(
    val id: String,
    val number: String,
    val name: String?,
    val durationSeconds: Long,
    val timestampMs: Long,
    val type: CallType
) {
    val displayName: String = name ?: formatPhoneNumber(number)
    val formattedDate: String = DateTimeFormatter.format(timestampMs)

    companion object {
        private val NON_DIGITS_REGEX = Regex("\\D")

        fun formatPhoneNumber(num: String): String {
            val digits = num.replace(NON_DIGITS_REGEX, "")
            return if (digits.length == 10) {
                "(${digits.substring(0, 3)}) ${digits.substring(3, 6)}-${digits.substring(6)}"
            } else if (digits.length == 11 && digits.startsWith("1")) {
                "+1 (${digits.substring(1, 4)}) ${digits.substring(4, 7)}-${digits.substring(7)}"
            } else {
                num
            }
        }
    }
}

enum class CallType {
    INCOMING,
    OUTGOING,
    MISSED,
    REJECTED,
    OTHER;

    companion object {
        fun fromAndroidType(type: Int): CallType {
            return when (type) {
                CallLog.Calls.INCOMING_TYPE -> INCOMING
                CallLog.Calls.OUTGOING_TYPE -> OUTGOING
                CallLog.Calls.MISSED_TYPE -> MISSED
                CallLog.Calls.REJECTED_TYPE -> REJECTED
                else -> OTHER
            }
        }
    }
}
