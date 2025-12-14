package com.sofindo.ems.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.sofindo.ems.R

/**
 * Draws continuous vertical separator lines across the entire RecyclerView height.
 * The x-positions are inferred from the first visible child by locating the
 * column boundary Views with the given view ids, then drawn as full-height lines.
 */
class RecyclerColumnDecoration(
    private val context: Context,
    private val separatorColorRes: Int = R.color.gray
) : RecyclerView.ItemDecoration() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(separatorColorRes)
        strokeWidth = context.resources.displayMetrics.density // 1dp
    }

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        super.onDrawOver(c, parent, state)

        val child = parent.getChildAt(0) ?: return

        // Try to find separator positions using known separator Views (the 1dp Views between columns)
        val separatorIds = intArrayOf(
            R.id.sep1, R.id.sep2, R.id.sep3, R.id.sep4, R.id.sep5, R.id.sep6, R.id.sep7
        )

        val xPositions = mutableListOf<Float>()
        separatorIds.forEach { id ->
            val v = child.findViewById<View?>(id)
            if (v != null) {
                val x = (v.left + child.left + v.translationX)
                xPositions.add(x)
            }
        }

        // Fallback: if separators are not tagged, infer from child column right edges
        if (xPositions.isEmpty()) {
            // infer equal columns by dividing width by 8 (Date, In, Out, High, Low, Amp, Volt, RecBy)
            val startX = child.left.toFloat()
            val totalWidth = child.width.toFloat()
            val columns = 8
            val colWidth = totalWidth / columns
            for (i in 1 until columns) {
                xPositions.add(startX + colWidth * i)
            }
        }

        val top = parent.paddingTop.toFloat()
        val bottom = (parent.height - parent.paddingBottom).toFloat()

        xPositions.forEach { x ->
            c.drawLine(x, top, x, bottom, paint)
        }
    }
}


