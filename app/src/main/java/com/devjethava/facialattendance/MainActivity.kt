package com.devjethava.facialattendance

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.devjethava.facialattendance.databinding.ActivityAttendanceBinding
import com.devjethava.facialattendance.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.btnAttendance.setOnClickListener {
            startActivity(Intent(this, AttendanceActivity::class.java))
        }

        binding.btnRegister.setOnClickListener {
            startActivity(Intent(this, RegistrationActivity::class.java))
        }

        binding.btnViewReport.setOnClickListener {
            startActivity(Intent(this, ReportActivity::class.java))
        }
    }
}