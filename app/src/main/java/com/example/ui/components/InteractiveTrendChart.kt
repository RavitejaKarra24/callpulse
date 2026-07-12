package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.TrendPoint
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun InteractiveTrendChart(
    points: List<TrendPoint>,
    isDurationBased: Boolean,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No trend data available",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    var selectedPointIdx by remember { mutableStateOf<Int?>(null) }
    var touchX by remember { mutableFloatStateOf(0f) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val density = LocalDensity.current

    val maxValue = remember(points) { points.maxOfOrNull { it.value } ?: 1f }
    val yMax = remember(maxValue) { if (maxValue == 0f) 5f else maxValue * 1.15f }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f))
            .padding(top = 14.dp, bottom = 6.dp, start = 6.dp, end = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(points) {
                    // Horizontal scrub only — vertical drags pass through to LazyColumn
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var trackedIdx: Int? = null
                        var totalDx = 0f
                        var totalDy = 0f
                        var isHorizontal: Boolean? = null

                        val paddingLeft = 40f
                        val usableWidth = size.width - 80f
                        val step = if (points.size > 1) usableWidth / (points.size - 1) else usableWidth

                        fun indexFor(x: Float): Int {
                            return ((x - paddingLeft + step / 2f) / step)
                                .roundToInt()
                                .coerceIn(0, points.lastIndex)
                        }

                        // Tap selection
                        trackedIdx = indexFor(down.position.x)
                        selectedPointIdx = trackedIdx
                        touchX = paddingLeft + trackedIdx * step

                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break

                            val delta = change.positionChange()
                            totalDx += delta.x
                            totalDy += delta.y

                            if (isHorizontal == null && (abs(totalDx) > 8f || abs(totalDy) > 8f)) {
                                isHorizontal = abs(totalDx) > abs(totalDy)
                                if (isHorizontal == false) {
                                    // Vertical scroll intent — clear selection and stop consuming
                                    selectedPointIdx = null
                                    break
                                }
                            }

                            if (isHorizontal == true) {
                                change.consume()
                                trackedIdx = indexFor(change.position.x)
                                selectedPointIdx = trackedIdx
                                touchX = paddingLeft + trackedIdx * step
                            }
                        } while (true)

                        // Clear scrubber shortly after release for taps; keep brief
                        selectedPointIdx = null
                    }
                }
                .drawWithCache {
                    val paddingLeft = 40f
                    val paddingRight = 40f
                    val paddingTop = 10f
                    val paddingBottom = 46f
                    val usableWidth = size.width - paddingLeft - paddingRight
                    val usableHeight = size.height - paddingTop - paddingBottom
                    val bottomY = size.height - paddingBottom
                    val topY = paddingTop
                    val stepX = if (points.size > 1) usableWidth / (points.size - 1) else usableWidth

                    val coordinates = List(points.size) { idx ->
                        val x = paddingLeft + idx * stepX
                        val y = bottomY - (points[idx].value / yMax) * usableHeight
                        Offset(x, y)
                    }

                    val areaPath = Path()
                    val linePath = Path()
                    if (coordinates.size > 1) {
                        areaPath.moveTo(coordinates.first().x, bottomY)
                        coordinates.forEach { areaPath.lineTo(it.x, it.y) }
                        areaPath.lineTo(coordinates.last().x, bottomY)
                        areaPath.close()

                        linePath.moveTo(coordinates.first().x, coordinates.first().y)
                        for (i in 1 until coordinates.size) {
                            val prev = coordinates[i - 1]
                            val curr = coordinates[i]
                            val controlX = (prev.x + curr.x) / 2f
                            linePath.cubicTo(controlX, prev.y, controlX, curr.y, curr.x, curr.y)
                        }
                    }

                    val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    val strokeWidth = 1.dp.toPx()
                    val lineStroke = 3.dp.toPx()

                    onDrawBehind {
                        // Grid
                        for (g in 0..4) {
                            val fraction = g / 4f
                            val gridY = bottomY - fraction * usableHeight
                            drawLine(
                                color = gridColor,
                                start = Offset(paddingLeft, gridY),
                                end = Offset(size.width - paddingRight, gridY),
                                strokeWidth = strokeWidth,
                                pathEffect = dash
                            )
                        }

                        if (coordinates.size > 1) {
                            drawPath(
                                path = areaPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        primaryColor.copy(alpha = 0.32f),
                                        primaryColor.copy(alpha = 0f)
                                    ),
                                    startY = topY,
                                    endY = bottomY
                                )
                            )
                            drawPath(
                                path = linePath,
                                color = primaryColor,
                                style = Stroke(width = lineStroke, cap = StrokeCap.Round)
                            )
                        } else if (coordinates.size == 1) {
                            drawCircle(
                                color = primaryColor,
                                radius = 5.dp.toPx(),
                                center = coordinates.first()
                            )
                        }

                        // X ticks
                        coordinates.forEach { pt ->
                            drawLine(
                                color = gridColor,
                                start = Offset(pt.x, bottomY),
                                end = Offset(pt.x, bottomY + 6f),
                                strokeWidth = strokeWidth
                            )
                        }

                        // Scrubber drawn outside cache would be ideal; redraw here via selected state
                        // Selection overlay is cheap and only when selectedPointIdx changes
                    }
                }
                // Separate lightweight overlay for selection so static path stays cached
                .drawWithCache {
                    val paddingLeft = 40f
                    val paddingTop = 10f
                    val paddingBottom = 46f
                    val usableWidth = size.width - 80f
                    val usableHeight = size.height - paddingTop - paddingBottom
                    val bottomY = size.height - paddingBottom
                    val stepX = if (points.size > 1) usableWidth / (points.size - 1) else usableWidth

                    val coordinates = List(points.size) { idx ->
                        val x = paddingLeft + idx * stepX
                        val y = bottomY - (points[idx].value / yMax) * usableHeight
                        Offset(x, y)
                    }

                    onDrawWithContent {
                        drawContent()
                        selectedPointIdx?.let { idx ->
                            if (idx in coordinates.indices) {
                                val pt = coordinates[idx]
                                drawLine(
                                    color = primaryColor.copy(alpha = 0.55f),
                                    start = Offset(pt.x, paddingTop),
                                    end = Offset(pt.x, bottomY),
                                    strokeWidth = 1.5.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                                )
                                drawCircle(
                                    color = primaryColor.copy(alpha = 0.22f),
                                    radius = 11.dp.toPx(),
                                    center = pt
                                )
                                drawCircle(color = primaryColor, radius = 5.dp.toPx(), center = pt)
                                drawCircle(color = Color.White, radius = 2.dp.toPx(), center = pt)
                            }
                        }
                    }
                }
        )

        // X labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(start = 28.dp, end = 28.dp, bottom = 2.dp)
        ) {
            points.forEachIndexed { idx, pt ->
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = pt.label,
                        fontSize = 10.sp,
                        fontWeight = if (idx == selectedPointIdx) FontWeight.Bold else FontWeight.Normal,
                        color = if (idx == selectedPointIdx) primaryColor else onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = selectedPointIdx != null,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(120)),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            selectedPointIdx?.let { idx ->
                if (idx in points.indices) {
                    val pt = points[idx]
                    val maxOffsetPx = with(density) { 220.dp.toPx() }
                    Card(
                        modifier = Modifier.offset {
                            val targetX = touchX - 48.dp.toPx()
                            IntOffset(targetX.toInt().coerceIn(8, maxOffsetPx.toInt()), 0)
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.inverseSurface,
                            contentColor = MaterialTheme.colorScheme.inverseOnSurface
                        ),
                        shape = RoundedCornerShape(8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = pt.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isDurationBased) {
                                    formatMinutes(pt.rawDurationSeconds)
                                } else {
                                    "${pt.rawCount} calls"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primaryContainer
                            )
                        }
                    }
                }
            }
        }

        if (selectedPointIdx == null) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = onSurfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.size(11.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "Swipe horizontally to explore",
                    fontSize = 9.sp,
                    color = onSurfaceVariant.copy(alpha = 0.45f)
                )
            }
        }
    }
}

private fun formatMinutes(seconds: Long): String {
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return when {
        hrs > 0 -> "${hrs}h ${mins}m"
        mins > 0 -> "${mins}m ${secs}s"
        else -> "${secs}s"
    }
}
