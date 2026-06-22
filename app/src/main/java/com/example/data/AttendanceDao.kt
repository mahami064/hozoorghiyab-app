package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance_sessions ORDER BY checkInTime DESC")
    fun getAllSessions(): Flow<List<AttendanceSession>>

    @Query("SELECT * FROM attendance_sessions WHERE checkOutTime IS NULL LIMIT 1")
    fun getActiveSession(): Flow<AttendanceSession?>

    @Query("SELECT * FROM attendance_sessions WHERE checkOutTime IS NULL LIMIT 1")
    suspend fun getActiveSessionOneShot(): AttendanceSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: AttendanceSession): Long

    @Update
    suspend fun updateSession(session: AttendanceSession)

    @Delete
    suspend fun deleteSession(session: AttendanceSession)

    @Query("DELETE FROM attendance_sessions")
    suspend fun clearAll()
}
