package com.chris.financeapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chris.financeapp.ui.theme.TextPrimary
import com.chris.financeapp.ui.theme.TextSecondary
import java.util.Locale

data class DonutSlice(
    val value: Double,
    val color: Color,
    val label: String
)

@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    centerText: String,
    centerValue: String,
    modifier: Modifier = Modifier
) {
    val total = slices.sumOf { it.value }
    
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            val width = size.width
            val height = size.height
            val minSize = minOf(width, height)
            val strokeWidth = 32.dp.toPx()
            
            val boundingBox = Size(minSize - strokeWidth, minSize - strokeWidth)
            val offset = Offset((width - minSize + strokeWidth) / 2, (height - minSize + strokeWidth) / 2)
            
            if (total == 0.0) {
                // Draw a simple gray placeholder donut
                drawArc(
                    color = Color.LightGray.copy(alpha = 0.2f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = offset,
                    size = boundingBox,
                    style = Stroke(width = strokeWidth)
                )
            } else {
                var startAngle = -90f
                slices.forEach { slice ->
                    val sweepAngle = ((slice.value / total) * 360f).toFloat()
                    drawArc(
                        color = slice.color,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = offset,
                        size = boundingBox,
                        style = Stroke(width = strokeWidth)
                    )
                    startAngle += sweepAngle
                }
            }
        }
        
        // Center text container
        Box(contentAlignment = Alignment.Center) {
            androidx.compose.foundation.layout.Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = centerText,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = centerValue,
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun LineChart(
    pensionValues: List<Double>,
    savingsValues: List<Double>,
    ages: List<Int>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.padding(horizontal = 16.dp, vertical = 24.dp)) {
        val width = size.width
        val height = size.height
        
        val maxVal = maxOf(
            pensionValues.maxOrNull() ?: 1.0,
            savingsValues.maxOrNull() ?: 1.0,
            100000.0 // Min scale floor
        )
        
        val dataCount = ages.size
        if (dataCount < 2) return@Canvas
        
        val stepX = width / (dataCount - 1)
        
        // Draw grid lines
        val gridCount = 5
        for (i in 0 until gridCount) {
            val y = height - (i * (height / (gridCount - 1)))
            drawLine(
                color = Color.White.copy(alpha = 0.1f),
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        // Helper to map values to Y screen space
        fun mapToY(value: Double): Float {
            val scale = value / maxVal
            return (height - (scale * (height - 30.dp.toPx()))).toFloat()
        }

        // Draw Pension line path (Amber/Orange)
        val pensionPath = Path().apply {
            moveTo(0f, mapToY(pensionValues[0]))
            for (i in 1 until dataCount) {
                lineTo(i * stepX, mapToY(pensionValues[i]))
            }
        }
        
        drawPath(
            path = pensionPath,
            color = Color(0xFFF59E0B),
            style = Stroke(width = 3.dp.toPx())
        )

        // Draw Savings line path (Teal/Indigo)
        val savingsPath = Path().apply {
            moveTo(0f, mapToY(savingsValues[0]))
            for (i in 1 until dataCount) {
                lineTo(i * stepX, mapToY(savingsValues[i]))
            }
        }
        
        drawPath(
            path = savingsPath,
            color = Color(0xFF06B6D4),
            style = Stroke(width = 3.dp.toPx())
        )

        // Draw Age indicators (every 10 ages)
        for (i in 0 until dataCount step 10) {
            val x = i * stepX
            drawLine(
                color = Color.White.copy(alpha = 0.05f),
                start = Offset(x, 0f),
                end = Offset(x, height),
                strokeWidth = 1.dp.toPx()
            )
        }
    }
}
