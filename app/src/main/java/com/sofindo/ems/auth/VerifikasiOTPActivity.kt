package com.sofindo.ems.auth

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.sofindo.ems.R
import com.sofindo.ems.api.RetrofitClient
import com.sofindo.ems.utils.applyTopAndBottomInsets
import com.sofindo.ems.utils.setupEdgeToEdge
import kotlinx.coroutines.launch

class VerifikasiOTPActivity : AppCompatActivity() {
    
    private lateinit var tvSentTo: TextView
    private lateinit var etOTPCode: EditText
    private lateinit var etNewPassword: EditText
    private lateinit var btnVerify: MaterialButton
    private lateinit var btnPasswordChange: MaterialButton
    private lateinit var btnResendOTP: TextView
    private lateinit var tvError: TextView
    private lateinit var toolbar: Toolbar
    private lateinit var ivPasswordToggle: ImageView
    private lateinit var llOTPSection: LinearLayout
    private lateinit var llPasswordSection: LinearLayout
    private lateinit var llOTPVerified: LinearLayout
    
    private var email = ""
    private var otpCode = ""
    private var isOTPVerified = false
    private var isVerifying = false
    private var isChanging = false
    private var isResending = false
    private var isPasswordVisible = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Enable edge-to-edge for Android 15+ (SDK 35)
        setupEdgeToEdge()
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verifikasi_otp)
        
        // Apply window insets to root layout
        findViewById<android.view.ViewGroup>(android.R.id.content)?.getChildAt(0)?.let { rootView ->
            rootView.applyTopAndBottomInsets()
        }
        
        email = intent.getStringExtra("email") ?: ""
        
        initViews()
        setupListeners()
        updateUI()
    }
    
    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        tvSentTo = findViewById(R.id.tv_sent_to)
        etOTPCode = findViewById(R.id.et_otp_code)
        etNewPassword = findViewById(R.id.et_new_password)
        btnVerify = findViewById(R.id.btn_verify)
        btnPasswordChange = findViewById(R.id.btn_password_change)
        btnResendOTP = findViewById(R.id.btn_resend_otp)
        tvError = findViewById(R.id.tv_error)
        ivPasswordToggle = findViewById(R.id.iv_password_toggle)
        llOTPSection = findViewById(R.id.ll_otp_section)
        llPasswordSection = findViewById(R.id.ll_password_section)
        llOTPVerified = findViewById(R.id.ll_otp_verified)
        
        // Setup toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = "Verify OTP"
        
        // Set email display
        tvSentTo.text = "OTP sent to: ${displayEmail(email)}"
        
        // Limit OTP input to 4 digits
        val otpFilter = InputFilter.LengthFilter(4)
        etOTPCode.filters = arrayOf(otpFilter, InputFilter { source, start, end, _, _, _ ->
            // Only allow digits
            for (i in start until end) {
                if (!Character.isDigit(source[i])) {
                    return@InputFilter ""
                }
            }
            null
        })
        
        // Password visibility toggle initial state
        isPasswordVisible = false
        ivPasswordToggle.setImageResource(R.drawable.ic_visibility_off)
    }
    
    private fun setupListeners() {
        btnVerify.setOnClickListener {
            if (!isVerifying) {
                hideKeyboard()
                handleVerifyOTP()
            }
        }
        
        btnPasswordChange.setOnClickListener {
            if (!isChanging) {
                hideKeyboard()
                handlePasswordChange()
            }
        }
        
        btnResendOTP.setOnClickListener {
            if (!isResending && !isVerifying && !isChanging) {
                hideKeyboard()
                handleResendOTP()
            }
        }
        
        ivPasswordToggle.setOnClickListener {
            togglePasswordVisibility()
        }
        
        // Handle toolbar back button
        toolbar.setNavigationOnClickListener {
            finish()
        }
    }
    
    private fun handleVerifyOTP() {
        otpCode = etOTPCode.text?.toString()?.trim() ?: ""
        
        // Validation
        if (!isValidOTP(otpCode)) {
            showError("Invalid OTP")
            return
        }
        
        // Verify OTP
        verifyOTP(otpCode)
    }
    
    private fun handlePasswordChange() {
        val newPassword = etNewPassword.text?.toString() ?: ""
        
        // Validation
        if (!isValidPassword(newPassword)) {
            showError("Password minimal 6 karakter")
            return
        }
        
        if (!isValidOTP(otpCode)) {
            showError("Invalid OTP")
            return
        }
        
        // Change password
        changePassword(otpCode, newPassword)
    }
    
    private fun isValidOTP(otp: String): Boolean {
        val digits = otp.filter { it.isDigit() }
        return digits.length == 4
    }
    
    private fun isValidPassword(password: String): Boolean {
        return password.length >= 6
    }
    
    private fun verifyOTP(otpCode: String) {
        isVerifying = true
        updateUI()
        
        lifecycleScope.launch {
            try {
                val requestBody = mapOf(
                    "email" to email,
                    "otp" to otpCode
                )
                val result = RetrofitClient.apiService.verifyOTP(requestBody)
                
                val success = result["success"] as? Boolean ?: false
                if (success) {
                    // OTP is valid
                    isOTPVerified = true
                    hideError()
                    updateUI()
                } else {
                    val message = result["message"]?.toString() ?: "Invalid OTP or email"
                    showError(message)
                    isOTPVerified = false
                }
            } catch (e: Exception) {
                android.util.Log.e("VerifikasiOTPActivity", "Error verifying OTP: ${e.message}", e)
                showError("Failed to verify OTP: ${e.message}")
                isOTPVerified = false
            } finally {
                isVerifying = false
                updateUI()
            }
        }
    }
    
    private fun changePassword(otpCode: String, newPassword: String) {
        isChanging = true
        updateUI()
        
        lifecycleScope.launch {
            try {
                val requestBody = mapOf(
                    "email" to email,
                    "otp" to otpCode,
                    "newPassword" to newPassword
                )
                val result = RetrofitClient.apiService.changePassword(requestBody)
                
                val success = result["success"] as? Boolean ?: false
                if (success) {
                    // Show success alert
                    showSuccessAlert(newPassword)
                } else {
                    val message = result["message"]?.toString() ?: "Failed to change password"
                    showError(message)
                }
            } catch (e: Exception) {
                android.util.Log.e("VerifikasiOTPActivity", "Error changing password: ${e.message}", e)
                showError("Failed to change password: ${e.message}")
            } finally {
                isChanging = false
                updateUI()
            }
        }
    }
    
    private fun handleResendOTP() {
        isResending = true
        updateResendUI()
        
        lifecycleScope.launch {
            try {
                val requestBody = mapOf("email" to email)
                val result = RetrofitClient.apiService.checkEmailAndSendOTP(requestBody)
                val success = result["success"] as? Boolean ?: false
                if (!success) {
                    val message = result["message"]?.toString() ?: "Failed to resend OTP"
                    showError(message)
                } else {
                    hideError()
                }
            } catch (e: Exception) {
                android.util.Log.e("VerifikasiOTPActivity", "Error resending OTP: ${e.message}", e)
                // Don't show error for resend failure, just enable button
            } finally {
                isResending = false
                updateResendUI()
            }
        }
    }
    
    private fun togglePasswordVisibility() {
        isPasswordVisible = !isPasswordVisible
        
        if (isPasswordVisible) {
            etNewPassword.transformationMethod = null
            etNewPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            ivPasswordToggle.setImageResource(R.drawable.ic_visibility)
        } else {
            etNewPassword.transformationMethod = PasswordTransformationMethod()
            ivPasswordToggle.setImageResource(R.drawable.ic_visibility_off)
        }
        
        // Move cursor to end
        etNewPassword.setSelection(etNewPassword.text?.length ?: 0)
    }
    
    private fun showError(message: String) {
        tvError.text = message
        tvError.visibility = View.VISIBLE
    }
    
    private fun hideError() {
        tvError.visibility = View.GONE
    }
    
    private fun updateUI() {
        if (isOTPVerified) {
            // Show OTP verified indicator and password section
            llOTPVerified.visibility = View.VISIBLE
            llPasswordSection.visibility = View.VISIBLE
            llOTPSection.visibility = View.GONE
            btnVerify.visibility = View.GONE
            
            // Update password change button
            if (isChanging) {
                btnPasswordChange.text = "Processing..."
                btnPasswordChange.isEnabled = false
            } else {
                btnPasswordChange.text = "Password Change"
                btnPasswordChange.isEnabled = true
            }
        } else {
            // Show OTP section
            llOTPVerified.visibility = View.GONE
            llPasswordSection.visibility = View.GONE
            llOTPSection.visibility = View.VISIBLE
            btnVerify.visibility = View.VISIBLE
            
            // Update verify button
            if (isVerifying) {
                btnVerify.text = "Verifying..."
                btnVerify.isEnabled = false
            } else {
                btnVerify.text = "Verify"
                btnVerify.isEnabled = true
            }
        }
    }
    
    private fun updateResendUI() {
        if (isResending) {
            btnResendOTP.text = "Resending..."
            btnResendOTP.isEnabled = false
        } else {
            btnResendOTP.text = "Resend OTP"
            btnResendOTP.isEnabled = true
        }
    }
    
    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val currentFocus = currentFocus
        if (currentFocus != null) {
            imm.hideSoftInputFromWindow(currentFocus.windowToken, 0)
            currentFocus.clearFocus()
        }
    }
    
    private fun displayEmail(email: String): String {
        val trimmed = email.trim()
        // Mask email: show first 3 chars and domain
        // Example: abc@example.com -> abc***@example.com
        val atIndex = trimmed.indexOf('@')
        if (atIndex > 0 && atIndex < trimmed.length - 1) {
            val localPart = trimmed.substring(0, atIndex)
            val domain = trimmed.substring(atIndex + 1)
            
            val prefix = if (localPart.length <= 3) localPart else localPart.substring(0, 3)
            return "$prefix***@$domain"
        }
        // Fallback: show first 3 chars only
        return if (trimmed.length <= 3) trimmed else "${trimmed.substring(0, 3)}***"
    }
    
    private fun showSuccessAlert(newPassword: String) {
        AlertDialog.Builder(this)
            .setTitle("Password Change successfully")
            .setMessage("Your password has been changed successfully. You will be automatically logged in.")
            .setCancelable(false)
            .show()
        
        // After 3 seconds, navigate back to login and auto-login
        // Same flow as iOS: dismiss VerifikasiOTPView → dismiss ForgotPasswordView → LoginView with auto-login
        Handler(Looper.getMainLooper()).postDelayed({
            // Navigate to login page with credentials for auto-login
            // Use finishAffinity to clear back stack (like iOS dismiss navigation stack)
            val intent = Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra("email", email)
                putExtra("password", newPassword)
                putExtra("autoLogin", true)
            }
            startActivity(intent)
            finishAffinity() // Clear entire navigation stack (ForgotPasswordActivity + VerifikasiOTPActivity)
        }, 3000)
    }
}
