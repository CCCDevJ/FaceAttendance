package com.devjethava.facialattendance

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.devjethava.facialattendance.adapter.ReportAdapter
import com.devjethava.facialattendance.database.AppDatabase
import com.devjethava.facialattendance.databinding.ActivityReportBinding
import com.devjethava.facialattendance.helper.AttendanceReport
import com.google.android.material.snackbar.Snackbar
import com.itextpdf.text.BaseColor
import com.itextpdf.text.Document
import com.itextpdf.text.Element
import com.itextpdf.text.Font
import com.itextpdf.text.Paragraph
import com.itextpdf.text.Phrase
import com.itextpdf.text.pdf.PdfPCell
import com.itextpdf.text.pdf.PdfPTable
import com.itextpdf.text.pdf.PdfWriter
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ReportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportBinding

    private lateinit var database: AppDatabase
    private lateinit var reportAdapter: ReportAdapter
    private var startDate: Calendar = Calendar.getInstance()
    private var endDate: Calendar = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        database = AppDatabase.getDatabase(this)

        setupRecyclerView()
    }

    private fun setupRecyclerView() {
        generateReport()
        reportAdapter = ReportAdapter()
        binding.reportRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ReportActivity)
            adapter = reportAdapter
        }
    }

    private fun generateReport() {
        lifecycleScope.launch {
            try {
                val report = database.attendanceDao()
                    .getAllAttendanceReport()
                reportAdapter.submitList(report)

                if (report.isEmpty()) {
                    showNoDataMessage()
                }
            } catch (e: Exception) {
                showError("Failed to generate report: ${e.message}")
            }
        }
    }

    private fun exportToPdf(report: List<AttendanceReport>) {
        try {
            val document = Document()
            val fileName = "Attendance_Report_${formatDate(Date())}.pdf"
            val filePath = File(getExternalFilesDir(null), fileName)

            PdfWriter.getInstance(document, FileOutputStream(filePath))
            document.open()

            // Add title
            val title =
                Paragraph("Attendance Report", Font(Font.FontFamily.HELVETICA, 18f, Font.BOLD))
            title.alignment = Element.ALIGN_CENTER
            document.add(title)
            document.add(Paragraph("\n"))

            // Add date range
            document.add(
                Paragraph(
                    "Period: ${formatDate(Date(startDate.timeInMillis))} to ${
                        formatDate(
                            Date(endDate.timeInMillis)
                        )
                    }"
                )
            )
            document.add(Paragraph("\n"))

            // Create table
            val table = PdfPTable(3)
            table.setWidths(floatArrayOf(2f, 2f, 1f))

            // Add headers
            arrayOf("Name", "Date/Time", "Type").forEach {
                table.addCell(PdfPCell(Phrase(it)).apply {
                    backgroundColor = BaseColor.LIGHT_GRAY
                })
            }

            // Add data
            report.forEach { attendance ->
                table.addCell(attendance.name)
                table.addCell(formatDateTime(Date(attendance.timestamp)))
                table.addCell(attendance.type)
            }

            document.add(table)
            document.close()

            // Show success message and share intent
            showSuccess("Report saved: ${filePath.absolutePath}")
            shareFile(filePath)
        } catch (e: Exception) {
            showError("Failed to export PDF: ${e.message}")
        }
    }

    private fun shareFile(file: File) {
        val uri = FileProvider.getUriForFile(
            this,
            "${packageName}.provider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(intent, "Share Report"))
    }

    private fun formatDate(date: Date): String {
        return SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(date)
    }

    private fun formatDateTime(date: Date): String {
        return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(date)
    }

    private fun showNoDataMessage() {
        Snackbar.make(
            binding.root,
            "No attendance records found for selected date range",
            Snackbar.LENGTH_LONG
        ).show()
    }

    private fun showError(message: String) {
        Snackbar.make(
            binding.root,
            message,
            Snackbar.LENGTH_LONG
        ).show()
    }

    private fun showSuccess(message: String) {
        Snackbar.make(
            binding.root,
            message,
            Snackbar.LENGTH_LONG
        ).show()
    }
}