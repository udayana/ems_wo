package com.sofindo.ems.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sofindo.ems.R
import com.sofindo.ems.services.MaintenanceService
import com.sofindo.ems.services.UserService
import com.sofindo.ems.adapters.MaintenanceJobTaskAdapter
import kotlinx.coroutines.launch

class MaintenanceJobTaskFragment : Fragment() {
    
    private lateinit var tvJobTitle: TextView
    private lateinit var tvPeriod: TextView
    private lateinit var tvProgressStatus: TextView
    private lateinit var ivProgressIcon: ImageView
    private lateinit var recyclerView: RecyclerView
    private lateinit var etNote: EditText
    private lateinit var btnSubmit: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var layoutContent: View
    private lateinit var layoutError: View
    private lateinit var tvErrorMessage: TextView
    private lateinit var btnRetry: Button
    
    private var assetNo: String = ""
    private var mntId: String = ""
    private var propertyName: String = ""
    private var propID: String = ""
    
    private var maintenanceAdapter: MaintenanceJobTaskAdapter? = null
    private var tasks: List<Map<String, Any>> = emptyList()
    private var isSubmitting = false
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_maintenance_job_task, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Get arguments
        arguments?.let { args ->
            assetNo = args.getString("assetNo", "")
            mntId = args.getString("mntId", "")
            propertyName = args.getString("propertyName", "")
            propID = args.getString("propID", "")
        }
        
        initViews(view)
        setupToolbar(view)
        loadMaintenanceTasks()
    }
    
    private fun initViews(view: View) {
        tvJobTitle = view.findViewById(R.id.tv_job_title)
        tvPeriod = view.findViewById(R.id.tv_period)
        tvProgressStatus = view.findViewById(R.id.tv_progress_status)
        ivProgressIcon = view.findViewById(R.id.iv_progress_icon)
        recyclerView = view.findViewById(R.id.recycler_view)
        etNote = view.findViewById(R.id.et_note)
        btnSubmit = view.findViewById(R.id.btn_submit)
        progressBar = view.findViewById(R.id.progress_bar)
        layoutContent = view.findViewById(R.id.layout_content)
        layoutError = view.findViewById(R.id.layout_error)
        tvErrorMessage = view.findViewById(R.id.tv_error_message)
        btnRetry = view.findViewById(R.id.btn_retry)
        
        // Setup RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(context)
        
        // Setup submit button
        btnSubmit.setOnClickListener {
            submitMaintenanceTasks()
        }
        
        // Setup retry button
        btnRetry.setOnClickListener {
            loadMaintenanceTasks()
        }
    }
    
    private fun setupToolbar(view: View) {
        val toolbar = view.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
        
        // Set job details
        tvJobTitle.text = propertyName
        tvPeriod.text = "Periode: monthly"
    }
    
    private fun loadMaintenanceTasks() {
        showLoading(true)
        showError(false)
        
        lifecycleScope.launch {
            try {
                // Get propID from UserService if not provided
                val currentPropID = if (propID.isNotEmpty()) propID else UserService.getCurrentPropID() ?: ""
                
                if (currentPropID.isEmpty()) {
                    throw Exception("Property ID tidak ditemukan. Silakan login ulang.")
                }
                
                val response = MaintenanceService.getMaintenanceTaskJob(
                    context = requireContext(),
                    noAssets = assetNo,
                    propID = currentPropID
                )
                
                if (response["data"] != null && response["data"] is List<*>) {
                    val maintenanceDataList = response["data"] as List<*>
                    
                    val processedTasks = mutableListOf<Map<String, Any>>()
                    
                    // Process all maintenance tasks from the API
                    for (recordIndex in maintenanceDataList.indices) {
                        val maintenanceData = maintenanceDataList[recordIndex] as? Map<String, Any>
                        val jobtask = maintenanceData?.get("jobtask")?.toString() ?: ""
                        
                        // Debug logging untuk melihat data dari API
                        android.util.Log.d("MaintenanceJobTask", "Maintenance data: $maintenanceData")
                        
                        // Parse jobtask text into individual tasks
                        if (jobtask.isNotEmpty()) {
                            var taskText = jobtask.trim()
                            
                            // Remove common numbering patterns and extra punctuation
                            taskText = taskText.replace(Regex("^\\d+[\\.\\)]\\s*"), "")
                            taskText = taskText.replace(Regex("^[-•*]\\s*"), "")
                            taskText = taskText.trim()
                            
                            if (taskText.isNotEmpty() && taskText.length > 3) {
                                val taskNo = maintenanceData?.get("no")?.toString() ?: ""
                                val mntId = maintenanceData?.get("mntId")?.toString() ?: ""
                                
                                android.util.Log.d("MaintenanceJobTask", "Task $recordIndex: taskNo=$taskNo, mntId=$mntId, title=$taskText")
                                
                                val mntUniq = maintenanceData?.get("mntUniq")?.toString() ?: ""
                                
                                android.util.Log.d("MaintenanceJobTask", "Task $recordIndex: taskNo=$taskNo, mntId=$mntId, mntUniq=$mntUniq, title=$taskText")
                                
                                processedTasks.add(
                                    mapOf(
                                        "id" to (recordIndex + 1).toString(),
                                        "title" to taskText,
                                        "completed" to (maintenanceData?.get("done") == 1 || maintenanceData?.get("done") == "1"),
                                        "taskNo" to taskNo,
                                        "mntId" to mntId,
                                        "mntUniq" to mntUniq
                                    )
                                )
                            }
                        }
                    }
                    
                    tasks = processedTasks
                    setupRecyclerView()
                    showLoading(false)
                } else {
                    throw Exception("Tidak ada data maintenance task yang ditemukan")
                }
                
            } catch (e: Exception) {
                showError(true, e.message ?: "Terjadi kesalahan saat memuat data")
            }
        }
    }
    
    private fun setupRecyclerView() {
        maintenanceAdapter = MaintenanceJobTaskAdapter(tasks) { taskId, isCompleted ->
            // Handle task completion toggle with proper API calls
            updateTaskStatus(taskId, isCompleted)
        }
        
        recyclerView.adapter = maintenanceAdapter
        updateProgressStatus()
    }
    
    private fun updateTaskStatus(taskId: String, isCompleted: Boolean) {
        lifecycleScope.launch {
            try {
                // Find the task
                val taskIndex = tasks.indexOfFirst { it["id"] == taskId }
                if (taskIndex == -1) return@launch
                
                val task = tasks[taskIndex]
                
                // Get user info
                val user = UserService.getCurrentUser()
                val userName = user?.fullName ?: user?.username ?: "Unknown User"
                
                // Get propID
                val currentPropID = if (propID.isNotEmpty()) propID else UserService.getCurrentPropID() ?: ""
                if (currentPropID.isEmpty()) {
                    Toast.makeText(context, "Property ID not found. Please login again.", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                // Step 1: Update task status
                val taskNo = task["taskNo"]?.toString() ?: ""
                val taskMntId = task["mntId"]?.toString() ?: ""
                val taskMntUniq = task["mntUniq"]?.toString() ?: ""
                android.util.Log.d("MaintenanceJobTask", "Updating task: taskNo=$taskNo, mntId=$taskMntId, mntUniq=$taskMntUniq, isCompleted=$isCompleted, userName=$userName")
                android.util.Log.d("MaintenanceJobTask", "Full task data: $task")
                
                // Use taskNo as taskId (6 digit number)
                val taskIdToUse = taskNo
                
                MaintenanceService.updateMaintenanceTaskStatus(
                    taskId = taskIdToUse,
                    isDone = isCompleted,
                    doneBy = if (isCompleted) userName else ""
                )
                
                // Update local state - only update the specific task
                val updatedTasks = tasks.toMutableList()
                updatedTasks[taskIndex] = updatedTasks[taskIndex].toMutableMap().apply {
                    put("completed", isCompleted)
                }
                tasks = updatedTasks
                // Don't call notifyItemChanged to avoid rebinding and resetting checkbox
                updateProgressStatus()
                
                // Update visual styling for the specific task item
                val holder = maintenanceAdapter?.let { adapter ->
                    (recyclerView.findViewHolderForAdapterPosition(taskIndex) as? MaintenanceJobTaskAdapter.TaskViewHolder)
                }
                holder?.let { viewHolder ->
                    val task = tasks[taskIndex]
                    val isCompleted = task["completed"] as? Boolean ?: false
                    
                    // Update text styling
                    if (isCompleted) {
                        viewHolder.tvTaskTitle.setTextColor(resources.getColor(R.color.grey, null))
                        viewHolder.tvTaskTitle.paintFlags = viewHolder.tvTaskTitle.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                        viewHolder.itemView.isActivated = true
                    } else {
                        viewHolder.tvTaskTitle.setTextColor(resources.getColor(R.color.black, null))
                        viewHolder.tvTaskTitle.paintFlags = viewHolder.tvTaskTitle.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
                        viewHolder.itemView.isActivated = false
                    }
                }
                
                // Step 2: If task is completed, check if all tasks are done
                if (isCompleted) {
                    val allCompleted = tasks.all { it["completed"] == true }
                    
                    if (allCompleted) {
                        // Step 3: Update maintenance notes and event
                        try {
                            val note = etNote.text.toString().trim()
                            
                            // Update notes
                            MaintenanceService.updateMaintenanceNotes(
                                mntId = mntId,
                                notes = note,
                                propID = currentPropID
                            )
                            
                            // Update event status
                            MaintenanceService.updateMaintenanceEvent(
                                mntId = mntId,
                                status = "done",
                                notes = ""
                            )
                            
                            Toast.makeText(context, "All tasks completed! Maintenance event updated.", Toast.LENGTH_LONG).show()
                        } catch (eventError: Exception) {
                            Toast.makeText(context, "Task updated but event update failed: ${eventError.message}", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(context, "Task marked as completed", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Task marked as incomplete", Toast.LENGTH_SHORT).show()
                }
                
            } catch (e: Exception) {
                val errorMsg = "Error updating task: ${e.message}"
                android.util.Log.e("MaintenanceJobTask", errorMsg)
                Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                
                // Revert the UI state if API call failed
                val updatedTasks = tasks.toMutableList()
                val taskIndex = updatedTasks.indexOfFirst { it["id"] == taskId }
                if (taskIndex != -1) {
                    updatedTasks[taskIndex] = updatedTasks[taskIndex].toMutableMap().apply {
                        put("completed", !isCompleted) // Revert to previous state
                    }
                    tasks = updatedTasks
                    // Don't call notifyItemChanged to avoid rebinding and resetting checkbox
                    updateProgressStatus()
                }
            }
        }
    }
    
    private fun updateProgressStatus() {
        val completedCount = tasks.count { it["completed"] == true }
        val totalCount = tasks.size
        
        tvProgressStatus.text = "Progress: $completedCount/$totalCount tasks completed"
        btnSubmit.text = "Submit Maintenance ($completedCount/$totalCount)"
        
        // Update progress icon based on completion
        if (completedCount == totalCount && totalCount > 0) {
            ivProgressIcon.setImageResource(R.drawable.ic_status_done)
            ivProgressIcon.setColorFilter(resources.getColor(R.color.status_done, null))
        } else {
            ivProgressIcon.setImageResource(R.drawable.ic_task)
            ivProgressIcon.setColorFilter(resources.getColor(R.color.blue, null))
        }
    }
    
    private fun submitMaintenanceTasks() {
        if (isSubmitting) return
        
        isSubmitting = true
        btnSubmit.isEnabled = false
        btnSubmit.text = "Submitting..."
        
        lifecycleScope.launch {
            try {
                val note = etNote.text.toString().trim()
                val completedTasks = tasks.filter { it["completed"] == true }
                
                if (completedTasks.isEmpty()) {
                    Toast.makeText(context, "Pilih minimal satu task untuk disubmit", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                // Get propID from UserService if not provided
                val currentPropID = if (propID.isNotEmpty()) propID else UserService.getCurrentPropID() ?: ""
                
                if (currentPropID.isEmpty()) {
                    throw Exception("Property ID tidak ditemukan. Silakan login ulang.")
                }
                
                // Submit completed tasks
                for (task in completedTasks) {
                    val taskId = task["id"]?.toString() ?: ""
                    
                    if (taskId.isNotEmpty()) {
                        MaintenanceService.updateMaintenanceTaskStatus(
                            taskId = taskId,
                            isDone = true,
                            doneBy = "user" // TODO: Get actual user ID
                        )
                    }
                }
                
                Toast.makeText(context, "Maintenance tasks berhasil disubmit", Toast.LENGTH_SHORT).show()
                
                // Clear note
                etNote.text.clear()
                
                // Refresh tasks
                loadMaintenanceTasks()
                
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isSubmitting = false
                btnSubmit.isEnabled = true
                btnSubmit.text = "Submit"
            }
        }
    }
    
    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        layoutContent.visibility = if (show) View.GONE else View.VISIBLE
    }
    
    private fun showError(show: Boolean, message: String = "") {
        layoutError.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            tvErrorMessage.text = message
        }
    }
    
    companion object {
        fun newInstance(
            assetNo: String,
            mntId: String,
            propertyName: String,
            propID: String
        ): MaintenanceJobTaskFragment {
            return MaintenanceJobTaskFragment().apply {
                arguments = Bundle().apply {
                    putString("assetNo", assetNo)
                    putString("mntId", mntId)
                    putString("propertyName", propertyName)
                    putString("propID", propID)
                }
            }
        }
    }
}
