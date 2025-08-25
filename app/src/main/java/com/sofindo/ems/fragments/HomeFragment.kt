package com.sofindo.ems.fragments

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.sofindo.ems.R
import com.sofindo.ems.adapters.WorkOrderAdapter
import com.sofindo.ems.api.RetrofitClient
import com.sofindo.ems.services.UserService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Timer
import java.util.TimerTask

class HomeFragment : Fragment() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var searchView: EditText
    private lateinit var btnFilter: ImageButton
    private lateinit var tvEmpty: TextView
    
    // Sama persis dengan Flutter _BacaWOViewState
    private var workOrders = mutableListOf<Map<String, Any>>()
    private var isLoading = true
    private var isLoadingMore = false
    private var hasMoreData = true
    private var currentPage = 1
    private var searchText = ""
    private var selectedStatus = ""
    private var currentPropID: String? = null
    private val statusCounts = mutableMapOf<String, Int>()
    private var isLoadingStatusCounts = true
    
    // Sama persis dengan Flutter statusOptions
    private val statusOptions = listOf("", "new", "received", "on progress", "pending", "done")
    
    // Search debounce timer (sama seperti Flutter)
    private var searchDebounce: Timer? = null
    
    // RecyclerView adapter
    private lateinit var workOrderAdapter: WorkOrderAdapter
    
    // Request codes
    companion object {
        private const val REQUEST_CHANGE_STATUS = 1001
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initViews(view)
        setupListeners()
        initializeData()
    }
    
    private fun initViews(view: View) {
        recyclerView = view.findViewById(R.id.recycler_view)
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh)
        searchView = view.findViewById(R.id.search_view)
        btnFilter = view.findViewById(R.id.btn_filter)
        tvEmpty = view.findViewById(R.id.tv_empty)
        
        // Setup RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(context)
        
        // Initialize adapter with menu callbacks for Home (Detail and Follow Up only)
        workOrderAdapter = WorkOrderAdapter(
            onItemClick = { workOrder ->
                // Handle work order click (this won't be used since we're using card click for menu)
                onWorkOrderClick(workOrder)
            },
            onEditClick = { workOrder ->
                // Handle edit click (not used in Home)
                onEditWorkOrder(workOrder)
            },
            onDeleteClick = { workOrder ->
                // Handle delete click (not used in Home)
                onDeleteWorkOrder(workOrder)
            },
            onDetailClick = { workOrder ->
                // Handle detail click
                onDetailWorkOrder(workOrder)
            },
            onFollowUpClick = { workOrder ->
                // Handle follow up click
                onFollowUpWorkOrder(workOrder)
            },
            showSender = false, 
            replaceWotoWithOrderBy = true,
            isHomeFragment = true
        )
        
        recyclerView.adapter = workOrderAdapter
        
        // Add infinite scroll listener
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
                
                if (!isLoadingMore && hasMoreData) {
                    if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount &&
                        firstVisibleItemPosition >= 0 &&
                        totalItemCount >= 10) {
                        
                        // Load more data
                        loadMoreData()
                    }
                }
            }
        })
    }
    
    private fun setupListeners() {
        swipeRefreshLayout.setOnRefreshListener {
            refreshData()
        }
        
        searchView.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                onSearchChanged(s?.toString() ?: "")
            }
        })
        
        btnFilter.setOnClickListener {
            showFilterPopup()
        }
    }
    
    private fun showFilterPopup() {
        val popup = PopupMenu(requireContext(), btnFilter)
        
        // Load status counts when filter is opened (sama seperti Flutter)
        lifecycleScope.launch {
            if (currentPropID != null && currentPropID!!.isNotEmpty()) {
                val userDept = UserService.getCurrentDept()
                val finalDept = userDept ?: "Engineering"
                loadStatusCountsInBackground(finalDept)
            }
        }
        
        statusOptions.forEach { status ->
            val count = if (status.isEmpty()) {
                statusCounts[""] ?: 0
            } else if (status.lowercase() == "new") {
                statusCounts["new"] ?: 0
            } else {
                statusCounts[status] ?: 0
            }
            
            val displayName = when {
                status.isEmpty() -> "All"
                status.lowercase() == "new" -> "NEW"
                else -> status.uppercase()
            }
            
            val menuItem = popup.menu.add("$count → $displayName")
            menuItem.setOnMenuItemClickListener {
                onStatusChanged(status)
                true
            }
        }
        
        popup.show()
    }
    
    private fun onWorkOrderClick(workOrder: Map<String, Any>) {
        // TODO: Navigate to work order detail
        val woNumber = workOrder["nour"]?.toString() ?: ""
        Toast.makeText(context, "Clicked WO: $woNumber", Toast.LENGTH_SHORT).show()
    }
    
    private fun onEditWorkOrder(workOrder: Map<String, Any>) {
        // TODO: Navigate to edit work order
        val woNumber = workOrder["nour"]?.toString() ?: ""
        Toast.makeText(context, "Edit WO: $woNumber", Toast.LENGTH_SHORT).show()
    }
    
    private fun onDeleteWorkOrder(workOrder: Map<String, Any>) {
        // TODO: Show delete confirmation dialog
        val woNumber = workOrder["nour"]?.toString() ?: ""
        Toast.makeText(context, "Delete WO: $woNumber", Toast.LENGTH_SHORT).show()
    }
    
    private fun onDetailWorkOrder(workOrder: Map<String, Any>) {
        // Navigate to work order detail activity
        val intent = android.content.Intent(context, com.sofindo.ems.activities.WorkOrderDetailActivity::class.java)
        intent.putExtra("workOrder", workOrder as java.io.Serializable)
        startActivity(intent)
    }
    
    private fun onFollowUpWorkOrder(workOrder: Map<String, Any>) {
        // Navigate to change status activity
        val intent = android.content.Intent(context, com.sofindo.ems.activities.ChangeStatusWOActivity::class.java)
        intent.putExtra("workOrder", workOrder as java.io.Serializable)
        startActivityForResult(intent, REQUEST_CHANGE_STATUS)
    }
    
    // Sama persis dengan Flutter _initializeData()
    private fun initializeData() {
        lifecycleScope.launch {
            try {
                // Get propID dan department sekaligus (sama seperti Flutter)
                val propID = UserService.getCurrentPropID()
                val userDept = UserService.getCurrentDept()
                val finalDept = userDept ?: "Engineering"
                
                android.util.Log.d("HomeFragment", "Debug: propID = $propID")
                android.util.Log.d("HomeFragment", "Debug: userDept = $userDept")
                android.util.Log.d("HomeFragment", "Debug: finalDept = $finalDept")
                
                currentPropID = propID ?: ""
                
                if (!currentPropID.isNullOrEmpty()) {
                    android.util.Log.d("HomeFragment", "Debug: Loading work orders...")
                    // Hanya load work orders, counter filter TIDAK di-load otomatis
                    // Counter filter akan di-load ketika user klik icon filter
                    loadWorkOrdersParallel(finalDept)
                } else {
                    android.util.Log.e("HomeFragment", "Debug: propID is empty or null!")
                    showEmptyState("No property ID found. Please login again.")
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeFragment", "Error initializing data: ${e.message}")
                showEmptyState("Error loading data: ${e.message}")
            } finally {
                isLoading = false
                isLoadingStatusCounts = false
                updateUI()
            }
        }
    }
    
    // Load work orders saja (tanpa status counts otomatis) - sama seperti Flutter
    private fun loadWorkOrdersParallel(department: String) {
        lifecycleScope.launch {
            try {
                // Debug: Log untuk troubleshooting
                android.util.Log.d("HomeFragment", "Loading work orders...")
                android.util.Log.d("HomeFragment", "propID: $currentPropID")
                android.util.Log.d("HomeFragment", "department: $department")
                android.util.Log.d("HomeFragment", "status: $selectedStatus")
                
                // Debug: Log the full URL
                val fullUrl = "https://emshotels.net/apiKu/baca_wo.php?propID=${currentPropID}&woto=${department}&status=${selectedStatus}&page=1"
                android.util.Log.d("HomeFragment", "Full API URL: $fullUrl")
                
                // Fetch langsung dari API tanpa cache
                val workOrdersResult = RetrofitClient.apiService.getWorkOrders(
                    propID = currentPropID!!,
                    woto = department,
                    status = selectedStatus,
                    page = 1
                )
                
                android.util.Log.d("HomeFragment", "API Response received: ${workOrdersResult.size} items")
                android.util.Log.d("HomeFragment", "API Response: $workOrdersResult")
                
                if (isAdded) {
                    workOrders.clear()
                    workOrders.addAll(workOrdersResult)
                    hasMoreData = workOrdersResult.size >= 10
                    currentPage = 2 // Set untuk next page
                    
                    android.util.Log.d("HomeFragment", "workOrders updated: ${workOrders.size} items")
                    
                    updateUI()
                    
                    // Load counter filter setelah work orders berhasil di-load
                    // Ini memastikan counter tersedia saat pertama kali filter diklik
                    loadStatusCountsInBackground(department)
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeFragment", "Error loading work orders", e)
                // Error loading work orders
                if (isAdded) {
                    showEmptyState("Failed to load work orders: ${e.message}")
                }
            }
        }
    }
    
    // Load status counts hanya ketika filter diklik - sama seperti Flutter
    private fun loadStatusCountsInBackground(department: String) {
        isLoadingStatusCounts = true
        
        lifecycleScope.launch {
            try {
                // Load dari API get_all_statuses
                val statusData = RetrofitClient.apiService.getAllStatuses(
                    propID = currentPropID!!,
                    woto = department
                )
                
                // Update status counts dari API
                updateStatusCountsFromAPI(statusData)
            } catch (e: Exception) {
                // API get_all_statuses failed
                // Set default values jika API gagal
                statusCounts.clear()
                statusCounts[""] = 0
                statusCounts["new"] = 0
                for (status in statusOptions) {
                    if (status.isNotEmpty()) {
                        statusCounts[status] = 0
                    }
                }
            } finally {
                if (isAdded) {
                    isLoadingStatusCounts = false
                }
            }
        }
    }
    
    // Optimasi: Update status counts dari API response - sama seperti Flutter
    private fun updateStatusCountsFromAPI(statusData: List<Map<String, Any>>) {
        if (!isAdded) return
        
        statusCounts.clear()
        var totalCount = 0
        
        for (status in statusData) {
            val statusName = status["status"]?.toString() ?: ""
            val count = (status["count"] as? Number)?.toInt() ?: 0
            
            if (statusName.isEmpty()) {
                statusCounts["new"] = count
            } else {
                statusCounts[statusName] = count
            }
            totalCount += count
        }
        
        statusCounts[""] = totalCount // Total untuk "All"
        
        // Pastikan semua status yang diperlukan ada
        for (status in statusOptions) {
            if (status.isNotEmpty() && !statusCounts.containsKey(status)) {
                statusCounts[status] = 0
            }
        }
        
        // Pastikan 'new' ada
        if (!statusCounts.containsKey("new")) {
            statusCounts["new"] = 0
        }
    }
    
    private fun loadData(reset: Boolean = false) {
        if (isLoading && !reset) return
        if (isLoadingMore && !reset) return
        
        if (reset) {
            isLoading = true
            isLoadingMore = false
        } else {
            isLoadingMore = true
        }
        
        lifecycleScope.launch {
            try {
                if (reset) {
                    workOrders.clear()
                    currentPage = 1
                    hasMoreData = true
                }
                
                if (searchText.isEmpty()) {
                    if (currentPropID.isNullOrEmpty()) {
                        isLoading = false
                        isLoadingMore = false
                        return@launch
                    }
                    
                    val userDept = UserService.getCurrentDept()
                    val finalDept = userDept ?: "Engineering"
                    
                    val workOrdersResult = RetrofitClient.apiService.getWorkOrders(
                        propID = currentPropID!!,
                        woto = finalDept,
                        status = selectedStatus,
                        page = currentPage
                    )
                    
                    if (isAdded) {
                        if (reset) {
                            workOrders.clear()
                            workOrders.addAll(workOrdersResult)
                        } else {
                            workOrders.addAll(workOrdersResult)
                        }
                        
                        if (workOrdersResult.size < 10) {
                            hasMoreData = false
                        } else {
                            currentPage++
                        }
                        
                        isLoading = false
                        isLoadingMore = false
                        updateUI()
                        
                        // Status counts TIDAK di-update otomatis, hanya ketika filter diklik
                    }
                }
            } catch (e: Exception) {
                if (isAdded) {
                    isLoading = false
                    isLoadingMore = false
                    Toast.makeText(context, "Failed to load work orders: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun refreshData() {
        loadData(reset = true)
    }
    
    // Load more data for infinite scroll - sama persis dengan Flutter
    private fun loadMoreData() {
        loadData(reset = false)
    }
    
    // Search dengan debounce - sama seperti Flutter onSearchChanged
    private fun onSearchChanged(value: String) {
        searchText = value
        
        // Cancel previous timer
        searchDebounce?.cancel()
        
        // Set new timer for debounce
        searchDebounce = Timer()
        searchDebounce?.schedule(object : TimerTask() {
            override fun run() {
                Handler(Looper.getMainLooper()).post {
                    // Just update the UI with filtered results
                    updateUI()
                }
            }
        }, 500) // 500ms debounce
    }
    
    // Get filtered work orders based on search text - optimasi performa (sama seperti Flutter)
    private fun getFilteredWorkOrders(): List<Map<String, Any>> {
        if (searchText.isEmpty()) {
            return workOrders
        }
        
        val searchLower = searchText.lowercase()
        return workOrders.filter { workOrder ->
            workOrder["nour"]?.toString()?.lowercase()?.contains(searchLower) == true ||
            workOrder["job"]?.toString()?.lowercase()?.contains(searchLower) == true ||
            workOrder["lokasi"]?.toString()?.lowercase()?.contains(searchLower) == true ||
            workOrder["woto"]?.toString()?.lowercase()?.contains(searchLower) == true
        }
    }
    
    private fun onStatusChanged(status: String) {
        selectedStatus = status
        searchText = ""
        searchView.setText("")
        
        // Load data dan update status counts - sama persis dengan Flutter
        loadData(reset = true)
        
        // Setelah data loaded, update status counts untuk filter baru
        // Status counts akan di-load di background
    }
    
    private fun updateUI() {
        swipeRefreshLayout.isRefreshing = false
        
        android.util.Log.d("HomeFragment", "updateUI called")
        android.util.Log.d("HomeFragment", "workOrders size: ${workOrders.size}")
        android.util.Log.d("HomeFragment", "searchText: '$searchText'")
        
        val filteredData = getFilteredWorkOrders()
        android.util.Log.d("HomeFragment", "filteredData size: ${filteredData.size}")
        
        if (filteredData.isEmpty()) {
            if (searchText.isNotEmpty()) {
                android.util.Log.d("HomeFragment", "Showing empty state for search")
                showEmptyState("No search results")
            } else {
                android.util.Log.d("HomeFragment", "Showing empty state - no data")
                showEmptyState("No work orders")
            }
        } else {
            android.util.Log.d("HomeFragment", "Hiding empty state, showing data")
            hideEmptyState()
            // Update RecyclerView adapter dengan filteredData
            workOrderAdapter.updateData(filteredData)
        }
    }
    
    private fun showEmptyState(message: String) {
        tvEmpty.text = message
        tvEmpty.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
    }
    
    private fun hideEmptyState() {
        tvEmpty.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_CHANGE_STATUS && resultCode == android.app.Activity.RESULT_OK) {
            // Refresh data after status change
            refreshData()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Cleanup search debounce timer
        searchDebounce?.cancel()
    }
}

