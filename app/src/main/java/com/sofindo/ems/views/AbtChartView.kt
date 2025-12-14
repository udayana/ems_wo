package com.sofindo.ems.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.sofindo.ems.models.ABTRecord
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class AbtChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    private var records: List<ABTRecord> = emptyList()
    private var chartWidth = 0f
    private var chartHeight = 0f
    private var chartLeft = 0f
    private var chartRight = 0f
    private var chartTop = 0f
    private var chartBottom = 0f
    
    private val axisLabelSize = 30f
    private val titleSize = 36f
    
    // Scrolling variables
    private var scrollX = 0f
    private var lastTouchX = 0f
    private var isScrolling = false
    private var minScrollX = 0f
    private var maxScrollX = 0f
    
    // Bar dimensions
    private val barWidth = 40f
    private val barSpacing = 20f
    
    fun setData(records: List<ABTRecord>) {
        this.records = records
        calculateScrollBounds()
        invalidate()
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        
        chartLeft = 60f
        chartRight = width - 20f
        chartTop = 100f  // Increased to add space after title
        chartBottom = height - 60f
        chartWidth = chartRight - chartLeft
        chartHeight = chartBottom - chartTop
        
        calculateScrollBounds()
    }
    
    private fun calculateScrollBounds() {
        if (records.isEmpty()) {
            minScrollX = 0f
            maxScrollX = 0f
            return
        }
        
        val totalContentWidth = records.size * (barWidth + barSpacing) + barSpacing
        val visibleWidth = chartWidth
        
        if (totalContentWidth <= visibleWidth) {
            minScrollX = 0f
            maxScrollX = 0f
        } else {
            minScrollX = -(totalContentWidth - visibleWidth)
            maxScrollX = 0f
        }
        
        // Ensure scrollX is within bounds
        scrollX = scrollX.coerceIn(minScrollX, maxScrollX)
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (records.isEmpty()) {
            drawNoDataMessage(canvas)
            return
        }
        
        drawBackground(canvas)
        drawAxes(canvas)
        drawData(canvas)
        drawLabels(canvas)
        drawTitle(canvas)
    }
    
    private fun drawBackground(canvas: Canvas) {
        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        
        // Draw grid lines
        paint.color = Color.parseColor("#F0F0F0")
        paint.strokeWidth = 1f
        paint.style = Paint.Style.STROKE
        
        val stepCount = 5
        for (i in 0..stepCount) {
            val y = chartTop + (i * chartHeight / stepCount)
            canvas.drawLine(chartLeft, y, chartRight, y, paint)
        }
    }
    
    private fun drawAxes(canvas: Canvas) {
        paint.color = Color.parseColor("#333333")
        paint.strokeWidth = 2f
        paint.style = Paint.Style.STROKE
        
        // Y-axis
        canvas.drawLine(chartLeft, chartTop, chartLeft, chartBottom, paint)
        
        // X-axis
        canvas.drawLine(chartLeft, chartBottom, chartRight, chartBottom, paint)
    }
    
    private fun drawData(canvas: Canvas) {
        if (records.isEmpty()) return
        
        val maxValue = getMaxValue()
        
        records.forEachIndexed { index, record ->
            val consume = record.consume.toDoubleOrNull() ?: 0.0
            val normalizedValue = consume / maxValue
            val barHeight = (normalizedValue * chartHeight).toFloat()
            
            // Calculate bar position with scrolling
            val barLeft = chartLeft + scrollX + (index * (barWidth + barSpacing)) + barSpacing
            val barTop = chartBottom - barHeight
            
            // Only draw if bar is visible
            if (barLeft + barWidth >= chartLeft && barLeft <= chartRight) {
                // Draw bar
                paint.color = Color.parseColor("#2E7D32") // Standard green
                paint.style = Paint.Style.FILL
                canvas.drawRect(barLeft, barTop, barLeft + barWidth, chartBottom, paint)
                
                // Draw bar border
                paint.color = Color.parseColor("#1B5E20") // Darker green
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f
                canvas.drawRect(barLeft, barTop, barLeft + barWidth, chartBottom, paint)
                
                // Draw value on top of bar if there's space
                if (barHeight > 30) {
                    textPaint.color = Color.parseColor("#2E7D32")
                    textPaint.textSize = 30f
                    textPaint.textAlign = Paint.Align.CENTER
                    val value = formatNumber(consume)
                    canvas.drawText(
                        value,
                        barLeft + (barWidth / 2),
                        barTop - 10,
                        textPaint
                    )
                }
            }
        }
    }
    
    private fun drawLabels(canvas: Canvas) {
        val maxValue = getMaxValue()
        val stepCount = 5
        val stepValue = maxValue / stepCount
        
        // Draw Y-axis labels
        textPaint.textSize = axisLabelSize
        textPaint.textAlign = Paint.Align.RIGHT
        
        for (i in 0..stepCount) {
            val value = i * stepValue
            val y = chartBottom - (i * chartHeight / stepCount)
            canvas.drawText(
                formatNumber(value),
                chartLeft - 10f,
                y + 4f,
                textPaint
            )
        }
        
        // Draw X-axis labels (dates)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 30f
        
        records.forEachIndexed { index, record ->
            val barLeft = chartLeft + scrollX + (index * (barWidth + barSpacing)) + barSpacing
            val barCenter = barLeft + (barWidth / 2)
            
            // Only draw if label is visible
            if (barCenter >= chartLeft - 20f && barCenter <= chartRight + 20f) {
                val dateFormat = SimpleDateFormat("d", Locale.getDefault())
                canvas.drawText(
                    dateFormat.format(record.date),
                    barCenter,
                    chartBottom + 40f,
                    textPaint
                )
            }
        }
    }
    
    private fun drawTitle(canvas: Canvas) {
        textPaint.textSize = titleSize
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = Color.parseColor("#2E7D32")
        canvas.drawText(
            "ABT Consumption Chart",
            width / 2f,
            chartTop - 50f,
            textPaint
        )
    }
    
    private fun drawNoDataMessage(canvas: Canvas) {
        textPaint.textSize = 16f
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = Color.parseColor("#666666")
        canvas.drawText(
            "No data available",
            width / 2f,
            height / 2f,
            textPaint
        )
    }
    
    private fun getMaxValue(): Double {
        if (records.isEmpty()) return 1.0
        
        return records.maxOfOrNull { record ->
            record.consume.toDoubleOrNull() ?: 0.0
        } ?: 0.0
    }
    
    private fun formatNumber(value: Double): String {
        return NumberFormat.getNumberInstance(Locale.getDefault()).format(value.toInt())
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                isScrolling = true
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isScrolling) {
                    val deltaX = event.x - lastTouchX
                    scrollX = (scrollX + deltaX).coerceIn(minScrollX, maxScrollX)
                    lastTouchX = event.x
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isScrolling = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
