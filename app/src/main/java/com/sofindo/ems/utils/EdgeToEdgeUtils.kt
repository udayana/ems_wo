package com.sofindo.ems.utils

import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowCompat
import androidx.core.view.updatePadding

/**
 * Utility functions for handling edge-to-edge display on Android 15+
 * Uses modern APIs compatible with Android 15 (API 35)
 */

/**
 * Enable edge-to-edge for the activity and apply proper insets handling
 * Uses modern APIs that are not deprecated in Android 15
 * 
 * Note: enableEdgeToEdge() already handles:
 * - WindowCompat.setDecorFitsSystemWindows(window, false)
 * - Setting up proper edge-to-edge configuration
 */
fun AppCompatActivity.setupEdgeToEdge() {
    // Enable edge-to-edge using modern API (required for Android 15+ targeting SDK 35)
    // This replaces deprecated Window.setDecorFitsSystemWindows()
    enableEdgeToEdge()
    
    // Set up window insets controller using WindowInsetsControllerCompat (not deprecated)
    // This replaces deprecated WindowInsetsController direct usage
    val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
    windowInsetsController?.let { controller ->
        // Use modern behavior constant from WindowInsetsControllerCompat
        controller.systemBarsBehavior = 
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

/**
 * Apply window insets to a view (typically the root view)
 * This ensures content is not hidden behind system bars
 */
fun View.applyWindowInsets(
    applyTop: Boolean = true,
    applyBottom: Boolean = true,
    applyLeft: Boolean = true,
    applyRight: Boolean = true
) {
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        
        view.updatePadding(
            top = if (applyTop) systemBars.top else view.paddingTop,
            bottom = if (applyBottom) systemBars.bottom else view.paddingBottom,
            left = if (applyLeft) systemBars.left else view.paddingLeft,
            right = if (applyRight) systemBars.right else view.paddingRight
        )
        
        insets
    }
}

/**
 * Apply window insets to a view, but only for top (status bar)
 */
fun View.applyTopInsets() {
    applyWindowInsets(applyTop = true, applyBottom = false, applyLeft = false, applyRight = false)
}

/**
 * Apply window insets to a view, but only for bottom (navigation bar)
 */
fun View.applyBottomInsets() {
    applyWindowInsets(applyTop = false, applyBottom = true, applyLeft = false, applyRight = false)
}

/**
 * Apply window insets to a view for both top and bottom
 */
fun View.applyTopAndBottomInsets() {
    applyWindowInsets(applyTop = true, applyBottom = true, applyLeft = false, applyRight = false)
}

