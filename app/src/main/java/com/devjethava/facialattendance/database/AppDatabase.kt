package com.devjethava.facialattendance.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.devjethava.facialattendance.database.dao.AttendanceDao
import com.devjethava.facialattendance.database.dao.EmployeeDao
import com.devjethava.facialattendance.database.entity.Attendance
import com.devjethava.facialattendance.database.entity.Employee

@Database(
    entities = [Employee::class, Attendance::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun employeeDao(): EmployeeDao
    abstract fun attendanceDao(): AttendanceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "facial_attendance_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}