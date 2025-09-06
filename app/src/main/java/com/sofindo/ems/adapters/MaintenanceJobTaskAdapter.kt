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

        // Set checkbox state
        holder.checkBox.isChecked = isCompleted

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

        // Handle checkbox click - prevent infinite loop
        holder.checkBox.setOnCheckedChangeListener(null) // Remove previous listener
        holder.checkBox.isChecked = isCompleted // Set state without triggering listener
        
        holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
            // Only call if state actually changed
            if (isChecked != isCompleted) {
                onTaskToggle(taskId, isChecked)
            }
        }

        // Make entire item clickable - but only if checkbox is not already being handled
        holder.itemView.setOnClickListener {
            // Only trigger if the click is not on the checkbox itself
            // This prevents double-triggering when clicking the checkbox
        }
    }

    override fun getItemCount(): Int = tasks.size

    fun updateTasks(newTasks: List<Map<String, Any>>) {
        tasks = newTasks
        notifyDataSetChanged()
    }
    

    

}
