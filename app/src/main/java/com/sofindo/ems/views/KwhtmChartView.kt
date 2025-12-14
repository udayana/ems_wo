package com.sofindo.ems.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.sofindo.ems.models.KwhtmRecord
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class KwhtmChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    private var records: List<KwhtmRecord> = emptyList()
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
    
    // Bar dimensions (larger bars like FuelChartView)
    private val barWidth = 18f
    private val barSpacing = 6f
    private val groupBarGap = 4f // Gap between LWBP and WBP bars in a group
    private val padding = 80f
    
    // Colors
    private val lwbpColor = Color.parseColor("#3498db") // Blue
    private val lwbpColorDark = Color.parseColor("#2980b9") // Darker blue
    private val wbpColor = Color.parseColor("#e74c3c") // Red
    private val wbpColorDark = Color.parseColor("#c0392b") // Darker red
    
    fun updateData(records: List<KwhtmRecord>) {
        this.records = records.sortedBy { it.date }
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
        val totalContentWidth = allDates.size * (barWidth * 2 + groupBarGap + barSpacing) + barSpacing
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
            
            val consumeLWBP = record?.consumeLWBP?.toDoubleOrNull() ?: 0.0
            val consumeWBP = record?.consumeWBP?.toDoubleOrNull() ?: 0.0
            
            val normalizedLWBP = if (maxValue > 0) consumeLWBP / maxValue else 0.0
            val normalizedWBP = if (maxValue > 0) consumeWBP / maxValue else 0.0
            
            val barHeightLWBP = (normalizedLWBP * chartHeight).toFloat()
            val barHeightWBP = (normalizedWBP * chartHeight).toFloat()
            
            // Calculate bar position with scrolling
            val groupLeft = chartLeft + scrollX + (index * (barWidth * 2 + groupBarGap + barSpacing)) + barSpacing
            val lwbpLeft = groupLeft
            val wbpLeft = groupLeft + barWidth + groupBarGap
            
            val lwbpTop = chartBottom - barHeightLWBP
            val wbpTop = chartBottom - barHeightWBP
            
            // Only draw if bars are visible
            if (groupLeft + (barWidth * 2) + groupBarGap >= chartLeft && groupLeft <= chartRight) {
                // Draw LWBP bar
                if (consumeLWBP > 0) {
                    paint.color = lwbpColor
                    paint.style = Paint.Style.FILL
                    canvas.drawRoundRect(
                        lwbpLeft, lwbpTop, lwbpLeft + barWidth, chartBottom, 3f, 3f, paint
                    )
                    
                    // Draw LWBP border
                    paint.color = lwbpColorDark
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 1.5f
                    canvas.drawRoundRect(
                        lwbpLeft, lwbpTop, lwbpLeft + barWidth, chartBottom, 3f, 3f, paint
                    )
                    
                    paint.style = Paint.Style.FILL
                }
                
                // Draw WBP bar
                if (consumeWBP > 0) {
                    paint.color = wbpColor
                    paint.style = Paint.Style.FILL
                    canvas.drawRoundRect(
                        wbpLeft, wbpTop, wbpLeft + barWidth, chartBottom, 3f, 3f, paint
                    )
                    
                    // Draw WBP border
                    paint.color = wbpColorDark
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 1.5f
                    canvas.drawRoundRect(
                        wbpLeft, wbpTop, wbpLeft + barWidth, chartBottom, 3f, 3f, paint
                    )
                    
                    paint.style = Paint.Style.FILL
                }
                
                // Draw placeholders for empty data
                if (consumeLWBP == 0.0 && consumeWBP == 0.0) {
                    paint.color = Color.parseColor("#E0E0E0")
                    paint.style = Paint.Style.FILL
                    canvas.drawRect(
                        groupLeft, chartBottom - 2f, groupLeft + (barWidth * 2) + groupBarGap, chartBottom, paint
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
        canvas.drawText("kwh", chartLeft - 10f, chartTop - 10f, textPaint)
        
        // Draw X-axis labels (dates) - Show every 3rd day
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 30f
        textPaint.color = Color.parseColor("#666666")
        
        val allDates = generateAllDatesInMonth()
        
        allDates.forEachIndexed { index, date ->
            // Show label only every 3rd day
            if (index % 3 == 0) {
                val groupLeft = chartLeft + scrollX + (index * (barWidth * 2 + groupBarGap + barSpacing)) + barSpacing
                val groupCenter = groupLeft + barWidth + (groupBarGap / 2)
                
                // Only draw if label is visible
                if (groupCenter >= chartLeft - 20f && groupCenter <= chartRight + 20f) {
                    val dateFormat = SimpleDateFormat("d", Locale.getDefault())
                    canvas.drawText(
                        dateFormat.format(date),
                        groupCenter,
                        chartBottom + 40f,
                        textPaint
                    )
                }
            }
        }
    }
    
    private fun drawTitle(canvas: Canvas) {
        // Draw title and legend side by side
        textPaint.textSize = titleSize
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = Color.parseColor("#1abc9c")
        canvas.drawText(
            "KWH Consumption Trend",
            width / 2f,
            chartTop - 50f,
            textPaint
        )
        
        // Draw legend below title
        drawLegend(canvas)
    }
    
    private fun drawLegend(canvas: Canvas) {
        val legendY = chartTop - 15f
        val legendItemHeight = 16f
        val legendItemWidth = 16f
        val legendSpacing = 30f
        
        // Calculate title width to position legend below it
        textPaint.textSize = 14f
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = Color.parseColor("#333333")
        
        // LWBP Legend (Blue)
        val legendStartX = width / 2f - 50f
        paint.color = lwbpColor
        paint.style = Paint.Style.FILL
        canvas.drawRect(legendStartX, legendY - legendItemHeight/2, legendStartX + legendItemWidth, legendY + legendItemHeight/2, paint)
        
        textPaint.color = Color.parseColor("#333333")
        textPaint.textSize = 14f
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("LWBP", legendStartX + legendItemWidth + 6f, legendY + 5f, textPaint)
        
        // WBP Legend (Red)
        val wbpLegendX = legendStartX + legendItemWidth + 6f + textPaint.measureText("LWBP") + legendSpacing
        paint.color = wbpColor
        paint.style = Paint.Style.FILL
        canvas.drawRect(wbpLegendX, legendY - legendItemHeight/2, wbpLegendX + legendItemWidth, legendY + legendItemHeight/2, paint)
        
        textPaint.color = Color.parseColor("#333333")
        textPaint.textSize = 14f
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("WBP", wbpLegendX + legendItemWidth + 6f, legendY + 5f, textPaint)
    }
    
    private fun drawNoDataMessage(canvas: Canvas) {
        textPaint.textSize = 32f
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = Color.parseColor("#999999")
        canvas.drawText(
            "No data available",
            width / 2f,
            height / 2f,
            textPaint
        )
    }
    
    private fun getMaxValue(): Double {
        if (records.isEmpty()) return 100.0
        
        val maxLWBP = records.maxOfOrNull { record ->
            record.consumeLWBP.toDoubleOrNull() ?: 0.0
        } ?: 0.0
        
        val maxWBP = records.maxOfOrNull { record ->
            record.consumeWBP.toDoubleOrNull() ?: 0.0
        } ?: 0.0
        
        val maxValue = maxOf(maxLWBP, maxWBP)
        return if (maxValue == 0.0) 100.0 else maxValue
    }
    
    private fun formatValue(value: Double): String {
        return NumberFormat.getNumberInstance(Locale.getDefault()).format(value.toInt())
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
