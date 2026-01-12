package com.sofindo.ems.base

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.sofindo.ems.utils.applyTopAndBottomInsets
import com.sofindo.ems.utils.setupEdgeToEdge

/**
 * Base Activity that handles edge-to-edge setup for all activities
 * All activities should extend this class instead of AppCompatActivity directly
 */
abstract class BaseActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Enable edge-to-edge for Android 15+ (SDK 35)
        setupEdgeToEdge()
        
        super.onCreate(savedInstanceState)
    }
    
    /**
     * Call this method after setContentView() to apply window insets to root layout
     */
    protected fun applyEdgeToEdgeInsets() {
        findViewById<android.view.ViewGroup>(android.R.id.content)?.getChildAt(0)?.let { rootView ->
            rootView.applyTopAndBottomInsets()
        }
    }
}






































