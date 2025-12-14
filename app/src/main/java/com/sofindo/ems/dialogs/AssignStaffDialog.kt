package com.sofindo.ems.dialogs

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.sofindo.ems.R
import com.sofindo.ems.models.Staff

class AssignStaffDialog(
    context: Context,
    private val staffList: List<Staff>,
    private val onStaffSelected: (Staff) -> Unit
) : Dialog(context) {

    private lateinit var recyclerView: RecyclerView
    private lateinit var llEmpty: LinearLayout
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_assign_staff)
        
        window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        
        initViews()
        setupRecyclerView()
    }
    
    private fun initViews() {
        recyclerView = findViewById(R.id.recycler_view_staff)
        llEmpty = findViewById(R.id.tv_empty_staff)
        progressBar = findViewById(R.id.progress_bar_staff)
        
        findViewById<ImageButton>(R.id.btn_close_dialog)?.setOnClickListener {
            dismiss()
        }
        
        // Hide progress, show content
        progressBar.visibility = View.GONE
        
        if (staffList.isEmpty()) {
            recyclerView.visibility = View.GONE
            llEmpty.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            llEmpty.visibility = View.GONE
        }
    }
    
    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = StaffAdapter(staffList) { staff ->
            onStaffSelected(staff)
            dismiss()
        }
    }
    
    private class StaffAdapter(
        private val staffList: List<Staff>,
        private val onItemClick: (Staff) -> Unit
    ) : RecyclerView.Adapter<StaffAdapter.StaffViewHolder>() {
        
        class StaffViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val ivPhoto: ImageView = itemView.findViewById(R.id.iv_staff_photo)
            val tvName: TextView = itemView.findViewById(R.id.tv_staff_name)
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StaffViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_staff, parent, false)
            return StaffViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: StaffViewHolder, position: Int) {
            // Validate position to prevent IndexOutOfBoundsException
            if (position < 0 || position >= staffList.size) {
                return
            }
            val staff = staffList[position]
            
            holder.tvName.text = staff.nama
            
            // Load photo
            val photoUrl = staff.photo
            if (!photoUrl.isNullOrEmpty()) {
                val imageUrl = "https://emshotels.net/images/user/profile/thumb/$photoUrl"
                Glide.with(holder.itemView.context)
                    .load(imageUrl)
                    .placeholder(R.drawable.ic_person)
                    .error(R.drawable.ic_person)
                    .circleCrop()
                    .into(holder.ivPhoto)
            } else {
                holder.ivPhoto.setImageResource(R.drawable.ic_person)
            }
            
            holder.itemView.setOnClickListener {
                onItemClick(staff)
            }
        }
        
        override fun getItemCount(): Int {
            return try {
                staffList.size
            } catch (e: Exception) {
                0
            }
        }
    }
}

