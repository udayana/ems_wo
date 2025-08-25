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
import com.sofindo.ems.R
import com.sofindo.ems.adapters.WorkOrderAdapter
import com.sofindo.ems.api.RetrofitClient
import com.sofindo.ems.services.UserService
import kotlinx.coroutines.launch
import java.util.Timer
import java.util.TimerTask

class OutboxFragment : Fragment() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var searchView: EditText
    private lateinit var btnFilter: ImageButton
    private lateinit var tvEmpty: TextView

    
    // Sama persis dengan HomeFragment
    private var workOrders = mutableListOf<Map<String, Any>>()
    private var isLoading = true
    private var isLoadingMore = false
    private var hasMoreData = true
    private var currentPage = 1
    private var searchText = ""
    private var selectedStatus = ""
    private var currentPropID: String? = null
    private var username: String? = null
    private var userDept: String? = null
    private val statusCounts = mutableMapOf<String, Int>()
    private var isLoadingStatusCounts = true
    
    // Sama persis dengan Flutter statusOptions
    private val statusOptions = listOf("", "new", "received", "on progress", "pending", "done")
    
    // Search debounce timer (sama seperti Flutter)
    private var searchDebounce: Timer? = null
    
    // RecyclerView adapter
    private lateinit var workOrderAdapter: WorkOrderAdapter
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_outbox, container, false)
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
        
        // Initialize adapter with menu callbacks for Outbox (Detail, Edit, Delete)
        workOrderAdapter = WorkOrderAdapter(
            onItemClick = { workOrder ->
                // Handle work order click (this won't be used since we're using card click for menu)
                onWorkOrderClick(workOrder)
            },
            onEditClick = { workOrder ->
                // Handle edit click
                onEditWorkOrder(workOrder)
            },
            onDeleteClick = { workOrder ->
                // Handle delete click
                onDeleteWorkOrder(workOrder)
            },
            onDetailClick = { workOrder ->
                // Handle detail click
                onDetailWorkOrder(workOrder)
            },
            onFollowUpClick = { workOrder ->
                // Handle follow up click (not used in Outbox)
                onFollowUpWorkOrder(workOrder)
            },
            showSender = false,
            replaceWotoWithOrderBy = false,
            isHomeFragment = false
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
                loadStatusCountsInBackground()
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
        Toast.makeText(context, "Clicked Outbox WO: $woNumber", Toast.LENGTH_SHORT).show()
    }
    
    private fun onEditWorkOrder(workOrder: Map<String, Any>) {
        val woNumber = workOrder["nour"]?.toString() ?: ""
        Toast.makeText(context, "Edit Outbox WO: $woNumber", Toast.LENGTH_SHORT).show()
        // TODO: Navigate to edit work order screen
    }
    
    private fun onDeleteWorkOrder(workOrder: Map<String, Any>) {
        val woNumber = workOrder["nour"]?.toString() ?: ""
        
        // Show confirmation dialog
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete Work Order")
            .setMessage("Are you sure you want to delete WO: $woNumber?")
            .setPositiveButton("Delete") { _, _ ->
                // TODO: Implement delete API call
                Toast.makeText(context, "Deleted WO: $woNumber", Toast.LENGTH_SHORT).show()
                // Refresh data after deletion
                refreshData()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun onDetailWorkOrder(workOrder: Map<String, Any>) {
        // Navigate to work order detail activity
        val intent = android.content.Intent(context, com.sofindo.ems.activities.WorkOrderDetailActivity::class.java)
        intent.putExtra("workOrder", workOrder as java.io.Serializable)
        startActivity(intent)
    }
    
    private fun onFollowUpWorkOrder(workOrder: Map<String, Any>) {
        // This function is not used in Outbox but required for adapter compatibility
        val woNumber = workOrder["nour"]?.toString() ?: ""
        Toast.makeText(context, "Follow Up WO: $woNumber (Outbox)", Toast.LENGTH_SHORT).show()
    }
    

    
    // Sama persis dengan HomeFragment tapi untuk outbox
    private fun initializeData() {
        lifecycleScope.launch {
            try {
                // Get user data sekaligus
                val propID = UserService.getCurrentPropID()
                val user = UserService.getCurrentUser()
                username = user?.username ?: user?.email
                userDept = user?.dept
                
                android.util.Log.d("OutboxFragment", "Debug: propID = $propID")
                android.util.Log.d("OutboxFragment", "Debug: username = $username")
                android.util.Log.d("OutboxFragment", "Debug: userDept = $userDept")
                
                currentPropID = propID ?: ""
                

                
                if (!currentPropID.isNullOrEmpty() && !username.isNullOrEmpty()) {
                    android.util.Log.d("OutboxFragment", "Debug: Loading outbox work orders...")
                    // Load outbox work orders
                    loadOutboxWorkOrdersParallel()
                } else {
                    android.util.Log.e("OutboxFragment", "Debug: Missing required data!")
                    showEmptyState("Missing user data. Please login again.")
                }
            } catch (e: Exception) {
                android.util.Log.e("OutboxFragment", "Error initializing data: ${e.message}")
                showEmptyState("Error loading data: ${e.message}")
            } finally {
                isLoading = false
                isLoadingStatusCounts = false
                updateUI()
            }
        }
    }
    
    // Load outbox work orders - menggunakan API outbox
    private fun loadOutboxWorkOrdersParallel() {
        lifecycleScope.launch {
            try {
                // Debug: Log untuk troubleshooting
                android.util.Log.d("OutboxFragment", "Loading outbox work orders...")
                android.util.Log.d("OutboxFragment", "propID: $currentPropID")
                android.util.Log.d("OutboxFragment", "username: $username")
                android.util.Log.d("OutboxFragment", "userDept: $userDept")
                android.util.Log.d("OutboxFragment", "status: $selectedStatus")
                android.util.Log.d("OutboxFragment", "searchText: $searchText")
                
                // DEBUG: Log data penting untuk outbox
                android.util.Log.d("OutboxFragment", "=== DEBUG OUTBOX DATA ===")
                android.util.Log.d("OutboxFragment", "userDept: ${userDept ?: ""}")
                android.util.Log.d("OutboxFragment", "orderBy: $username")
                android.util.Log.d("OutboxFragment", "=== END DEBUG ===")
                
                // Fetch dari API outbox
                val workOrdersResult = RetrofitClient.apiService.getWorkOrdersOut(
                    propID = currentPropID!!,
                    orderBy = username!!,
                    status = selectedStatus,
                    page = 1,
                    userDept = userDept ?: ""
                )
                
                android.util.Log.d("OutboxFragment", "API Response received: ${workOrdersResult.size} items")
                android.util.Log.d("OutboxFragment", "API Response: $workOrdersResult")
                
                if (isAdded) {
                    workOrders.clear()
                    workOrders.addAll(workOrdersResult)
                    
                    // Sort work orders by WO number (nour) in descending order (highest first)
                    sortWorkOrdersByWONumber(workOrders)
                    
                    hasMoreData = workOrdersResult.size >= 10
                    currentPage = 2 // Set untuk next page
                    
                    android.util.Log.d("OutboxFragment", "workOrders updated: ${workOrders.size} items")
                    android.util.Log.d("OutboxFragment", "Work orders sorted by WO number (highest first)")
                    
                    updateUI()
                    
                    // Load counter filter setelah work orders berhasil di-load
                    loadStatusCountsInBackground()
                }
            } catch (e: Exception) {
                android.util.Log.e("OutboxFragment", "Error loading outbox work orders", e)
                // Error loading work orders
                if (isAdded) {
                    showEmptyState("Failed to load outbox work orders: ${e.message}")
                }
            }
        }
    }
    
    // Load status counts untuk outbox
    private fun loadStatusCountsInBackground() {
        isLoadingStatusCounts = true
        
        lifecycleScope.launch {
            try {
                // Set default values karena getAllStatusesOutbox tidak ada
                statusCounts.clear()
                statusCounts[""] = 0
                statusCounts["new"] = 0
                for (status in statusOptions) {
                    if (status.isNotEmpty()) {
                        statusCounts[status] = 0
                    }
                }
            } catch (e: Exception) {
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
    
    // Optimasi: Update status counts dari API response
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
                
                if (currentPropID.isNullOrEmpty() || username.isNullOrEmpty()) {
                    isLoading = false
                    isLoadingMore = false
                    return@launch
                }
                
                // Load dari API outbox
                val workOrdersResult = RetrofitClient.apiService.getWorkOrdersOut(
                    propID = currentPropID!!,
                    orderBy = username!!,
                    status = selectedStatus,
                    page = currentPage,
                    userDept = userDept ?: ""
                )
                
                if (isAdded) {
                    if (reset) {
                        workOrders.clear()
                        workOrders.addAll(workOrdersResult)
                    } else {
                        workOrders.addAll(workOrdersResult)
                    }
                    
                    // Sort work orders by WO number (nour) in descending order (highest first)
                    sortWorkOrdersByWONumber(workOrders)
                    
                    if (workOrdersResult.size < 10) {
                        hasMoreData = false
                    } else {
                        currentPage++
                    }
                    
                    isLoading = false
                    isLoadingMore = false
                    updateUI()
                }
            } catch (e: Exception) {
                if (isAdded) {
                    isLoading = false
                    isLoadingMore = false
                    Toast.makeText(context, "Failed to load outbox work orders: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun refreshData() {
        loadData(reset = true)
    }
    
    // Load more data for infinite scroll
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
    
    // Sort work orders by WO number (nour) in descending order (highest first)
    private fun sortWorkOrdersByWONumber(workOrdersList: MutableList<Map<String, Any>>) {
        workOrdersList.sortByDescending { workOrder ->
            val nour = workOrder["nour"]?.toString() ?: ""
            try {
                nour.toIntOrNull() ?: 0
            } catch (e: NumberFormatException) {
                0
            }
        }
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
        
        // Load data dan update status counts
        loadData(reset = true)
    }
    
    private fun updateUI() {
        swipeRefreshLayout.isRefreshing = false
        
        android.util.Log.d("OutboxFragment", "updateUI called")
        android.util.Log.d("OutboxFragment", "workOrders size: ${workOrders.size}")
        android.util.Log.d("OutboxFragment", "searchText: '$searchText'")
        
        val filteredData = getFilteredWorkOrders()
        android.util.Log.d("OutboxFragment", "filteredData size: ${filteredData.size}")
        
        if (filteredData.isEmpty()) {
            if (searchText.isNotEmpty()) {
                android.util.Log.d("OutboxFragment", "Showing empty state for search")
                showEmptyState("No search results")
            } else {
                android.util.Log.d("OutboxFragment", "Showing empty state - no data")
                showEmptyState("No outbox work orders")
            }
        } else {
            android.util.Log.d("OutboxFragment", "Hiding empty state, showing data")
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
    
    override fun onDestroy() {
        super.onDestroy()
        // Cleanup search debounce timer
        searchDebounce?.cancel()
    }
}
