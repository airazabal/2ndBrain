package com.alex.a2ndbrain.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val pillarColors = listOf(
    Color(0xFF1E88E5),  // Steps — blue
    Color(0xFF7E57C2),  // Sleep — purple
    Color(0xFF43A047),  // Exercise — green
    Color(0xFFFF9800)   // Focus — orange
)

@Composable
fun SenseOfDayWidget(
    score: Int,
    context: String,
    pillars: List<SenseOfDayPillar>,
    onPillarClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val animatedScore by animateFloatAsState(
        targetValue = score.toFloat(),
        animationSpec = tween(900),
        label = "sod_score"
    )

    val hue = (animatedScore / 100f * 120f).coerceIn(0f, 120f)
    val scoreColor = Color.hsv(hue, 0.72f, 0.88f)

    // Animate each pillar's progress independently
    val animP0 by animateFloatAsState(targetValue = pillars.getOrNull(0)?.progress ?: 0f, animationSpec = tween(700), label = "p0")
    val animP1 by animateFloatAsState(targetValue = pillars.getOrNull(1)?.progress ?: 0f, animationSpec = tween(700), label = "p1")
    val animP2 by animateFloatAsState(targetValue = pillars.getOrNull(2)?.progress ?: 0f, animationSpec = tween(700), label = "p2")
    val animP3 by animateFloatAsState(targetValue = pillars.getOrNull(3)?.progress ?: 0f, animationSpec = tween(700), label = "p3")
    val animatedProgresses = listOf(animP0, animP1, animP2, animP3)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SENSE OF DAY",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Text(
                    text = "$score / 100",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = scoreColor
                )
            }

            Spacer(Modifier.height(12.dp))

            // Arc + context text
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(100.dp)
                ) {
                    Canvas(modifier = Modifier.size(100.dp)) {
                        val strokePx = 11.dp.toPx()
                        val inset = strokePx / 2f
                        val arcSize = Size(size.width - strokePx, size.height - strokePx)
                        val topLeft = Offset(inset, inset)

                        val totalArc = 270f
                        val gapDeg = 4f
                        val segmentDeg = (totalArc - 3 * gapDeg) / 4f  // ~64.5°

                        pillarColors.forEachIndexed { i, color ->
                            val segStart = 135f + i * (segmentDeg + gapDeg)
                            val progress = animatedProgresses.getOrElse(i) { 0f }
                            // Dim background track for this segment
                            drawArc(
                                color = color.copy(alpha = 0.18f),
                                startAngle = segStart,
                                sweepAngle = segmentDeg,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokePx, cap = StrokeCap.Round)
                            )
                            // Progress fill
                            val fillSweep = progress * segmentDeg
                            if (fillSweep > 0f) {
                                drawArc(
                                    color = color,
                                    startAngle = segStart,
                                    sweepAngle = fillSweep,
                                    useCenter = false,
                                    topLeft = topLeft,
                                    size = arcSize,
                                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                                )
                            }
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$score",
                            fontSize = 30.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = scoreColor,
                            lineHeight = 30.sp
                        )
                        Text(
                            text = "/ 100",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            lineHeight = 10.sp
                        )
                    }
                }

                Text(
                    text = context,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    modifier = Modifier.weight(1f)
                )
            }

            if (pillars.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pillars.forEachIndexed { i, pillar ->
                        PillarIndicator(
                            pillar = pillar,
                            color = pillarColors.getOrElse(i) { Color.Gray },
                            onClick = { onPillarClick(pillar.label) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PillarIndicator(
    pillar: SenseOfDayPillar,
    color: Color,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = pillar.progress,
        animationSpec = tween(700),
        label = "pillar_${pillar.label}"
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = pillar.label.uppercase(),
                fontSize = 8.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                maxLines = 1
            )
            if (pillar.progress >= 1f) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Goal met",
                    tint = color,
                    modifier = Modifier.size(8.dp)
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = pillar.value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = pillar.goalText,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(5.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(color = Color.Gray.copy(alpha = 0.18f))
                if (animatedProgress > 0f) {
                    drawRect(
                        color = color.copy(alpha = 0.8f),
                        size = Size(size.width * animatedProgress, size.height)
                    )
                }
            }
        }
    }
}
