package com.sofindo.ems.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.sofindo.ems.models.FuelRecord
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class FuelChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    private var records: List<FuelRecord> = emptyList()
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
    
    // Bar dimensions (larger bars like WatertankChartView)
    private val barWidth = 18f
    private val barSpacing = 6f
    private val padding = 80f
    
    fun setData(records: List<FuelRecord>) {
        this.records = records
        calculateScrollBounds()
        invalidate()
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        
        chartLeft = 60f
        chartRight = width - 20f
        chartTop = 100f  // Space after title
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
        
        // Use all dates in month
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
        paint.color = Color.parseColor("#1abc9c") // EMS green
        paint.strokeWidth = 2f
        paint.style = Paint.Style.STROKE
        
        // Y-axis
        canvas.drawLine(chartLeft, chartTop, chartLeft, chartBottom, paint)
        
        // X-axis
        canvas.drawLine(chartLeft, chartBottom, chartRight, chartBottom, paint)
    }
    
    private fun drawData(canvas: Canvas) {
        if (records.isEmpty()) return
        
        val maxValue = getMaxFuelValue()
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
            
            val fuel = record?.newFuel?.toDoubleOrNull() ?: 0.0
            val normalizedValue = if (maxValue > 0) fuel / maxValue else 0.0
            val barHeight = (normalizedValue * chartHeight).toFloat()
            
            // Calculate bar position with scrolling
            val barLeft = chartLeft + scrollX + (index * (barWidth + barSpacing)) + barSpacing
            val barTop = chartBottom - barHeight
            
            // Only draw if bar is visible
            if (barLeft + barWidth >= chartLeft && barLeft <= chartRight) {
                if (fuel > 0) {
                    // Draw bar
                    paint.color = Color.parseColor("#1abc9c")
                    paint.style = Paint.Style.FILL
                    canvas.drawRoundRect(
                        barLeft, barTop, barLeft + barWidth, chartBottom, 3f, 3f, paint
                    )
                    
                    // Draw value on top of bar if there's space
                    if (barHeight > 20 && record != null) {
                        textPaint.color = Color.parseColor("#1abc9c")
                        textPaint.textSize = 20f
                        textPaint.textAlign = Paint.Align.CENTER
                        val valueText = formatValue(fuel)
                        canvas.drawText(
                            valueText,
                            barLeft + (barWidth / 2),
                            barTop - 8,
                            textPaint
                        )
                    }
                } else {
                    // Draw gray placeholder for empty days
                    paint.color = Color.parseColor("#E0E0E0")
                    paint.style = Paint.Style.FILL
                    canvas.drawRect(
                        barLeft, chartBottom - 2f, barLeft + barWidth, chartBottom, paint
                    )
                }
            }
        }
    }
    
    private fun drawLabels(canvas: Canvas) {
        val maxValue = getMaxFuelValue()
        val stepCount = 5
        val stepValue = maxValue / stepCount
        
        // Draw Y-axis labels
        textPaint.textSize = axisLabelSize
        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.color = Color.parseColor("#666666")
        
        for (i in 0..stepCount) {
            val value = i * stepValue
            val y = chartBottom - (i * chartHeight / stepCount)
            canvas.drawText(
                formatValue(value),
                chartLeft - 10f,
                y + 4f,
                textPaint
            )
        }
        
        // Draw unit label
        textPaint.textSize = 20f
        canvas.drawText("ltr", chartLeft - 10f, chartTop - 10f, textPaint)
        
        // Draw X-axis labels (dates) - Show every 3rd day
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 30f
        textPaint.color = Color.parseColor("#666666")
        
        val allDates = generateAllDatesInMonth()
        
        allDates.forEachIndexed { index, date ->
            // Show label only every 3rd day
            if (index % 3 == 0) {
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
    }
    
    private fun drawTitle(canvas: Canvas) {
        textPaint.textSize = titleSize
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = Color.parseColor("#1abc9c")
        canvas.drawText(
            "Fuel Consumption Trend",
            width / 2f,
            chartTop - 50f,
            textPaint
        )
    }
    
    private fun drawNoDataMessage(canvas: Canvas) {
        textPaint.textSize = 32f
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = Color.parseColor("#999999")
        canvas.drawText(
            "No fuel data",
            width / 2f,
            height / 2f,
            textPaint
        )
    }
    
    private fun getMaxFuelValue(): Double {
        if (records.isEmpty()) return 100.0
        
        val maxFuel = records.maxOfOrNull { record ->
            record.newFuel.toDoubleOrNull() ?: 0.0
        } ?: 100.0
        
        return if (maxFuel == 0.0) 100.0 else maxFuel
    }
    
    private fun formatValue(value: Double): String {
        return if (value == value.toInt().toDouble()) {
            "${value.toInt()}"
        } else {
            String.format(Locale.US, "%.1f", value)
        }
    }
    
    private fun generateAllDatesInMonth(): List<Date> {
        if (records.isEmpty()) {
            // If no records, use current month
            val calendar = Calendar.getInstance()
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
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Use fixed height based on typical chart dimensions
        // chartTop (100) + chartHeight (320) + date labels (60) + some padding
        val desiredHeight = 480 // ~320dp for chart + ~160dp for title and labels
        
        val width = resolveSize(MeasureSpec.getSize(widthMeasureSpec), widthMeasureSpec)
        val height = resolveSize(desiredHeight, heightMeasureSpec)
        
        setMeasuredDimension(width, height)
    }
}
