package com.sofindo.ems.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.sofindo.ems.R
import com.sofindo.ems.services.MaintenanceService
import com.sofindo.ems.services.UserService
import com.sofindo.ems.services.AssetService
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import kotlinx.coroutines.launch

class MaintenanceDetailFragment : Fragment() {
    
    private lateinit var ivAssetImage: android.widget.ImageView
    private lateinit var tvAssetNo: TextView
    private lateinit var tvCategory: TextView
    private lateinit var tvProperty: TextView
    private lateinit var tvMerk: TextView
    private lateinit var tvModel: TextView
    private lateinit var tvSerialNo: TextView
    private lateinit var tvCapacity: TextView
    private lateinit var tvSupplier: TextView
    private lateinit var tvDatePurchased: TextView
    private lateinit var tvLocation: TextView
    private lateinit var tvMaintenanceId: TextView
    private lateinit var tvDescription: TextView
    private lateinit var layoutDescription: View
    private lateinit var btnViewJobTasks: android.widget.Button
    private lateinit var btnViewHistory: android.widget.Button
    private lateinit var progressBar: ProgressBar
    
    private var mntId: String = ""
    private var propID: String = ""
    private var assetUrl: String = ""
    private var assetData: Map<String, Any>? = null
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_maintenance_detail, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Get arguments
        arguments?.let { args ->
            mntId = args.getString("mntId", "")
            propID = args.getString("propID", "")
            assetUrl = args.getString("assetUrl", "")
        }
        
        initViews(view)
        loadMaintenanceDetails()
    }
    
    private fun initViews(view: View) {
        ivAssetImage = view.findViewById(R.id.iv_asset_image)
        tvAssetNo = view.findViewById(R.id.tv_asset_no)
        tvCategory = view.findViewById(R.id.tv_category)
        tvProperty = view.findViewById(R.id.tv_property)
        tvMerk = view.findViewById(R.id.tv_merk)
        tvModel = view.findViewById(R.id.tv_model)
        tvSerialNo = view.findViewById(R.id.tv_serial_no)
        tvCapacity = view.findViewById(R.id.tv_capacity)
        tvSupplier = view.findViewById(R.id.tv_supplier)
        tvDatePurchased = view.findViewById(R.id.tv_date_purchased)
        tvLocation = view.findViewById(R.id.tv_location)
        tvMaintenanceId = view.findViewById(R.id.tv_maintenance_id)
        tvDescription = view.findViewById(R.id.tv_description)
        layoutDescription = view.findViewById(R.id.layout_description)
        btnViewJobTasks = view.findViewById(R.id.btn_view_job_tasks)
        btnViewHistory = view.findViewById(R.id.btn_view_history)
        progressBar = view.findViewById(R.id.progress_bar)
        
        // Setup toolbar back button
        val toolbar = view.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
        
        btnViewJobTasks.setOnClickListener { 
            // Navigate to Maintenance Job Task Fragment
            val fragment = MaintenanceJobTaskFragment.newInstance(
                assetNo = assetData?.get("no")?.toString() ?: "",
                mntId = assetData?.get("mntId")?.toString() ?: "",
                propertyName = assetData?.get("property")?.toString() ?: "",
                propID = propID
            )
            
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack("maintenance_job_task")
                .commit()
        }
        btnViewHistory.setOnClickListener { 
            Toast.makeText(context, "View History clicked", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun loadMaintenanceDetails() {
        progressBar.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                if (assetUrl.isNotEmpty()) {
                    // Load asset details from QR scan
                    loadAssetDetails()
                } else if (mntId.isNotEmpty()) {
                    // Load maintenance details
                    loadMaintenanceData()
                } else {
                    Toast.makeText(context, "No data provided", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }
    
    private suspend fun loadAssetDetails() {
        try {
            assetData = AssetService.getAssetDetail(assetUrl)
            
            // Debug logging untuk melihat response API
            android.util.Log.d("MaintenanceDetail", "Asset data received: $assetData")
            
            // Update UI with asset data
            tvAssetNo.text = assetData?.get("no")?.toString() ?: "N/A"
            tvCategory.text = assetData?.get("category")?.toString() ?: "N/A"
            tvProperty.text = assetData?.get("property")?.toString() ?: "N/A"
            tvMerk.text = assetData?.get("merk")?.toString() ?: "N/A"
            tvModel.text = assetData?.get("model")?.toString() ?: "N/A"
            tvSerialNo.text = assetData?.get("serno")?.toString() ?: "N/A"
            tvCapacity.text = assetData?.get("capacity")?.toString() ?: "N/A"
            tvSupplier.text = assetData?.get("supplier")?.toString() ?: "N/A"
            tvDatePurchased.text = assetData?.get("datePurchased")?.toString() ?: "N/A"
            tvLocation.text = assetData?.get("lokasi")?.toString() ?: "N/A"
            tvMaintenanceId.text = assetData?.get("mntId")?.toString() ?: "N/A"
            
            // Load description if available
            val description = assetData?.get("keterangan")?.toString()
            if (!description.isNullOrEmpty()) {
                tvDescription.text = description
                layoutDescription.visibility = View.VISIBLE
            } else {
                layoutDescription.visibility = View.GONE
            }
            
            // Load image if available - sesuai dengan Flutter
            val imageUrl = assetData?.get("imageUrl")?.toString()
            android.util.Log.d("MaintenanceDetail", "Image URL: $imageUrl")
            
            // Image always visible - either real image or placeholder
            ivAssetImage.visibility = View.VISIBLE
            
            if (!imageUrl.isNullOrEmpty()) {
                // Load image using Glide - sesuai dengan Flutter implementation
                Glide.with(this)
                    .load(imageUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.ic_image_placeholder)
                    .error(R.drawable.ic_image_placeholder)
                    .centerCrop()
                    .into(ivAssetImage)
                    
                android.util.Log.d("MaintenanceDetail", "Loading image from: $imageUrl")
            } else {
                // Show placeholder if no image URL
                ivAssetImage.setImageResource(R.drawable.ic_image_placeholder)
                android.util.Log.d("MaintenanceDetail", "No image URL found, showing placeholder")
            }
            
        } catch (e: Exception) {
            android.util.Log.e("MaintenanceDetail", "Error loading asset", e)
            Toast.makeText(context, "Error loading asset: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private suspend fun loadMaintenanceData() {
        // Get propID from UserService if not provided
        val currentPropID = if (propID.isNotEmpty()) propID else UserService.getCurrentPropID() ?: ""
        
        if (currentPropID.isEmpty()) {
            Toast.makeText(context, "Property ID not found. Please login again.", Toast.LENGTH_SHORT).show()
            return
        }
        
        // TODO: Load maintenance details if needed
        // For now, we'll use placeholder data
        tvAssetNo.text = "Maintenance Task"
        tvCategory.text = "Maintenance description will be loaded here"
        tvProperty.text = "Date: ${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())}"
        tvLocation.text = "Status: Pending"
    }
    
    // Removed updateMaintenanceStatus function as it's no longer needed
    
    companion object {
        fun newInstance(mntId: String, propID: String, assetUrl: String = ""): MaintenanceDetailFragment {
            return MaintenanceDetailFragment().apply {
                arguments = Bundle().apply {
                    putString("mntId", mntId)
                    putString("propID", propID)
                    putString("assetUrl", assetUrl)
                }
            }
        }
    }
}
