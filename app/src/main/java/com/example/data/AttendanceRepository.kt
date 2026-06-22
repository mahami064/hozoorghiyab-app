package com.example.data

import kotlinx.coroutines.flow.Flow

class AttendanceRepository(private val attendanceDao: AttendanceDao) {

    val allSessions: Flow<List<AttendanceSession>> = attendanceDao.getAllSessions()
    val activeSession: Flow<AttendanceSession?> = attendanceDao.getActiveSession()

    suspend fun startSession(
        checkInTime: Long,
        dateString: String,
        locationName: String?,
        latitude: Double?,
        longitude: Double?,
        isManual: Boolean,
        workType: String,
        notes: String?
    ) {
        val session = AttendanceSession(
            checkInTime = checkInTime,
            dateString = dateString,
            checkInLocation = locationName,
            checkInLatitude = latitude,
            checkInLongitude = longitude,
            isCheckInManual = isManual,
            workType = workType,
            notes = notes
        )
        attendanceDao.insertSession(session)
    }

    suspend fun endActiveSession(
        checkOutTime: Long,
        locationName: String?,
        latitude: Double?,
        longitude: Double?,
        isManual: Boolean
    ) {
        val active = attendanceDao.getActiveSessionOneShot()
        if (active != null) {
            val durationSeconds = (checkOutTime - active.checkInTime) / 1000
            val updated = active.copy(
                checkOutTime = checkOutTime,
                checkOutLocation = locationName,
                checkOutLatitude = latitude,
                checkOutLongitude = longitude,
                isCheckOutManual = isManual,
                durationSeconds = if (durationSeconds > 0) durationSeconds else 0
            )
            attendanceDao.updateSession(updated)
        }
    }

    suspend fun addCompletedManualSession(
        checkInTime: Long,
        checkOutTime: Long,
        dateString: String,
        locationName: String?,
        workType: String,
        notes: String?
    ) {
        val durationSeconds = (checkOutTime - checkInTime) / 1000
        val session = AttendanceSession(
            checkInTime = checkInTime,
            checkOutTime = checkOutTime,
            dateString = dateString,
            checkInLocation = locationName,
            checkOutLocation = locationName,
            isCheckInManual = true,
            isCheckOutManual = true,
            durationSeconds = if (durationSeconds > 0) durationSeconds else 0,
            notes = notes,
            workType = workType
        )
        attendanceDao.insertSession(session)
    }

    suspend fun deleteSession(session: AttendanceSession) {
        attendanceDao.deleteSession(session)
    }

    suspend fun clearAll() {
        attendanceDao.clearAll()
    }
}
