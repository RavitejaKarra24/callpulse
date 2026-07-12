package com.example.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CallLogHelper
import com.example.model.CallRecord
import com.example.model.CallType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

enum class TimeRange {
    WEEKLY,
    MONTHLY,
    YEARLY,
    ALL_TIME
}

data class ContactStats(
    val name: String?,
    val number: String,
    val callCount: Int,
    val totalDurationSeconds: Long,
    val incomingCount: Int,
    val outgoingCount: Int,
    val missedCount: Int,
    val percentageOfTotalDuration: Float
) {
    val displayName: String = name ?: formatPhoneNumber(number)

    companion object {
        private val NON_DIGITS_REGEX = Regex("\\D")

        fun formatPhoneNumber(num: String): String {
            val digits = num.replace(NON_DIGITS_REGEX, "")
            return if (digits.length == 10) {
                "(${digits.substring(0, 3)}) ${digits.substring(3, 6)}-${digits.substring(6)}"
            } else {
                num
            }
        }
    }
}

data class TrendPoint(
    val label: String,
    val value: Float,
    val rawCount: Int,
    val rawDurationSeconds: Long,
    val dateMs: Long
)

data class HourlyStat(
    val label: String,
    val count: Int,
    val percentage: Float
)

data class CallStatsUiState(
    val isLoading: Boolean = true,
    val timeRange: TimeRange = TimeRange.ALL_TIME,
    val searchQuery: String = "",
    val totalCalls: Int = 0,
    val totalDurationSeconds: Long = 0,
    val averageDurationSeconds: Long = 0,
    val incomingCount: Int = 0,
    val outgoingCount: Int = 0,
    val missedCount: Int = 0,
    val rejectedCount: Int = 0,
    val topContacts: List<ContactStats> = emptyList(),
    val trendPoints: List<TrendPoint> = emptyList(),
    val hourlyStats: List<HourlyStat> = emptyList(),
    val isDemoMode: Boolean = false,
    val hasPermission: Boolean = false,
    val filteredCallList: List<CallRecord> = emptyList(),
    val isTrendDurationBased: Boolean = true
)

@OptIn(FlowPreview::class)
class CallStatsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(CallStatsUiState())
    val uiState: StateFlow<CallStatsUiState> = _uiState.asStateFlow()

    private var allRecords: List<CallRecord> = emptyList()
    private var timeFilteredCache: List<CallRecord> = emptyList()
    private var cachedRange: TimeRange? = null

    private val searchQueryFlow = MutableStateFlow("")
    private var processJob: Job? = null

    init {
        viewModelScope.launch {
            searchQueryFlow
                .debounce(200)
                .distinctUntilChanged()
                .collect { query ->
                    applySearchFilter(query)
                }
        }
    }

    fun loadData(context: Context, forceDemo: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val appContext = context.applicationContext
            val hasPerm = CallLogHelper.hasCallLogPermission(appContext)

            val (isDemo, records) = withContext(Dispatchers.IO) {
                when {
                    forceDemo -> true to CallLogHelper.generateSampleCallLogs()
                    hasPerm -> {
                        val realRecords = CallLogHelper.queryCallLog(appContext)
                        if (realRecords.isEmpty()) {
                            true to CallLogHelper.generateSampleCallLogs()
                        } else {
                            false to realRecords
                        }
                    }
                    else -> true to CallLogHelper.generateSampleCallLogs()
                }
            }

            allRecords = records
            cachedRange = null
            timeFilteredCache = emptyList()

            _uiState.update {
                it.copy(
                    isDemoMode = isDemo,
                    hasPermission = hasPerm
                )
            }

            processRecords()
        }
    }

    fun setTimeRange(range: TimeRange) {
        if (_uiState.value.timeRange == range) return
        _uiState.update { it.copy(timeRange = range) }
        processRecords()
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchQueryFlow.value = query
    }

    fun toggleTrendMetric() {
        _uiState.update { it.copy(isTrendDurationBased = !it.isTrendDurationBased) }
        processRecords()
    }

    fun toggleDemoMode(context: Context) {
        val currentDemo = _uiState.value.isDemoMode
        loadData(context, forceDemo = !currentDemo)
    }

    private fun processRecords() {
        processJob?.cancel()
        processJob = viewModelScope.launch {
            val range = _uiState.value.timeRange
            val isDuration = _uiState.value.isTrendDurationBased
            val source = allRecords

            val computed = withContext(Dispatchers.Default) {
                val filteredByTime = filterRecordsByTimeRange(source, range)
                computeAllStats(filteredByTime, range, isDuration)
            }

            timeFilteredCache = computed.timeFiltered
            cachedRange = range

            _uiState.update {
                it.copy(
                    isLoading = false,
                    totalCalls = computed.totalCalls,
                    totalDurationSeconds = computed.totalDurationSeconds,
                    averageDurationSeconds = computed.averageDurationSeconds,
                    incomingCount = computed.incomingCount,
                    outgoingCount = computed.outgoingCount,
                    missedCount = computed.missedCount,
                    rejectedCount = computed.rejectedCount,
                    topContacts = computed.topContacts,
                    trendPoints = computed.trendPoints,
                    hourlyStats = computed.hourlyStats
                )
            }

            applySearchFilter(_uiState.value.searchQuery)
        }
    }

    private fun applySearchFilter(query: String) {
        viewModelScope.launch(Dispatchers.Default) {
            val range = _uiState.value.timeRange
            val filteredByTime = if (cachedRange == range && timeFilteredCache.isNotEmpty()) {
                timeFilteredCache
            } else {
                filterRecordsByTimeRange(allRecords, range).also {
                    timeFilteredCache = it
                    cachedRange = range
                }
            }

            val q = query.trim().lowercase()
            val searchFiltered = if (q.isEmpty()) {
                filteredByTime
            } else {
                filteredByTime.filter {
                    (it.name?.lowercase()?.contains(q) == true) ||
                        it.number.contains(q, ignoreCase = true)
                }
            }

            // Cap list size for smooth LazyColumn scrolling on large histories
            val limited = if (searchFiltered.size > MAX_LIST_ITEMS) {
                searchFiltered.take(MAX_LIST_ITEMS)
            } else {
                searchFiltered
            }

            _uiState.update { it.copy(filteredCallList = limited) }
        }
    }

    private data class ComputedStats(
        val timeFiltered: List<CallRecord>,
        val totalCalls: Int,
        val totalDurationSeconds: Long,
        val averageDurationSeconds: Long,
        val incomingCount: Int,
        val outgoingCount: Int,
        val missedCount: Int,
        val rejectedCount: Int,
        val topContacts: List<ContactStats>,
        val trendPoints: List<TrendPoint>,
        val hourlyStats: List<HourlyStat>
    )

    private fun computeAllStats(
        filteredByTime: List<CallRecord>,
        range: TimeRange,
        isDuration: Boolean
    ): ComputedStats {
        val totalCalls = filteredByTime.size
        var totalDurSec = 0L
        var incoming = 0
        var outgoing = 0
        var missed = 0
        var rejected = 0

        for (rec in filteredByTime) {
            when (rec.type) {
                CallType.INCOMING -> {
                    incoming++
                    totalDurSec += rec.durationSeconds
                }
                CallType.OUTGOING -> {
                    outgoing++
                    totalDurSec += rec.durationSeconds
                }
                CallType.MISSED -> missed++
                CallType.REJECTED -> rejected++
                CallType.OTHER -> {
                    incoming++
                    totalDurSec += rec.durationSeconds
                }
            }
        }

        val connectedCalls = incoming + outgoing
        val avgDur = if (connectedCalls > 0) totalDurSec / connectedCalls else 0L

        return ComputedStats(
            timeFiltered = filteredByTime,
            totalCalls = totalCalls,
            totalDurationSeconds = totalDurSec,
            averageDurationSeconds = avgDur,
            incomingCount = incoming,
            outgoingCount = outgoing,
            missedCount = missed,
            rejectedCount = rejected,
            topContacts = computeTopContacts(filteredByTime, totalDurSec),
            trendPoints = computeTrendPoints(filteredByTime, range, isDuration),
            hourlyStats = computeHourlyStats(filteredByTime)
        )
    }

    private fun filterRecordsByTimeRange(records: List<CallRecord>, range: TimeRange): List<CallRecord> {
        if (range == TimeRange.ALL_TIME) return records

        val boundary = Calendar.getInstance()
        when (range) {
            TimeRange.WEEKLY -> boundary.add(Calendar.DAY_OF_YEAR, -7)
            TimeRange.MONTHLY -> {
                boundary.set(Calendar.DAY_OF_MONTH, 1)
                boundary.set(Calendar.HOUR_OF_DAY, 0)
                boundary.set(Calendar.MINUTE, 0)
                boundary.set(Calendar.SECOND, 0)
                boundary.set(Calendar.MILLISECOND, 0)
            }
            TimeRange.YEARLY -> {
                boundary.set(Calendar.DAY_OF_YEAR, 1)
                boundary.set(Calendar.HOUR_OF_DAY, 0)
                boundary.set(Calendar.MINUTE, 0)
                boundary.set(Calendar.SECOND, 0)
                boundary.set(Calendar.MILLISECOND, 0)
            }
            TimeRange.ALL_TIME -> return records
        }

        val boundaryMs = boundary.timeInMillis
        return records.filter { it.timestampMs >= boundaryMs }
    }

    private fun computeTopContacts(records: List<CallRecord>, globalDurationSec: Long): List<ContactStats> {
        val groups = records.groupBy { it.number }
        val contactStatsList = ArrayList<ContactStats>(groups.size)

        for ((number, calls) in groups) {
            val name = calls.firstOrNull()?.name
            val count = calls.size
            var dur = 0L
            var inc = 0
            var out = 0
            var mis = 0

            for (c in calls) {
                when (c.type) {
                    CallType.INCOMING -> {
                        inc++
                        dur += c.durationSeconds
                    }
                    CallType.OUTGOING -> {
                        out++
                        dur += c.durationSeconds
                    }
                    CallType.MISSED, CallType.REJECTED -> mis++
                    else -> {
                        inc++
                        dur += c.durationSeconds
                    }
                }
            }

            val percentage = if (globalDurationSec > 0) dur.toFloat() / globalDurationSec else 0f

            contactStatsList.add(
                ContactStats(
                    name = name,
                    number = number,
                    callCount = count,
                    totalDurationSeconds = dur,
                    incomingCount = inc,
                    outgoingCount = out,
                    missedCount = mis,
                    percentageOfTotalDuration = percentage
                )
            )
        }

        return contactStatsList
            .sortedWith(
                compareByDescending<ContactStats> { it.totalDurationSeconds }
                    .thenByDescending { it.callCount }
            )
            .take(TOP_CONTACTS_LIMIT)
    }

    private fun computeTrendPoints(
        records: List<CallRecord>,
        range: TimeRange,
        isDuration: Boolean
    ): List<TrendPoint> {
        val calendar = Calendar.getInstance()
        val points = mutableListOf<TrendPoint>()

        when (range) {
            TimeRange.WEEKLY -> {
                val format = SimpleDateFormat("EEE", Locale.getDefault())
                val dailyStats = linkedMapOf<String, Pair<Long, Int>>()
                val dayTimestamps = mutableMapOf<String, Long>()

                val tempCal = Calendar.getInstance()
                val orderedLabels = ArrayList<String>(7)
                for (i in 0..6) {
                    val label = format.format(tempCal.time)
                    orderedLabels.add(0, label)
                    dailyStats[label] = Pair(0L, 0)
                    dayTimestamps[label] = tempCal.timeInMillis
                    tempCal.add(Calendar.DAY_OF_YEAR, -1)
                }

                for (rec in records) {
                    calendar.timeInMillis = rec.timestampMs
                    val label = format.format(calendar.time)
                    val current = dailyStats[label] ?: continue
                    val isTalk = rec.type == CallType.INCOMING || rec.type == CallType.OUTGOING
                    val dSec = if (isTalk) rec.durationSeconds else 0L
                    dailyStats[label] = Pair(current.first + dSec, current.second + 1)
                }

                for (label in orderedLabels.distinct()) {
                    val data = dailyStats[label] ?: Pair(0L, 0)
                    val value = if (isDuration) (data.first / 60f) else data.second.toFloat()
                    points.add(
                        TrendPoint(
                            label = label,
                            value = value,
                            rawCount = data.second,
                            rawDurationSeconds = data.first,
                            dateMs = dayTimestamps[label] ?: 0L
                        )
                    )
                }
            }

            TimeRange.MONTHLY -> {
                val weeklyStats = Array(5) { Pair(0L, 0) }

                for (rec in records) {
                    calendar.timeInMillis = rec.timestampMs
                    var week = calendar.get(Calendar.WEEK_OF_MONTH)
                    if (week < 1) week = 1
                    if (week > 5) week = 5
                    val idx = week - 1
                    val current = weeklyStats[idx]
                    val isTalk = rec.type == CallType.INCOMING || rec.type == CallType.OUTGOING
                    val dSec = if (isTalk) rec.durationSeconds else 0L
                    weeklyStats[idx] = Pair(current.first + dSec, current.second + 1)
                }

                for (w in 1..5) {
                    val data = weeklyStats[w - 1]
                    val value = if (isDuration) (data.first / 60f) else data.second.toFloat()
                    points.add(
                        TrendPoint(
                            label = "Wk $w",
                            value = value,
                            rawCount = data.second,
                            rawDurationSeconds = data.first,
                            dateMs = w.toLong()
                        )
                    )
                }
            }

            TimeRange.YEARLY -> {
                val format = SimpleDateFormat("MMM", Locale.getDefault())
                val monthlyStats = Array(12) { Pair(0L, 0) }
                val labels = Array(12) { "" }

                val tempCal = Calendar.getInstance()
                tempCal.set(Calendar.MONTH, Calendar.JANUARY)
                tempCal.set(Calendar.DAY_OF_MONTH, 1)
                for (m in 0..11) {
                    labels[m] = format.format(tempCal.time)
                    tempCal.add(Calendar.MONTH, 1)
                }

                for (rec in records) {
                    calendar.timeInMillis = rec.timestampMs
                    val m = calendar.get(Calendar.MONTH)
                    val current = monthlyStats[m]
                    val isTalk = rec.type == CallType.INCOMING || rec.type == CallType.OUTGOING
                    val dSec = if (isTalk) rec.durationSeconds else 0L
                    monthlyStats[m] = Pair(current.first + dSec, current.second + 1)
                }

                for (m in 0..11) {
                    val data = monthlyStats[m]
                    val value = if (isDuration) (data.first / 60f) else data.second.toFloat()
                    points.add(
                        TrendPoint(
                            label = labels[m],
                            value = value,
                            rawCount = data.second,
                            rawDurationSeconds = data.first,
                            dateMs = m.toLong()
                        )
                    )
                }
            }

            TimeRange.ALL_TIME -> {
                val format = SimpleDateFormat("yyyy", Locale.getDefault())
                val yearlyStats = linkedMapOf<String, Pair<Long, Int>>()
                val yearTimestamps = mutableMapOf<String, Long>()

                for (rec in records) {
                    calendar.timeInMillis = rec.timestampMs
                    val label = format.format(calendar.time)
                    val current = yearlyStats.getOrPut(label) { Pair(0L, 0) }
                    yearTimestamps[label] = calendar.get(Calendar.YEAR).toLong()

                    val isTalk = rec.type == CallType.INCOMING || rec.type == CallType.OUTGOING
                    val dSec = if (isTalk) rec.durationSeconds else 0L
                    yearlyStats[label] = Pair(current.first + dSec, current.second + 1)
                }

                yearlyStats.forEach { (label, data) ->
                    val value = if (isDuration) (data.first / 60f) else data.second.toFloat()
                    points.add(
                        TrendPoint(
                            label = label,
                            value = value,
                            rawCount = data.second,
                            rawDurationSeconds = data.first,
                            dateMs = yearTimestamps[label] ?: 0L
                        )
                    )
                }
                points.sortBy { it.dateMs }

                if (points.size == 1) {
                    val single = points.first()
                    val prevLabel = (single.label.toIntOrNull()?.minus(1))?.toString() ?: "Prev"
                    points.add(0, TrendPoint(prevLabel, 0f, 0, 0, single.dateMs - 1))
                }
            }
        }

        return points
    }

    private fun computeHourlyStats(records: List<CallRecord>): List<HourlyStat> {
        val calendar = Calendar.getInstance()
        var morning = 0
        var afternoon = 0
        var evening = 0
        var night = 0

        for (rec in records) {
            calendar.timeInMillis = rec.timestampMs
            when (calendar.get(Calendar.HOUR_OF_DAY)) {
                in 6..11 -> morning++
                in 12..17 -> afternoon++
                in 18..21 -> evening++
                else -> night++
            }
        }

        val total = (morning + afternoon + evening + night).toFloat()
        return listOf(
            HourlyStat("Morning (6am–12pm)", morning, if (total > 0f) morning / total else 0f),
            HourlyStat("Afternoon (12pm–6pm)", afternoon, if (total > 0f) afternoon / total else 0f),
            HourlyStat("Evening (6pm–10pm)", evening, if (total > 0f) evening / total else 0f),
            HourlyStat("Night (10pm–6am)", night, if (total > 0f) night / total else 0f)
        )
    }

    companion object {
        private const val MAX_LIST_ITEMS = 80
        private const val TOP_CONTACTS_LIMIT = 15
    }
}
