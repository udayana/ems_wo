package com.sofindo.ems.fragments

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.content.SharedPreferences
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.sofindo.ems.MainActivity
import com.sofindo.ems.R
import com.sofindo.ems.adapters.WorkOrderAdapter
import com.sofindo.ems.api.RetrofitClient
import com.sofindo.ems.dialogs.AssignStaffDialog
import com.sofindo.ems.models.Staff
import com.sofindo.ems.services.UserService
import com.sofindo.ems.utils.NetworkUtils
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.net.ConnectException
import java.net.UnknownHostException
import java.util.Timer
import java.util.TimerTask

class HomeFragment : Fragment() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var searchView: EditText
    private lateinit var btnFilter: ImageButton
    private lateinit var tvEmpty: TextView
    private lateinit var btnTabIn: Button
    private lateinit var btnTabOut: Button
    private var indicatorActiveTab: View? = null
    private lateinit var contentContainer: FrameLayout
    
    // Tab state - mirip iOS @AppStorage("lastSelectedHomeTab")
    private lateinit var sharedPreferences: SharedPreferences
    private var lastSelectedTab: String = "in"
    
    // Child fragments
    private var outboxContentFragment: Fragment? = null
    
    // Exactly like Flutter _BacaWOViewState
    private var workOrders = mutableListOf<Map<String, Any>>()
    private var isLoading = false
    private var isLoadingMore = false
    private var hasMoreData = true
    private var currentPage = 1
    private var searchText = ""
    private var selectedStatus = ""
    private var currentPropID: String? = null
    private val statusCounts = mutableMapOf<String, Int>()
    private var isLoadingStatusCounts = true
    
    // Exactly like Flutter statusOptions
    private val statusOptions = listOf("", "new", "received", "on progress", "pending", "done")
    
    // Search debounce timer (same as Flutter)
    private var searchDebounce: Timer? = null
    
    // RecyclerView adapter
    private lateinit var workOrderAdapter: WorkOrderAdapter
    private var userJabatan: String? = null
    
    // Activity result launcher for change status
    private val changeStatusLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            // Refresh data after status change
            refreshData()
        }
    }

    private val highlightHandler = Handler(Looper.getMainLooper())
    private var clearHighlightRunnable: Runnable? = null
    private var pendingFocusWoId: String? = null
    
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
        btnTabIn = view.findViewById(R.id.btn_tab_in)
        btnTabOut = view.findViewById(R.id.btn_tab_out)
        // indicatorActiveTab is optional - not present in new layout, will remain null
        contentContainer = view.findViewById(R.id.content_container)
        
        recyclerView = view.findViewById(R.id.recycler_view)
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh)
        searchView = view.findViewById(R.id.search_view)
        btnFilter = view.findViewById(R.id.btn_filter)
        tvEmpty = view.findViewById(R.id.tv_empty)
        
        // Get shared preferences untuk menyimpan tab selection (mirip iOS @AppStorage)
        sharedPreferences = requireContext().getSharedPreferences("ems_user_prefs", android.content.Context.MODE_PRIVATE)
        lastSelectedTab = sharedPreferences.getString("lastSelectedHomeTab", "in") ?: "in"
        
        // Log saved tab state for debugging
        android.util.Log.d("HomeFragment", "Restored last selected tab: $lastSelectedTab")
        
        // Note: AppBarLayout with fitsSystemWindows="true" handles window insets automatically
        // No need for manual padding adjustment
        
        // Setup RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(context)
        
        // Ensure RecyclerView starts from top when first loaded
        recyclerView.post {
            if (recyclerView.layoutManager != null) {
                (recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(0, 0)
            }
        }
        
        // Setup tab indicator position based on saved state
        updateTabIndicator()
        
        // Load initial tab content based on saved state
        switchTabContent()
        
        // Get user jabatan and initialize adapter
        lifecycleScope.launch {
            val user = UserService.getCurrentUser()
            userJabatan = user?.jabatan
            val currentUserName = user?.fullName ?: user?.username ?: user?.email
            
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
                onForwardClick = { workOrder ->
                    // Handle forward click
                    onForwardWorkOrder(workOrder)
                },
                onNeedReviewClick = { workOrder ->
                    // Not used in HomeFragment
                },
                onReviewClick = { workOrder ->
                    // Not used in HomeFragment
                },
                showSender = false, 
                replaceWotoWithOrderBy = true,
                isHomeFragment = true,
                userJabatan = userJabatan,
                currentUserName = currentUserName
            )
            
            recyclerView.adapter = workOrderAdapter

            pendingFocusWoId?.let {
                android.util.Log.d("HomeFragment", "Adapter initialized, retry focus for WO: $it")
                tryScrollToPendingWorkOrder()
            }
        }
        
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
        // Tab switcher listeners - mirip iOS
        btnTabIn.setOnClickListener {
            if (lastSelectedTab != "in") {
                lastSelectedTab = "in"
                sharedPreferences.edit().putString("lastSelectedHomeTab", "in").apply()
                android.util.Log.d("HomeFragment", "Tab switched to: in (saved to SharedPreferences)")
                updateTabIndicator()
                switchTabContent()
            }
        }
        
        btnTabOut.setOnClickListener {
            if (lastSelectedTab != "out") {
                lastSelectedTab = "out"
                sharedPreferences.edit().putString("lastSelectedHomeTab", "out").apply()
                android.util.Log.d("HomeFragment", "Tab switched to: out (saved to SharedPreferences)")
                updateTabIndicator()
                switchTabContent()
            }
        }
        
        // Swipe refresh listener
        swipeRefreshLayout.setOnRefreshListener {
            refreshData()
        }
        
        // Filter button listener
        btnFilter.setOnClickListener {
            android.util.Log.d("HomeFragment", "Filter button clicked")
            try {
                showFilterPopup()
            } catch (e: Exception) {
                android.util.Log.e("HomeFragment", "Error showing filter popup: ${e.message}", e)
                Toast.makeText(context, "Error showing filter: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Search view listener
        searchView.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                onSearchChanged(s?.toString() ?: "")
            }
        })
    }
    
    // Public function to switch to outbox tab (called from MainActivity after creating work order)
    fun switchToOutboxTab() {
        // Check if view is created first
        if (view == null) {
            android.util.Log.w("HomeFragment", "switchToOutboxTab called before view creation, will retry after view is ready")
            viewLifecycleOwner.lifecycleScope.launch {
                delay(100)
                if (view != null && ::sharedPreferences.isInitialized) {
                    switchToOutboxTab()
                }
            }
            return
        }
        
        // Check if sharedPreferences is initialized (view must be created first)
        if (!::sharedPreferences.isInitialized) {
            try {
                sharedPreferences = requireContext().getSharedPreferences("ems_user_prefs", android.content.Context.MODE_PRIVATE)
            } catch (e: Exception) {
                android.util.Log.e("HomeFragment", "Error initializing sharedPreferences: ${e.message}", e)
                // Retry after view is ready
                view?.postDelayed({
                    if (::sharedPreferences.isInitialized) {
                        switchToOutboxTab()
                    }
                }, 100)
                return
            }
        }
        
        if (lastSelectedTab != "out") {
            lastSelectedTab = "out"
            try {
                sharedPreferences.edit().putString("lastSelectedHomeTab", "out").apply()
                android.util.Log.d("HomeFragment", "Tab switched to: out programmatically (saved to SharedPreferences)")
                updateTabIndicator()
                switchTabContent()
                // Refresh outbox data
                outboxContentFragment?.let { fragment ->
                    if (fragment is OutboxFragment) {
                        fragment.refreshData()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeFragment", "Error in switchToOutboxTab: ${e.message}", e)
            }
        } else {
            // Already on outbox tab, just refresh data
            try {
                outboxContentFragment?.let { fragment ->
                    if (fragment is OutboxFragment) {
                        fragment.refreshData()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeFragment", "Error refreshing outbox data: ${e.message}", e)
            }
        }
    }
    
    private fun updateTabIndicator() {
        // Update tab button styles - mirip iOS
        if (lastSelectedTab == "in") {
            btnTabIn.textSize = 14f
            btnTabIn.typeface = android.graphics.Typeface.DEFAULT_BOLD
            btnTabIn.setTextColor(resources.getColor(R.color.primary_color, null))
            
            btnTabOut.textSize = 14f
            btnTabOut.typeface = android.graphics.Typeface.DEFAULT
            btnTabOut.setTextColor(resources.getColor(android.R.color.darker_gray, null))
        } else {
            btnTabOut.textSize = 14f
            btnTabOut.typeface = android.graphics.Typeface.DEFAULT_BOLD
            btnTabOut.setTextColor(resources.getColor(R.color.primary_color, null))
            
            btnTabIn.textSize = 14f
            btnTabIn.typeface = android.graphics.Typeface.DEFAULT
            btnTabIn.setTextColor(resources.getColor(android.R.color.darker_gray, null))
        }
        
        // Update indicator position if it exists (optional - not in new layout)
        indicatorActiveTab?.let { indicator ->
            val parentLayout = indicator.parent as? android.view.ViewGroup
            if (parentLayout != null) {
                val params = indicator.layoutParams as? android.widget.LinearLayout.LayoutParams
                    ?: android.widget.LinearLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                val parentWidth = parentLayout.width
                if (parentWidth > 0) {
                    if (lastSelectedTab == "in") {
                        params.width = (parentWidth / 2) - 32
                        params.weight = 0f
                        params.setMargins(16, 0, 0, 0)
                    } else {
                        params.width = (parentWidth / 2) - 32
                        params.weight = 0f
                        params.setMargins(parentWidth / 2 + 16, 0, 16, 0)
                    }
                    indicator.layoutParams = params
                } else {
                    params.width = 0
                    params.weight = 0.5f
                    params.setMargins(if (lastSelectedTab == "in") 16 else android.view.ViewGroup.LayoutParams.MATCH_PARENT, 0, 16, 0)
                    indicator.layoutParams = params
                }
            }
        }
    }
    
    private fun switchTabContent() {
        // Switch antara Home content dan Outbox content - mirip iOS
        try {
            if (lastSelectedTab == "in") {
                // Show Home content (current implementation)
                swipeRefreshLayout.visibility = View.VISIBLE
                searchView.visibility = View.VISIBLE
                btnFilter.visibility = View.VISIBLE
                
                // Ensure listeners are set up for filter and search
                // (They should already be set in setupListeners, but ensure they're active)
                
                // Hide Outbox content if exists
                outboxContentFragment?.let { fragment ->
                    if (fragment.isAdded) {
                        childFragmentManager.beginTransaction()
                            .hide(fragment)
                            .commit()
                    }
                }
                
                // Update UI when switching to "in" tab
                if (::workOrderAdapter.isInitialized) {
                    updateUI()
                }
            } else {
                // Show Outbox content - load OutboxFragment as child fragment
                swipeRefreshLayout.visibility = View.GONE
                searchView.visibility = View.GONE
                btnFilter.visibility = View.GONE
                
                if (outboxContentFragment == null || !outboxContentFragment!!.isAdded) {
                    outboxContentFragment = OutboxFragment()
                    childFragmentManager.beginTransaction()
                        .add(R.id.content_container, outboxContentFragment!!, "outbox")
                        .commit()
                } else {
                    childFragmentManager.beginTransaction()
                        .show(outboxContentFragment!!)
                        .commit()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("HomeFragment", "Error switching tab content", e)
        }
    }
    
    fun focusOnWorkOrder(woId: String) {
        pendingFocusWoId = woId
        android.util.Log.d("HomeFragment", "focusOnWorkOrder called with $woId")
        tryScrollToPendingWorkOrder()
    }
    
    private fun tryScrollToPendingWorkOrder() {
        val targetWoId = pendingFocusWoId ?: return
        if (!isAdded) {
            android.util.Log.d("HomeFragment", "Fragment not attached, waiting for WO: $targetWoId")
            return
        }
        if (!::recyclerView.isInitialized || !::workOrderAdapter.isInitialized) {
            android.util.Log.d("HomeFragment", "Views not initialized, waiting for WO: $targetWoId")
            return
        }
        val filteredData = getFilteredWorkOrders()
        val targetIndex = filteredData.indexOfFirst { workOrder ->
            val woIdValue = workOrder["woId"]?.toString()?.takeIf { it.isNotEmpty() }
            val nourValue = workOrder["nour"]?.toString()?.takeIf { it.isNotEmpty() }
            targetWoId.equals(woIdValue, ignoreCase = true) || targetWoId.equals(nourValue, ignoreCase = true)
        }
        if (targetIndex < 0) {
            android.util.Log.d("HomeFragment", "Work order $targetWoId not in current dataset yet")
            return
        }
        pendingFocusWoId = null
        val targetWorkOrder = filteredData[targetIndex]
        recyclerView.post {
            recyclerView.smoothScrollToPosition(targetIndex)
            workOrderAdapter.setHighlightForWorkOrder(targetWorkOrder)
            clearHighlightRunnable?.let { highlightHandler.removeCallbacks(it) }
            clearHighlightRunnable = Runnable {
                workOrderAdapter.clearHighlight()
            }
            highlightHandler.postDelayed(clearHighlightRunnable!!, 4000)
        }
    }
    
    private fun showFilterPopup() {
        if (!isAdded || context == null) {
            android.util.Log.w("HomeFragment", "Cannot show filter popup: fragment not added or context is null")
            return
        }
        
        android.util.Log.d("HomeFragment", "showFilterPopup called - workOrders count: ${workOrders.size}, statusCounts: $statusCounts")
        
        // Ensure statusCounts is initialized even if empty
        if (statusCounts.isEmpty()) {
            // Initialize with zeros for all status options
            statusCounts[""] = 0
            statusCounts["new"] = 0
            for (s in statusOptions) {
                if (s.isNotEmpty() && s != "new") {
                    statusCounts[s] = 0
                }
            }
            android.util.Log.d("HomeFragment", "Initialized statusCounts with zeros: $statusCounts")
        }
        
        // Quick calculation from existing data for immediate display
        recomputeStatusCountsFromWorkOrders()
        
        // Create and show popup with quick count
        try {
            val popup = PopupMenu(requireContext(), btnFilter)
            updateFilterMenuItems(popup)
            android.util.Log.d("HomeFragment", "Showing filter popup with ${statusOptions.size} menu items")
            popup.show()
        } catch (e: Exception) {
            android.util.Log.e("HomeFragment", "Error creating/showing popup menu: ${e.message}", e)
            Toast.makeText(context, "Error showing filter menu: ${e.message}", Toast.LENGTH_SHORT).show()
            return
        }

        // Then calculate accurately based on all pages asynchronously
        // Note: PopupMenu cannot be updated after show, so we'll just update statusCounts
        // for next time the popup is opened
        lifecycleScope.launch {
            if (currentPropID != null && currentPropID!!.isNotEmpty()) {
                val userDept = UserService.getCurrentDept()
                val finalDept = userDept ?: "Engineering"
                val precise = fetchInboxStatusCountsAllPages(finalDept)
                if (!isAdded) return@launch
                if (precise.isNotEmpty()) {
                    statusCounts.clear()
                    statusCounts.putAll(precise)
                    android.util.Log.d("HomeFragment", "Status counts updated from API: $statusCounts")
                    // Status counts updated for next time popup is opened
                }
            }
        }
    }
    
    private fun updateFilterMenuItems(popup: PopupMenu) {
        popup.menu.clear()
        statusOptions.forEach { status ->
            val count = if (status.isEmpty()) {
                statusCounts[""] ?: 0
            } else if (status.lowercase() == "new") {
                statusCounts["new"] ?: 0
            } else {
                statusCounts[status.lowercase()] ?: statusCounts[status] ?: 0
            }
            val displayName = when {
                status.isEmpty() -> "All"
                status.lowercase() == "new" -> "NEW"
                else -> status.uppercase()
            }
            val menuItem = popup.menu.add("$count → $displayName")
            menuItem.setOnMenuItemClickListener {
                android.util.Log.d("HomeFragment", "Filter selected: $status ($displayName)")
                onStatusChanged(status)
                true
            }
        }
        android.util.Log.d("HomeFragment", "Filter menu items updated with statusCounts: $statusCounts")
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
        changeStatusLauncher.launch(intent)
    }
    
    private fun onForwardWorkOrder(workOrder: Map<String, Any>) {
        // Load staff list and show dialog
        lifecycleScope.launch {
            try {
                val user = UserService.getCurrentUser()
                val propID = currentPropID ?: user?.propID
                val dept = UserService.getCurrentDept() ?: user?.dept
                
                android.util.Log.d("HomeFragment", "onForwardWorkOrder - propID: $propID, dept: $dept")
                
                if (propID.isNullOrEmpty() || dept.isNullOrEmpty()) {
                    android.util.Log.e("HomeFragment", "propID or dept is empty")
                    Toast.makeText(context, "Unable to get property or department information", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                // Show loading dialog
                val loadingDialog = android.app.ProgressDialog(context).apply {
                    setMessage("Loading staff list...")
                    setCancelable(false)
                    show()
                }
                
                try {
                    // Load staff list
                    val apiService = RetrofitClient.apiService
                    android.util.Log.d("HomeFragment", "Calling getStaffList API...")
                    val staffList = apiService.getStaffList(propID = propID, dept = dept)
                    android.util.Log.d("HomeFragment", "Staff list loaded: ${staffList.size} staff")
                    
                    loadingDialog.dismiss()
                    
                    // Show dialog
                    val dialog = AssignStaffDialog(
                        context = requireContext(),
                        staffList = staffList,
                        onStaffSelected = { staff ->
                            assignWorkOrderToStaff(workOrder, staff)
                        }
                    )
                    dialog.show()
                    
                } catch (e: retrofit2.HttpException) {
                    loadingDialog.dismiss()
                    android.util.Log.e("HomeFragment", "HTTP error loading staff: ${e.code()} - ${e.message()}")
                    val errorBody = e.response()?.errorBody()?.string()
                    android.util.Log.e("HomeFragment", "Error body: $errorBody")
                    Toast.makeText(context, "Error loading staff: ${e.message()}", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    loadingDialog.dismiss()
                    android.util.Log.e("HomeFragment", "Error loading staff list", e)
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
                
            } catch (e: Exception) {
                android.util.Log.e("HomeFragment", "Unexpected error in onForwardWorkOrder", e)
                Toast.makeText(context, "Error loading staff list: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun assignWorkOrderToStaff(workOrder: Map<String, Any>, staff: Staff) {
        lifecycleScope.launch {
            try {
                val propID = currentPropID
                val woId = workOrder["woId"]?.toString()?.takeIf { it.isNotEmpty() }
                val nour = workOrder["nour"]?.toString()
                
                if (propID.isNullOrEmpty()) {
                    Toast.makeText(context, "Unable to get property information", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                android.util.Log.d("HomeFragment", "Assigning WO - woId: $woId, nour: $nour, propID: $propID, namaAssign: ${staff.nama}")
                
                val apiService = RetrofitClient.apiService
                
                // Use woId if available, otherwise use nour + propID (exactly like Swift)
                val result = if (!woId.isNullOrEmpty()) {
                    // UPDATE tblwo SET assignto=$namaAssign WHERE woId=$woId
                    apiService.assignWorkOrder(
                        namaAssign = staff.nama,
                        woId = woId,
                        nour = null,
                        propID = null
                    )
                } else {
                    // UPDATE tblwo SET assignto=$namaAssign WHERE nour=$nour AND propID=$propID
                    apiService.assignWorkOrder(
                        namaAssign = staff.nama,
                        woId = null,
                        nour = nour?.takeIf { it.isNotEmpty() },
                        propID = propID
                    )
                }
                
                val success = result["success"] as? Boolean ?: false
                if (success) {
                    Toast.makeText(context, "Work order assigned to ${staff.nama}", Toast.LENGTH_SHORT).show()
                    // Refresh data
                    refreshData()
                } else {
                    val message = result["message"]?.toString() ?: "Failed to assign work order"
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
                
            } catch (e: Exception) {
                Toast.makeText(context, "Error assigning work order: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    // Exactly like Flutter _initializeData()
    private fun initializeData() {
        // Set loading state and show loading UI
        isLoading = true
        showLoadingState()
        
        // Try to get data synchronously first for faster initial load
        val propIDSync = UserService.getCurrentPropIDSync()
        val userDeptSync = UserService.getCurrentDeptSync()
        
        if (!propIDSync.isNullOrEmpty()) {
            currentPropID = propIDSync
            val finalDept = userDeptSync ?: "Engineering"
            
            // Start loading data immediately with sync data
            loadWorkOrdersParallel(finalDept)
        } else {
            // Fallback to async if sync data not available
            lifecycleScope.launch {
                try {
                    // Get propID and department at once (same as Flutter)
                    val propID = UserService.getCurrentPropID()
                    val userDept = UserService.getCurrentDept()
                    val finalDept = userDept ?: "Engineering"
                    
                    // User data loaded
                    
                    currentPropID = propID ?: ""
                    
                    if (!currentPropID.isNullOrEmpty()) {
                        // Only load work orders, counter filter is NOT loaded automatically
                        // Counter filter will be loaded when user clicks filter icon
                        loadWorkOrdersParallel(finalDept)
                    } else {
                        showEmptyState("No property ID found. Please login again.")
                        isLoading = false
                        isLoadingStatusCounts = false
                    }
                } catch (e: Exception) {
                    showEmptyState("Error loading data: ${e.message}")
                    isLoading = false
                    isLoadingStatusCounts = false
                }
            }
        }
    }
    
            // Load work orders only (without automatic status counts) - same as Flutter
    private fun loadWorkOrdersParallel(department: String) {
        lifecycleScope.launch {
            try {
                // Load work orders
                
                // Fetch directly from API without cache with timeout
                val workOrdersResult = withTimeout(15000) { // 15 seconds timeout
                    RetrofitClient.apiService.getWorkOrders(
                        propID = currentPropID!!,
                        woto = department,
                        status = selectedStatus,
                        page = 1
                    )
                }
                
                // API response received
                
                if (isAdded) {
                    workOrders.clear()
                    workOrders.addAll(workOrdersResult)
                    hasMoreData = workOrdersResult.size >= 10
                    currentPage = 2 // Set for next page
                    
                    // Work orders updated
                    
                    // Load counter filter after work orders are successfully loaded
                    // This ensures counter is available when filter is first clicked
                    loadStatusCountsInBackground(department)
                    
                    // Set loading to false and update UI after data is loaded
                    isLoading = false
                    updateUI()
                    // Recalculate status counter from loaded data
                    recomputeStatusCountsFromWorkOrders()
                    
                    // Scroll to top after data is loaded
                    recyclerView.post {
                        recyclerView.scrollToPosition(0)
                    }
                }
            } catch (e: Exception) {
                // Error loading work orders
                if (isAdded) {
                    isLoading = false
                    handleLoadError(e)
                }
            }
        }
    }
    
            // Load status counts only when filter is clicked - same as Flutter
    private fun recomputeStatusCountsFromWorkOrders() {
        if (!isAdded) return
        isLoadingStatusCounts = true
        val localCounts = mutableMapOf<String, Int>()
        // Initialize all status options with 0
        localCounts[""] = 0
        localCounts["new"] = 0
        for (s in statusOptions) {
            if (s.isNotEmpty() && s != "new") {
                localCounts[s] = 0
            }
        }
        
        var total = 0
        for (wo in workOrders) {
            val raw = wo["status"]?.toString()?.lowercase()?.trim() ?: ""
            val key = if (raw.isEmpty()) "new" else raw
            localCounts[key] = (localCounts[key] ?: 0) + 1
            total++
        }
        localCounts[""] = total
        
        // Ensure all keys exist
        for (s in statusOptions) {
            if (s.isEmpty()) {
                localCounts[""] = total
            } else if (s.lowercase() == "new") {
                if (!localCounts.containsKey("new")) {
                    localCounts["new"] = 0
                }
            } else {
                if (!localCounts.containsKey(s)) {
                    localCounts[s] = 0
                }
            }
        }
        
        statusCounts.clear()
        statusCounts.putAll(localCounts)
        isLoadingStatusCounts = false
        
        android.util.Log.d("HomeFragment", "Status counts recomputed: $statusCounts")
    }
    
    // Get all Inbox pages to calculate accurate counter per status
    private suspend fun fetchInboxStatusCountsAllPages(department: String): Map<String, Int> {
        return try {
            val prop = currentPropID ?: return emptyMap()
            val localCounts = mutableMapOf<String, Int>()
            localCounts[""] = 0
            for (s in statusOptions) {
                if (!localCounts.containsKey(s)) {
                    localCounts[s] = 0
                }
            }

            var pageNum = 1
            var total = 0
            while (true) {
                val pageData = RetrofitClient.apiService.getWorkOrders(
                    propID = prop,
                    woto = department,
                    status = "",
                    page = pageNum
                )
                if (pageData.isEmpty()) break
                for (wo in pageData) {
                    val raw = wo["status"]?.toString()?.lowercase() ?: ""
                    val key = if (raw.isEmpty()) "new" else raw
                    localCounts[key] = (localCounts[key] ?: 0) + 1
                    total++
                }
                // If server pagination is 10/item, continue until less than 10
                if (pageData.size < 10) break
                pageNum++
            }
            localCounts[""] = total
            for (s in statusOptions) {
                if (!localCounts.containsKey(s)) {
                    localCounts[s] = 0
                }
            }
            localCounts
        } catch (e: Exception) {
            android.util.Log.e("HomeFragment", "Error fetching inbox status counts: ${e.message}", e)
            emptyMap()
        }
    }
    
    private fun loadStatusCountsInBackground(department: String) {
        isLoadingStatusCounts = true
        
        lifecycleScope.launch {
            try {
                // Load from API get_all_statuses
                val statusData = RetrofitClient.apiService.getAllStatuses(
                    propID = currentPropID!!,
                    woto = department
                )
                
                // Update status counts from API
                updateStatusCountsFromAPI(statusData)
            } catch (e: Exception) {
                // API get_all_statuses failed
                // Set default values if API fails
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
    
            // Optimization: Update status counts from API response - same as Flutter
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
        
        statusCounts[""] = totalCount // Total for "All"
        
        // Ensure all required statuses exist
        for (status in statusOptions) {
            if (status.isNotEmpty() && !statusCounts.containsKey(status)) {
                statusCounts[status] = 0
            }
        }
        
        // Ensure 'new' exists
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
            // Show loading state for reset operations
            showLoadingState()
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
                        showEmptyState("No property ID found. Please login again.")
                        return@launch
                    }
                    
                    val userDept = UserService.getCurrentDept()
                    val finalDept = userDept ?: "Engineering"
                    
                    val workOrdersResult = withTimeout(15000) { // 15 seconds timeout
                        RetrofitClient.apiService.getWorkOrders(
                            propID = currentPropID!!,
                            woto = finalDept,
                            status = selectedStatus,
                            page = currentPage
                        )
                    }
                    
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
                        // Recalculate status counter from loaded data
                        recomputeStatusCountsFromWorkOrders()
                        
                        // Scroll to top when reset (first load)
                        if (reset) {
                            recyclerView.post {
                                recyclerView.scrollToPosition(0)
                            }
                        }
                        
                        // Status counts are NOT updated automatically, only when filter is clicked
                    }
                }
            } catch (e: Exception) {
                if (isAdded) {
                    isLoading = false
                    isLoadingMore = false
                    handleLoadError(e)
                }
            }
        }
    }
    
    private fun refreshData() {
        // Scroll to top before refreshing
        recyclerView.scrollToPosition(0)
        loadData(reset = true)
    }
    
            // Load more data for infinite scroll - exactly like Flutter
    private fun loadMoreData() {
        loadData(reset = false)
    }
    
    // Search with debounce - same as Flutter onSearchChanged
    private fun onSearchChanged(value: String) {
        val newSearchText = value.trim()
        if (searchText == newSearchText) {
            return // No change, skip
        }
        
        searchText = newSearchText
        android.util.Log.d("HomeFragment", "Search text changed: '$searchText'")
        
        // Cancel previous timer
        searchDebounce?.cancel()
        
        // Set new timer for debounce
        searchDebounce = Timer()
        searchDebounce?.schedule(object : TimerTask() {
            override fun run() {
                Handler(Looper.getMainLooper()).post {
                    // Just update the UI with filtered results
                    if (isAdded && ::workOrderAdapter.isInitialized) {
                        updateUI()
                    }
                }
            }
        }, 500) // 500ms debounce
    }
    
    // Get filtered work orders based on search text and status - performance optimization (same as Flutter)
    private fun getFilteredWorkOrders(): List<Map<String, Any>> {
        var filtered: List<Map<String, Any>> = workOrders
        
        // Filter by status first (if selected)
        if (selectedStatus.isNotEmpty()) {
            filtered = filtered.filter { workOrder ->
                val woStatus = workOrder["status"]?.toString()?.lowercase()?.trim() ?: ""
                val statusKey = if (woStatus.isEmpty()) "new" else woStatus
                val selectedKey = if (selectedStatus.lowercase() == "new") "new" else selectedStatus.lowercase()
                statusKey == selectedKey
            }
        }
        
        // Then filter by search text
        if (searchText.isNotEmpty()) {
            val searchLower = searchText.lowercase()
            filtered = filtered.filter { workOrder ->
                workOrder["nour"]?.toString()?.lowercase()?.contains(searchLower) == true ||
                workOrder["job"]?.toString()?.lowercase()?.contains(searchLower) == true ||
                workOrder["lokasi"]?.toString()?.lowercase()?.contains(searchLower) == true ||
                workOrder["woto"]?.toString()?.lowercase()?.contains(searchLower) == true ||
                workOrder["orderBy"]?.toString()?.lowercase()?.contains(searchLower) == true
            }
        }
        
        return filtered
    }
    
    private fun onStatusChanged(status: String) {
        selectedStatus = status
        searchText = ""
        searchView.setText("")
        
        // Load data and update status counts - exactly like Flutter
        loadData(reset = true)
        
        // After data is loaded, update status counts for new filter
        // Status counts will be loaded in background
    }
    
    private fun updateUI() {
        swipeRefreshLayout.isRefreshing = false
        
        val filteredData = getFilteredWorkOrders()
        
        if (isLoading) {
            // Still loading, keep showing loading state
            showLoadingState()
        } else if (filteredData.isEmpty()) {
            if (searchText.isNotEmpty()) {
                showEmptyState("No search results")
            } else {
                showEmptyState("No work orders")
            }
        } else {
            hideEmptyState()
            if (::workOrderAdapter.isInitialized) {
                workOrderAdapter.updateData(filteredData)
                tryScrollToPendingWorkOrder()
            }
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
    
    private fun showLoadingState() {
        tvEmpty.text = "Loading work orders..."
        tvEmpty.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
    }
    
    private fun handleLoadError(e: Exception) {
        // Check if offline mode based on exception type and message
        val isOffline = (e.message?.contains("Unable to resolve host", ignoreCase = true) == true) ||
                       (e.message?.contains("No address associated", ignoreCase = true) == true) ||
                       (e is java.net.UnknownHostException) ||
                       (e is java.net.ConnectException) ||
                       (!NetworkUtils.isNetworkAvailable(requireContext()))
        
        if (isOffline) {
            showOfflineModeDialog()
        } else {
            val errorMessage = when {
                e is kotlinx.coroutines.TimeoutCancellationException -> "Request timeout. Please check your connection."
                e.message?.contains("malformed JSON", ignoreCase = true) == true -> {
                    // JSON parsing error - show user-friendly message
                    android.util.Log.e("HomeFragment", "JSON parsing error: ${e.message}", e)
                    "Server response format error. Please try again or contact support."
                }
                e.message?.contains("Failed to parse JSON", ignoreCase = true) == true -> {
                    android.util.Log.e("HomeFragment", "JSON parsing error: ${e.message}", e)
                    "Server response format error. Please try again or contact support."
                }
                else -> {
                    android.util.Log.e("HomeFragment", "Error loading work orders: ${e.message}", e)
                    "Failed to load work orders: ${e.message?.take(100) ?: "Unknown error"}"
                }
            }
            showEmptyState(errorMessage)
        }
    }
    
    private fun showOfflineModeDialog() {
        if (!isAdded) return
        
        AlertDialog.Builder(requireContext())
            .setTitle("Offline Mode")
            .setMessage("Tidak ada koneksi internet. Data akan disinkronkan saat koneksi tersedia.")
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                showEmptyState("Offline Mode")
            }
            .setCancelable(false)
            .show()
    }

    
    override fun onDestroy() {
        super.onDestroy()
        // Cleanup search debounce timer
        searchDebounce?.cancel()
        clearHighlightRunnable?.let { highlightHandler.removeCallbacks(it) }
    }
}

