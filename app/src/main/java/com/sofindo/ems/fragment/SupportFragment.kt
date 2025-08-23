package com.sofindo.ems.fragment

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.sofindo.ems.R
import com.sofindo.ems.api.RetrofitClient
import com.sofindo.ems.services.UserService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.ResponseBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class SupportFragment : Fragment() {
	private lateinit var ivBackButton: ImageView
	private lateinit var tvTitle: TextView
	private lateinit var spCategory: Spinner
	private lateinit var etDescription: EditText
	private lateinit var ivScreenshotPreview: ImageView
	private lateinit var btnAddScreenshot: Button
	private lateinit var btnRemoveScreenshot: Button
	private lateinit var btnSubmit: Button
	private lateinit var progressBar: ProgressBar

	private var selectedCategoryValue: String? = null
	private var selectedImageUri: Uri? = null
	private var isSubmitting: Boolean = false

	// Launchers
	private val photoPickerLauncher = registerForActivityResult(
		ActivityResultContracts.PickVisualMedia()
	) { uri ->
		uri?.let { handleSelectedImage(it) }
	}

	private val pickImageLegacy = registerForActivityResult(
		ActivityResultContracts.StartActivityForResult()
	) { result ->
		if (result.resultCode == Activity.RESULT_OK) {
			result.data?.data?.let { uri -> handleSelectedImage(uri) }
		}
	}

	private val cameraLauncher = registerForActivityResult(
		ActivityResultContracts.StartActivityForResult()
	) { result ->
		if (result.resultCode == Activity.RESULT_OK) {
			result.data?.extras?.get("data")?.let { bmp ->
				lifecycleScope.launch {
					val uri = saveBitmapToUri(bmp as Bitmap)
					handleSelectedImage(uri)
				}
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
		etDescription.addTextChangedListener(object: android.text.TextWatcher {
			override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
			override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updateSubmitEnabledState() }
			override fun afterTextChanged(s: android.text.Editable?) {}
		})
		updateSubmitEnabledState()
	}

	private fun initViews(view: View) {
		ivBackButton = view.findViewById(R.id.iv_back_button)
		tvTitle = view.findViewById(R.id.tv_title)
		spCategory = view.findViewById(R.id.sp_category)
		etDescription = view.findViewById(R.id.et_description)
		ivScreenshotPreview = view.findViewById(R.id.iv_screenshot_preview)
		btnAddScreenshot = view.findViewById(R.id.btn_add_screenshot)
		btnRemoveScreenshot = view.findViewById(R.id.btn_remove_screenshot)
		btnSubmit = view.findViewById(R.id.btn_submit)
		progressBar = view.findViewById(R.id.progressBar)
	}

	private fun setupCategorySpinner() {
		val categories = listOf(
			"Select issue category..." to null,
			"Technical Issue" to "technical",
			"Login Problem" to "login",
			"Work Order Issue" to "workorder",
			"File Upload Problem" to "upload",
			"Performance Issue" to "performance",
			"Other" to "other"
		)
		val labels = categories.map { it.first }
		val adapter = ArrayAdapter(requireContext(), R.layout.item_spinner_category, labels)
		adapter.setDropDownViewResource(R.layout.item_spinner_category_dropdown)
		spCategory.adapter = adapter
		spCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
			override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
				selectedCategoryValue = categories[position].second
				updateSubmitEnabledState()
			}
			override fun onNothingSelected(parent: AdapterView<*>) {}
		}
	}

	private fun setupListeners() {
		ivBackButton.setOnClickListener { requireActivity().supportFragmentManager.popBackStack() }
		btnAddScreenshot.setOnClickListener { showImageSourceDialog() }
		btnRemoveScreenshot.setOnClickListener { clearScreenshot() }
		btnSubmit.setOnClickListener { submitTicket() }
	}

	private fun loadUserDefaults() {
		// Preload description placeholder if needed; user name/email loaded in submit time
		updateSubmitEnabledState()
	}

	private fun updateSubmitEnabledState() {
		val enabled = !isSubmitting && etDescription.text.toString().trim().isNotEmpty() && !selectedCategoryValue.isNullOrEmpty()
		btnSubmit.isEnabled = enabled
		btnSubmit.alpha = if (enabled) 1f else 0.5f
	}

	private fun showImageSourceDialog() {
		val options = arrayOf("Camera", "Gallery")
		androidx.appcompat.app.AlertDialog.Builder(requireContext())
			.setTitle("Select Photo Source")
			.setItems(options) { _, which ->
				when (which) {
					0 -> openCamera()
					1 -> openGallery()
				}
			}
			.show()
	}

	private fun openGallery() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
		} else {
			val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
			pickImageLegacy.launch(intent)
		}
	}

	private fun openCamera() {
		val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
		cameraLauncher.launch(intent)
	}

	private fun handleSelectedImage(uri: Uri) {
		selectedImageUri = uri
		ivScreenshotPreview.setImageURI(uri)
		ivScreenshotPreview.visibility = View.VISIBLE
		btnRemoveScreenshot.visibility = View.VISIBLE
	}

	private fun clearScreenshot() {
		selectedImageUri = null
		ivScreenshotPreview.setImageDrawable(null)
		ivScreenshotPreview.visibility = View.GONE
		btnRemoveScreenshot.visibility = View.GONE
	}

	private fun resizeImage(uri: Uri): Bitmap {
		val inputStream = requireContext().contentResolver.openInputStream(uri)
		val originalBitmap = BitmapFactory.decodeStream(inputStream)
		inputStream?.close()
		val maxSize = 640
		val width = originalBitmap.width
		val height = originalBitmap.height
		val scale = if (width > height) maxSize.toFloat() / width else maxSize.toFloat() / height
		val newW = (width * scale).toInt()
		val newH = (height * scale).toInt()
		return Bitmap.createScaledBitmap(originalBitmap, newW, newH, true)
	}

	private fun saveBitmapToUri(bitmap: Bitmap): Uri {
		val tempFile = File(requireContext().cacheDir, "support_screenshot.jpg")
		val outputStream = FileOutputStream(tempFile)
		bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
		outputStream.close()
		return Uri.fromFile(tempFile)
	}

	private suspend fun uploadAttachmentIfAny(): String? {
		val uri = selectedImageUri ?: return null
		return withContext(Dispatchers.IO) {
			try {
				val resized = resizeImage(uri)
				val tmp = File(requireContext().cacheDir, "support_attach.jpg")
				FileOutputStream(tmp).use { fos ->
					resized.compress(Bitmap.CompressFormat.JPEG, 80, fos)
				}
				val body = tmp.asRequestBody("image/jpeg".toMediaTypeOrNull())
				val part = MultipartBody.Part.createFormData("attachment", "support_attach.jpg", body)
				val resp: ResponseBody = RetrofitClient.instance.uploadSupportAttachment(part)
				val raw = resp.string()
				// Expect JSON { success: true, file_path: "..." }
				try {
					val json = org.json.JSONObject(raw)
					if (json.optBoolean("success", false)) {
						json.optString("file_path", null)
					} else null
				} catch (e: Exception) {
					null
				}
			} catch (e: Exception) {
				null
			}
		}
	}

	private fun setLoading(loading: Boolean) {
		isSubmitting = loading
		progressBar.visibility = if (loading) View.VISIBLE else View.GONE
		btnSubmit.isEnabled = !loading
		btnSubmit.alpha = if (!loading) 1f else 0.5f
	}

	private fun showSuccessDialogAndReturn() {
		val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.view_success_dialog, null)
		val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
			.setView(dialogView)
			.setCancelable(false)
			.create()
		dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
		dialog.show()
		// Auto dismiss after 3.5 seconds then go back
		view?.postDelayed({
			if (dialog.isShowing) dialog.dismiss()
			requireActivity().supportFragmentManager.popBackStack()
		}, 3500)
	}

	private fun submitTicket() {
		lifecycleScope.launch {
			setLoading(true)
			try {
				val user = UserService.getCurrentUser(requireContext())
				val name = user?.fullName ?: user?.username ?: ""
				val email = user?.email ?: ""
				val mobile = user?.phoneNumber
				val category = selectedCategoryValue ?: ""
				val description = etDescription.text.toString().trim()

				if (name.isEmpty() || email.isEmpty() || category.isEmpty() || description.isEmpty()) {
					Toast.makeText(context, "Please fill all required fields", Toast.LENGTH_SHORT).show()
					return@launch
				}

				val screenshotPath = uploadAttachmentIfAny()
				val resp = RetrofitClient.instance.submitSupportTicket(
					name = name,
					email = email,
					mobileNumber = mobile,
					issueCategory = category,
					description = description,
					screenshotPath = screenshotPath
				)
				val raw = resp.string().trim()
				if (raw.equals("success", ignoreCase = true)) {
					resetForm()
					showSuccessDialogAndReturn()
				} else {
					try {
						val json = org.json.JSONObject(raw)
						resetForm()
						showSuccessDialogAndReturn()
					} catch (e: Exception) {
						Toast.makeText(context, "Server response: $raw", Toast.LENGTH_LONG).show()
					}
				}
			} catch (e: Exception) {
				Toast.makeText(context, "Error submitting ticket: ${e.message}", Toast.LENGTH_LONG).show()
			} finally {
				setLoading(false)
				updateSubmitEnabledState()
			}
		}
	}

	private fun resetForm() {
		etDescription.setText("")
		spCategory.setSelection(0)
		clearScreenshot()
	}
}
