package com.alex.a2ndbrain.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun burnoutColor(level: BurnoutLevel) = when (level) {
    BurnoutLevel.LOW      -> Color(0xFF388E3C)
    BurnoutLevel.MODERATE -> Color(0xFFF57C00)
    BurnoutLevel.HIGH     -> Color(0xFFE64A19)
    BurnoutLevel.CRITICAL -> Color(0xFFC62828)
}

private fun componentColor(score: Int) = when {
    score < 26 -> Color(0xFF388E3C)
    score < 51 -> Color(0xFFF57C00)
    score < 76 -> Color(0xFFE64A19)
    else       -> Color(0xFFC62828)
}

@Composable
fun BurnoutRiskWidget(
    burnoutRisk: BurnoutRisk,
    modifier: Modifier = Modifier
) {
    val levelColor = burnoutColor(burnoutRisk.level)
    val levelLabel = burnoutRisk.level.name

    val animatedScore by animateFloatAsState(
        targetValue = burnoutRisk.score.toFloat(),
        animationSpec = tween(900),
        label = "burnout_score"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "BURNOUT RISK",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = levelColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = levelLabel,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = levelColor
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "${burnoutRisk.score}",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = levelColor
                )

                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth((animatedScore / 100f).coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(3.dp))
                                .background(levelColor)
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ComponentPill("Sleep",    burnoutRisk.sleepScore)
                        ComponentPill("Activity", burnoutRisk.workoutScore)
                        ComponentPill("Screen",   burnoutRisk.digitalScore)
                        ComponentPill("Meetings", burnoutRisk.meetingScore)
                    }
                }
            }

            if (burnoutRisk.drivers.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Driven by: ${burnoutRisk.drivers.joinToString(" + ")}",
                    fontSize = 11.sp,
                    color = levelColor.copy(alpha = 0.85f),
                    fontWeight = FontWeight.Medium
                )
            } else {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "All pillars in a healthy range.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
            }
        }
    }
}

@Composable
private fun ComponentPill(label: String, score: Int) {
    val color = componentColor(score)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            fontSize = 8.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            letterSpacing = 0.sp
        )
    }
}
