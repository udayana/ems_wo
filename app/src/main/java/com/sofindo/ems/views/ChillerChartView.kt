package com.sofindo.ems.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.sofindo.ems.models.ChillerRecord
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class ChillerChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    private var records: List<ChillerRecord> = emptyList()
    private var maxTemp: Double = 0.0
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
    
    fun setData(records: List<ChillerRecord>, maxTemp: Double = 0.0) {
        this.records = records
        this.maxTemp = maxTemp
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
        
        // Use all dates in month instead of just records
        val allDates = generateAllDatesInMonth()
        val totalContentWidth = allDates.size * (barWidth + barSpacing) + barSpacing
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
        val allDates = generateAllDatesInMonth()
        
        allDates.forEachIndexed { index, date ->
            // Find record for this date
            val record = records.find { 
                val recordCalendar = Calendar.getInstance()
                recordCalendar.time = it.date
                val dateCalendar = Calendar.getInstance()
                dateCalendar.time = date
                recordCalendar.get(Calendar.DAY_OF_MONTH) == dateCalendar.get(Calendar.DAY_OF_MONTH)
            }
            
            val temp = record?.tempRecord?.toDoubleOrNull() ?: 0.0
            val normalizedValue = if (maxValue > 0) temp / maxValue else 0.0
            val barHeight = (normalizedValue * chartHeight).toFloat()
            
            // Calculate bar position with scrolling
            val barLeft = chartLeft + scrollX + (index * (barWidth + barSpacing)) + barSpacing
            val barTop = chartBottom - barHeight
            
            // Only draw if bar is visible
            if (barLeft + barWidth >= chartLeft && barLeft <= chartRight) {
                // Determine color based on maxTemp threshold
                val isOverMaxTemp = maxTemp > 0 && temp > maxTemp
                val barColor = if (isOverMaxTemp) "#D32F2F" else "#2E7D32" // Red if over max, green otherwise
                val borderColor = if (isOverMaxTemp) "#B71C1C" else "#1B5E20" // Darker red/green
                
                // Draw bar
                paint.color = Color.parseColor(barColor)
                paint.style = Paint.Style.FILL
                canvas.drawRect(barLeft, barTop, barLeft + barWidth, chartBottom, paint)
                
                // Draw bar border
                paint.color = Color.parseColor(borderColor)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f
                canvas.drawRect(barLeft, barTop, barLeft + barWidth, chartBottom, paint)
                
                // Draw value on top of bar if there's space and data exists
                if (barHeight > 30 && record != null) {
                    textPaint.color = Color.parseColor(barColor)
                    textPaint.textSize = 30f
                    textPaint.textAlign = Paint.Align.CENTER
                    val value = formatNumber(temp)
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
        textPaint.color = Color.parseColor("#2E7D32") // Green for Y-axis labels
        
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
        
        // Draw X-axis labels (dates) - Show all dates from 1 to end of month
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 30f
        textPaint.color = Color.parseColor("#2E7D32") // Green for X-axis labels
        
        // Generate all dates for the month
        val allDates = generateAllDatesInMonth()
        
        allDates.forEachIndexed { index, date ->
            val barLeft = chartLeft + scrollX + (index * (barWidth + barSpacing)) + barSpacing
            val barCenter = barLeft + (barWidth / 2)
            
            // Only draw if label is visible
            if (barCenter >= chartLeft - 20f && barCenter <= chartRight + 20f) {
                val dateFormat = SimpleDateFormat("d", Locale.getDefault())
                canvas.drawText(
                    dateFormat.format(date),
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
            "Chiller Temperature Chart",
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
            record.tempRecord.toDoubleOrNull() ?: 0.0
        } ?: 0.0
    }
    
    private fun formatNumber(value: Double): String {
        return NumberFormat.getNumberInstance(Locale.getDefault()).format(value.toInt())
    }
    
    private fun generateAllDatesInMonth(): List<Date> {
        if (records.isEmpty()) return emptyList()
        
        val calendar = Calendar.getInstance()
        val firstRecord = records.first()
        calendar.time = firstRecord.date
        
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        
        val allDates = mutableListOf<Date>()
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        
        for (day in 1..daysInMonth) {
            val dateCalendar = Calendar.getInstance()
            dateCalendar.set(year, month, day)
            allDates.add(dateCalendar.time)
        }
        
        return allDates
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
