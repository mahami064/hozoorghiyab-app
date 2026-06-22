package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "attendance_sessions")
data class AttendanceSession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val checkInTime: Long,
    val checkOutTime: Long? = null,
    val dateString: String, // e.g., "1405/04/01"
    val checkInLocation: String? = null,
    val checkOutLocation: String? = null,
    val checkInLatitude: Double? = null,
    val checkInLongitude: Double? = null,
    val checkOutLatitude: Double? = null,
    val checkOutLongitude: Double? = null,
    val isCheckInManual: Boolean = false,
    val isCheckOutManual: Boolean = false,
    val durationSeconds: Long? = null,
    val notes: String? = null,
    val workType: String = "WORK" // e.g. "WORK" (حضور), "REMOTE" (دورکاری), "MISSION" (ماموریت), "VACATION" (مرخصی)
)
