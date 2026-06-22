package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.AttendanceRepository
import com.example.data.AttendanceSession
import com.example.data.JalaliCalendar
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone

class AttendanceViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AttendanceRepository
    val allSessions: StateFlow<List<AttendanceSession>>
    val activeSession: StateFlow<AttendanceSession?>

    private val _currentTimerText = MutableStateFlow("۰0:۰۰:۰۰")
    val currentTimerText: StateFlow<String> = _currentTimerText.asStateFlow()

    private var timerJob: Job? = null

    init {
        val database = AppDatabase.getDatabase(application)
        repository = AttendanceRepository(database.attendanceDao())
        
        allSessions = repository.allSessions.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        activeSession = repository.activeSession.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

        // Observe active session to start/stop the clock ticker
        viewModelScope.launch {
            activeSession.collect { active ->
                if (active != null) {
                    startTimer(active.checkInTime)
                } else {
                    stopTimer()
                }
            }
        }
    }

    private fun startTimer(checkInTime: Long) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                val elapsedSeconds = (System.currentTimeMillis() - checkInTime) / 1000
                _currentTimerText.value = formatTimerString(elapsedSeconds)
                delay(1000)
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        _currentTimerText.value = "۰۰:۰۰:۰۰"
    }

    private fun formatTimerString(totalSeconds: Long): String {
        val hrs = totalSeconds / 3600
        val mins = (totalSeconds % 3600) / 60
        val secs = totalSeconds % 60
        val latinDuration = "%02d:%02d:%02d".format(hrs, mins, secs)
        return convertToPersianDigits(latinDuration)
    }

    fun convertToPersianDigits(input: String): String {
        return input
            .replace('0', '۰')
            .replace('1', '۱')
            .replace('2', '۲')
            .replace('3', '۳')
            .replace('4', '۴')
            .replace('5', '۵')
            .replace('6', '۶')
            .replace('7', '۷')
            .replace('8', '۸')
            .replace('9', '۹')
    }

    fun punchIn(
        locationName: String?,
        latitude: Double?,
        longitude: Double?,
        isManual: Boolean,
        workType: String,
        notes: String?
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val jalali = JalaliCalendar.getFromMillis(now)
            repository.startSession(
                checkInTime = now,
                dateString = jalali.format(),
                locationName = locationName ?: "نامشخص",
                latitude = latitude,
                longitude = longitude,
                isManual = isManual,
                workType = workType,
                notes = notes
            )
        }
    }

    fun punchOut(
        locationName: String?,
        latitude: Double?,
        longitude: Double?,
        isManual: Boolean
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repository.endActiveSession(
                checkOutTime = now,
                locationName = locationName ?: "نامشخص",
                latitude = latitude,
                longitude = longitude,
                isManual = isManual
            )
        }
    }

    fun addManualPastSession(
        year: Int,
        month: Int,
        day: Int,
        checkInHour: Int,
        checkInMinute: Int,
        checkOutHour: Int,
        checkOutMinute: Int,
        locationName: String,
        workType: String,
        notes: String?
    ) {
        viewModelScope.launch {
            // Reconstruct Gregorian calendar for the Jalali input
            val julianDay = jalaliToJulian(year, month, day)
            val gregorianDate = julianToGregorian(julianDay)

            val checkInCal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Tehran")).apply {
                set(Calendar.YEAR, gregorianDate.first)
                set(Calendar.MONTH, gregorianDate.second - 1)
                set(Calendar.DAY_OF_MONTH, gregorianDate.third)
                set(Calendar.HOUR_OF_DAY, checkInHour)
                set(Calendar.MINUTE, checkInMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val checkOutCal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Tehran")).apply {
                set(Calendar.YEAR, gregorianDate.first)
                set(Calendar.MONTH, gregorianDate.second - 1)
                set(Calendar.DAY_OF_MONTH, gregorianDate.third)
                set(Calendar.HOUR_OF_DAY, checkOutHour)
                set(Calendar.MINUTE, checkOutMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // If checkout is numerically before checkin, assume next day or reject. But typically same day.
            var checkInTime = checkInCal.timeInMillis
            var checkOutTime = checkOutCal.timeInMillis
            if (checkOutTime < checkInTime) {
                // assume checkout is on next day
                checkOutCal.add(Calendar.DAY_OF_YEAR, 1)
                checkOutTime = checkOutCal.timeInMillis
            }

            val jalaliDateString = "$year/${month.toString().padStart(2, '0')}/${day.toString().padStart(2, '0')}"

            repository.addCompletedManualSession(
                checkInTime = checkInTime,
                checkOutTime = checkOutTime,
                dateString = jalaliDateString,
                locationName = locationName,
                workType = workType,
                notes = notes
            )
        }
    }

    fun deleteSession(session: AttendanceSession) {
        viewModelScope.launch {
            repository.deleteSession(session)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }

    // Helper conversion from Jalali back to Gregorian representation to calculate accurate timestamps
    private fun jalaliToJulian(jy: Int, jm: Int, jd: Int): Long {
        val jy2 = jy - 979
        val jm2 = jm - 1
        val jd2 = jd - 1

        var jGp = jy2 / 33
        var jY33 = jy2 % 33

        var jDayNo = 365 * jy2 + jGp * 8 + (jY33 + 3) / 4
        for (i in 0 until jm2) {
            jDayNo += if (i < 6) 31 else 30
        }
        jDayNo += jd2

        return jDayNo + 1953254L
    }

    private fun julianToGregorian(jdn: Long): Triple<Int, Int, Int> {
        val l = jdn + 68569
        val n = (4 * l) / 146097
        val l2 = l - (146097 * n + 3) / 4
        val i = (4000 * (l2 + 1)) / 1461001
        val l3 = l2 - (1461 * i) / 4 + 31
        val j = (80 * l3) / 2447
        val d = l3 - (2447 * j) / 80
        val l4 = j / 11
        val m = j + 2 - 12 * l4
        val y = 100 * (n - 49) + i + l4

        return Triple(y.toInt(), m.toInt(), d.toInt())
    }

    fun formatDurationPersian(durationInSeconds: Long?): String {
        if (durationInSeconds == null || durationInSeconds == 0L) return convertToPersianDigits("۰ دقیقه")
        val hrs = durationInSeconds / 3600
        val mins = (durationInSeconds % 3600) / 60
        
        return when {
            hrs == 0L -> convertToPersianDigits("$mins دقیقه")
            mins == 0L -> convertToPersianDigits("$hrs ساعت")
            else -> convertToPersianDigits("$hrs ساعت و $mins دقیقه")
        }
    }
}
