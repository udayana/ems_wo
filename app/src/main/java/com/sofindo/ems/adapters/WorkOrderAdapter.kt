package com.sofindo.ems.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
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

        val ivWorkOrderImage: ImageView? = itemView.findViewById(R.id.iv_work_order_image)

        val tvWoNumber: TextView? = itemView.findViewById(R.id.tv_wo_number)
        val tvWoStatus: TextView? = itemView.findViewById(R.id.tv_wo_status)
        val tvJobDescription: TextView? = itemView.findViewById(R.id.tv_job_description)
        val tvLocation: TextView? = itemView.findViewById(R.id.tv_location)
        val tvDepartment: TextView? = itemView.findViewById(R.id.tv_department)
        val tvDate: TextView? = itemView.findViewById(R.id.tv_date)
        val tvPriority: TextView? = itemView.findViewById(R.id.tv_priority)

        val btnMenu: ImageButton? = itemView.findViewById(R.id.btn_menu)
        val btnForward: ImageButton? = itemView.findViewById(R.id.btn_forward)

        val ivToIcon: ImageView? = itemView.findViewById(R.id.iv_to_icon)
        val tvToLabel: TextView? = itemView.findViewById(R.id.tv_to_label)

        val llAssignTo: LinearLayout? = itemView.findViewById(R.id.ll_assign_to)
        val tvAssignTo: TextView? = itemView.findViewById(R.id.tv_assign_to)

        val llReview: LinearLayout? = itemView.findViewById(R.id.ll_review)
        val tvReviewLabel: TextView? = itemView.findViewById(R.id.tv_review_label)
        val ivStar1: ImageView? = itemView.findViewById(R.id.iv_star_1)
        val ivStar2: ImageView? = itemView.findViewById(R.id.iv_star_2)
        val ivStar3: ImageView? = itemView.findViewById(R.id.iv_star_3)
        val ivStar4: ImageView? = itemView.findViewById(R.id.iv_star_4)
        val ivStar5: ImageView? = itemView.findViewById(R.id.iv_star_5)

        val btnNeedReview: AppCompatButton? = itemView.findViewById(R.id.btn_need_review)
        val tvRemarks: TextView? = itemView.findViewById(R.id.tv_remarks)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WorkOrderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_work_order, parent, false)
        return WorkOrderViewHolder(view)
    }

    override fun onBindViewHolder(holder: WorkOrderViewHolder, position: Int) {
        if (position !in workOrders.indices) return

        val workOrder = workOrders[position]
        val context = holder.itemView.context

        holder.tvWoNumber?.text = "WO: ${workOrder["nour"] ?: "-"}"
        holder.tvJobDescription?.text = workOrder["job"]?.toString() ?: "-"
        holder.tvLocation?.text = workOrder["lokasi"]?.toString() ?: "-"

        val orderBy = workOrder["orderBy"]?.toString()
        val woto = workOrder["woto"]?.toString()

        if (isHomeFragment) {
            holder.ivToIcon?.setImageResource(R.drawable.ic_person)
            holder.tvToLabel?.visibility = View.GONE
            holder.tvDepartment?.text = "by: ${orderBy ?: "-"}"
        } else {
            holder.ivToIcon?.setImageResource(R.drawable.ic_send)
            holder.tvToLabel?.visibility = View.VISIBLE
            holder.tvDepartment?.text = woto ?: "-"
        }

        val assignTo = workOrder["assignto"]?.toString()
        if (!assignTo.isNullOrEmpty()) {
            holder.llAssignTo?.visibility = View.VISIBLE
            holder.tvAssignTo?.text = "Assign to: $assignTo"
        } else {
            holder.llAssignTo?.visibility = View.GONE
        }

        holder.tvDate?.text = formatDateTime(workOrder["mulainya"]?.toString())
        holder.tvPriority?.text = workOrder["priority"]?.toString()?.uppercase() ?: "LOW"

        val status = workOrder["status"]?.toString() ?: ""
        holder.tvWoStatus?.text = if (status.isEmpty()) "NEW" else status.uppercase()

        val statusColor = getStatusColor(status)
        holder.tvWoStatus?.backgroundTintList =
            android.content.res.ColorStateList.valueOf(statusColor)

        if (userJabatan?.lowercase() == "head" && isHomeFragment && status.lowercase() != "done") {
            holder.btnForward?.visibility = View.VISIBLE
            holder.btnForward?.setOnClickListener { onForwardClick(workOrder) }
        } else {
            holder.btnForward?.visibility = View.GONE
        }

        val remarks = workOrder["remarks"]?.toString()
        if (!remarks.isNullOrEmpty()) {
            holder.tvRemarks?.visibility = View.VISIBLE
            holder.tvRemarks?.text = "Note: $remarks"
        } else {
            holder.tvRemarks?.visibility = View.GONE
        }

        val photoUrl = workOrder["photo"]?.toString()
        if (!photoUrl.isNullOrEmpty()) {
            val imageUrl = "https://emshotels.net/manager/workorder/photo/$photoUrl"
            holder.ivWorkOrderImage?.let {
                Glide.with(context).load(imageUrl)
                    .placeholder(R.drawable.ic_photo)
                    .error(R.drawable.ic_photo)
                    .into(it)
            }
        } else {
            holder.ivWorkOrderImage?.setImageResource(R.drawable.ic_photo)
        }

        holder.btnMenu?.visibility = View.GONE
        holder.itemView.setOnClickListener { onItemClick(workOrder) }
    }

    override fun getItemCount(): Int = workOrders.size

    fun updateData(newWorkOrders: List<Map<String, Any>>) {
        workOrders = newWorkOrders
        notifyDataSetChanged()
    }

    private fun getStatusColor(status: String): Int {
        return when (status.lowercase()) {
            "done" -> android.graphics.Color.parseColor("#1ABC9C")
            "pending" -> android.graphics.Color.parseColor("#FFC107")
            "on progress" -> android.graphics.Color.parseColor("#FF9800")
            "received" -> android.graphics.Color.parseColor("#2196F3")
            else -> android.graphics.Color.parseColor("#9E9E9E")
        }
    }

    private fun formatDateTime(dateTimeStr: String?): String {
        if (dateTimeStr.isNullOrEmpty()) return "-"
        val formats = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd")
        for (f in formats) {
            try {
                val d = SimpleDateFormat(f, Locale.getDefault()).parse(dateTimeStr)
                if (d != null) {
                    return SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(d)
                }
            } catch (_: Exception) {}
        }
        return dateTimeStr
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
}
