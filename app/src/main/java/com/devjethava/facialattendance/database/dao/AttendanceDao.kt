package com.devjethava.facialattendance.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.devjethava.facialattendance.database.entity.Attendance
import com.devjethava.facialattendance.helper.AttendanceReport

@Dao
interface AttendanceDao {
    @Insert
    suspend fun insertAttendance(attendance: Attendance)

    @Query("SELECT * FROM attendance WHERE timestamp >= :startDate AND timestamp <= :endDate")
    suspend fun getAttendanceByDateRange(startDate: Long, endDate: Long): List<Attendance>

    @Query(
        """
        SELECT e.name, a.timestamp, a.type 
        FROM attendance a 
        JOIN employees e ON a.employeeId = e.id 
        WHERE a.timestamp >= :startDate AND a.timestamp <= :endDate
    """
    )
    suspend fun getAttendanceReport(startDate: Long, endDate: Long): List<AttendanceReport>
}