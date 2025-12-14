package com.sofindo.ems.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.View.MeasureSpec
import com.sofindo.ems.models.TemperatureRecord
import java.text.SimpleDateFormat
import java.util.*

class TemperatureChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    private var records: List<TemperatureRecord> = emptyList()
    private var maxTempSetting: Double = 0.0
    
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
    
    // Bar dimensions (larger bars like other chart views)
    private val barWidth = 18f
    private val barSpacing = 6f
    private val padding = 80f
    
    private val chartColor = Color.parseColor("#1abc9c")
    private val maxTempColor = Color.RED
    private val gridColor = Color.parseColor("#E0E0E0")
    private val textColor = Color.parseColor("#666666")
    
    fun setData(records: List<TemperatureRecord>, maxTemp: Double) {
        this.records = records
        this.maxTempSetting = maxTemp
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
    }
    
    private fun drawBackground(canvas: Canvas) {
        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL
        canvas.drawRect(chartLeft, chartTop, chartRight, chartBottom, paint)
        
        paint.color = gridColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawRect(chartLeft, chartTop, chartRight, chartBottom, paint)
        paint.style = Paint.Style.FILL
    }
    
    private fun drawAxes(canvas: Canvas) {
        val maxValue = getMaxTempValue()
        val stepCount = 5
        val stepValue = maxValue / stepCount
        
        paint.color = gridColor
        paint.strokeWidth = 1f
        
        for (i in 0..stepCount) {
            val y = chartBottom - (i * chartHeight / stepCount)
            canvas.drawLine(chartLeft, y, chartRight, y, paint)
        }
    }
    
    private fun drawData(canvas: Canvas) {
        if (records.isEmpty()) return
        
        val maxValue = getMaxTempValue()
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
                if (temp > 0) {
                    // Determine bar color based on maxTempSetting
                    val barColor = if (maxTempSetting > 0 && temp >= maxTempSetting) {
                        maxTempColor
                    } else {
                        chartColor
                    }
                    
                    // Draw bar
                    paint.color = barColor
                    paint.style = Paint.Style.FILL
                    canvas.drawRoundRect(
                        barLeft, barTop, barLeft + barWidth, chartBottom, 3f, 3f, paint
                    )
                    
                    // Draw value on top of bar if there's space
                    if (barHeight > 20 && record != null) {
                        textPaint.color = barColor
                        textPaint.textSize = 20f
                        textPaint.textAlign = Paint.Align.CENTER
                        val valueText = String.format("%.1f°", temp)
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
        val maxValue = getMaxTempValue()
        val stepCount = 5
        val stepValue = maxValue / stepCount
        
        // Draw Y-axis labels
        textPaint.textSize = axisLabelSize
        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.color = textColor
        
        for (i in 0..stepCount) {
            val value = i * stepValue
            val y = chartBottom - (i * chartHeight / stepCount)
            canvas.drawText(
                String.format("%.1f°", value),
                chartLeft - 10f,
                y + 4f,
                textPaint
            )
        }
        
        // Draw X-axis labels (dates) - Show every 3rd day
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 30f
        textPaint.color = textColor
        
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
        textPaint.color = chartColor
        canvas.drawText(
            "Temperature Trend",
            width / 2f,
            chartTop - 50f,
            textPaint
        )
    }
    
    private fun drawLegend(canvas: Canvas) {
        val legendY = chartTop + 10f
        val legendSpacing = 120f
        
        // Red legend item (Above Max Temp)
        paint.color = maxTempColor
        paint.style = Paint.Style.FILL
        val redLegendX = chartLeft + 20f
        val redRect = RectF(redLegendX, legendY, redLegendX + 20f, legendY + 20f)
        canvas.drawRoundRect(redRect, 4f, 4f, paint)
        
        textPaint.textSize = 24f
        textPaint.color = Color.BLACK
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Above Max", redLegendX + 30f, legendY + 15f, textPaint)
        
        // Green/Teal legend item (Normal)
        val greenLegendX = redLegendX + legendSpacing
        paint.color = chartColor
        val greenRect = RectF(greenLegendX, legendY, greenLegendX + 20f, legendY + 20f)
        canvas.drawRoundRect(greenRect, 4f, 4f, paint)
        
        canvas.drawText("Normal", greenLegendX + 30f, legendY + 15f, textPaint)
    }
    
    private fun drawNoDataMessage(canvas: Canvas) {
        textPaint.textSize = 32f
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = Color.parseColor("#999999")
        canvas.drawText(
            "No temperature data",
            width / 2f,
            height / 2f,
            textPaint
        )
    }
    
    private fun getMaxTempValue(): Double {
        if (records.isEmpty()) return 10.0
        
        val maxTemp = records.maxOfOrNull { record ->
            record.tempRecord.toDoubleOrNull() ?: 0.0
        } ?: 10.0
        
        return if (maxTemp == 0.0) 10.0 else (maxTemp * 1.1) // Add 10% padding
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
