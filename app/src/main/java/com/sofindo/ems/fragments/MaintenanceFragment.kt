package com.sofindo.ems.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

import com.sofindo.ems.R
import com.sofindo.ems.adapters.MaintenanceAdapter
import com.sofindo.ems.models.Maintenance
import com.sofindo.ems.services.MaintenanceService
import com.sofindo.ems.services.UserService
import kotlinx.coroutines.launch

class MaintenanceFragment : Fragment() {
    
    private lateinit var toolbar: Toolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvError: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var btnOpenScanner: MaterialButton
    
    private var maintenanceList: List<Maintenance> = emptyList()
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_maintenance, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initViews(view)
        loadMaintenanceData()
    }
    
    private fun initViews(view: View) {
        toolbar = view.findViewById(R.id.toolbar)
        recyclerView = view.findViewById(R.id.recycler_view)
        progressBar = view.findViewById(R.id.progress_bar)
        tvError = view.findViewById(R.id.tv_error)
        tvEmpty = view.findViewById(R.id.tv_empty)
        btnOpenScanner = view.findViewById(R.id.btn_open_scanner_custom)
        
        // Setup Open Scanner Button
        btnOpenScanner.setOnClickListener {
            openScanner()
        }
        
        // Setup RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(context)
    }
    
    private fun loadMaintenanceData() {
        showLoading(true)
        hideError()
        hideEmpty()
        
        lifecycleScope.launch {
            try {
                // Debug: Check if user is logged in
                val currentUser = UserService.getCurrentUser()
                val currentPropID = UserService.getCurrentPropID()
                
                // Check user login status
                
                if (currentUser == null || currentPropID.isNullOrEmpty()) {
                    showError("User not logged in or Property ID not found. Please login again.")
                    return@launch
                }
                
                maintenanceList = MaintenanceService.getMaintenanceThisWeek(requireContext())
                
                if (maintenanceList.isEmpty()) {
                    showEmpty()
                } else {
                    showMaintenanceList()
                }
                
            } catch (e: Exception) {
                showError(e.message ?: "Unknown error occurred")
            } finally {
                showLoading(false)
            }
        }
    }
    
    private fun showMaintenanceList() {
        val adapter = MaintenanceAdapter(maintenanceList) { maintenance ->
            // Handle item click - navigate to detail
            navigateToMaintenanceDetail(maintenance)
        }
        
        recyclerView.adapter = adapter
        recyclerView.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE
        tvError.visibility = View.GONE
    }
    
    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }
    
    private fun showError(message: String) {
        tvError.text = message
        tvError.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        tvEmpty.visibility = View.GONE
    }
    
    private fun hideError() {
        tvError.visibility = View.GONE
    }
    
    private fun showEmpty() {
        tvEmpty.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        tvError.visibility = View.GONE
    }
    
    private fun hideEmpty() {
        tvEmpty.visibility = View.GONE
    }
    
    private fun navigateToMaintenanceDetail(maintenance: Maintenance) {
        // Navigate to MaintenanceDetailFragment
        val detailFragment = com.sofindo.ems.fragments.MaintenanceDetailFragment.newInstance(
            maintenance.id.toString(), 
            maintenance.propID
        )
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, detailFragment)
            .addToBackStack(null)
            .commit()
    }
    
    private fun openScanner() {
        // Navigate to QR Scanner Fragment
        val qrScannerFragment = com.sofindo.ems.fragments.QRScannerFragment.newInstance()
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, qrScannerFragment)
            .addToBackStack(null)
            .commit()
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh data when returning to this fragment
        loadMaintenanceData()
    }
}

