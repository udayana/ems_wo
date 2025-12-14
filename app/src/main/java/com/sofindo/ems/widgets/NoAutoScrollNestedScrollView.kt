package com.sofindo.ems.widgets

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.core.widget.NestedScrollView

class NoAutoScrollNestedScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : NestedScrollView(context, attrs, defStyleAttr) {

    private var preventAutoScroll = false

    fun setPreventAutoScroll(prevent: Boolean) {
        preventAutoScroll = prevent
    }

    override fun requestChildFocus(child: View?, focused: View?) {
        // Always call super to ensure proper focus handling
        super.requestChildFocus(child, focused)
        
        // If preventAutoScroll is enabled and focused view is EditText, prevent scrolling
        if (preventAutoScroll && focused is android.widget.EditText) {
            // Post to prevent scroll after focus is set
            post {
                scrollTo(0, 0)
            }
        }
    }
}

