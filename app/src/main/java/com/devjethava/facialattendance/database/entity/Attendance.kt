package com.devjethava.facialattendance.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "attendance")
data class Attendance(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val employeeId: String,
    val timestamp: Long,
    val type: String // CHECK_IN or CHECK_OUT
)
