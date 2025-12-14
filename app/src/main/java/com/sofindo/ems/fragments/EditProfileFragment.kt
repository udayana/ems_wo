package com.sofindo.ems.fragments

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.sofindo.ems.R
import com.sofindo.ems.models.User
import com.sofindo.ems.services.UserService
import com.sofindo.ems.services.ProfileService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditProfileFragment : Fragment() {
    
    private lateinit var ivProfile: ImageView
    private lateinit var etFullName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPhone: EditText
    private lateinit var btnUploadPhoto: Button
    private lateinit var btnSaveChanges: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var progressBarSave: ProgressBar
    private lateinit var tvTapToChange: TextView
    
    private var selectedImageUri: Uri? = null
    private var currentUser: User? = null
    private var isUploading = false
    private var isSaving = false
    
    private val pickImage = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val intentData = result.data
            val selectedUri = intentData?.data ?: intentData?.clipData?.getItemAt(0)?.uri
            selectedUri?.let { uri ->
                selectedImageUri = uri
                loadSelectedImage(uri)
                btnUploadPhoto.visibility = View.VISIBLE
            }
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_edit_profile, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initViews(view)
        setupListeners()
        loadUserData()
    }
    
    private fun initViews(view: View) {
        ivProfile = view.findViewById(R.id.iv_profile)
        etFullName = view.findViewById(R.id.et_full_name)
        etEmail = view.findViewById(R.id.et_email)
        etPhone = view.findViewById(R.id.et_phone)
        btnUploadPhoto = view.findViewById(R.id.btn_upload_photo)
        btnSaveChanges = view.findViewById(R.id.btn_save_changes)
        progressBar = view.findViewById(R.id.progress_bar)
        progressBarSave = view.findViewById(R.id.progress_bar_save)
        tvTapToChange = view.findViewById(R.id.tv_tap_to_change)
    }
    
    private fun setupListeners() {
        ivProfile.setOnClickListener {
            openImagePicker()
        }
        
        btnUploadPhoto.setOnClickListener {
            uploadProfilePhoto()
        }
        
        btnSaveChanges.setOnClickListener {
            saveProfileChanges()
        }
    }
    
    private fun loadUserData() {
        lifecycleScope.launch {
            try {
                currentUser = UserService.getCurrentUser()
                
                if (currentUser != null) {
                    // Load user data from local storage
                    etFullName.setText(currentUser!!.fullName ?: "")
                    etEmail.setText(currentUser!!.email)
                    etPhone.setText(currentUser!!.phoneNumber ?: "")
                    
                    // Load profile image
                    if (!currentUser!!.profileImage.isNullOrEmpty()) {
                        Glide.with(requireContext())
                            .load(getProfileImageUrl(currentUser!!.profileImage!!) + "?t=" + System.currentTimeMillis())
                            .placeholder(R.drawable.ic_person)
                            .error(R.drawable.ic_person)
                            .circleCrop()
                            .into(ivProfile)
                    } else {
                        ivProfile.setImageResource(R.drawable.ic_person)
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to load user data: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        pickImage.launch(Intent.createChooser(intent, "Select Photo"))
    }
    
    private fun loadSelectedImage(uri: Uri) {
        Glide.with(requireContext())
            .load(uri)
            .circleCrop()
            .into(ivProfile)
    }
    
    private fun uploadProfilePhoto() {
        if (selectedImageUri == null || currentUser == null) return
        
        lifecycleScope.launch {
            try {
                isUploading = true
                updateUploadUI()
                
                // Upload photo to server
                val result = ProfileService.uploadProfilePhoto(
                    currentUser!!.id,
                    selectedImageUri!!,
                    requireContext()
                )
                
                if (result["success"] == true) {
                    // Update local user data with the correct filename format
                    val updatedUser = currentUser!!.copy(
                        profileImage = "USER_${currentUser!!.id}.jpg"
                    )
                    UserService.saveUser(updatedUser)
                    
                    // Clear Glide cache to force refresh
                    Glide.get(requireContext()).clearMemory()
                    Thread {
                        Glide.get(requireContext()).clearDiskCache()
                    }.start()
                    
                    Toast.makeText(requireContext(), "Profile photo updated successfully!", Toast.LENGTH_LONG).show()
                    
                    // Hide upload button
                    btnUploadPhoto.visibility = View.GONE
                    selectedImageUri = null
                    
                    // Refresh the profile image display with cache busting
                    loadUserData()
                    
                } else {
                    throw Exception(result["message"]?.toString() ?: "Upload failed")
                }
                
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to upload photo: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isUploading = false
                updateUploadUI()
            }
        }
    }
    
    private fun saveProfileChanges() {
        val fullName = etFullName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val phone = etPhone.text.toString().trim()
        
        // Validation
        if (fullName.isEmpty()) {
            etFullName.error = "Full name is required"
            return
        }
        
        if (email.isEmpty()) {
            etEmail.error = "Email is required"
            return
        }
        
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.error = "Please enter a valid email"
            return
        }
        
        if (phone.isEmpty()) {
            etPhone.error = "Phone number is required"
            return
        }
        
        // Check if there are any changes
        if (currentUser != null) {
            val hasChanges = currentUser!!.fullName != fullName || 
                           currentUser!!.email != email || 
                           currentUser!!.phoneNumber != phone
            
            if (!hasChanges) {
                Toast.makeText(requireContext(), "No changes to save", Toast.LENGTH_SHORT).show()
                return
            }
        }
        
        // Save profile changes
        lifecycleScope.launch {
            try {
                isSaving = true
                updateSaveUI()
                
                if (currentUser != null) {
                    val result = ProfileService.updateUserProfile(
                        currentUser!!.id,
                        fullName,
                        email,
                        phone
                    )
                    
                    if (result["success"] == true) {
                        // Update local user data
                        val updatedUser = currentUser!!.copy(
                            fullName = fullName,
                            email = email,
                            phoneNumber = phone
                        )
                        UserService.saveUser(updatedUser)
                        
                        Toast.makeText(requireContext(), "Profile updated successfully!", Toast.LENGTH_LONG).show()
                        
                        // Navigate back
                        requireActivity().supportFragmentManager.popBackStack()
                        
                    } else {
                        throw Exception(result["message"]?.toString() ?: "Update failed")
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to update profile: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isSaving = false
                updateSaveUI()
            }
        }
    }
    

    
    private fun updateUploadUI() {
        if (isUploading) {
            btnUploadPhoto.text = "Uploading..."
            btnUploadPhoto.isEnabled = false
            progressBar.visibility = View.VISIBLE
        } else {
            btnUploadPhoto.text = "Upload Photo"
            btnUploadPhoto.isEnabled = true
            progressBar.visibility = View.GONE
        }
    }
    
    private fun updateSaveUI() {
        if (isSaving) {
            btnSaveChanges.text = "Saving..."
            btnSaveChanges.isEnabled = false
            progressBarSave.visibility = View.VISIBLE
        } else {
            btnSaveChanges.text = "Save Changes"
            btnSaveChanges.isEnabled = true
            progressBarSave.visibility = View.GONE
        }
    }
    
    private fun getProfileImageUrl(profileImage: String): String {
        return "https://emshotels.net/images/user/profile/thumb/$profileImage"
    }
    
    fun saveProfile(): Boolean {
        val fullName = etFullName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val phone = etPhone.text.toString().trim()
        
        // Validation
        if (fullName.isEmpty()) {
            etFullName.error = "Full name is required"
            return false
        }
        
        if (email.isEmpty()) {
            etEmail.error = "Email is required"
            return false
        }
        
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.error = "Please enter a valid email"
            return false
        }
        
        if (phone.isEmpty()) {
            etPhone.error = "Phone number is required"
            return false
        }
        
        // Save profile
        lifecycleScope.launch {
            try {
                if (currentUser != null) {
                    val result = ProfileService.updateUserProfile(
                        currentUser!!.id,
                        fullName,
                        email,
                        phone
                    )
                    
                    if (result["success"] == true) {
                        // Update local user data
                        val updatedUser = currentUser!!.copy(
                            fullName = fullName,
                            email = email,
                            phoneNumber = phone
                        )
                        UserService.saveUser(updatedUser)
                        
                        Toast.makeText(requireContext(), "Profile updated successfully!", Toast.LENGTH_LONG).show()
                        
                        // Navigate back
                        requireActivity().supportFragmentManager.popBackStack()
                        
                    } else {
                        throw Exception(result["message"]?.toString() ?: "Update failed")
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to update profile: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
        
        return true
    }
}
