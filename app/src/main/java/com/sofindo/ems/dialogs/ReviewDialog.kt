package com.sofindo.ems.dialogs

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.*
import androidx.core.content.ContextCompat
import com.sofindo.ems.R

class ReviewDialog(
    context: Context,
    private val woNumber: String,
    private val createdAtDate: String? = null, // For 3-day validation (tglWo for new, created_at for edit)
    private val initialRating: Int = 0, // Pre-filled rating for edit mode
    private val initialComment: String = "", // Pre-filled comment for edit mode
    private val onSave: (rating: Int, comment: String) -> Unit,
    private val onCancel: () -> Unit
) : Dialog(context) {

    private lateinit var btnClose: ImageButton
    private lateinit var tvTitle: TextView
    private lateinit var tvWoNumber: TextView
    private lateinit var llRating: LinearLayout
    private lateinit var etComment: EditText
    private lateinit var tvError: TextView
    private lateinit var tvExpiredWarning: TextView
    private lateinit var btnCancel: Button
    private lateinit var btnSubmit: Button
    
    private var selectedRating = initialRating
    private val starButtons = mutableListOf<ImageButton>()
    
    // Check if review is expired (more than 3 days)
    private fun isExpired(): Boolean {
        val dateString = createdAtDate ?: return false
        
        if (dateString.isEmpty()) {
            return false
        }
        
        // Check if date is "0000-00-00" or similar empty date format
        if (dateString == "0000-00-00" || 
            dateString == "0000-00-00 00:00:00" || 
            dateString.contains("0000-00-00")) {
            return false
        }
        
        return try {
            val formatter = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            val createdDate = formatter.parse(dateString)
            
            if (createdDate == null) {
                // Try date only format
                val formatter2 = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                val createdDate2 = formatter2.parse(dateString)
                
                if (createdDate2 == null) {
                    return false
                }
                
                val daysSince = ((System.currentTimeMillis() - createdDate2.time) / (1000 * 60 * 60 * 24)).toInt()
                daysSince > 3
            } else {
                val daysSince = ((System.currentTimeMillis() - createdDate.time) / (1000 * 60 * 60 * 24)).toInt()
                daysSince > 3
            }
        } catch (e: Exception) {
            false // If can't parse, allow submission
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_review)
        
        // Set dialog to half screen height
        val displayMetrics = context.resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val dialogHeight = (screenHeight * 0.5).toInt()
        
        window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dialogHeight
        )
        
        // Set dialog position to bottom
        window?.setGravity(android.view.Gravity.BOTTOM)
        
        // Set background transparent to show rounded corners
        window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        initViews()
        setupListeners()
        updateStarButtons()
    }
    
    private fun initViews() {
        btnClose = findViewById(R.id.btn_close)
        tvTitle = findViewById(R.id.tv_title)
        tvWoNumber = findViewById(R.id.tv_wo_number)
        llRating = findViewById(R.id.ll_rating)
        etComment = findViewById(R.id.et_comment)
        tvError = findViewById(R.id.tv_error)
        btnCancel = findViewById(R.id.btn_cancel)
        btnSubmit = findViewById(R.id.btn_submit)
        
        // Create expired warning TextView programmatically
        tvExpiredWarning = TextView(context).apply {
            visibility = View.GONE
            textSize = 12f
            setPadding(32, 16, 32, 16)
            setBackgroundColor(0x1AFF9800.toInt()) // Orange background with transparency
        }
        
        // Set WO number
        tvWoNumber.text = "WO #$woNumber"
        
        // Get star buttons
        starButtons.add(findViewById(R.id.btn_star_1))
        starButtons.add(findViewById(R.id.btn_star_2))
        starButtons.add(findViewById(R.id.btn_star_3))
        starButtons.add(findViewById(R.id.btn_star_4))
        starButtons.add(findViewById(R.id.btn_star_5))
        
        // Pre-fill rating and comment if provided (for edit mode)
        if (initialRating > 0) {
            selectedRating = initialRating
        }
        if (initialComment.isNotEmpty()) {
            etComment.setText(initialComment)
        }
    }
    
    private fun setupListeners() {
        btnClose.setOnClickListener {
            onCancel()
            dismiss()
        }
        
        btnCancel.setOnClickListener {
            onCancel()
            dismiss()
        }
        
        btnSubmit.setOnClickListener {
            validateAndSubmit()
        }
        
        // Star button listeners
        for (i in starButtons.indices) {
            val starIndex = i + 1
            starButtons[i].setOnClickListener {
                selectedRating = starIndex
                updateStarButtons()
                updateSubmitButton()
                hideError()
            }
        }
        
        // Update submit button when comment changes
        etComment.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                updateSubmitButton()
                hideError()
            }
        })
        
        // Initial state
        updateSubmitButton()
        updateExpiredWarning()
    }
    
    private fun updateExpiredWarning() {
        val expired = isExpired()
        if (expired) {
            // Set warning text and color
            tvExpiredWarning.text = "Review submission is no longer available. More than 3 days have passed since the work order was created."
            tvExpiredWarning.setTextColor(ContextCompat.getColor(context, android.R.color.holo_orange_dark))
            tvExpiredWarning.visibility = View.VISIBLE
            
            // Add warning to layout if not already added
            val parent = tvError.parent as? ViewGroup
            if (parent != null && tvExpiredWarning.parent == null) {
                val index = parent.indexOfChild(tvError)
                // Create layout params similar to tvError
                val params = android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 8
                }
                tvExpiredWarning.layoutParams = params
                parent.addView(tvExpiredWarning, index + 1)
            }
        } else {
            tvExpiredWarning.visibility = View.GONE
        }
    }
    
    private fun updateStarButtons() {
        for (i in starButtons.indices) {
            val starIndex = i + 1
            if (starIndex <= selectedRating) {
                // Filled star
                starButtons[i].setImageResource(R.drawable.ic_star_filled)
                starButtons[i].setColorFilter(ContextCompat.getColor(context, R.color.star_filled))
            } else {
                // Empty star
                starButtons[i].setImageResource(R.drawable.ic_star)
                starButtons[i].setColorFilter(ContextCompat.getColor(context, R.color.star_empty))
            }
        }
    }
    
    private fun updateSubmitButton() {
        val hasRating = selectedRating > 0
        val hasComment = etComment.text.toString().trim().isNotEmpty()
        val expired = isExpired()
        
        btnSubmit.isEnabled = hasRating && hasComment && !expired
        btnSubmit.alpha = if (hasRating && hasComment && !expired) 1.0f else 0.5f
    }
    
    private fun validateAndSubmit() {
        val rating = selectedRating
        val comment = etComment.text.toString().trim()
        
        if (isExpired()) {
            showError("Review submission is no longer available. More than 3 days have passed.")
            return
        }
        
        if (rating == 0) {
            showError("Please select a rating")
            return
        }
        
        if (comment.isEmpty()) {
            showError("Please enter a comment")
            return
        }
        
        hideError()
        onSave(rating, comment)
    }
    
    private fun showError(message: String) {
        tvError.text = message
        tvError.visibility = View.VISIBLE
    }
    
    private fun hideError() {
        tvError.visibility = View.GONE
    }
    
    fun setSubmitting(isSubmitting: Boolean) {
        val expired = isExpired()
        btnSubmit.isEnabled = !isSubmitting && selectedRating > 0 && etComment.text.toString().trim().isNotEmpty() && !expired
        btnCancel.isEnabled = !isSubmitting
        btnClose.isEnabled = !isSubmitting
        
        if (isSubmitting) {
            btnSubmit.text = "Submitting..."
            btnSubmit.isEnabled = false
        } else {
            btnSubmit.text = "Submit Review"
            updateSubmitButton()
        }
    }
    
    fun setErrorMessage(message: String?) {
        if (message != null) {
            showError(message)
        } else {
            hideError()
        }
    }
}

