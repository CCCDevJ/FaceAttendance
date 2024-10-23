package com.devjethava.facialattendance.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.devjethava.facialattendance.database.entity.Employee

@Dao
interface EmployeeDao {
    @Query("SELECT * FROM employees")
    suspend fun getAllEmployees(): List<Employee>

    @Query("SELECT * FROM employees WHERE id = :id")
    suspend fun getEmployeeById(id: String): Employee?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmployee(employee: Employee)

    @Delete
    suspend fun deleteEmployee(employee: Employee)
}