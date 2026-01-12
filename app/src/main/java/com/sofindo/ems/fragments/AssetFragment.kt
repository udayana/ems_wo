package com.sofindo.ems.fragments

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import android.widget.EditText
import com.sofindo.ems.R
import com.sofindo.ems.adapters.AssetSearchAdapter
import com.sofindo.ems.services.AssetService
import com.sofindo.ems.services.UserService
import com.sofindo.ems.utils.ImageUtils
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class AssetFragment : Fragment() {
    
    private lateinit var toolbar: Toolbar
    private lateinit var spinnerLokasi: Spinner
    private lateinit var etSearch: EditText
    private lateinit var btnAddAsset: ImageButton
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmpty: TextView
    private lateinit var layoutAssetDetail: LinearLayout
    
    // Detail form views
    private lateinit var ivAssetImage: ImageView
    private lateinit var btnChangeImage: Button
    private lateinit var btnSaveImage: MaterialButton
    private lateinit var etCategory: TextInputEditText
    private lateinit var etLokasi: TextInputEditText
    private lateinit var etProperty: TextInputEditText
    private lateinit var etMerk: TextInputEditText
    private lateinit var etModel: TextInputEditText
    private lateinit var etSerno: TextInputEditText
    private lateinit var etCapacity: TextInputEditText
    private lateinit var etDatePurchased: TextInputEditText
    private lateinit var etSupplier: TextInputEditText
    private lateinit var etKeterangan: TextInputEditText
    private lateinit var btnSave: Button
    private lateinit var btnDelete: Button
    private lateinit var layoutNavigationButtons: LinearLayout
    private lateinit var btnPrevious: MaterialButton
    private lateinit var btnNext: MaterialButton
    
    private var locations: List<String> = emptyList()
    private var searchResults: List<Map<String, Any>> = emptyList()
    private var selectedAsset: Map<String, Any>? = null
    private var assetDetail: Map<String, Any>? = null
    private var selectedImageFile: File? = null
    private var selectedThumbFile: File? = null
    private var currentPropID: String = ""
    private var isNewAsset: Boolean = false
    
    // Navigation state
    private var assetsInSameLocation: List<Map<String, Any>> = emptyList()
    private var currentAssetIndex: Int = -1
    
    private var searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null
    
    // Activity result launchers
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            cameraPhotoFile?.let { file ->
                handleCameraImage(file)
            }
        }
    }
    
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { handleGalleryImage(it) }
    }
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showImageSourceDialog()
        } else {
            Toast.makeText(requireContext(), "Camera permission is required", Toast.LENGTH_SHORT).show()
        }
    }
    
    private var cameraPhotoFile: File? = null
    private var cameraPhotoUri: Uri? = null
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_asset, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initViews(view)
        setupToolbar()
        
        // Get propID in coroutine
        lifecycleScope.launch {
            currentPropID = UserService.getCurrentPropID() ?: ""
            loadLocations()
        }
        
        setupSearch()
    }
    
    private fun initViews(view: View) {
        toolbar = view.findViewById(R.id.toolbar)
        spinnerLokasi = view.findViewById(R.id.spinner_lokasi)
        etSearch = view.findViewById(R.id.et_search)
        btnAddAsset = view.findViewById(R.id.btn_add_asset)
        recyclerView = view.findViewById(R.id.recycler_view)
        progressBar = view.findViewById(R.id.progress_bar)
        tvEmpty = view.findViewById(R.id.tv_empty)
        layoutAssetDetail = view.findViewById(R.id.layout_asset_detail)
        
        // Detail form views
        ivAssetImage = view.findViewById(R.id.iv_asset_image)
        btnChangeImage = view.findViewById(R.id.btn_change_image)
        btnSaveImage = view.findViewById(R.id.btn_save_image)
        etCategory = view.findViewById(R.id.et_category)
        etLokasi = view.findViewById(R.id.et_lokasi)
        etProperty = view.findViewById(R.id.et_property)
        etMerk = view.findViewById(R.id.et_merk)
        etModel = view.findViewById(R.id.et_model)
        etSerno = view.findViewById(R.id.et_serno)
        etCapacity = view.findViewById(R.id.et_capacity)
        etDatePurchased = view.findViewById(R.id.et_date_purchased)
        etSupplier = view.findViewById(R.id.et_supplier)
        etKeterangan = view.findViewById(R.id.et_keterangan)
        btnSave = view.findViewById(R.id.btn_save)
        btnDelete = view.findViewById(R.id.btn_delete)
        layoutNavigationButtons = view.findViewById(R.id.layout_navigation_buttons)
        btnPrevious = view.findViewById(R.id.btn_previous)
        btnNext = view.findViewById(R.id.btn_next)
        
        // Setup navigation buttons
        btnPrevious.setOnClickListener {
            navigateToPreviousAsset()
        }
        btnNext.setOnClickListener {
            navigateToNextAsset()
        }
        
        // Setup RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(context)
        
        // Setup date picker
        etDatePurchased.setOnClickListener {
            showDatePicker()
        }
        
        // Setup change image button
        btnChangeImage.setOnClickListener {
            showImageSourceDialog()
        }
        
        // Setup save image button (separate from save data)
        btnSaveImage.setOnClickListener {
            uploadPhotoOnly()
        }
        
        // Setup save button (for data only, no photo)
        btnSave.setOnClickListener {
            saveAsset()
        }

        // Setup add asset button
        btnAddAsset.setOnClickListener {
            showNewAssetForm()
        }

        // Setup delete button
        btnDelete.setOnClickListener {
            confirmAndDeleteAsset()
        }
    }
    
    private fun setupToolbar() {
        toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }
    
    private suspend fun loadLocations() {
        if (currentPropID.isEmpty()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "Property ID not found", Toast.LENGTH_SHORT).show()
            }
            return
        }
        
        try {
            showLoading(true)
            locations = AssetService.getInventoryLocations(currentPropID)
            setupLocationSpinner()
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "Error loading locations: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } finally {
            showLoading(false)
        }
    }
    
    private fun setupLocationSpinner() {
        val allLocations = listOf("All Locations") + locations
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, allLocations)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerLokasi.adapter = adapter
        
        spinnerLokasi.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, @Suppress("UNUSED_PARAMETER") view: View?, position: Int, id: Long) {
                if (etSearch.text?.toString()?.length ?: 0 >= 2) {
                    performSearch()
                }
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
    
    private fun setupSearch() {
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val searchText = s?.toString() ?: ""
                
                // Cancel previous search
                searchRunnable?.let { searchHandler.removeCallbacks(it) }

                // Khusus: jika user ketik "*" maka tetap lakukan search (all assets di lokasi terpilih)
                val isShowAllRequest = searchText.trim() == "*"

                if (isShowAllRequest || searchText.length >= 2) {
                    // Debounce search - wait 500ms after user stops typing
                    searchRunnable = Runnable {
                        performSearch()
                    }
                    searchHandler.postDelayed(searchRunnable!!, 500)
                } else {
                    hideSearchResults()
                }
            }
        })
    }
    
    private fun performSearch() {
        val searchText = etSearch.text?.toString()?.trim() ?: ""
        if (currentPropID.isEmpty()) {
            return
        }

        // Izinkan "*" sebagai permintaan untuk menampilkan semua asset pada lokasi terpilih,
        // sehingga tidak terkena batas minimal 2 karakter.
        if (searchText != "*" && searchText.length < 2) {
            return
        }
        
        val selectedLocation = spinnerLokasi.selectedItem?.toString()
        val locationFilter = if (selectedLocation == "All Locations" || selectedLocation == null) {
            null
        } else {
            selectedLocation
        }
        
        lifecycleScope.launch {
            try {
                showLoading(true)
                searchResults = AssetService.searchInventory(currentPropID, searchText, locationFilter)
                showSearchResults()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error searching: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                hideSearchResults()
            } finally {
                showLoading(false)
            }
        }
    }
    
    private fun showSearchResults() {
        if (searchResults.isEmpty()) {
            tvEmpty.text = "No assets found"
            tvEmpty.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            tvEmpty.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            
            val adapter = AssetSearchAdapter(searchResults) { asset ->
                onAssetSelected(asset)
            }
            recyclerView.adapter = adapter
        }
        layoutAssetDetail.visibility = View.GONE
    }
    
    private fun hideSearchResults() {
        tvEmpty.text = "Type at least 2 characters to search"
        tvEmpty.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        layoutAssetDetail.visibility = View.GONE
    }
    
    private fun onAssetSelected(asset: Map<String, Any>) {
        selectedAsset = asset
        
        // Reset navigation state saat memilih asset baru dari search
        assetsInSameLocation = emptyList()
        currentAssetIndex = -1
        layoutNavigationButtons.visibility = View.GONE
        
        // Get No from search result - handle different data types and key variations
        val noValue = asset["No"] ?: asset["no"] ?: asset.get("No")
        val assetNo: Int? = when (noValue) {
            is Int -> noValue
            is Double -> noValue.toInt()
            is Number -> noValue.toInt()
            is String -> noValue.toIntOrNull()
            else -> noValue?.toString()?.toIntOrNull()
        }
        
        if (assetNo == null) {
            // Debug: log what we received
            android.util.Log.e("AssetFragment", "Asset data: $asset")
            android.util.Log.e("AssetFragment", "No value: $noValue (type: ${noValue?.javaClass?.simpleName})")
            android.util.Log.e("AssetFragment", "Available keys: ${asset.keys.joinToString()}")
            
            // Try to show more helpful error
            val errorMsg = "Asset No not found. Available keys: ${asset.keys.joinToString()}"
            Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_LONG).show()
            return
        }
        
        lifecycleScope.launch {
            try {
                showLoading(true)
                
                // Use No (primary key) directly - simple and reliable
                val detail = AssetService.getInventoryDetail(no = assetNo)
                showAssetDetail(detail)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error loading asset detail: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                showLoading(false)
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh image when fragment is resumed to show latest image from server
        assetDetail?.let { asset ->
            val imageName = asset["Gambar"]?.toString()
            if (!imageName.isNullOrEmpty() && layoutAssetDetail.visibility == View.VISIBLE) {
                // Force refresh image when returning to fragment - always get latest from server
                loadAssetImage(imageName, forceRefresh = true)
            }
        }
    }
    
    private fun showAssetDetail(asset: Map<String, Any>, forceRefreshImage: Boolean = false) {
        // Save the detail for later use
        assetDetail = asset
        isNewAsset = false
        
        // Hide search results
        recyclerView.visibility = View.GONE
        tvEmpty.visibility = View.GONE
        
        // Show detail form
        layoutAssetDetail.visibility = View.VISIBLE
        
        // Hide keyboard first
        hideKeyboard()
        
        // Request focus on the layout to prevent auto-focus on input fields
        layoutAssetDetail.requestFocus()
        
        // Fill form fields (without triggering focus)
        etCategory.setText(asset["Category"]?.toString() ?: "")
        etLokasi.setText(asset["Lokasi"]?.toString() ?: "")
        etProperty.setText(asset["Property"]?.toString() ?: "")
        etMerk.setText(asset["Merk"]?.toString() ?: "")
        etModel.setText(asset["Model"]?.toString() ?: "")
        etSerno.setText(asset["serno"]?.toString() ?: "")
        etCapacity.setText(asset["Capacity"]?.toString() ?: "")
        
        val datePurchased = asset["DatePurchased"]?.toString()
        etDatePurchased.setText(datePurchased ?: "")
        
        etSupplier.setText(asset["Suplier"]?.toString() ?: "")
        etKeterangan.setText(asset["Keterangan"]?.toString() ?: "")
        
        // Clear focus from all input fields to prevent keyboard
        etCategory.clearFocus()
        etLokasi.clearFocus()
        etProperty.clearFocus()
        etMerk.clearFocus()
        etModel.clearFocus()
        etSerno.clearFocus()
        etCapacity.clearFocus()
        etDatePurchased.clearFocus()
        etSupplier.clearFocus()
        etKeterangan.clearFocus()
        
        // Load image from correct path with cache busting if forceRefreshImage is true
        val imageName = asset["Gambar"]?.toString()
        if (!imageName.isNullOrEmpty()) {
            loadAssetImage(imageName, forceRefreshImage)
        } else {
            ivAssetImage.setImageResource(R.drawable.photo_preview_background)
        }
        
        selectedImageFile = null
        selectedThumbFile = null
        
        // Hide Save Image button when showing asset detail
        btnSaveImage.visibility = View.GONE

        // Delete hanya untuk asset yang sudah ada
        btnDelete.visibility = View.VISIBLE
        
        // Sembunyikan tombol navigasi saat pertama kali buka (belum save/upload)
        layoutNavigationButtons.visibility = View.GONE
    }

    private fun showNewAssetForm() {
        isNewAsset = true
        assetDetail = null
        selectedAsset = null
        
        // Reset navigation state
        assetsInSameLocation = emptyList()
        currentAssetIndex = -1
        layoutNavigationButtons.visibility = View.GONE

        // Sembunyikan list & empty state
        recyclerView.visibility = View.GONE
        tvEmpty.visibility = View.GONE

        // Tampilkan form kosong
        layoutAssetDetail.visibility = View.VISIBLE
        hideKeyboard()
        layoutAssetDetail.requestFocus()

        // Prefill lokasi dari spinner (jika bukan All Locations)
        val selectedLocation = spinnerLokasi.selectedItem?.toString()
        val lokasiPrefill = if (selectedLocation != null && selectedLocation != "All Locations") {
            selectedLocation
        } else {
            ""
        }

        // Prefill property dari teks search (jika bukan "*" dan panjang >= 2)
        val searchText = etSearch.text?.toString()?.trim().orEmpty()
        val propertyPrefill = if (searchText.isNotEmpty() && searchText != "*" && searchText.length >= 2) {
            searchText
        } else {
            ""
        }

        etCategory.setText("")
        etLokasi.setText(lokasiPrefill)
        etProperty.setText(propertyPrefill)
        etMerk.setText("")
        etModel.setText("")
        etSerno.setText("")
        etCapacity.setText("")
        etDatePurchased.setText("")
        etSupplier.setText("")
        etKeterangan.setText("")

        ivAssetImage.setImageResource(R.drawable.photo_preview_background)
        selectedImageFile = null
        selectedThumbFile = null

        // Untuk asset baru, upload photo baru bisa dilakukan setelah No terbentuk (setelah insert)
        btnSaveImage.visibility = View.GONE
        btnDelete.visibility = View.GONE
    }
    
    private fun loadAssetImage(imageName: String, forceRefresh: Boolean = false) {
        val imageUrl = "https://emshotels.net/admin/pages/maintenance/photo/$imageName"
        
        if (forceRefresh) {
            // Clear current image view first
            Glide.with(this).clear(ivAssetImage)
            
            // Clear Glide cache before loading new image
            Glide.get(requireContext()).clearMemory()
            Thread {
                Glide.get(requireContext()).clearDiskCache()
            }.start()
            
            // Wait a bit for cache clearing, then load with cache busting
            Handler(Looper.getMainLooper()).postDelayed({
                // Add timestamp for cache busting
                val urlWithTimestamp = "$imageUrl?t=${System.currentTimeMillis()}"
                
                // Load with skip cache options - FORCE NO CACHE
                val requestOptions = RequestOptions()
                    .skipMemoryCache(true) // Skip memory cache
                    .diskCacheStrategy(DiskCacheStrategy.NONE) // Skip disk cache completely
                    .placeholder(R.drawable.photo_preview_background)
                    .error(R.drawable.photo_preview_background)
                
                Glide.with(this)
                    .load(urlWithTimestamp)
                    .apply(requestOptions)
                    .into(ivAssetImage)
            }, 500) // Longer delay to ensure cache is cleared
        } else {
            // Load with cache busting even for normal load to ensure fresh image
            val urlWithTimestamp = "$imageUrl?t=${System.currentTimeMillis()}"
            val requestOptions = RequestOptions()
                .skipMemoryCache(false) // Allow memory cache for normal load
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC) // Use disk cache but allow refresh
                .placeholder(R.drawable.photo_preview_background)
                .error(R.drawable.photo_preview_background)
            
            Glide.with(this)
                .load(urlWithTimestamp)
                .apply(requestOptions)
                .into(ivAssetImage)
        }
    }
    
    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        
        DatePickerDialog(requireContext(), { _, selectedYear, selectedMonth, selectedDay ->
            val date = String.format("%04d-%02d-%02d", selectedYear, selectedMonth + 1, selectedDay)
            etDatePurchased.setText(date)
        }, year, month, day).show()
    }
    
    private fun showImageSourceDialog() {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Select Photo Source")
            .setItems(arrayOf("Camera", "Photo Gallery")) { _, which ->
                when (which) {
                    0 -> openCamera()
                    1 -> openGallery()
                }
            }
            .show()
    }
    
    private fun openCamera() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }
        
        try {
            cameraPhotoFile = createImageFile()
            cameraPhotoUri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                cameraPhotoFile!!
            )
            cameraLauncher.launch(cameraPhotoUri)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Failed to open camera: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun openGallery() {
        galleryLauncher.launch("image/*")
    }
    
    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = requireContext().getExternalFilesDir(null)
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
    }
    
    private fun handleCameraImage(file: File) {
        lifecycleScope.launch {
            try {
                // Show preview immediately from original file FIRST (for instant feedback)
                withContext(Dispatchers.Main) {
                    updateImagePreview(file)
                    // Show Save Image button immediately
                    btnSaveImage.visibility = View.VISIBLE
                }
                
                // Then resize + square‑crop in background (non-blocking)
                val resizedFile = withContext(Dispatchers.IO) {
                    ImageUtils.resizeAndSquareCropJpegInPlace(file, size = 420, quality = 85)
                }
                selectedImageFile = resizedFile
                
                // Create thumbnail 100x100px - do in background
                val thumbFile = withContext(Dispatchers.IO) {
                    createThumbnail(resizedFile)
                }
                selectedThumbFile = thumbFile
                
                // Update preview with resized file on main thread
                withContext(Dispatchers.Main) {
                    updateImagePreview(resizedFile)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Failed to process image: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun handleGalleryImage(uri: Uri) {
        lifecycleScope.launch {
            try {
                // Copy file from gallery - do in background
                val tempFile = withContext(Dispatchers.IO) {
                    val file = createImageFile()
                    val outputStream = FileOutputStream(file)
                    
                    requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                    outputStream.close()
                    file
                }
                
                // Show preview immediately from copied file FIRST (for instant feedback)
                withContext(Dispatchers.Main) {
                    updateImagePreview(tempFile)
                    // Show Save Image button immediately
                    btnSaveImage.visibility = View.VISIBLE
                }
                
                // Then resize + square‑crop in background (non-blocking)
                val resizedFile = withContext(Dispatchers.IO) {
                    ImageUtils.resizeAndSquareCropJpegInPlace(tempFile, size = 420, quality = 85)
                }
                selectedImageFile = resizedFile
                
                // Create thumbnail 100x100px - do in background
                val thumbFile = withContext(Dispatchers.IO) {
                    createThumbnail(resizedFile)
                }
                selectedThumbFile = thumbFile
                
                // Update preview with resized file on main thread
                withContext(Dispatchers.Main) {
                    updateImagePreview(resizedFile)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Failed to load image: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun createThumbnail(sourceFile: File): File {
        val thumbFile = File(sourceFile.parent, "thumb_${sourceFile.name}")
        
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(sourceFile.absolutePath, bounds)
        val srcW = bounds.outWidth
        val srcH = bounds.outHeight
        if (srcW <= 0 || srcH <= 0) return sourceFile
        
        // Calculate thumbnail dimensions (100x100px proportional)
        val thumbSize = 100
        val scale = kotlin.math.min(thumbSize.toFloat() / srcW, thumbSize.toFloat() / srcH)
        val thumbWidth = (srcW * scale).toInt()
        val thumbHeight = (srcH * scale).toInt()
        
        // Decode and resize (simple decode, thumbnail only)
        val decodeOpts = BitmapFactory.Options().apply {
            inJustDecodeBounds = false
            inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
        }
        
        val bmp = BitmapFactory.decodeFile(sourceFile.absolutePath, decodeOpts) ?: return sourceFile
        val thumbBmp = android.graphics.Bitmap.createScaledBitmap(bmp, thumbWidth, thumbHeight, true)
        
        // Save thumbnail
        FileOutputStream(thumbFile, false).use { out ->
            thumbBmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
            out.flush()
        }
        
        bmp.recycle()
        thumbBmp.recycle()
        
        return thumbFile
    }
    
    private fun updateImagePreview(file: File) {
        // Verify file exists
        if (!file.exists()) {
            android.util.Log.e("AssetFragment", "Preview file does not exist: ${file.absolutePath}")
            return
        }
        
        // Clear current image first to ensure new image is displayed
        Glide.with(this).clear(ivAssetImage)
        
        // Load immediately using Bitmap for INSTANT preview (no delay)
        try {
            // Decode bitmap with lower sample size for faster loading
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = false
                inSampleSize = 2 // Load at half resolution for instant preview
                inPreferredConfig = android.graphics.Bitmap.Config.RGB_565 // Faster decode
            }
            
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, options)
            if (bitmap != null) {
                // Set bitmap directly for INSTANT preview - no delay!
                ivAssetImage.setImageBitmap(bitmap)
            } else {
                // Fallback: try with Glide
                val requestOptions = RequestOptions()
                    .skipMemoryCache(true)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .placeholder(R.drawable.photo_preview_background)
                    .error(R.drawable.photo_preview_background)
                
                Glide.with(this)
                    .load(file)
                    .apply(requestOptions)
                    .into(ivAssetImage)
            }
        } catch (e: Exception) {
            android.util.Log.e("AssetFragment", "Error loading preview: ${e.message}")
            // Fallback to Glide
            val requestOptions = RequestOptions()
                .skipMemoryCache(true)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .placeholder(R.drawable.photo_preview_background)
                .error(R.drawable.photo_preview_background)
            
            Glide.with(this)
                .load(file)
                .apply(requestOptions)
                .into(ivAssetImage)
        }
    }
    
    private fun saveAsset() {
        if (currentPropID.isEmpty()) {
            Toast.makeText(requireContext(), "Property ID not found", Toast.LENGTH_SHORT).show()
            return
        }

        val category = etCategory.text?.toString() ?: ""
        val lokasi = etLokasi.text?.toString()
        val property = etProperty.text?.toString()
        val merk = etMerk.text?.toString()
        val model = etModel.text?.toString()
        val serno = etSerno.text?.toString() ?: ""
        val capacity = etCapacity.text?.toString()
        val datePurchased = etDatePurchased.text?.toString()
        val suplier = etSupplier.text?.toString() ?: ""
        val keterangan = etKeterangan.text?.toString() ?: ""

        lifecycleScope.launch {
            try {
                showLoading(true)
                btnSave.isEnabled = false

                if (isNewAsset) {
                    // INSERT asset baru
                    val response = AssetService.insertInventory(
                        propID = currentPropID,
                        category = category,
                        lokasi = lokasi,
                        property = property,
                        merk = merk,
                        model = model,
                        serno = serno,
                        capacity = capacity,
                        datePurchased = datePurchased,
                        suplier = suplier,
                        keterangan = keterangan
                    )

                    val newNo = (response["No"] as? Number)?.toInt()
                        ?: response["No"]?.toString()?.toIntOrNull()

                    if (newNo != null) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), "Asset created successfully!", Toast.LENGTH_SHORT).show()
                        }
                        val detail = AssetService.getInventoryDetail(no = newNo)
                        showAssetDetail(detail)
                        // Load assets in same location untuk navigasi
                        loadAssetsInSameLocation()
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), "Asset created but No not returned by server", Toast.LENGTH_LONG).show()
                            // Tetap sembunyikan form agar user tidak bingung
                            layoutAssetDetail.visibility = View.GONE
                            tvEmpty.text = "Asset created"
                            tvEmpty.visibility = View.VISIBLE
                        }
                    }
                } else {
                    // UPDATE asset yang sudah ada
                    val asset = assetDetail ?: return@launch
                    val assetNo = (asset["No"] as? Number)?.toInt()
                        ?: (asset["No"]?.toString()?.toIntOrNull())
                        ?: return@launch

                    val tgl = asset["tgl"]?.toString()
                        ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                    val mntld = asset["mntld"]?.toString()

                    AssetService.updateInventory(
                        no = assetNo,
                        propID = currentPropID,
                        tgl = tgl,
                        mntld = mntld,
                        category = category,
                        lokasi = lokasi,
                        property = property,
                        merk = merk,
                        model = model,
                        serno = serno,
                        capacity = capacity,
                        datePurchased = datePurchased,
                        suplier = suplier,
                        keterangan = keterangan
                    )

                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Asset data updated successfully!", Toast.LENGTH_SHORT).show()
                    }

                    val refreshedDetail = AssetService.getInventoryDetail(no = assetNo)
                    showAssetDetail(refreshedDetail)
                    // Load assets in same location untuk navigasi
                    loadAssetsInSameLocation()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error saving asset: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                showLoading(false)
                btnSave.isEnabled = true
            }
        }
    }
    
    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }
    
    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        val view = activity?.currentFocus
        if (view != null) {
            imm.hideSoftInputFromWindow(view.windowToken, 0)
            view.clearFocus()
        }
    }
    
    private fun uploadPhotoOnly() {
        val asset = assetDetail ?: return
        val assetNo = (asset["No"] as? Number)?.toInt() 
            ?: (asset["No"]?.toString()?.toIntOrNull())
            ?: return
        
        val imageFile = selectedImageFile
        val thumbFile = selectedThumbFile
        
        if (imageFile == null || !imageFile.exists()) {
            Toast.makeText(requireContext(), "No photo selected", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch {
            try {
                btnSaveImage.isEnabled = false
                btnSaveImage.text = "Uploading..."
                
                AssetService.uploadInventoryPhoto(
                    no = assetNo,
                    photoFile = imageFile,
                    thumbFile = thumbFile
                )
                
                // Cleanup temporary thumbnail file after upload
                thumbFile?.let {
                    try {
                        if (it.exists()) {
                            it.delete()
                        }
                    } catch (e: Exception) {
                        // Ignore cleanup errors
                    }
                }
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Photo uploaded successfully!", Toast.LENGTH_SHORT).show()
                }
                
                // Wait a moment for server to save the file, then refresh
                delay(500)
                
                // Clear Glide cache to force refresh of image
                withContext(Dispatchers.Main) {
                    Glide.with(requireContext()).clear(ivAssetImage)
                    Glide.get(requireContext()).clearMemory()
                }
                Thread {
                    Glide.get(requireContext()).clearDiskCache()
                }.start()
                
                // Wait for cache clearing
                delay(500)
                
                // Refresh the detail to show new photo with cache busting
                val refreshedDetail = AssetService.getInventoryDetail(no = assetNo)
                showAssetDetail(refreshedDetail, forceRefreshImage = true)
                
                // Load assets in same location untuk navigasi
                loadAssetsInSameLocation()
                
                // Hide Save Image button after successful upload
                withContext(Dispatchers.Main) {
                    btnSaveImage.visibility = View.GONE
                }
                
                // Clear selected files after successful upload
                selectedImageFile = null
                selectedThumbFile = null
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error uploading photo: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                btnSaveImage.isEnabled = true
                btnSaveImage.text = "UPLOAD"
            }
        }
    }

    /**
     * Load semua asset di lokasi yang sama dengan asset saat ini
     * Dipanggil setelah save/upload/create berhasil
     */
    private fun loadAssetsInSameLocation() {
        val asset = assetDetail ?: return
        val lokasi = asset["Lokasi"]?.toString()?.trim()
        
        // Jika lokasi kosong, sembunyikan tombol navigasi
        if (lokasi.isNullOrEmpty()) {
            layoutNavigationButtons.visibility = View.GONE
            return
        }
        
        lifecycleScope.launch {
            try {
                // Search dengan wildcard "*" dan filter lokasi
                val assets = AssetService.searchInventory(
                    propID = currentPropID,
                    searchText = "*",
                    lokasi = lokasi
                )
                
                assetsInSameLocation = assets
                
                // Cari index asset saat ini
                val currentAssetNo = (asset["No"] as? Number)?.toInt()
                    ?: (asset["No"]?.toString()?.toIntOrNull())
                
                if (currentAssetNo != null) {
                    currentAssetIndex = assetsInSameLocation.indexOfFirst { assetItem ->
                        val itemNo = (assetItem["No"] as? Number)?.toInt()
                            ?: (assetItem["No"]?.toString()?.toIntOrNull())
                        itemNo == currentAssetNo
                    }
                } else {
                    currentAssetIndex = -1
                }
                
                // Update tombol navigasi
                updateNavigationButtons()
            } catch (e: Exception) {
                android.util.Log.e("AssetFragment", "Error loading assets in same location: ${e.message}")
                // Jika error, sembunyikan tombol navigasi
                layoutNavigationButtons.visibility = View.GONE
            }
        }
    }
    
    /**
     * Update state tombol Previous & Next berdasarkan currentAssetIndex
     */
    private fun updateNavigationButtons() {
        // Jika hanya ada 1 asset atau kurang, sembunyikan tombol
        if (assetsInSameLocation.size <= 1) {
            layoutNavigationButtons.visibility = View.GONE
            return
        }
        
        // Tampilkan tombol navigasi
        layoutNavigationButtons.visibility = View.VISIBLE
        
        // Update Previous button
        val canGoPrevious = currentAssetIndex > 0
        btnPrevious.isEnabled = canGoPrevious
        btnPrevious.alpha = if (canGoPrevious) 1.0f else 0.5f
        
        // Update Next button
        val canGoNext = currentAssetIndex >= 0 && currentAssetIndex < assetsInSameLocation.size - 1
        btnNext.isEnabled = canGoNext
        btnNext.alpha = if (canGoNext) 1.0f else 0.5f
    }
    
    /**
     * Navigate ke asset sebelumnya
     */
    private fun navigateToPreviousAsset() {
        if (currentAssetIndex <= 0 || assetsInSameLocation.isEmpty()) {
            return
        }
        
        currentAssetIndex--
        val previousAsset = assetsInSameLocation[currentAssetIndex]
        
        // Load detail asset sebelumnya
        lifecycleScope.launch {
            try {
                showLoading(true)
                
                val noValue = previousAsset["No"] ?: previousAsset["no"]
                val assetNo: Int? = when (noValue) {
                    is Int -> noValue
                    is Double -> noValue.toInt()
                    is Number -> noValue.toInt()
                    is String -> noValue.toIntOrNull()
                    else -> noValue?.toString()?.toIntOrNull()
                }
                
                if (assetNo != null) {
                    val detail = AssetService.getInventoryDetail(no = assetNo)
                    showAssetDetailWithoutLoadingList(detail)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error loading previous asset: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                showLoading(false)
            }
        }
    }
    
    /**
     * Navigate ke asset berikutnya
     */
    private fun navigateToNextAsset() {
        if (currentAssetIndex < 0 || currentAssetIndex >= assetsInSameLocation.size - 1) {
            return
        }
        
        currentAssetIndex++
        val nextAsset = assetsInSameLocation[currentAssetIndex]
        
        // Load detail asset berikutnya
        lifecycleScope.launch {
            try {
                showLoading(true)
                
                val noValue = nextAsset["No"] ?: nextAsset["no"]
                val assetNo: Int? = when (noValue) {
                    is Int -> noValue
                    is Double -> noValue.toInt()
                    is Number -> noValue.toInt()
                    is String -> noValue.toIntOrNull()
                    else -> noValue?.toString()?.toIntOrNull()
                }
                
                if (assetNo != null) {
                    val detail = AssetService.getInventoryDetail(no = assetNo)
                    showAssetDetailWithoutLoadingList(detail)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error loading next asset: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                showLoading(false)
            }
        }
    }
    
    /**
     * Show asset detail tanpa reload assetsInSameLocation (untuk navigasi)
     * Sama seperti showAssetDetail() tapi preserve navigation state
     */
    private fun showAssetDetailWithoutLoadingList(asset: Map<String, Any>, forceRefreshImage: Boolean = false) {
        // Save the detail for later use
        assetDetail = asset
        isNewAsset = false
        
        // Hide search results
        recyclerView.visibility = View.GONE
        tvEmpty.visibility = View.GONE
        
        // Show detail form
        layoutAssetDetail.visibility = View.VISIBLE
        
        // Hide keyboard first
        hideKeyboard()
        
        // Request focus on the layout to prevent auto-focus on input fields
        layoutAssetDetail.requestFocus()
        
        // Fill form fields (without triggering focus)
        etCategory.setText(asset["Category"]?.toString() ?: "")
        etLokasi.setText(asset["Lokasi"]?.toString() ?: "")
        etProperty.setText(asset["Property"]?.toString() ?: "")
        etMerk.setText(asset["Merk"]?.toString() ?: "")
        etModel.setText(asset["Model"]?.toString() ?: "")
        etSerno.setText(asset["serno"]?.toString() ?: "")
        etCapacity.setText(asset["Capacity"]?.toString() ?: "")
        
        val datePurchased = asset["DatePurchased"]?.toString()
        etDatePurchased.setText(datePurchased ?: "")
        
        etSupplier.setText(asset["Suplier"]?.toString() ?: "")
        etKeterangan.setText(asset["Keterangan"]?.toString() ?: "")
        
        // Clear focus from all input fields to prevent keyboard
        etCategory.clearFocus()
        etLokasi.clearFocus()
        etProperty.clearFocus()
        etMerk.clearFocus()
        etModel.clearFocus()
        etSerno.clearFocus()
        etCapacity.clearFocus()
        etDatePurchased.clearFocus()
        etSupplier.clearFocus()
        etKeterangan.clearFocus()
        
        // Load image from correct path with cache busting if forceRefreshImage is true
        val imageName = asset["Gambar"]?.toString()
        if (!imageName.isNullOrEmpty()) {
            loadAssetImage(imageName, forceRefreshImage)
        } else {
            ivAssetImage.setImageResource(R.drawable.photo_preview_background)
        }
        
        selectedImageFile = null
        selectedThumbFile = null
        
        // Hide Save Image button when showing asset detail
        btnSaveImage.visibility = View.GONE
        
        // Delete hanya untuk asset yang sudah ada
        btnDelete.visibility = View.VISIBLE
        
        // Update index dan tombol navigasi (tanpa reload list)
        val currentAssetNo = (asset["No"] as? Number)?.toInt()
            ?: (asset["No"]?.toString()?.toIntOrNull())
        
        if (currentAssetNo != null && assetsInSameLocation.isNotEmpty()) {
            currentAssetIndex = assetsInSameLocation.indexOfFirst { assetItem ->
                val itemNo = (assetItem["No"] as? Number)?.toInt()
                    ?: (assetItem["No"]?.toString()?.toIntOrNull())
                itemNo == currentAssetNo
            }
        }
        
        updateNavigationButtons()
    }
    
    private fun confirmAndDeleteAsset() {
        if (isNewAsset) {
            // Asset baru belum tersimpan, cukup tutup form
            layoutAssetDetail.visibility = View.GONE
            tvEmpty.text = "Asset creation cancelled"
            tvEmpty.visibility = View.VISIBLE
            return
        }

        val asset = assetDetail ?: return
        val assetNo = (asset["No"] as? Number)?.toInt()
            ?: (asset["No"]?.toString()?.toIntOrNull())
            ?: return

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete Asset")
            .setMessage("Are you sure you want to delete this asset?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    try {
                        showLoading(true)
                        AssetService.deleteInventory(no = assetNo)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), "Asset deleted successfully", Toast.LENGTH_SHORT).show()

                            // Sembunyikan detail & refresh state
                            assetDetail = null
                            layoutAssetDetail.visibility = View.GONE
                            recyclerView.visibility = View.GONE
                            tvEmpty.text = "Asset deleted"
                            tvEmpty.visibility = View.VISIBLE
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), "Error deleting asset: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    } finally {
                        showLoading(false)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
