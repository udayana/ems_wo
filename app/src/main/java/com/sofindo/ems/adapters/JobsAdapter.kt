package com.sofindo.ems.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.sofindo.ems.R
import com.sofindo.ems.models.JobItem
import java.io.File
import androidx.core.content.FileProvider

class JobsAdapter(
    private var jobs: List<JobItem>,
    private val onPhotoClick: (Int) -> Unit,
    private val onJobDescriptionChange: (Int, String) -> Unit,
    private val onRemoveJob: (Int) -> Unit,
    private val onAddPhoto: (Int) -> Unit
) : RecyclerView.Adapter<JobsAdapter.JobViewHolder>() {
    
    var photos: List<List<File>> = emptyList()
    
    class JobViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val etJob: EditText = itemView.findViewById(R.id.et_job)
        val btnPhoto: ImageButton = itemView.findViewById(R.id.btn_photo)
        val ivPhotoThumbnail: ImageView = itemView.findViewById(R.id.iv_photo_thumbnail)
        val tvPhotoCount: TextView = itemView.findViewById(R.id.tv_photo_count)
        var textWatcher: android.text.TextWatcher? = null
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): JobViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_job_row, parent, false)
        return JobViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: JobViewHolder, position: Int) {
        // Validate position to prevent IndexOutOfBoundsException
        if (position < 0 || position >= jobs.size) {
            return
        }
        
        val job = jobs[position]
        val context = holder.itemView.context
        
        holder.etJob.setText(job.description)
        holder.etJob.hint = "job ${position + 1}"
        
        // Remove previous listener if exists
        holder.textWatcher?.let { watcher ->
            holder.etJob.removeTextChangedListener(watcher)
        }
        
        // Set text change listener
        val textWatcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val newText = s?.toString() ?: ""
                // Auto capitalize first letter only
                if (newText.isNotEmpty()) {
                    val firstChar = newText[0]
                    if (firstChar.isLowerCase() && firstChar.isLetter()) {
                        val capitalized = firstChar.uppercase() + newText.substring(1)
                        holder.etJob.removeTextChangedListener(this)
                        holder.etJob.setText(capitalized)
                        holder.etJob.setSelection(capitalized.length)
                        holder.etJob.addTextChangedListener(this)
                    }
                }
                onJobDescriptionChange(position, newText)
            }
        }
        
        holder.textWatcher = textWatcher
        holder.etJob.addTextChangedListener(textWatcher)
        
        // Photo button
        if (position < photos.size && photos[position].isNotEmpty()) {
            // Show thumbnail
            val firstPhoto = photos[position][0]
            val photoUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                firstPhoto
            )
            Glide.with(context)
                .load(photoUri)
                .centerCrop()
                .into(holder.ivPhotoThumbnail)
            
            holder.ivPhotoThumbnail.visibility = View.VISIBLE
            holder.btnPhoto.visibility = View.GONE
            
            // Show photo count if more than 1
            if (photos[position].size > 1) {
                holder.tvPhotoCount.text = "+${photos[position].size - 1}"
                holder.tvPhotoCount.visibility = View.VISIBLE
            } else {
                holder.tvPhotoCount.visibility = View.GONE
            }
            
            holder.ivPhotoThumbnail.setOnClickListener {
                onPhotoClick(position)
            }
        } else {
            // Show add photo button
            holder.ivPhotoThumbnail.visibility = View.GONE
            holder.tvPhotoCount.visibility = View.GONE
            holder.btnPhoto.visibility = View.VISIBLE
            holder.btnPhoto.contentDescription = "Add photo ${position + 1}"
            
            holder.btnPhoto.setOnClickListener {
                onAddPhoto(position)
            }
        }
    }
    
    override fun getItemCount(): Int {
        return try {
            jobs.size
        } catch (e: Exception) {
            0
        }
    }
    
    fun updateJobs(newJobs: List<JobItem>) {
        jobs = newJobs
        try {
            notifyDataSetChanged()
        } catch (e: Exception) {
            android.util.Log.e("JobsAdapter", "Error in notifyDataSetChanged: ${e.message}", e)
        }
    }
}

