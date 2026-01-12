package com.sofindo.ems.fragments

import android.Manifest
import android.app.AlertDialog
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.sofindo.ems.R
import com.sofindo.ems.adapters.MaintenanceAdapter
import com.sofindo.ems.models.Maintenance
import com.sofindo.ems.services.MaintenanceService
import com.sofindo.ems.services.UserService
import com.sofindo.ems.utils.ImageUtils
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

class MaintenanceFragment : Fragment() {
    
    private lateinit var toolbar: Toolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvError: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var btnOpenScanner: MaterialButton
    private lateinit var btnMenu: android.widget.ImageButton
    private lateinit var btnCamera: android.widget.ImageButton
    
    private var maintenanceList: List<Maintenance> = emptyList()
    
    // Camera related
    private var cameraPhotoFile: File? = null
    private var cameraPhotoUri: Uri? = null
    
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
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openCamera()
        } else {
            Toast.makeText(requireContext(), "Camera permission is required", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_maintenance, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initViews(view)
        loadMaintenanceData()
    }
    
    private fun initViews(view: View) {
        toolbar = view.findViewById(R.id.toolbar)
        recyclerView = view.findViewById(R.id.recycler_view)
        progressBar = view.findViewById(R.id.progress_bar)
        tvError = view.findViewById(R.id.tv_error)
        tvEmpty = view.findViewById(R.id.tv_empty)
        btnOpenScanner = view.findViewById(R.id.btn_open_scanner_custom)
        btnMenu = view.findViewById(R.id.btn_menu)
        btnCamera = view.findViewById(R.id.btn_camera)
        
        // Setup Menu Button
        btnMenu.setOnClickListener {
            navigateToAsset()
        }
        
        // Setup Camera Button
        btnCamera.setOnClickListener {
            checkCameraPermission()
        }
        
        // Setup Open Scanner Button
        btnOpenScanner.setOnClickListener {
            openScanner()
        }
        
        // Setup RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(context)
    }
    
    private fun navigateToProfile() {
        val profileFragment = ProfileFragment()
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, profileFragment)
            .addToBackStack(null)
            .commit()
    }
    
    private fun navigateToAsset() {
        val assetFragment = AssetFragment()
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, assetFragment)
            .addToBackStack(null)
            .commit()
    }
    
    private fun loadMaintenanceData() {
        showLoading(true)
        hideError()
        hideEmpty()
        
        lifecycleScope.launch {
            try {
                // Debug: Check if user is logged in
                val currentUser = UserService.getCurrentUser()
                val currentPropID = UserService.getCurrentPropID()
                
                // Check user login status
                
                if (currentUser == null || currentPropID.isNullOrEmpty()) {
                    showError("User not logged in or Property ID not found. Please login again.")
                    return@launch
                }
                
                maintenanceList = MaintenanceService.getMaintenanceThisWeek(requireContext())
                
                if (maintenanceList.isEmpty()) {
                    showEmpty()
                } else {
                    showMaintenanceList()
                }
                
            } catch (e: Exception) {
                showError(e.message ?: "Unknown error occurred")
            } finally {
                showLoading(false)
            }
        }
    }
    
    private fun showMaintenanceList() {
        val adapter = MaintenanceAdapter(maintenanceList)
        
        recyclerView.adapter = adapter
        recyclerView.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE
        tvError.visibility = View.GONE
    }
    
    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }
    
    private fun showError(message: String) {
        tvError.text = message
        tvError.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        tvEmpty.visibility = View.GONE
    }
    
    private fun hideError() {
        tvError.visibility = View.GONE
    }
    
    private fun showEmpty() {
        tvEmpty.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        tvError.visibility = View.GONE
    }
    
    private fun hideEmpty() {
        tvEmpty.visibility = View.GONE
    }
    
    private fun openScanner() {
        // Navigate to QR Scanner Fragment
        val qrScannerFragment = com.sofindo.ems.fragments.QRScannerFragment.newInstance()
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, qrScannerFragment)
            .addToBackStack(null)
            .commit()
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh data when returning to this fragment
        loadMaintenanceData()
    }
    
    // Camera functions
    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) 
            == PackageManager.PERMISSION_GRANTED) {
            openCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    
    private fun openCamera() {
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
    
    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = requireContext().getExternalFilesDir(null)
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
    }
    
    private fun handleCameraImage(file: File) {
        lifecycleScope.launch {
            try {
                // Resize + square‑crop image to 420x420 with 90% quality
                val resizedFile = withContext(Dispatchers.IO) {
                    ImageUtils.resizeAndSquareCropJpegInPlace(file, size = 420, quality = 90)
                }
                
                // Show dialog to input photo name
                withContext(Dispatchers.Main) {
                    showPhotoNameDialog(resizedFile)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Failed to process image: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun showPhotoNameDialog(photoFile: File) {
        val input = EditText(requireContext())
        input.hint = "Enter photo name"
        
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Save Photo")
            .setMessage("Enter a name for this photo:")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val photoName = input.text.toString().trim()
                if (photoName.isNotEmpty()) {
                    // Convert to uppercase
                    savePhotoWithName(photoFile, photoName.uppercase())
                } else {
                    // Use timestamp as default name
                    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    savePhotoWithName(photoFile, "PHOTO_$timeStamp")
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
        
        // Show keyboard and focus on input when dialog is shown
        dialog.setOnShowListener {
            // Change Save button color to green
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.green)
            )
            
            // Post to ensure dialog is fully displayed before requesting focus
            input.post {
                input.requestFocus()
                // Show keyboard
                val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
        }
        
        dialog.show()
    }
    
    private fun savePhotoWithName(sourceFile: File, photoName: String) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Convert to uppercase and only remove truly invalid characters (keep spaces)
                    val sanitizedName = photoName.uppercase().replace(Regex("[^A-Z0-9 ._-]"), "")
                    val fileName = "${sanitizedName}.jpg"
                    
                    // Save to MediaStore (Gallery) for Android 10+ (API 29+)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val contentValues = ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/EMS")
                            put(MediaStore.Images.Media.IS_PENDING, 1)
                        }
                        
                        val uri = requireContext().contentResolver.insert(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            contentValues
                        )
                        
                        uri?.let {
                            requireContext().contentResolver.openOutputStream(it)?.use { outputStream ->
                                sourceFile.inputStream().use { inputStream ->
                                    inputStream.copyTo(outputStream)
                                }
                            }
                            
                            // Mark as not pending so it appears in gallery
                            contentValues.clear()
                            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                            requireContext().contentResolver.update(it, contentValues, null, null)
                            
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    requireContext(),
                                    "Photo saved to Gallery: $fileName",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } ?: throw Exception("Failed to create MediaStore entry")
                    } else {
                        // For Android 9 and below, save to Pictures directory
                        val picturesDir = Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_PICTURES
                        )
                        val emsDir = File(picturesDir, "EMS")
                        if (!emsDir.exists()) {
                            emsDir.mkdirs()
                        }
                        
                        val finalFile = File(emsDir, fileName)
                        
                        // Copy resized file to Pictures/EMS directory
                        sourceFile.inputStream().use { input ->
                            finalFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        
                        // Notify MediaStore about the new file
                        val contentValues = ContentValues().apply {
                            put(MediaStore.Images.Media.DATA, finalFile.absolutePath)
                            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        }
                        requireContext().contentResolver.insert(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            contentValues
                        )
                        
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                requireContext(),
                                "Photo saved to Gallery/Pictures/EMS: $fileName",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(),
                        "Failed to save photo: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    
    private fun calculateInSampleSize(srcW: Int, srcH: Int, reqMaxSide: Int): Int {
        var inSampleSize = 1
        val longest = kotlin.math.max(srcW, srcH)
        while (longest / inSampleSize > reqMaxSide * 2) {
            inSampleSize *= 2
        }
        return inSampleSize
    }
}
    
