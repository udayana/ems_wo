package com.sofindo.ems.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.sofindo.ems.R
import com.sofindo.ems.dialogs.ImageViewerDialog
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
        val layoutPhotos: LinearLayout = itemView.findViewById(R.id.layout_photos)
        val ivPhoto1: ImageView = itemView.findViewById(R.id.iv_photo_1)
        val ivPhoto2: ImageView = itemView.findViewById(R.id.iv_photo_2)
        val ivPhoto3: ImageView = itemView.findViewById(R.id.iv_photo_3)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_maintenance_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        // Validate position to prevent IndexOutOfBoundsException
        if (position < 0 || position >= historyList.size) {
            return
        }
        
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

        // Set photos (photo1, photo2, photo3)
        val photo1 = history["photo1"]?.toString()?.trim() ?: ""
        val photo2 = history["photo2"]?.toString()?.trim() ?: ""
        val photo3 = history["photo3"]?.toString()?.trim() ?: ""
        
        val hasPhotos = photo1.isNotEmpty() && photo1 != "null" || 
                       photo2.isNotEmpty() && photo2 != "null" || 
                       photo3.isNotEmpty() && photo3 != "null"
        
        if (hasPhotos) {
            holder.layoutPhotos.visibility = View.VISIBLE
            
            // Base URL for photos
            val baseUrl = "https://emshotels.net/photo/maintenance/"
            
            // Load photo1
            if (photo1.isNotEmpty() && photo1 != "null") {
                holder.ivPhoto1.visibility = View.VISIBLE
                val photo1Url = "$baseUrl$photo1"
                Glide.with(context)
                    .load(photo1Url)
                    .placeholder(R.drawable.photo_preview_background)
                    .error(R.drawable.photo_preview_background)
                    .centerCrop()
                    .into(holder.ivPhoto1)
                
                // Add click listener to show full screen image
                holder.ivPhoto1.setOnClickListener {
                    val dialog = ImageViewerDialog(context, photo1Url)
                    dialog.show()
                }
            } else {
                holder.ivPhoto1.visibility = View.GONE
            }
            
            // Load photo2
            if (photo2.isNotEmpty() && photo2 != "null") {
                holder.ivPhoto2.visibility = View.VISIBLE
                val photo2Url = "$baseUrl$photo2"
                Glide.with(context)
                    .load(photo2Url)
                    .placeholder(R.drawable.photo_preview_background)
                    .error(R.drawable.photo_preview_background)
                    .centerCrop()
                    .into(holder.ivPhoto2)
                
                // Add click listener to show full screen image
                holder.ivPhoto2.setOnClickListener {
                    val dialog = ImageViewerDialog(context, photo2Url)
                    dialog.show()
                }
            } else {
                holder.ivPhoto2.visibility = View.GONE
            }
            
            // Load photo3
            if (photo3.isNotEmpty() && photo3 != "null") {
                holder.ivPhoto3.visibility = View.VISIBLE
                val photo3Url = "$baseUrl$photo3"
                Glide.with(context)
                    .load(photo3Url)
                    .placeholder(R.drawable.photo_preview_background)
                    .error(R.drawable.photo_preview_background)
                    .centerCrop()
                    .into(holder.ivPhoto3)
                
                // Add click listener to show full screen image
                holder.ivPhoto3.setOnClickListener {
                    val dialog = ImageViewerDialog(context, photo3Url)
                    dialog.show()
                }
            } else {
                holder.ivPhoto3.visibility = View.GONE
            }
        } else {
            holder.layoutPhotos.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int {
        return try {
            historyList.size
        } catch (e: Exception) {
            0
        }
    }

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
