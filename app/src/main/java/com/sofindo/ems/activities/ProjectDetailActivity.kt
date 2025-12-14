package com.sofindo.ems.activities

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import androidx.lifecycle.lifecycleScope
import com.sofindo.ems.R
import com.sofindo.ems.dialogs.ImageViewerDialog
import com.sofindo.ems.models.ProjectDetail
import com.sofindo.ems.services.UserService
import com.sofindo.ems.utils.applyTopAndBottomInsets
import com.sofindo.ems.utils.setupEdgeToEdge
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.io.IOException
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class ProjectDetailActivity : AppCompatActivity() {

    private lateinit var toolbar: View
    private lateinit var backButton: ImageButton
    private lateinit var toolbarTitle: TextView
    private lateinit var btnUpdate: Button
    private lateinit var scrollView: ScrollView
    private lateinit var loadingContainer: LinearLayout
    private lateinit var errorContainer: LinearLayout
    private lateinit var tvErrorMessage: TextView
    private lateinit var btnRetry: Button
    private lateinit var contentContainer: LinearLayout

    // Project Info Section
    private lateinit var tvProjectName: TextView
    private lateinit var llLocation: LinearLayout
    private lateinit var tvLocation: TextView
    private lateinit var llCategory: LinearLayout
    private lateinit var tvCategory: TextView
    private lateinit var llPriority: LinearLayout
    private lateinit var tvPriority: TextView
    private lateinit var llStatus: LinearLayout
    private lateinit var tvStatus: TextView
    private lateinit var llCreatedDate: LinearLayout
    private lateinit var tvCreatedDate: TextView
    private lateinit var llCreatedBy: LinearLayout
    private lateinit var tvCreatedBy: TextView
    private lateinit var llRemarks: LinearLayout
    private lateinit var tvRemarks: TextView

    // Jobs Section
    private lateinit var jobsSection: LinearLayout
    private lateinit var tvJobsTitle: TextView
    private lateinit var rvJobs: RecyclerView

    // Materials Section
    private lateinit var materialsSection: LinearLayout
    private lateinit var tvMaterialsTitle: TextView
    private lateinit var llMaterialsList: LinearLayout
    private lateinit var llTotalCost: LinearLayout
    private lateinit var tvTotalCost: TextView

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var httpClient: OkHttpClient

    private var projectId: String = ""
    private var propID: String = ""
    private var projectDetail: ProjectDetail? = null

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        // Enable edge-to-edge for Android 15+ (SDK 35)
        setupEdgeToEdge()
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_project_detail)
        
        // Apply window insets to root layout
        findViewById<android.view.ViewGroup>(android.R.id.content)?.getChildAt(0)?.let { rootView ->
            rootView.applyTopAndBottomInsets()
        }

        projectId = intent.getStringExtra("projectId") ?: intent.getStringExtra("project") ?: ""
        propID = intent.getStringExtra("propID") ?: ""

        android.util.Log.d("ProjectDetailActivity", "Loading project detail - projectId: $projectId, propID: $propID")

        if (projectId.isEmpty() || propID.isEmpty()) {
            android.util.Log.e("ProjectDetailActivity", "Missing projectId or propID")
            showError("Project ID atau propID tidak ditemukan")
            return
        }

        initViews()
        setupToolbar()
        checkUserJabatan()
        loadProjectDetail()
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        backButton = toolbar.findViewById(R.id.back_button)
        toolbarTitle = toolbar.findViewById(R.id.toolbar_title)
        btnUpdate = toolbar.findViewById(R.id.btn_update)
        scrollView = findViewById(R.id.scroll_view)
        loadingContainer = findViewById(R.id.loading_container)
        errorContainer = findViewById(R.id.error_container)
        tvErrorMessage = findViewById(R.id.tv_error_message)
        btnRetry = findViewById(R.id.btn_retry)
        contentContainer = findViewById(R.id.content_container)

        // Project Info
        tvProjectName = findViewById(R.id.tv_project_name)
        llLocation = findViewById(R.id.ll_location)
        tvLocation = findViewById(R.id.tv_location)
        llCategory = findViewById(R.id.ll_category)
        tvCategory = findViewById(R.id.tv_category)
        llPriority = findViewById(R.id.ll_priority)
        tvPriority = findViewById(R.id.tv_priority)
        llStatus = findViewById(R.id.ll_status)
        tvStatus = findViewById(R.id.tv_status)
        llCreatedDate = findViewById(R.id.ll_created_date)
        tvCreatedDate = findViewById(R.id.tv_created_date)
        llCreatedBy = findViewById(R.id.ll_created_by)
        tvCreatedBy = findViewById(R.id.tv_created_by)
        llRemarks = findViewById(R.id.ll_remarks)
        tvRemarks = findViewById(R.id.tv_remarks)

        // Jobs Section
        jobsSection = findViewById(R.id.jobs_section)
        tvJobsTitle = findViewById(R.id.tv_jobs_title)
        rvJobs = findViewById(R.id.rv_jobs)

        // Materials Section
        materialsSection = findViewById(R.id.materials_section)
        tvMaterialsTitle = findViewById(R.id.tv_materials_title)
        llMaterialsList = findViewById(R.id.ll_materials_list)
        llTotalCost = findViewById(R.id.ll_total_cost)
        tvTotalCost = findViewById(R.id.tv_total_cost)

        sharedPreferences = getSharedPreferences("ems_user_prefs", Context.MODE_PRIVATE)
        httpClient = OkHttpClient()

        toolbarTitle.text = "Project Detail"
    }

    private fun setupToolbar() {
        backButton.setOnClickListener {
            finish()
        }

        btnRetry.setOnClickListener {
            loadProjectDetail()
        }
        
        btnUpdate.setOnClickListener {
            // Navigate to UpdateProjectActivity
            val intent = android.content.Intent(this, UpdateProjectActivity::class.java)
            intent.putExtra("projectId", projectId)
            intent.putExtra("propID", propID)
            intent.putExtra("projectName", projectDetail?.projectName ?: "")
            intent.putExtra("currentStatus", projectDetail?.status ?: "")
            startActivityForResult(intent, 1001)
        }
    }
    
    private fun checkUserJabatan() {
        lifecycleScope.launch {
            try {
                val user = UserService.getCurrentUser()
                val jabatan = user?.jabatan?.lowercase() ?: ""
                
                // Show Update button if jabatan is not 'staff'
                if (jabatan != "staff" && jabatan.isNotEmpty()) {
                    btnUpdate.visibility = View.VISIBLE
                } else {
                    btnUpdate.visibility = View.GONE
                }
            } catch (e: Exception) {
                android.util.Log.e("ProjectDetailActivity", "Error checking user jabatan", e)
                btnUpdate.visibility = View.GONE
            }
        }
    }

    private fun loadProjectDetail() {
        showLoading(true)
        hideError()

        // Build URL with query parameters
        android.util.Log.d("ProjectDetailActivity", "Building URL with projectId: $projectId, propID: $propID")
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("emshotels.net")
            .addPathSegment("apiKu")
            .addPathSegment("project-read.php")
            .addQueryParameter("projectId", projectId)
            .addQueryParameter("propID", propID)
            .build()
        
        android.util.Log.d("ProjectDetailActivity", "Final URL: $url")

        val request = Request.Builder()
            .url(url)
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                handler.post {
                    showLoading(false)
                    showError("Network error: ${e.message}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseBody = response.body?.string() ?: ""
                    handler.post {
                        showLoading(false)

                        if (response.isSuccessful) {
                            try {
                                parseProjectDetail(responseBody)
                            } catch (e: Exception) {
                                android.util.Log.e("ProjectDetailActivity", "Error parsing data", e)
                                showError("Failed to parse data: ${e.message}")
                            }
                        } else {
                            showError("Server error: ${response.code}")
                        }
                    }
                } catch (e: Exception) {
                    handler.post {
                        showLoading(false)
                        android.util.Log.e("ProjectDetailActivity", "Error reading response", e)
                        showError("Error reading response: ${e.message}")
                    }
                }
            }
        })
    }

    private fun parseProjectDetail(jsonString: String) {
        try {
            val json = JSONObject(jsonString)
            val status = json.optString("status", "")

            if (status == "success") {
                val dataDict = json.optJSONObject("data")
                if (dataDict != null) {
                    projectDetail = ProjectDetail.fromJson(dataDict)
                    displayProjectDetail()
                } else {
                    showError("Failed to load project detail")
                }
            } else {
                val message = json.optString("message", "Failed to load project detail")
                showError(message)
            }
        } catch (e: Exception) {
            showError("Failed to parse response: ${e.message}")
        }
    }

    private fun displayProjectDetail() {
        val detail = projectDetail ?: return

        // Project Name
        tvProjectName.text = detail.projectName

        // Location
        if (!detail.lokasi.isNullOrEmpty()) {
            llLocation.visibility = View.VISIBLE
            tvLocation.text = detail.lokasi
        } else {
            llLocation.visibility = View.GONE
        }

        // Category
        if (!detail.category.isNullOrEmpty()) {
            llCategory.visibility = View.VISIBLE
            tvCategory.text = detail.category
        } else {
            llCategory.visibility = View.GONE
        }

        // Priority
        if (!detail.priority.isNullOrEmpty()) {
            llPriority.visibility = View.VISIBLE
            tvPriority.text = detail.priority
            tvPriority.setTextColor(getPriorityColor(detail.priority))
        } else {
            llPriority.visibility = View.GONE
        }

        // Status
        if (!detail.status.isNullOrEmpty()) {
            llStatus.visibility = View.VISIBLE
            tvStatus.text = detail.status.uppercase()
            tvStatus.backgroundTintList = android.content.res.ColorStateList.valueOf(
                getStatusColor(detail.status)
            )
        } else {
            llStatus.visibility = View.GONE
        }

        // Created Date
        if (!detail.createdDate.isNullOrEmpty()) {
            llCreatedDate.visibility = View.VISIBLE
            tvCreatedDate.text = formatDate(detail.createdDate)
        } else {
            llCreatedDate.visibility = View.GONE
        }

        // Created By
        if (!detail.createdBy.isNullOrEmpty()) {
            llCreatedBy.visibility = View.VISIBLE
            tvCreatedBy.text = detail.createdBy
        } else {
            llCreatedBy.visibility = View.GONE
        }

        // Remarks
        if (!detail.remarks.isNullOrEmpty()) {
            llRemarks.visibility = View.VISIBLE
            tvRemarks.text = detail.remarks
        } else {
            llRemarks.visibility = View.GONE
        }

        // Jobs Section
        if (detail.jobs.isNotEmpty()) {
            jobsSection.visibility = View.VISIBLE
            displayJobs(detail.jobs)
        } else {
            jobsSection.visibility = View.GONE
        }

        // Materials Section
        if (detail.materials.isNotEmpty()) {
            materialsSection.visibility = View.VISIBLE
            displayMaterials(detail.materials, detail.totalMaterialCost)
        } else {
            materialsSection.visibility = View.GONE
        }

        contentContainer.visibility = View.VISIBLE
    }

    private fun displayJobs(jobs: List<com.sofindo.ems.models.ProjectJob>) {
        val adapter = object : RecyclerView.Adapter<JobViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): JobViewHolder {
                val view = layoutInflater.inflate(R.layout.item_project_job, parent, false)
                return JobViewHolder(view)
            }

            override fun onBindViewHolder(holder: JobViewHolder, position: Int) {
                // Validate position to prevent IndexOutOfBoundsException
                if (position < 0 || position >= jobs.size) {
                    return
                }
                val job = jobs[position]
                holder.bind(job)
            }

            override fun getItemCount(): Int {
                return try {
                    jobs.size
                } catch (e: Exception) {
                    0
                }
            }
        }

        rvJobs.layoutManager = LinearLayoutManager(this)
        rvJobs.adapter = adapter
    }

    private inner class JobViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvJobDescription: TextView = itemView.findViewById(R.id.tv_job_description)
        private val rvPhotos: RecyclerView = itemView.findViewById(R.id.rv_photos)

        fun bind(job: com.sofindo.ems.models.ProjectJob) {
            tvJobDescription.text = "Job ${job.jobIndex + 1}: ${job.jobDescription}"

            // Filter photos by type: only show "before" photos, and include photoDone if available
            val beforePhotos = job.photos.filter { it.photoType.lowercase() == "before" }

            if (beforePhotos.isNotEmpty()) {
                rvPhotos.visibility = View.VISIBLE
                val photosAdapter = object : RecyclerView.Adapter<PhotoPairViewHolder>() {
                    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoPairViewHolder {
                        val view = layoutInflater.inflate(R.layout.item_project_photo_pair, parent, false)
                        return PhotoPairViewHolder(view)
                    }

                    override fun onBindViewHolder(holder: PhotoPairViewHolder, position: Int) {
                        // Validate position to prevent IndexOutOfBoundsException
                        if (position < 0 || position >= beforePhotos.size) {
                            return
                        }
                        val photo = beforePhotos[position]
                        holder.bind(photo)
                    }

                    override fun getItemCount(): Int {
                        return try {
                            beforePhotos.size
                        } catch (e: Exception) {
                            0
                        }
                    }
                }

                rvPhotos.layoutManager = LinearLayoutManager(this@ProjectDetailActivity, LinearLayoutManager.HORIZONTAL, false)
                rvPhotos.adapter = photosAdapter
            } else {
                rvPhotos.visibility = View.GONE
            }
        }
    }

    private inner class PhotoPairViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivBeforePhoto: ImageView = itemView.findViewById(R.id.iv_before_photo)
        private val ivAfterPhoto: ImageView = itemView.findViewById(R.id.iv_after_photo)

        fun bind(photo: com.sofindo.ems.models.ProjectPhoto) {
            // Display before photo
            if (photo.photo.isNotEmpty()) {
                val cleanPhotoName = photo.photo.split("/").lastOrNull() ?: photo.photo
                val imageUrl = "https://emshotels.net/photo/project/$cleanPhotoName"

                Glide.with(this@ProjectDetailActivity)
                    .load(imageUrl)
                    .placeholder(R.drawable.ic_photo)
                    .error(R.drawable.ic_photo)
                    .centerCrop()
                    .into(ivBeforePhoto)

                ivBeforePhoto.setOnClickListener {
                    val dialog = ImageViewerDialog(this@ProjectDetailActivity, imageUrl)
                    dialog.show()
                }
            }

            // Display photoDone (after photo) if available
            if (!photo.photoDone.isNullOrEmpty()) {
                ivAfterPhoto.visibility = View.VISIBLE
                val cleanPhotoDoneName = photo.photoDone.split("/").lastOrNull() ?: photo.photoDone
                val photoDoneUrl = "https://emshotels.net/photo/project/$cleanPhotoDoneName"

                Glide.with(this@ProjectDetailActivity)
                    .load(photoDoneUrl)
                    .placeholder(R.drawable.ic_photo)
                    .error(R.drawable.ic_photo)
                    .centerCrop()
                    .into(ivAfterPhoto)

                ivAfterPhoto.setOnClickListener {
                    val dialog = ImageViewerDialog(this@ProjectDetailActivity, photoDoneUrl)
                    dialog.show()
                }
            } else {
                ivAfterPhoto.visibility = View.GONE
            }
        }
    }

    private fun displayMaterials(materials: List<com.sofindo.ems.models.ProjectMaterial>, totalCost: Double) {
        llMaterialsList.removeAllViews()

        for (material in materials) {
            val materialView = layoutInflater.inflate(R.layout.item_project_material, null)
            val tvMaterialName: TextView = materialView.findViewById(R.id.tv_material_name)
            val tvMaterialDetail: TextView = materialView.findViewById(R.id.tv_material_detail)
            val tvMaterialAmount: TextView = materialView.findViewById(R.id.tv_material_amount)

            tvMaterialName.text = material.materialName
            tvMaterialDetail.text = "${material.quantity} ${material.unit} × ${formatCurrency(material.unitPrice)}"
            tvMaterialAmount.text = formatCurrency(material.amount)

            llMaterialsList.addView(materialView)
        }

        tvTotalCost.text = formatCurrency(totalCost)
        llTotalCost.visibility = View.VISIBLE
    }

    private fun getStatusColor(status: String): Int {
        return when (status.lowercase()) {
            "active", "on progress", "in progress" -> resources.getColor(R.color.orange, theme)
            "completed", "done", "finished" -> resources.getColor(R.color.green, theme)
            "pending", "waiting" -> resources.getColor(android.R.color.holo_orange_light, theme)
            "cancelled", "canceled" -> resources.getColor(R.color.red, theme)
            else -> resources.getColor(R.color.gray, theme)
        }
    }

    private fun getPriorityColor(priority: String): Int {
        return when (priority.lowercase()) {
            "high" -> resources.getColor(R.color.red, theme)
            "medium" -> resources.getColor(R.color.orange, theme)
            "low" -> resources.getColor(R.color.green, theme)
            else -> resources.getColor(R.color.gray, theme)
        }
    }

    private fun formatDate(dateString: String): String {
        return try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val date = dateFormat.parse(dateString)
            if (date != null) {
                val outputFormat = SimpleDateFormat("dd MMM yyyy - HH:mm", Locale.getDefault())
                outputFormat.format(date)
            } else {
                dateString
            }
        } catch (e: Exception) {
            dateString
        }
    }

    private fun formatCurrency(amount: Double): String {
        val formatter = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
        formatter.maximumFractionDigits = 0
        return formatter.format(amount)
    }

    private fun showLoading(show: Boolean) {
        loadingContainer.visibility = if (show) View.VISIBLE else View.GONE
        contentContainer.visibility = if (show) View.GONE else View.VISIBLE
        scrollView.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun showError(message: String) {
        tvErrorMessage.text = message
        errorContainer.visibility = View.VISIBLE
        contentContainer.visibility = View.GONE
        scrollView.visibility = View.GONE
    }

    private fun hideError() {
        errorContainer.visibility = View.GONE
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            // Reload project detail after update
            loadProjectDetail()
        }
    }
}

