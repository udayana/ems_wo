package com.sofindo.ems.fragments

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.textfield.TextInputEditText
import com.sofindo.ems.R
import com.sofindo.ems.models.User
import com.sofindo.ems.services.SupportService
import com.sofindo.ems.services.UserService
import com.sofindo.ems.utils.PermissionUtils
import kotlinx.coroutines.launch

class SupportFragment : Fragment() {
    
    private lateinit var spinnerCategory: AutoCompleteTextView
    private lateinit var etDescription: TextInputEditText
    private lateinit var btnAddScreenshot: Button
    private lateinit var btnSubmitTicket: Button
    private lateinit var progressBarSubmit: ProgressBar
    private lateinit var cardImagePreview: CardView
    private lateinit var ivScreenshotPreview: ImageView
    private lateinit var btnRemoveScreenshot: ImageView
    
    private var selectedImageUri: Uri? = null
    private var currentUser: User? = null
    private var isSubmitting = false
    
    private val categories = listOf(
        "Technical Issue",
        "Login Problem", 
        "Work Order Issue",
        "File Upload Problem",
        "Performance Issue",
        "Other"
    )
    
    private val categoryValues = listOf(
        "technical",
        "login",
        "workorder", 
        "upload",
        "performance",
        "other"
    )
    
    private val pickImage = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedImageUri = uri
                showImagePreview(uri)
            }
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_support, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initViews(view)
        setupCategorySpinner()
        setupListeners()
        loadUserData()
    }
    
    private fun initViews(view: View) {
        spinnerCategory = view.findViewById(R.id.spinner_category)
        etDescription = view.findViewById(R.id.et_description)
        btnAddScreenshot = view.findViewById(R.id.btn_add_screenshot)
        btnSubmitTicket = view.findViewById(R.id.btn_submit_ticket)
        progressBarSubmit = view.findViewById(R.id.progress_bar_submit)
        cardImagePreview = view.findViewById(R.id.card_image_preview)
        ivScreenshotPreview = view.findViewById(R.id.iv_screenshot_preview)
        btnRemoveScreenshot = view.findViewById(R.id.btn_remove_screenshot)
    }
    
    private fun setupCategorySpinner() {
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, categories)
        spinnerCategory.setAdapter(adapter)
    }
    
    private fun setupListeners() {
        btnAddScreenshot.setOnClickListener {
            // Hide keyboard before showing image source dialog
            hideKeyboard()
            showImageSourceDialog()
        }
        
        btnRemoveScreenshot.setOnClickListener {
            removeScreenshot()
        }
        
        btnSubmitTicket.setOnClickListener {
            submitTicket()
        }
    }
    
    private fun loadUserData() {
        lifecycleScope.launch {
            try {
                currentUser = UserService.getCurrentUser()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to load user data: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun showImageSourceDialog() {
        // Hide keyboard before showing dialog
        hideKeyboard()
        
        AlertDialog.Builder(requireContext())
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
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        pickImage.launch(intent)
    }
    
    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImage.launch(intent)
    }
    
    private fun showImagePreview(uri: Uri) {
        Glide.with(requireContext())
            .load(uri)
            .centerCrop()
            .into(ivScreenshotPreview)
        
        cardImagePreview.visibility = View.VISIBLE
        btnAddScreenshot.visibility = View.GONE
        
        // Hide keyboard after selecting image
        hideKeyboard()
    }
    
    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val currentFocus = requireActivity().currentFocus
        if (currentFocus != null) {
            imm.hideSoftInputFromWindow(currentFocus.windowToken, 0)
        }
    }
    
    private fun removeScreenshot() {
        selectedImageUri = null
        cardImagePreview.visibility = View.GONE
        btnAddScreenshot.visibility = View.VISIBLE
    }
    
    private fun submitTicket() {
        val category = spinnerCategory.text.toString()
        val description = etDescription.text.toString().trim()
        
        // Validation
        if (category.isEmpty()) {
            spinnerCategory.error = "Please select a category"
            return
        }
        
        if (description.isEmpty()) {
            etDescription.error = "Please enter a description"
            return
        }
        
        if (currentUser == null) {
            Toast.makeText(requireContext(), "User data not available", Toast.LENGTH_LONG).show()
            return
        }
        
        // Get category value
        val categoryIndex = categories.indexOf(category)
        val categoryValue = if (categoryIndex >= 0) categoryValues[categoryIndex] else "other"
        
        // Submit ticket
        lifecycleScope.launch {
            try {
                isSubmitting = true
                updateSubmitUI()
                
                var screenshotPath: String? = null
                
                // Upload screenshot if selected
                if (selectedImageUri != null) {
                    val uploadResult = SupportService.uploadSupportAttachment(
                        selectedImageUri!!,
                        requireContext()
                    )
                    
                    if (uploadResult["success"] == true) {
                        // Use the actual file path from response
                        screenshotPath = uploadResult["file_path"]?.toString() ?: "support_attachment.jpg"
                    } else {
                        // Continue without screenshot if upload fails - don't show error to user
                        // The upload might have actually succeeded but response parsing failed
                        screenshotPath = null
                    }
                }
                
                // Submit ticket
                val result = SupportService.submitSupportTicket(
                    name = currentUser!!.fullName ?: currentUser!!.username ?: currentUser!!.email,
                    email = currentUser!!.email,
                    mobileNumber = currentUser!!.phoneNumber,
                    issue = categoryValue,
                    description = description,
                    screenshotPath = screenshotPath
                )
                
                if (result["success"] == true) {
                    // Show success message with checkmark
                    showSuccessMessage()
                    
                    // Reset form
                    resetForm()
                    
                    // Navigate back to profile after 5 seconds
                    lifecycleScope.launch {
                        kotlinx.coroutines.delay(5000)
                        requireActivity().supportFragmentManager.popBackStack()
                    }
                    
                } else {
                    throw Exception(result["message"]?.toString() ?: "Submit failed")
                }
                
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to submit ticket: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isSubmitting = false
                updateSubmitUI()
            }
        }
    }
    
    private fun showSuccessMessage() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_success, null)
        
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()
        
        dialog.show()
        
        // Auto dismiss after 5 seconds
        lifecycleScope.launch {
            kotlinx.coroutines.delay(5000)
            if (dialog.isShowing) {
                dialog.dismiss()
            }
        }
    }
    
    private fun resetForm() {
        spinnerCategory.text.clear()
        etDescription.text?.clear()
        removeScreenshot()
    }
    
    private fun updateSubmitUI() {
        if (isSubmitting) {
            btnSubmitTicket.text = "Submitting ticket..."
            btnSubmitTicket.isEnabled = false
            progressBarSubmit.visibility = View.VISIBLE
        } else {
            btnSubmitTicket.text = "Submit to support team"
            btnSubmitTicket.isEnabled = true
            progressBarSubmit.visibility = View.GONE
        }
    }
}
