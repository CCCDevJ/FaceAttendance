package com.devjethava.facialattendance.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.devjethava.facialattendance.R
import com.devjethava.facialattendance.helper.AttendanceReport
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReportAdapter : RecyclerView.Adapter<ReportAdapter.ReportViewHolder>() {
    private var reports = listOf<AttendanceReport>()

    class ReportViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.tvName)
        val dateText: TextView = view.findViewById(R.id.tvDate)
        val typeText: TextView = view.findViewById(R.id.tvType)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReportViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_attendance_report, parent, false)
        return ReportViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReportViewHolder, position: Int) {
        val report = reports[position]
        holder.nameText.text = report.name
        holder.dateText.text =
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(report.timestamp))
        holder.typeText.text = report.type
    }

    override fun getItemCount() = reports.size

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(newReports: List<AttendanceReport>) {
        reports = newReports
        notifyDataSetChanged()
    }
}