package com.sofindo.ems.dialogs

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.Window
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import com.bumptech.glide.Glide
import com.sofindo.ems.R

class ImageViewerDialog(
    context: Context,
    private val imageUrl: String
) : Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen) {

    private lateinit var ivFullImage: ImageView
    private lateinit var btnClose: ImageButton
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Set full screen
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window?.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        
        setContentView(R.layout.dialog_image_viewer)
        
        initViews()
        setupListeners()
        loadImage()
    }
    
    private fun initViews() {
        ivFullImage = findViewById(R.id.iv_full_image)
        btnClose = findViewById(R.id.btn_close)
        progressBar = findViewById(R.id.progress_bar)
    }
    
    private fun setupListeners() {
        btnClose.setOnClickListener {
            dismiss()
        }
        
        // Close on image tap
        ivFullImage.setOnClickListener {
            dismiss()
        }
    }
    
    private fun loadImage() {
        progressBar.visibility = android.view.View.VISIBLE
        
        Glide.with(context)
            .load(imageUrl)
            .placeholder(R.drawable.ic_photo)
            .error(R.drawable.ic_photo)
            .into(ivFullImage)
            
        // Hide progress bar after a short delay
        ivFullImage.postDelayed({
            progressBar.visibility = android.view.View.GONE
        }, 1000)
    }
}
