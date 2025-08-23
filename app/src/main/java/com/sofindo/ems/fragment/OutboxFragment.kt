package com.sofindo.ems.fragment

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.sofindo.ems.R
import com.sofindo.ems.adapter.WorkOrderAdapter
import com.sofindo.ems.api.RetrofitClient
import com.sofindo.ems.models.StatusCount
import com.sofindo.ems.models.WorkOrder
import com.sofindo.ems.services.UserService
import kotlinx.coroutines.launch

class OutboxFragment : Fragment() {

	private lateinit var etSearch: EditText
	private lateinit var btnFilter: ImageButton
	private lateinit var rvWorkOrders: RecyclerView
	private lateinit var swipeRefreshLayout: SwipeRefreshLayout
	private lateinit var llLoading: LinearLayout
	private lateinit var llEmpty: LinearLayout
	private lateinit var tvEmptyMessage: TextView

	private var workOrders = mutableListOf<WorkOrder>()
	private var filteredWorkOrders = mutableListOf<WorkOrder>()
	private var selectedStatus = ""
	private var searchText = ""
	private var currentPage = 1
	private var isLoading = false
	private var isLoadingMore = false
	private var hasMoreData = true
	private var statusCounts = mutableMapOf<String, Int>()
	private var isLoadingStatusCounts = true

	private var propID: String? = null
	private var username: String? = null
	private var userDept: String? = null

	private val statusList = listOf("", "new", "received", "on progress", "pending", "done")

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
		setupRecyclerView()
		setupListeners()
		initializeUserData()
	}

	private fun initViews(view: View) {
		etSearch = view.findViewById(R.id.et_search)
		btnFilter = view.findViewById(R.id.btn_filter)
		rvWorkOrders = view.findViewById(R.id.rv_work_orders)
		swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout)
		llLoading = view.findViewById(R.id.ll_loading)
		llEmpty = view.findViewById(R.id.ll_empty)
		tvEmptyMessage = view.findViewById(R.id.tv_empty_message)
	}

	private fun setupRecyclerView() {
		rvWorkOrders.layoutManager = LinearLayoutManager(context)
		val adapter = WorkOrderAdapter(
			onItemClick = { workOrder ->
				// Navigate to detail
				// TODO: Implement detail navigation
			},
			onChangeStatusClick = { workOrder ->
				// Show delete confirmation
				showDeleteConfirmation(workOrder)
			}
		)
		rvWorkOrders.adapter = adapter
	}

	private fun setupListeners() {
		etSearch.addTextChangedListener(object : TextWatcher {
			override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
			override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
				searchText = s.toString()
				filterWorkOrders()
			}
			override fun afterTextChanged(s: Editable?) {}
		})

		btnFilter.setOnClickListener {
			showFilterDialog()
		}

		swipeRefreshLayout.setOnRefreshListener {
			refreshData()
		}
	}

	private fun initializeUserData() {
		lifecycleScope.launch {
			try {
				val user = UserService.getCurrentUser(requireContext())
				if (user != null) {
					propID = user.propID
					username = user.fullName ?: user.username
					userDept = user.dept
					loadData(page = 1)
				} else {
					showEmptyState("User data not found")
				}
			} catch (e: Exception) {
				showEmptyState("Error loading user data")
			}
		}
	}

	private fun loadData(page: Int = 1) {
		if (isLoading && page == 1) return
		if (isLoadingMore && page > 1) return

		if (propID.isNullOrEmpty() || username.isNullOrEmpty()) {
			showEmptyState("User data not available")
			return
		}

		setLoading(page == 1)

		lifecycleScope.launch {
			try {
				val workOrdersData = RetrofitClient.instance.getWorkOrdersOut(
					propID = propID!!,
					orderBy = username!!,
					status = selectedStatus,
					page = page,
					userDept = userDept ?: ""
				)

				if (page == 1) {
					workOrders.clear()
				}
				workOrders.addAll(workOrdersData)

				if (workOrdersData.size < 10) {
					hasMoreData = false
				} else {
					currentPage = page + 1
				}

				filterWorkOrders()
				updateUI()

			} catch (e: Exception) {
				Toast.makeText(context, "Failed to load work orders: ${e.message}", Toast.LENGTH_SHORT).show()
				showEmptyState("Failed to load data")
			} finally {
				setLoading(false)
				swipeRefreshLayout.isRefreshing = false
			}
		}
	}

	private fun loadStatusCounts() {
		if (propID.isNullOrEmpty()) return

		lifecycleScope.launch {
			try {
				isLoadingStatusCounts = true
				val statusData = RetrofitClient.instance.getAllStatusesOutbox(
					propID = propID!!,
					dept = userDept ?: ""
				)

				statusCounts.clear()
				var totalCount = 0

				for (status in statusData) {
					val statusName = status.status ?: ""
					val count = status.count
					
					if (statusName.isEmpty()) {
						statusCounts["new"] = count
					} else {
						statusCounts[statusName] = count
					}
					totalCount += count
				}

				statusCounts[""] = totalCount

				// Ensure all statuses exist
				for (status in statusList) {
					if (status.isNotEmpty() && !statusCounts.containsKey(status)) {
						statusCounts[status] = 0
					}
				}

				if (!statusCounts.containsKey("new")) {
					statusCounts["new"] = 0
				}

			} catch (e: Exception) {
				// Set default values if API fails
				statusCounts.clear()
				statusCounts[""] = 0
				statusCounts["new"] = 0
				for (status in statusList) {
					if (status.isNotEmpty()) {
						statusCounts[status] = 0
					}
				}
			} finally {
				isLoadingStatusCounts = false
			}
		}
	}

	private fun filterWorkOrders() {
		filteredWorkOrders.clear()
		
		if (searchText.isEmpty()) {
			filteredWorkOrders.addAll(workOrders)
		} else {
			val searchLower = searchText.lowercase()
			filteredWorkOrders.addAll(workOrders.filter { workOrder ->
				workOrder.nour?.lowercase()?.contains(searchLower) == true ||
				workOrder.job?.lowercase()?.contains(searchLower) == true ||
				workOrder.lokasi?.lowercase()?.contains(searchLower) == true ||
				workOrder.woto?.lowercase()?.contains(searchLower) == true
			})
		}

		(rvWorkOrders.adapter as? WorkOrderAdapter)?.submitList(filteredWorkOrders.toList())
		updateUI()
	}

	private fun updateUI() {
		when {
			isLoading -> {
				llLoading.visibility = View.VISIBLE
				rvWorkOrders.visibility = View.GONE
				llEmpty.visibility = View.GONE
			}
			filteredWorkOrders.isEmpty() -> {
				llLoading.visibility = View.GONE
				rvWorkOrders.visibility = View.GONE
				llEmpty.visibility = View.VISIBLE
				tvEmptyMessage.text = if (searchText.isNotEmpty()) "No search results" else "No work orders"
			}
			else -> {
				llLoading.visibility = View.GONE
				rvWorkOrders.visibility = View.VISIBLE
				llEmpty.visibility = View.GONE
			}
		}
	}

	private fun setLoading(loading: Boolean) {
		isLoading = loading
		updateUI()
	}

	private fun showEmptyState(message: String) {
		llLoading.visibility = View.GONE
		rvWorkOrders.visibility = View.GONE
		llEmpty.visibility = View.VISIBLE
		tvEmptyMessage.text = message
	}

	private fun refreshData() {
		workOrders.clear()
		currentPage = 1
		hasMoreData = true
		loadData(page = 1)
		loadStatusCounts()
	}

	private fun showFilterDialog() {
		// Load status counts when filter dialog opens
		loadStatusCounts()

		val statusOptions = statusList.map { status ->
			val count = statusCounts[status] ?: 0
			val displayName = when {
				status.isEmpty() -> "All"
				status.lowercase() == "new" -> "NEW"
				else -> status.uppercase()
			}
			"$count → $displayName" to status
		}

		val options = statusOptions.map { it.first }.toTypedArray()

		AlertDialog.Builder(requireContext())
			.setTitle("Filter by Status")
			.setItems(options) { _, which ->
				val selectedStatusValue = statusOptions[which].second
				onStatusChanged(selectedStatusValue)
			}
			.show()
	}

	private fun onStatusChanged(status: String) {
		selectedStatus = status
		currentPage = 1
		workOrders.clear()
		hasMoreData = true
		loadData(page = 1)
		loadStatusCounts()
	}

	private fun showDeleteConfirmation(workOrder: WorkOrder) {
		AlertDialog.Builder(requireContext())
			.setTitle("Delete Work Order")
			.setMessage("Are you sure you want to delete this work order?")
			.setPositiveButton("Delete") { _, _ ->
				deleteWorkOrder(workOrder.woId ?: "")
			}
			.setNegativeButton("Cancel", null)
			.show()
	}

	private fun deleteWorkOrder(woId: String) {
		lifecycleScope.launch {
			try {
				val result = RetrofitClient.instance.deleteWorkOrder(woId = woId)
				val success = result["success"] as? Boolean ?: false
				
				if (success) {
					workOrders.removeAll { it.woId == woId }
					filterWorkOrders()
					Toast.makeText(context, "Work order deleted successfully", Toast.LENGTH_SHORT).show()
				} else {
					val message = result["message"] as? String ?: "Failed to delete"
					Toast.makeText(context, "Failed to delete: $message", Toast.LENGTH_SHORT).show()
				}
			} catch (e: Exception) {
				Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
			}
		}
	}

	// Method to be called when tab is activated
	fun onTabActivated() {
		if (propID != null && username != null) {
			refreshData()
		}
	}
}





