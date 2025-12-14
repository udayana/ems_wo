package com.sofindo.ems.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.sofindo.ems.R
import java.text.SimpleDateFormat
import java.util.*

class WorkOrderAdapter(
    private var workOrders: List<Map<String, Any>> = emptyList(),
    private val onItemClick: (Map<String, Any>) -> Unit = {},
    private val onEditClick: (Map<String, Any>) -> Unit = {},
    private val onDeleteClick: (Map<String, Any>) -> Unit = {},
    private val onDetailClick: (Map<String, Any>) -> Unit = {},
    private val onFollowUpClick: (Map<String, Any>) -> Unit = {},
    private val onForwardClick: (Map<String, Any>) -> Unit = {},
    private val onNeedReviewClick: (Map<String, Any>) -> Unit = {},
    private val onReviewClick: (Map<String, Any>) -> Unit = {},
    private val showSender: Boolean = false,
    private val replaceWotoWithOrderBy: Boolean = false,
    private val isHomeFragment: Boolean = false,
    private val userJabatan: String? = null,
    private val currentUserName: String? = null
) : RecyclerView.Adapter<WorkOrderAdapter.WorkOrderViewHolder>() {

    private var highlightedWoId: String? = null
    private var highlightedNour: String? = null

    class WorkOrderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Image
        val ivWorkOrderImage: ImageView = itemView.findViewById(R.id.iv_work_order_image)
        
        // Content
        val tvWoNumber: TextView = itemView.findViewById(R.id.tv_wo_number)
        val tvWoStatus: TextView = itemView.findViewById(R.id.tv_wo_status)
        val tvJobDescription: TextView = itemView.findViewById(R.id.tv_job_description)
        val tvLocation: TextView = itemView.findViewById(R.id.tv_location)
        val tvDepartment: TextView = itemView.findViewById(R.id.tv_department)
        val tvDate: TextView = itemView.findViewById(R.id.tv_date)
        val tvPriority: TextView = itemView.findViewById(R.id.tv_priority)
        
        // Menu button (hidden but kept for reference)
        val btnMenu: ImageButton = itemView.findViewById(R.id.btn_menu)
        
        // Forward button
        val btnForward: ImageButton = itemView.findViewById(R.id.btn_forward)

        // To UI controls (look up by name to avoid NoSuchFieldError if IDs not compiled yet)
        val ivToIcon: ImageView? = run {
            val resId = itemView.resources.getIdentifier("iv_to_icon", "id", itemView.context.packageName)
            if (resId != 0) itemView.findViewById(resId) else null
        }
        val tvToLabel: TextView? = run {
            val resId = itemView.resources.getIdentifier("tv_to_label", "id", itemView.context.packageName)
            if (resId != 0) itemView.findViewById(resId) else null
        }
        
        // Assign To
        val llAssignTo: LinearLayout = itemView.findViewById(R.id.ll_assign_to)
        val tvAssignTo: TextView = itemView.findViewById(R.id.tv_assign_to)
        
        // Review Section
        val llReview: LinearLayout = itemView.findViewById(R.id.ll_review)
        val tvReviewLabel: TextView = itemView.findViewById(R.id.tv_review_label)
        val ivStar1: ImageView = itemView.findViewById(R.id.iv_star_1)
        val ivStar2: ImageView = itemView.findViewById(R.id.iv_star_2)
        val ivStar3: ImageView = itemView.findViewById(R.id.iv_star_3)
        val ivStar4: ImageView = itemView.findViewById(R.id.iv_star_4)
        val ivStar5: ImageView = itemView.findViewById(R.id.iv_star_5)
        
        // Need Review Button
        val btnNeedReview: AppCompatButton = itemView.findViewById(R.id.btn_need_review)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WorkOrderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_work_order, parent, false)
        return WorkOrderViewHolder(view)
    }

    override fun onBindViewHolder(holder: WorkOrderViewHolder, position: Int) {
        // Validate position to prevent IndexOutOfBoundsException
        if (position < 0 || position >= workOrders.size) {
            return
        }
        
        val workOrder = workOrders[position]
        val context = holder.itemView.context
        
        // Bind WO Number
        holder.tvWoNumber.text = "WO: ${workOrder["nour"] ?: "N/A"}"
        
        // Bind Job Description
        holder.tvJobDescription.text = workOrder["job"]?.toString() ?: "No Job Title"
        
        // Bind Location
        holder.tvLocation.text = workOrder["lokasi"]?.toString() ?: "No Location"
        
        // Bind right-side label depending on fragment
        val orderByValue = workOrder["orderBy"]?.toString() ?: workOrder["orderby"]?.toString()
        val wotoValue = workOrder["woto"]?.toString()
        if (isHomeFragment) {
            // In Home: show "by: <orderBy>" and hide "To:" label, show person icon
            holder.ivToIcon?.setImageResource(R.drawable.ic_person)
            holder.tvToLabel?.visibility = View.GONE
            holder.ivToIcon?.visibility = View.VISIBLE
            holder.tvDepartment.text = "by: ${orderByValue ?: "Unknown"}"
        } else {
            // In Outbox: show "To: <woto>" with send icon
            holder.ivToIcon?.setImageResource(R.drawable.ic_send)
            holder.tvToLabel?.visibility = View.VISIBLE
            holder.ivToIcon?.visibility = View.VISIBLE
            holder.tvDepartment.text = wotoValue ?: "Unknown"
        }
        
        // Bind Assign To (only show if assignto is not empty)
        val assignTo = workOrder["assignto"]?.toString() ?: workOrder["assignTo"]?.toString()
        if (!assignTo.isNullOrEmpty()) {
            holder.llAssignTo.visibility = View.VISIBLE
            holder.tvAssignTo.text = "Assign to: $assignTo"
        } else {
            holder.llAssignTo.visibility = View.GONE
        }
        
        // Set orange border if assignto matches current user
        val isAssignedToCurrentUser = !currentUserName.isNullOrEmpty() && 
                                      !assignTo.isNullOrEmpty() && 
                                      assignTo.equals(currentUserName, ignoreCase = true)

        val woIdValue = workOrder["woId"]?.toString()?.takeIf { it.isNotEmpty() }
        val nourValue = workOrder["nour"]?.toString()?.takeIf { it.isNotEmpty() }
        val isHighlighted = (highlightedWoId != null && highlightedWoId.equals(woIdValue, ignoreCase = true)) ||
                (highlightedNour != null && highlightedNour.equals(nourValue, ignoreCase = true))

        val backgroundRes = when {
            isHighlighted -> R.drawable.work_order_item_background_highlight
            isAssignedToCurrentUser -> R.drawable.work_order_item_background_assigned
            else -> R.drawable.work_order_item_background
        }
        holder.itemView.background = ContextCompat.getDrawable(context, backgroundRes)
        
        // Bind Date (use start date-time from 'mulainya')
        holder.tvDate.text = formatDateTime(workOrder["mulainya"]?.toString())
        
        // Bind Priority
        val priority = workOrder["priority"]?.toString() ?: ""
        holder.tvPriority.text = getPriorityText(priority)
        
        // Bind Status
        val status = workOrder["status"]?.toString() ?: ""
        val statusText = if (status.isEmpty()) "NEW" else status.uppercase()
        holder.tvWoStatus.text = statusText
        
        // Set status color and background
        val statusColor = getStatusColor(status)
        holder.tvWoStatus.backgroundTintList = android.content.res.ColorStateList.valueOf(statusColor)
        
        // Show/hide forward button based on user jabatan and status
        // Only show if: user is head, in home fragment, and status is not "done"
        val statusLower = status.lowercase()
        if (userJabatan?.lowercase() == "head" && 
            isHomeFragment && 
            statusLower != "done") {
            holder.btnForward.visibility = View.VISIBLE
            holder.btnForward.setOnClickListener {
                onForwardClick(workOrder)
            }
        } else {
            holder.btnForward.visibility = View.GONE
        }
        
        // Show/hide Need Review button
        // Only show in OutboxFragment if: status = "done" && orderBy = currentUserName && is_review != 1
        // Check if is_review is not 1 (not reviewed yet)
        val isNotReviewed = workOrder["is_review"]?.let { reviewValue ->
            when (reviewValue) {
                is Number -> reviewValue.toInt() != 1
                is String -> {
                    val reviewInt = reviewValue.toIntOrNull()
                    reviewInt == null || reviewInt != 1
                }
                is Boolean -> !reviewValue // false = not reviewed, true = reviewed
                else -> true // Unknown type, assume not reviewed
            }
        } ?: true // Field doesn't exist, assume not reviewed
        
        val showNeedReview = !isHomeFragment && 
                            statusLower == "done" && 
                            !currentUserName.isNullOrEmpty() && 
                            !orderByValue.isNullOrEmpty() &&
                            orderByValue.equals(currentUserName, ignoreCase = true) &&
                            isNotReviewed
        
        if (showNeedReview) {
            holder.btnNeedReview.visibility = View.VISIBLE
            holder.btnNeedReview.setOnClickListener {
                onNeedReviewClick(workOrder)
            }
        } else {
            holder.btnNeedReview.visibility = View.GONE
        }
        
        // Show/hide Review section
        // HomeFragment: show if is_review == 1
        // OutboxFragment: show if is_review == 1 && orderBy == currentUserName (reviewed by me)
        val isReviewed = workOrder["is_review"]?.let { reviewValue ->
            when (reviewValue) {
                is Number -> reviewValue.toInt() == 1
                is String -> reviewValue.toIntOrNull() == 1
                is Boolean -> reviewValue
                else -> false
            }
        } ?: false
        
        val reviewRating = workOrder["review_rating"]?.let { ratingValue ->
            when (ratingValue) {
                is Number -> ratingValue.toInt()
                is String -> ratingValue.toIntOrNull() ?: 0
                else -> 0
            }
        } ?: 0
        
        // Check if reviewed by current user (for OutboxFragment)
        val isReviewedByMe = !isHomeFragment && 
                            isReviewed && 
                            !currentUserName.isNullOrEmpty() && 
                            !orderByValue.isNullOrEmpty() &&
                            orderByValue.equals(currentUserName, ignoreCase = true)
        
        if (isHomeFragment && isReviewed && reviewRating > 0) {
            // HomeFragment: show "Review: ⭐⭐⭐"
            holder.llReview.visibility = View.VISIBLE
            holder.tvReviewLabel.text = "Review: "
            updateRatingStars(holder, reviewRating)
        } else if (isReviewedByMe && reviewRating > 0) {
            // OutboxFragment: show "You reviewed: ⭐⭐⭐" if reviewed by current user (clickable for edit)
            holder.llReview.visibility = View.VISIBLE
            holder.tvReviewLabel.text = "You reviewed: "
            holder.tvReviewLabel.setTextColor(ContextCompat.getColor(context, android.R.color.holo_blue_dark))
            updateRatingStars(holder, reviewRating)
            
            // Make review section clickable for edit
            holder.llReview.isClickable = true
            holder.llReview.isFocusable = true
            
            // Consume touch events to prevent itemView from receiving them
            holder.llReview.setOnTouchListener { view, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        // Prevent parent from intercepting touch events
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                        false // Continue handling
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        // Call onReviewClick when touch is released
                        onReviewClick(workOrder)
                        true // Event consumed, don't pass to itemView
                    }
                    android.view.MotionEvent.ACTION_CANCEL -> {
                        false
                    }
                    else -> false
                }
            }
            
            // Also make TextView clickable as backup
            holder.tvReviewLabel.isClickable = true
            holder.tvReviewLabel.setOnTouchListener { view, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                        false
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        onReviewClick(workOrder)
                        true // Event consumed
                    }
                    android.view.MotionEvent.ACTION_CANCEL -> {
                        false
                    }
                    else -> false
                }
            }
            
            // Add visual feedback - underline text to indicate it's clickable
            holder.tvReviewLabel.paintFlags = holder.tvReviewLabel.paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
        } else {
            // Hide review section
            holder.llReview.visibility = View.GONE
            holder.llReview.isClickable = false
            holder.llReview.setOnClickListener(null)
            holder.llReview.setOnTouchListener(null)
            holder.tvReviewLabel.setOnTouchListener(null)
            // Reset underline
            holder.tvReviewLabel.paintFlags = holder.tvReviewLabel.paintFlags and android.graphics.Paint.UNDERLINE_TEXT_FLAG.inv()
        }
        
        // Handle image (same as Flutter)
        val photoUrl = workOrder["photo"]?.toString()
        
        if (!photoUrl.isNullOrEmpty()) {
            val imageUrl = "https://emshotels.net/manager/workorder/photo/$photoUrl"
            Glide.with(context)
                .load(imageUrl)
                .placeholder(R.drawable.ic_photo)
                .error(R.drawable.ic_photo)
                .into(holder.ivWorkOrderImage)
        } else {
            holder.ivWorkOrderImage.setImageResource(R.drawable.ic_photo)
        }
        
        // Set click listener for image
        holder.ivWorkOrderImage.setOnClickListener {
            val photoUrl = workOrder["photo"]?.toString()
            if (!photoUrl.isNullOrEmpty()) {
                val imageUrl = "https://emshotels.net/manager/workorder/photo/$photoUrl"
                val dialog = com.sofindo.ems.dialogs.ImageViewerDialog(context, imageUrl)
                dialog.show()
            }
        }
        
        // Hide the menu button since we're using card click instead
        holder.btnMenu.visibility = View.GONE
        
        // Set click listener for entire card to show popup menu
        holder.itemView.setOnClickListener { view ->
            showPopupMenu(holder.itemView, workOrder)
        }
    }

    override fun getItemCount(): Int {
        return try {
            workOrders.size
        } catch (e: Exception) {
            0
        }
    }

    fun updateData(newWorkOrders: List<Map<String, Any>>) {
        // Ensure update happens on main thread and is thread-safe
        workOrders = newWorkOrders
        try {
            notifyDataSetChanged()
        } catch (e: Exception) {
            // If notify fails, ignore - RecyclerView will handle it
            android.util.Log.e("WorkOrderAdapter", "Error in notifyDataSetChanged: ${e.message}", e)
        }
    }

    fun setHighlightForWorkOrder(workOrder: Map<String, Any>) {
        highlightedWoId = workOrder["woId"]?.toString()?.takeIf { it.isNotEmpty() }
        highlightedNour = workOrder["nour"]?.toString()?.takeIf { it.isNotEmpty() }
        notifyDataSetChanged()
    }

    fun clearHighlight() {
        if (highlightedWoId != null || highlightedNour != null) {
            highlightedWoId = null
            highlightedNour = null
            notifyDataSetChanged()
        }
    }
    
    private fun showPopupMenu(view: View, workOrder: Map<String, Any>) {
        // Create custom popup menu with themed context
        val themedContext = android.view.ContextThemeWrapper(view.context, R.style.PopupMenuStyle)
        val popupMenu = android.widget.PopupMenu(themedContext, view)
        
        // Use different menu based on fragment type
        if (isHomeFragment) {
            popupMenu.menuInflater.inflate(R.menu.work_order_home_menu, popupMenu.menu)
        } else {
            popupMenu.menuInflater.inflate(R.menu.work_order_menu, popupMenu.menu)
        }
        
        // Set popup menu background using reflection
        try {
            val popupField = popupMenu.javaClass.getDeclaredField("mPopup")
            popupField.isAccessible = true
            val popupWindow = popupField.get(popupMenu)
            
            if (popupWindow is android.widget.PopupWindow) {
                popupWindow.setBackgroundDrawable(view.context.getDrawable(R.drawable.popup_menu_background))
                popupWindow.elevation = 8f
            }
        } catch (e: Exception) {
            // Popup menu background setting failed
        }
        
        // Set click listener for all cases
        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_detail -> {
                    onDetailClick(workOrder)
                    true
                }
                R.id.action_edit -> {
                    onEditClick(workOrder)
                    true
                }
                R.id.action_delete -> {
                    onDeleteClick(workOrder)
                    true
                }
                R.id.action_follow_up -> {
                    onFollowUpClick(workOrder)
                    true
                }
                else -> false
            }
        }
        
        popupMenu.show()
    }
    
    // Helper function to update rating stars
    private fun updateRatingStars(holder: WorkOrderViewHolder, rating: Int) {
        val stars = listOf(holder.ivStar1, holder.ivStar2, holder.ivStar3, holder.ivStar4, holder.ivStar5)
        val context = holder.itemView.context
        
        for (i in stars.indices) {
            val starIndex = i + 1
            if (starIndex <= rating) {
                // Filled star - yellow
                stars[i].setImageResource(R.drawable.ic_star_filled)
                stars[i].setColorFilter(ContextCompat.getColor(context, R.color.star_filled))
            } else {
                // Empty star - gray
                stars[i].setImageResource(R.drawable.ic_star)
                stars[i].setColorFilter(ContextCompat.getColor(context, R.color.star_empty))
            }
        }
    }
    
    // Helper functions (exactly like Flutter)
    private fun getPriorityText(priority: String): String {
        return if (priority.isEmpty()) "LOW" else priority.uppercase()
    }
    
    private fun getStatusColor(status: String): Int {
        return when (status.lowercase()) {
            "done" -> android.graphics.Color.parseColor("#FF1ABC9C")
            "pending" -> android.graphics.Color.parseColor("#FFC107")
            "on progress" -> android.graphics.Color.parseColor("#FF9800")
            "received" -> android.graphics.Color.parseColor("#2196F3")
            "" -> android.graphics.Color.parseColor("#9E9E9E")
            else -> android.graphics.Color.parseColor("#9E9E9E")
        }
    }
    
    private fun formatDateTime(dateTimeStr: String?): String {
        if (dateTimeStr.isNullOrEmpty()) return "-"
        if (dateTimeStr.startsWith("0000-00-00")) return "-"

        val candidates = listOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd"
        )

        for (pattern in candidates) {
            try {
                val parser = SimpleDateFormat(pattern, Locale.getDefault())
                val date = parser.parse(dateTimeStr)
                if (date != null) {
                    val output = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
                    return output.format(date)
                }
            } catch (_: Exception) { }
        }

        return dateTimeStr
    }
}

