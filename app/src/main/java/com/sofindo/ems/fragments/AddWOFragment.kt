package com.sofindo.ems.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.sofindo.ems.R
import com.sofindo.ems.api.RetrofitClient
import com.sofindo.ems.services.UserService
import kotlinx.coroutines.launch

class AddWOFragment : Fragment() {
    
    private lateinit var etJob: EditText
    private lateinit var etLocation: EditText
    private lateinit var etRemarks: EditText
    private lateinit var spinnerCategory: Spinner
    private lateinit var spinnerDepartment: Spinner
    private lateinit var spinnerPriority: Spinner
    private lateinit var btnSubmit: Button
    private lateinit var tvLoading: TextView
    
    private var categories = mutableListOf<String>()
    private var departments = mutableListOf<String>()
    private val priorities = listOf("Low", "Medium", "High")
    
    private var currentPropID: String? = null
    private var username: String? = null
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_add_wo, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initViews(view)
        setupListeners()
        initializeData()
    }
    
    private fun initViews(view: View) {
        etJob = view.findViewById(R.id.et_job)
        etLocation = view.findViewById(R.id.et_location)
        etRemarks = view.findViewById(R.id.et_remarks)
        spinnerCategory = view.findViewById(R.id.spinner_category)
        spinnerDepartment = view.findViewById(R.id.spinner_department)
        spinnerPriority = view.findViewById(R.id.spinner_priority)
        btnSubmit = view.findViewById(R.id.btn_submit)
        tvLoading = view.findViewById(R.id.tv_loading)
    }
    
    private fun setupListeners() {
        btnSubmit.setOnClickListener {
            submitWorkOrder()
        }
    }
    
    private fun initializeData() {
        lifecycleScope.launch {
            try {
                val user = UserService.getCurrentUser()
                currentPropID = user?.propID
                username = user?.username ?: user?.email
                
                if (!currentPropID.isNullOrEmpty() && !username.isNullOrEmpty()) {
                    loadMasterData()
                } else {
                    showError("User data not found")
                }
            } catch (e: Exception) {
                showError("Failed to load data: ${e.message}")
            }
        }
    }
    
    private fun loadMasterData() {
        lifecycleScope.launch {
            try {
                val categoriesResult = RetrofitClient.apiService.getCategories()
                val departmentsResult = RetrofitClient.apiService.getDepartments()
                
                if (isAdded) {
                    categories.clear()
                    categories.addAll(categoriesResult)
                    if (!categories.contains("General")) {
                        categories.add(0, "General")
                    }
                    
                    departments.clear()
                    departments.addAll(departmentsResult)
                    
                    setupSpinners()
                    hideLoading()
                }
            } catch (e: Exception) {
                if (isAdded) {
                    showError("Failed to load master data: ${e.message}")
                }
            }
        }
    }
    
    private fun setupSpinners() {
        // Category Spinner
        val categoryAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, categories)
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCategory.adapter = categoryAdapter
        
        // Department Spinner
        val departmentAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, departments)
        departmentAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerDepartment.adapter = departmentAdapter
        
        // Priority Spinner
        val priorityAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, priorities)
        priorityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerPriority.adapter = priorityAdapter
        
        // Set defaults
        if (categories.isNotEmpty()) {
            spinnerCategory.setSelection(0) // General
        }
        
        val engineeringIndex = departments.indexOfFirst { it.equals("Engineering", ignoreCase = true) }
        if (engineeringIndex >= 0) {
            spinnerDepartment.setSelection(engineeringIndex)
        } else if (departments.isNotEmpty()) {
            spinnerDepartment.setSelection(0)
        }
        
        spinnerPriority.setSelection(0) // Low
    }
    
    private fun submitWorkOrder() {
        val job = etJob.text.toString().trim()
        val location = etLocation.text.toString().trim()
        val remarks = etRemarks.text.toString().trim()
        val category = spinnerCategory.selectedItem.toString()
        val department = spinnerDepartment.selectedItem.toString()
        val priority = spinnerPriority.selectedItem.toString()
        
        // Validation
        if (job.isEmpty()) {
            showError("Job description is required")
            return
        }
        
        if (location.isEmpty()) {
            showError("Location is required")
            return
        }
        
        if (currentPropID.isNullOrEmpty() || username.isNullOrEmpty()) {
            showError("User data not found")
            return
        }
        
        showLoading()
        
        lifecycleScope.launch {
            try {
                val result = RetrofitClient.apiService.addWorkOrder(
                    propID = currentPropID!!,
                    username = username!!,
                    job = job,
                    location = location,
                    remarks = remarks,
                    category = category,
                    department = department,
                    priority = priority
                )
                
                if (isAdded) {
                    if (result["success"] == true || result["success"] == "true") {
                        showSuccess("Work order added successfully")
                        clearForm()
                    } else {
                        showError(result["message"]?.toString() ?: "Failed to add work order")
                    }
                }
            } catch (e: Exception) {
                if (isAdded) {
                    showError("Failed to submit work order: ${e.message}")
                }
            } finally {
                hideLoading()
            }
        }
    }
    
    private fun clearForm() {
        etJob.text.clear()
        etLocation.text.clear()
        etRemarks.text.clear()
        spinnerCategory.setSelection(0)
        val engineeringIndex = departments.indexOfFirst { it.equals("Engineering", ignoreCase = true) }
        if (engineeringIndex >= 0) {
            spinnerDepartment.setSelection(engineeringIndex)
        }
        spinnerPriority.setSelection(0)
    }
    
    private fun showLoading() {
        btnSubmit.isEnabled = false
        tvLoading.visibility = View.VISIBLE
    }
    
    private fun hideLoading() {
        btnSubmit.isEnabled = true
        tvLoading.visibility = View.GONE
    }
    
    private fun showError(message: String) {
        // TODO: Show error message
        hideLoading()
    }
    
    private fun showSuccess(message: String) {
        // TODO: Show success message
        hideLoading()
    }
}
