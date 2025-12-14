package com.sofindo.ems.dialogs

import android.app.Dialog
import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.os.Bundle
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.sofindo.ems.R

class ImageViewerDialog(
    context: Context,
    private val imageUrl: String,
    private val title: String? = null
) : Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen) {

    private lateinit var ivFullImage: ImageView
    private lateinit var btnClose: ImageButton
    private lateinit var progressBar: ProgressBar
    private lateinit var tvTitle: android.widget.TextView
    
    // Zoom and pan variables
    private var matrix: Matrix = Matrix()
    private var savedMatrix: Matrix = Matrix()
    private var mode: Int = NONE
    private var start: PointF = PointF()
    private var mid: PointF = PointF()
    private var oldDist: Float = 1f
    private var minScale: Float = 1f
    private var maxScale: Float = 5f
    
    companion object {
        private const val NONE = 0
        private const val DRAG = 1
        private const val ZOOM = 2
    }

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
        tvTitle = findViewById(R.id.tv_title)
        
        // Set title if provided
        if (title != null) {
            tvTitle.text = title
            tvTitle.visibility = android.view.View.VISIBLE
        } else {
            tvTitle.visibility = android.view.View.GONE
        }
    }
    
    private fun setupListeners() {
        btnClose.setOnClickListener {
            dismiss()
        }
        
        // Setup zoom and pan for image
        setupImageZoom()
    }
    
    private fun setupImageZoom() {
        ivFullImage.scaleType = ImageView.ScaleType.MATRIX
        
        ivFullImage.setOnTouchListener { v, event ->
            val view = v as ImageView
            
            when (event.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_DOWN -> {
                    savedMatrix.set(matrix)
                    start.set(event.x, event.y)
                    mode = DRAG
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    oldDist = spacing(event)
                    if (oldDist > 10f) {
                        savedMatrix.set(matrix)
                        midPoint(mid, event)
                        mode = ZOOM
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                    mode = NONE
                }
                MotionEvent.ACTION_MOVE -> {
                    if (mode == DRAG) {
                        matrix.set(savedMatrix)
                        matrix.postTranslate(event.x - start.x, event.y - start.y)
                    } else if (mode == ZOOM) {
                        val newDist = spacing(event)
                        if (newDist > 10f) {
                            matrix.set(savedMatrix)
                            val scale = newDist / oldDist
                            val currentScale = getScale(matrix)
                            
                            // Limit zoom between minScale and maxScale
                            if (scale * currentScale < minScale) {
                                matrix.postScale(minScale / currentScale, minScale / currentScale, mid.x, mid.y)
                            } else if (scale * currentScale > maxScale) {
                                matrix.postScale(maxScale / currentScale, maxScale / currentScale, mid.x, mid.y)
                            } else {
                                matrix.postScale(scale, scale, mid.x, mid.y)
                            }
                        }
                    }
                }
            }
            
            view.imageMatrix = matrix
            true
        }
        
        // Double tap to zoom
        ivFullImage.setOnClickListener {
            val currentScale = getScale(matrix)
            val targetScale = if (currentScale > minScale) minScale else maxScale
            
            val animScale = targetScale / currentScale
            val centerX = ivFullImage.width / 2f
            val centerY = ivFullImage.height / 2f
            
            matrix.postScale(animScale, animScale, centerX, centerY)
            ivFullImage.imageMatrix = matrix
        }
    }
    
    private fun spacing(event: MotionEvent): Float {
        val x = event.getX(0) - event.getX(1)
        val y = event.getY(0) - event.getY(1)
        return kotlin.math.sqrt((x * x + y * y).toDouble()).toFloat()
    }
    
    private fun midPoint(point: PointF, event: MotionEvent) {
        val x = event.getX(0) + event.getX(1)
        val y = event.getY(0) + event.getY(1)
        point.set(x / 2, y / 2)
    }
    
    private fun getScale(matrix: Matrix): Float {
        val values = FloatArray(9)
        matrix.getValues(values)
        return values[Matrix.MSCALE_X]
    }
    
    private fun loadImage() {
        progressBar.visibility = android.view.View.VISIBLE
        
        val requestOptions = RequestOptions()
            .placeholder(R.drawable.ic_photo)
            .error(R.drawable.ic_photo)
        
        Glide.with(context)
            .load(imageUrl)
            .apply(requestOptions)
            .transition(DrawableTransitionOptions.withCrossFade())
            .into(object : com.bumptech.glide.request.target.CustomTarget<android.graphics.drawable.Drawable>() {
                override fun onResourceReady(
                    resource: android.graphics.drawable.Drawable,
                    transition: com.bumptech.glide.request.transition.Transition<in android.graphics.drawable.Drawable>?
                ) {
                    progressBar.visibility = android.view.View.GONE
                    ivFullImage.setImageDrawable(resource)
                    // Reset matrix when image is loaded
                    ivFullImage.post {
                        matrix.reset()
                        ivFullImage.imageMatrix = matrix
                    }
                }
                
                override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {
                    // Called when the resource is cleared
                }
            })
    }
}
