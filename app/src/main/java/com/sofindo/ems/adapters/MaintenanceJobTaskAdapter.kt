package com.sofindo.ems.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sofindo.ems.R

class MaintenanceJobTaskAdapter(
    private var tasks: List<Map<String, Any>>,
    private val onTaskToggle: (String, Boolean) -> Unit
) : RecyclerView.Adapter<MaintenanceJobTaskAdapter.TaskViewHolder>() {

    class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTaskNumber: TextView = itemView.findViewById(R.id.tv_task_number)
        val tvTaskTitle: TextView = itemView.findViewById(R.id.tv_task_title)
        val checkBox: CheckBox = itemView.findViewById(R.id.checkbox_task)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_maintenance_job_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        // Validate position to prevent IndexOutOfBoundsException
        if (position < 0 || position >= tasks.size) {
            return
        }
        val task = tasks[position]
        val taskId = task["id"]?.toString() ?: ""
        val taskTitle = task["title"]?.toString() ?: ""
        val isCompleted = task["completed"] as? Boolean ?: false
        val doneBy = task["doneBy"]?.toString() ?: ""

        // Set task number
        holder.tvTaskNumber.text = taskId

        // Set task title with doneBy info if completed
        val displayTitle = if (isCompleted && doneBy.isNotEmpty()) {
            "$taskTitle (by: $doneBy)"
        } else {
            taskTitle
        }
        holder.tvTaskTitle.text = displayTitle

        // Set text color and style based on completion status
        if (isCompleted) {
            holder.tvTaskTitle.setTextColor(holder.itemView.context.getColor(R.color.grey))
            holder.tvTaskTitle.paintFlags = holder.tvTaskTitle.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            holder.itemView.isActivated = true
        } else {
            holder.tvTaskTitle.setTextColor(holder.itemView.context.getColor(R.color.black))
            holder.tvTaskTitle.paintFlags = holder.tvTaskTitle.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
            holder.itemView.isActivated = false
        }

        // Set checkbox color
        holder.checkBox.buttonTintList = android.content.res.ColorStateList.valueOf(
            holder.itemView.context.getColor(R.color.status_done)
        )

        // CRITICAL: Remove previous listener FIRST to prevent wrong binding
        holder.checkBox.setOnCheckedChangeListener(null)
        
        // CRITICAL: Store taskId in tag BEFORE setting state to prevent wrong binding
        holder.checkBox.tag = taskId
        holder.itemView.tag = taskId
        
        // Set checkbox state WITHOUT triggering listener (must be after removing listener)
        holder.checkBox.isChecked = isCompleted
        
        // Set new listener that uses tag to ensure correct task (not position)
        // This ensures even if view is recycled, listener always uses correct taskId
        holder.checkBox.setOnCheckedChangeListener { checkBox, isChecked ->
            // Get taskId from tag (this is the correct task for this view, regardless of position)
            val taggedTaskId = checkBox.tag as? String
            if (taggedTaskId.isNullOrEmpty() || taggedTaskId != taskId) {
                // Tag doesn't match current task - ignore (view was recycled)
                return@setOnCheckedChangeListener
            }
            
            // Find the task by taskId (not by position) to get current state
            val currentTask = tasks.find { it["id"]?.toString() == taggedTaskId }
            if (currentTask == null) {
                return@setOnCheckedChangeListener
            }
            
            val currentIsCompleted = currentTask["completed"] as? Boolean ?: false
            
            // Only trigger if state actually changed
            if (isChecked != currentIsCompleted) {
                onTaskToggle(taggedTaskId, isChecked)
            }
        }

        // Make entire item clickable - toggle checkbox when item is clicked
        holder.itemView.setOnClickListener {
            // Get taskId from tag
            val taggedTaskId = holder.itemView.tag as? String
            if (taggedTaskId.isNullOrEmpty()) {
                return@setOnClickListener
            }
            
            // Toggle checkbox (this will trigger the OnCheckedChangeListener)
            holder.checkBox.toggle()
        }
    }

    override fun getItemCount(): Int {
        return try {
            tasks.size
        } catch (e: Exception) {
            0
        }
    }

    fun updateTasks(newTasks: List<Map<String, Any>>) {
        tasks = newTasks
        try {
            // Use notifyDataSetChanged to ensure all views are properly rebound with correct tags
            // This is safer than notifyItemChanged when dealing with view recycling issues
            notifyDataSetChanged()
        } catch (e: Exception) {
            android.util.Log.e("MaintenanceJobTaskAdapter", "Error in notifyDataSetChanged: ${e.message}", e)
        }
    }
    

    

}
