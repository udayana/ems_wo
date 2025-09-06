package com.sofindo.ems.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.sofindo.ems.R
import com.sofindo.ems.adapters.MaintenanceHistoryAdapter
import com.sofindo.ems.services.MaintenanceService
import com.sofindo.ems.services.UserService
import kotlinx.coroutines.launch

class MaintenanceHistoryFragment : Fragment() {
    
    private lateinit var toolbar: Toolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var layoutError: View
    private lateinit var tvErrorMessage: TextView
    private lateinit var layoutEmpty: View
    private lateinit var tvEmptyMessage: TextView
    
    private var historyList = mutableListOf<Map<String, Any>>()
    private var isLoading = false
    
    // Parameters from arguments
    private var assetNo = ""
    private var mntId = ""
    private var propertyName = ""
    private var propID = ""
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_maintenance_history, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Get parameters from arguments
        arguments?.let { args ->
            assetNo = args.getString("assetNo", "")
            mntId = args.getString("mntId", "")
            propertyName = args.getString("propertyName", "")
            propID = args.getString("propID", "")
        }
        
        initViews(view)
        setupToolbar()
        setupSwipeRefresh()
        loadMaintenanceHistory()
    }
    
    private fun initViews(view: View) {
        toolbar = view.findViewById(R.id.toolbar)
        recyclerView = view.findViewById(R.id.recycler_view)
        progressBar = view.findViewById(R.id.progress_bar)
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout)
        layoutError = view.findViewById(R.id.layout_error)
        tvErrorMessage = view.findViewById(R.id.tv_error_message)
        layoutEmpty = view.findViewById(R.id.layout_empty)
        tvEmptyMessage = view.findViewById(R.id.tv_empty_message)
        
        // Setup RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(context)
    }
    
    private fun setupToolbar() {
        toolbar.title = propertyName
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
        
        // Add refresh button
        toolbar.inflateMenu(R.menu.maintenance_history_menu)
        toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_refresh -> {
                    loadMaintenanceHistory()
                    true
                }
                else -> false
            }
        }
    }
    
    private fun setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener {
            loadMaintenanceHistory()
        }
    }
    
    private fun loadMaintenanceHistory() {
        if (isLoading) return
        
        isLoading = true
        showLoading(true)
        showError(false)
        
        lifecycleScope.launch {
            try {
                // Get propID from UserService if not provided
                val currentPropID = if (propID.isNotEmpty()) propID else UserService.getCurrentPropID() ?: ""
                
                if (currentPropID.isEmpty()) {
                    throw Exception("Property ID tidak ditemukan. Silakan login ulang.")
                }
                
                val historyData = MaintenanceService.getMaintenanceHistory(
                    mntId = mntId,
                    propID = currentPropID
                )
                
                historyList.clear()
                historyList.addAll(historyData)
                
                setupRecyclerView()
                showLoading(false)
                updateUI()
                
            } catch (e: Exception) {
                showError(true, e.message ?: "Terjadi kesalahan saat memuat data")
            } finally {
                isLoading = false
                swipeRefreshLayout.isRefreshing = false
            }
        }
    }
    
    private fun setupRecyclerView() {
        val adapter = MaintenanceHistoryAdapter(historyList)
        recyclerView.adapter = adapter
    }
    
    private fun updateUI() {
        swipeRefreshLayout.isRefreshing = false
        
        if (historyList.isEmpty()) {
            showEmptyState(true)
        } else {
            showEmptyState(false)
        }
    }
    
    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }
    
    private fun showError(show: Boolean, message: String = "") {
        layoutError.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            tvErrorMessage.text = message
        }
    }
    
    private fun showEmptyState(show: Boolean) {
        layoutEmpty.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }
    
    companion object {
        fun newInstance(
            assetNo: String,
            mntId: String,
            propertyName: String,
            propID: String
        ): MaintenanceHistoryFragment {
            return MaintenanceHistoryFragment().apply {
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
