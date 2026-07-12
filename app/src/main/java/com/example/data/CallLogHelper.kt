package com.example.data

import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CallLog
import androidx.core.content.ContextCompat
import com.example.model.CallRecord
import com.example.model.CallType
import java.util.Calendar
import java.util.Random

object CallLogHelper {

    private const val MAX_CALL_LOG_ROWS = 3000

    fun hasCallLogPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasContactsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun queryCallLog(context: Context): List<CallRecord> {
        val callRecords = mutableListOf<CallRecord>()
        if (!hasCallLogPermission(context)) {
            return callRecords
        }

        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.DURATION,
            CallLog.Calls.DATE,
            CallLog.Calls.TYPE
        )

        try {
            val cursor: Cursor? = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )

            cursor?.use {
                val idIdx = it.getColumnIndex(CallLog.Calls._ID)
                val numberIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIdx = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val durationIdx = it.getColumnIndex(CallLog.Calls.DURATION)
                val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
                val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)

                // Cap raw history so UI/stats stay responsive on phones with huge logs
                var count = 0
                while (it.moveToNext() && count < MAX_CALL_LOG_ROWS) {
                    val id = if (idIdx != -1) it.getString(idIdx) else ""
                    val number = if (numberIdx != -1) it.getString(numberIdx) ?: "Unknown" else "Unknown"
                    val name = if (nameIdx != -1) it.getString(nameIdx) else null
                    val duration = if (durationIdx != -1) it.getLong(durationIdx) else 0L
                    val date = if (dateIdx != -1) it.getLong(dateIdx) else 0L
                    val typeInt = if (typeIdx != -1) it.getInt(typeIdx) else 0

                    callRecords.add(
                        CallRecord(
                            id = id,
                            number = number,
                            name = name,
                            durationSeconds = duration,
                            timestampMs = date,
                            type = CallType.fromAndroidType(typeInt)
                        )
                    )
                    count++
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return callRecords
    }

    fun generateSampleCallLogs(): List<CallRecord> {
        val records = mutableListOf<CallRecord>()
        val random = Random(42) // Fixed seed for reproducible beautiful statistics

        val contacts = listOf(
            ContactMock("Mom", "+15551010001", durationRange = 120..900, probIncoming = 0.65f),
            ContactMock("Sarah Connor", "+15551010002", durationRange = 300..1800, probIncoming = 0.50f),
            ContactMock("Alex Mercer", "+15551010003", durationRange = 180..1500, probIncoming = 0.45f),
            ContactMock("Boss Office", "+15551010004", durationRange = 60..400, probIncoming = 0.40f),
            ContactMock("David (Gym)", "+15551010005", durationRange = 30..120, probIncoming = 0.30f),
            ContactMock("Pizza Delivery", "+15551010006", durationRange = 20..60, probIncoming = 0.10f),
            ContactMock("Spam Risk", "+18005550199", durationRange = 0..0, probIncoming = 0.95f),
            ContactMock("Dentist Clinic", "+15551010007", durationRange = 45..180, probIncoming = 0.35f),
            ContactMock("Tech Support", "+18881234567", durationRange = 600..2400, probIncoming = 0.20f)
        )

        val now = Calendar.getInstance()
        
        // Generate call records over the past 365 days
        for (i in 1..220) {
            val date = now.clone() as Calendar
            // Stagger calls backwards over the past 365 days
            val daysAgo = random.nextInt(365)
            date.add(Calendar.DAY_OF_YEAR, -daysAgo)
            
            // Generate realistic time of call (mostly daytime and evening)
            val hourDistribution = doubleArrayOf(
                0.01, 0.00, 0.00, 0.00, 0.00, 0.01, // 12am - 6am (almost none)
                0.05, 0.08, 0.10, 0.08, 0.08, 0.07, // 6am - 12pm
                0.06, 0.12, 0.08, 0.08, 0.10, 0.12, // 12pm - 6pm (lunch peak, evening commute)
                0.15, 0.18, 0.10, 0.06, 0.03, 0.02  // 6pm - midnight (leisure peak)
            )
            
            var hour = 12
            val roll = random.nextDouble()
            var cumulative = 0.0
            for (h in 0..23) {
                cumulative += hourDistribution[h]
                if (roll <= cumulative) {
                    hour = h
                    break
                }
            }
            date.set(Calendar.HOUR_OF_DAY, hour)
            date.set(Calendar.MINUTE, random.nextInt(60))
            date.set(Calendar.SECOND, random.nextInt(60))

            // Select contact based on weighted probability
            var selectedContact = contacts[random.nextInt(contacts.size)]
            
            // Fine-tune some behaviors:
            // "Boss" only calls/gets called on Mon-Fri (1 to 5 in Calendar) between 9 and 17
            val dayOfWeek = date.get(Calendar.DAY_OF_WEEK)
            val isWeekend = (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY)
            if (selectedContact.name == "Boss Office" && (isWeekend || hour < 9 || hour > 17)) {
                // Reroll contact to Mom or Sarah
                selectedContact = if (random.nextBoolean()) contacts[0] else contacts[1]
            }
            
            // "Pizza Delivery" only on weekends or evenings
            if (selectedContact.name == "Pizza Delivery" && hour < 17) {
                selectedContact = contacts[random.nextInt(3)] // Reroll to first three
            }

            // Determine call type
            val typeRoll = random.nextFloat()
            val type: CallType
            val duration: Long

            if (selectedContact.name == "Spam Risk") {
                type = if (random.nextFloat() < 0.70f) CallType.REJECTED else CallType.MISSED
                duration = 0
            } else {
                type = if (typeRoll < selectedContact.probIncoming) {
                    // Incoming call
                    if (random.nextFloat() < 0.12f) {
                        CallType.MISSED // 12% missed calls
                    } else {
                        CallType.INCOMING
                    }
                } else {
                    // Outgoing call
                    if (random.nextFloat() < 0.05f) {
                        CallType.REJECTED // 5% rejected outgoing
                    } else {
                        CallType.OUTGOING
                    }
                }

                duration = if (type == CallType.MISSED || type == CallType.REJECTED) {
                    0L
                } else {
                    val min = selectedContact.durationRange.first
                    val max = selectedContact.durationRange.last
                    (min + random.nextInt(max - min + 1)).toLong()
                }
            }

            records.add(
                CallRecord(
                    id = "mock_$i",
                    number = selectedContact.number,
                    name = selectedContact.name,
                    durationSeconds = duration,
                    timestampMs = date.timeInMillis,
                    type = type
                )
            )
        }

        // Sort records descending by date
        return records.sortedByDescending { it.timestampMs }
    }

    private data class ContactMock(
        val name: String,
        val number: String,
        val durationRange: IntRange,
        val probIncoming: Float
    )
}
