package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val IncomingColor = Color(0xFF00C853)
private val OutgoingColor = Color(0xFF2979FF)
private val MissedColor = Color(0xFFFF1744)
private val RejectedColor = Color(0xFFFF9100)

@Composable
fun DonutChart(
    incomingCount: Int,
    outgoingCount: Int,
    missedCount: Int,
    rejectedCount: Int,
    modifier: Modifier = Modifier
) {
    val total = (incomingCount + outgoingCount + missedCount + rejectedCount).toFloat()
    val incomingAngle = if (total > 0f) (incomingCount / total) * 360f else 0f
    val outgoingAngle = if (total > 0f) (outgoingCount / total) * 360f else 0f
    val missedAngle = if (total > 0f) (missedCount / total) * 360f else 0f
    val rejectedAngle = if (total > 0f) (rejectedCount / total) * 360f else 0f

    val animatedProgress = remember { Animatable(0f) }
    LaunchedEffect(incomingCount, outgoingCount, missedCount, rejectedCount) {
        animatedProgress.snapTo(0f)
        animatedProgress.animateTo(1f, animationSpec = tween(durationMillis = 700))
    }

    val progress = animatedProgress.value
    val totalInt = total.toInt()
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(148.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(132.dp)
                    .drawWithCache {
                        val strokeWidth = 16.dp.toPx()
                        val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                        val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
                        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                        val gapAngle = 3f

                        onDrawBehind {
                            if (total == 0f) {
                                drawArc(
                                    color = Color.LightGray.copy(alpha = 0.3f),
                                    startAngle = 0f,
                                    sweepAngle = 360f,
                                    useCenter = false,
                                    topLeft = topLeft,
                                    size = arcSize,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                )
                            } else {
                                var start = -90f
                                fun drawSlice(angle: Float, color: Color) {
                                    if (angle <= 0f) return
                                    val sweep = (angle * progress - gapAngle).coerceAtLeast(0f)
                                    drawArc(
                                        color = color,
                                        startAngle = start,
                                        sweepAngle = sweep,
                                        useCenter = false,
                                        topLeft = topLeft,
                                        size = arcSize,
                                        style = stroke
                                    )
                                    start += angle
                                }
                                drawSlice(outgoingAngle, OutgoingColor)
                                drawSlice(incomingAngle, IncomingColor)
                                drawSlice(missedAngle, MissedColor)
                                drawSlice(rejectedAngle, RejectedColor)
                            }
                        }
                    }
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$totalInt",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Total",
                    style = MaterialTheme.typography.labelSmall,
                    color = onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.width(20.dp))

        Column(modifier = Modifier.weight(1f)) {
            LegendItem("Outgoing", outgoingCount, totalInt, OutgoingColor)
            Spacer(modifier = Modifier.height(8.dp))
            LegendItem("Incoming", incomingCount, totalInt, IncomingColor)
            Spacer(modifier = Modifier.height(8.dp))
            LegendItem("Missed", missedCount, totalInt, MissedColor)
            Spacer(modifier = Modifier.height(8.dp))
            LegendItem("Rejected", rejectedCount, totalInt, RejectedColor)
        }
    }
}

@Composable
private fun LegendItem(
    label: String,
    count: Int,
    total: Int,
    color: Color
) {
    val percentage = if (total > 0) (count.toFloat() / total * 100).toInt() else 0
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .drawWithCache {
                    onDrawBehind { drawCircle(color) }
                }
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "$count ($percentage%)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
