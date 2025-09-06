package com.sofindo.ems.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sofindo.ems.R
import java.text.SimpleDateFormat
import java.util.*

class MaintenanceHistoryAdapter(
    private val historyList: List<Map<String, Any>>
) : RecyclerView.Adapter<MaintenanceHistoryAdapter.HistoryViewHolder>() {

    class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvDate: TextView = itemView.findViewById(R.id.tv_date)
        val tvRecordNumber: TextView = itemView.findViewById(R.id.tv_record_number)
        val tvJobTask: TextView = itemView.findViewById(R.id.tv_job_task)
        val tvRemark: TextView = itemView.findViewById(R.id.tv_remark)
        val tvNotes: TextView = itemView.findViewById(R.id.tv_notes)
        val layoutJobTask: View = itemView.findViewById(R.id.layout_job_task)
        val layoutRemark: View = itemView.findViewById(R.id.layout_remark)
        val layoutNotes: View = itemView.findViewById(R.id.layout_notes)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_maintenance_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val history = historyList[position]
        val context = holder.itemView.context
        
        // Debug: Log all data for this record
        android.util.Log.d("MaintenanceHistory", "Record ${position + 1} data: $history")

        // Set date
        val date = history["date"]?.toString() ?: ""
        holder.tvDate.text = formatDate(date)

        // Set record number
        holder.tvRecordNumber.text = "Record ${position + 1}"

        // Set job task
        val jobTask = history["jobtask"]?.toString() ?: ""
        if (jobTask.isNotEmpty()) {
            holder.layoutJobTask.visibility = View.VISIBLE
            holder.tvJobTask.text = jobTask
        } else {
            holder.layoutJobTask.visibility = View.GONE
        }

        // Set remark (description)
        val remark = history["remark"]?.toString() ?: ""
        if (remark.isNotEmpty()) {
            holder.layoutRemark.visibility = View.VISIBLE
            holder.tvRemark.text = remark
        } else {
            holder.layoutRemark.visibility = View.GONE
        }

        // Set notes (from tblevent via LEFT JOIN)
        val notes = history["notes"]?.toString()?.trim() ?: ""
        
        if (notes.isNotEmpty() && notes != "null") {
            holder.layoutNotes.visibility = View.VISIBLE
            holder.tvNotes.text = notes
        } else {
            holder.layoutNotes.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = historyList.size

    private fun formatDate(dateString: String): String {
        if (dateString.isEmpty()) return "No Date"

        return try {
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateString)
            val months = arrayOf(
                "January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"
            )
            val calendar = Calendar.getInstance()
            calendar.time = date
            val day = calendar.get(Calendar.DAY_OF_MONTH)
            val month = months[calendar.get(Calendar.MONTH)]
            val year = calendar.get(Calendar.YEAR)
            "$day $month $year"
        } catch (e: Exception) {
            dateString
        }
    }
}
