package com.sofindo.ems.auth

import android.content.Intent
import android.os.Bundle
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.sofindo.ems.R
import com.sofindo.ems.api.RetrofitClient
import kotlinx.coroutines.launch
import java.util.regex.Pattern

class RegisterActivity : AppCompatActivity() {
    
    private lateinit var etPropId: EditText
    private lateinit var etName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etHandphone: EditText
    private lateinit var etPassword: EditText
    private lateinit var spinnerDepartment: Spinner
    private lateinit var btnSignUp: MaterialButton
    private lateinit var tvError: TextView
    private lateinit var tvBackToLogin: TextView
    private lateinit var ivPasswordToggle: ImageView
    private lateinit var toolbar: Toolbar
    
    private var isLoading = false
    private var isPasswordVisible = false
    private var selectedDepartment = "A & G"
    
    // Validation states
    private var isPropIdValid = false
    private var isNameValid = false
    private var isEmailValid = false
    private var isHandphoneValid = false
    private var isPasswordValid = false
    
    private val departments = listOf(
        "A & G",
        "Engineering", 
        "Housekeeping",
        "F&B",
        "Security",
        "IT",
        "Finance",
        "HR",
        "Sales",
        "Marketing"
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)
        
        initViews()
        setupSpinner()
        setupListeners()
    }
    
    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        etPropId = findViewById(R.id.et_prop_id)
        etName = findViewById(R.id.et_name)
        etEmail = findViewById(R.id.et_email)
        etHandphone = findViewById(R.id.et_handphone)
        etPassword = findViewById(R.id.et_password)
        spinnerDepartment = findViewById(R.id.spinner_department)
        btnSignUp = findViewById(R.id.btn_sign_up)
        tvError = findViewById(R.id.tv_error)
        tvBackToLogin = findViewById(R.id.tv_back_to_login)
        ivPasswordToggle = findViewById(R.id.iv_password_toggle)
        
        // Setup toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        
        // Disable sign up button initially
        btnSignUp.isEnabled = false
        
        // Initialize validation states to false
        isPropIdValid = false
        isNameValid = false
        isEmailValid = false
        isHandphoneValid = false
        isPasswordValid = false
    }
    
    private fun setupSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, departments)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerDepartment.adapter = adapter
        
        spinnerDepartment.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedDepartment = departments[position]
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedDepartment = departments[0]
            }
        }
    }
    
    private fun setupListeners() {
        btnSignUp.setOnClickListener {
            if (!isLoading) {
                hideKeyboard() // Hide keyboard when button is clicked
                handleRegister()
            }
        }
        
        tvBackToLogin.setOnClickListener {
            finish()
        }
        
        // Handle toolbar back button
        toolbar.setNavigationOnClickListener {
            finish()
        }
        
        ivPasswordToggle.setOnClickListener {
            togglePasswordVisibility()
        }
        
        // Auto-fill "08" when handphone field is tapped
        etHandphone.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && etHandphone.text.isNullOrEmpty()) {
                etHandphone.setText("08")
                etHandphone.setSelection(2)
            }
        }
        
        // Add text change listeners for real-time validation
        etPropId.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                validatePropId(s?.toString() ?: "")
                updateSignUpButton()
            }
        })
        
        etName.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                validateName(s?.toString() ?: "")
                updateSignUpButton()
            }
        })
        
        etEmail.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                validateEmail(s?.toString() ?: "")
                updateSignUpButton()
            }
        })
        
        etHandphone.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                validateHandphone(s?.toString() ?: "")
                updateSignUpButton()
            }
        })
        
        etPassword.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                validatePassword(s?.toString() ?: "")
                updateSignUpButton()
            }
        })
    }
    
    private fun handleRegister() {
        val propId = etPropId.text?.toString()?.trim() ?: ""
        val name = etName.text?.toString()?.trim() ?: ""
        val email = etEmail.text?.toString()?.trim() ?: ""
        val handphone = etHandphone.text?.toString()?.trim() ?: ""
        val password = etPassword.text?.toString() ?: ""
        
        // Validation with specific error messages
        val errors = mutableListOf<String>()
        
        if (!isPropIdValid) {
            if (propId.isEmpty()) {
                errors.add("Property ID is required")
            } else {
                errors.add("Property ID must be numbers only")
            }
        }
        
        if (!isNameValid) {
            if (name.isEmpty()) {
                errors.add("Name is required")
            } else {
                errors.add("Name must be maximum 15 characters")
            }
        }
        
        if (!isEmailValid) {
            if (email.isEmpty()) {
                errors.add("Email is required")
            } else {
                errors.add("Please enter a valid email address")
            }
        }
        
        if (!isHandphoneValid) {
            if (handphone.isEmpty()) {
                errors.add("Phone number is required")
            } else if (!handphone.startsWith("08")) {
                errors.add("Phone number must start with 08")
            } else {
                errors.add("Phone number must be at least 10 digits")
            }
        }
        
        if (!isPasswordValid) {
            if (password.isEmpty()) {
                errors.add("Password is required")
            } else {
                errors.add("Password must be at least 6 characters")
            }
        }
        
        if (errors.isNotEmpty()) {
            showError("Please fix the following errors: ${errors.joinToString(", ")}")
            return
        }
        
        // Register with API
        registerWithAPI(propId, name, email, handphone, password)
    }
    
    private fun registerWithAPI(propId: String, name: String, email: String, handphone: String, password: String) {
        isLoading = true
        updateUI()
        
        lifecycleScope.launch {
            try {
                val result = RetrofitClient.apiService.registerUser(
                    propID = propId,
                    nama = name,
                    email = email,
                    telp = handphone,
                    dept = selectedDepartment,
                    password = password
                )
                
                if (result["success"] == true) {
                    // Registration successful
                    Toast.makeText(this@RegisterActivity, 
                        "Registration successful! Please login with your new account.", 
                        Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    // Registration failed
                    val message = result["message"]?.toString() ?: "Registration failed. Please try again."
                    showError(message)
                }
            } catch (e: Exception) {
                showError("Registration failed: ${e.message}")
            } finally {
                isLoading = false
                updateUI()
            }
        }
    }
    
    // Validation functions
    private fun validatePropId(propId: String) {
        val trimmed = propId.trim()
        isPropIdValid = trimmed.isNotEmpty() && trimmed.matches(Regex("^[0-9]+$"))
        updateFieldBackground(etPropId, isPropIdValid)
    }
    
    private fun validateName(name: String) {
        val trimmed = name.trim()
        isNameValid = trimmed.isNotEmpty() && trimmed.length <= 15
        updateFieldBackground(etName, isNameValid)
    }
    
    private fun validateEmail(email: String) {
        val trimmed = email.trim()
        isEmailValid = trimmed.isNotEmpty() && isValidEmail(trimmed)
        updateFieldBackground(etEmail, isEmailValid)
    }
    
    private fun validateHandphone(handphone: String) {
        val trimmed = handphone.trim()
        isHandphoneValid = trimmed.isNotEmpty() && trimmed.startsWith("08") && trimmed.length >= 10
        updateFieldBackground(etHandphone, isHandphoneValid)
    }
    
    private fun validatePassword(password: String) {
        isPasswordValid = password.isNotEmpty() && password.length >= 6
        updateFieldBackground(etPassword, isPasswordValid)
    }
    
    private fun updateFieldBackground(editText: EditText, isValid: Boolean) {
        if (isValid) {
            editText.setBackgroundResource(R.drawable.register_input_background)
        } else {
            editText.setBackgroundResource(R.drawable.register_input_error_background)
        }
    }
    
    private fun updateSignUpButton() {
        val allValid = isPropIdValid && isNameValid && isEmailValid && isHandphoneValid && isPasswordValid
        val wasDisabled = !btnSignUp.isEnabled
        
        // Only enable if all validations are true and not loading
        btnSignUp.isEnabled = allValid && !isLoading
        
        // Hide keyboard when button becomes enabled for the first time
        if (wasDisabled && btnSignUp.isEnabled && allValid) {
            hideKeyboard()
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
    
    private fun isValidEmail(email: String): Boolean {
        val emailPattern = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
        )
        return emailPattern.matcher(email).matches()
    }
    
    private fun showError(message: String) {
        tvError.text = message
        tvError.visibility = View.VISIBLE
    }
    
    private fun hideError() {
        tvError.visibility = View.GONE
    }
    
    private fun updateUI() {
        if (isLoading) {
            btnSignUp.text = "Signing up..."
            btnSignUp.isEnabled = false
            hideError()
        } else {
            btnSignUp.text = "SIGN UP"
            // Don't call updateSignUpButton here, let the text watchers handle it
        }
    }
    
    private fun togglePasswordVisibility() {
        isPasswordVisible = !isPasswordVisible
        
        if (isPasswordVisible) {
            etPassword.transformationMethod = null
            ivPasswordToggle.setImageResource(R.drawable.ic_visibility)
        } else {
            etPassword.transformationMethod = PasswordTransformationMethod()
            ivPasswordToggle.setImageResource(R.drawable.ic_visibility_off)
        }
        
        // Move cursor to end
        etPassword.setSelection(etPassword.text?.length ?: 0)
    }
}
