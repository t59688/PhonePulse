package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.BorderDark
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevatedDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.util.TimeFormatter

@Composable
fun LivePulseHeroCard(
    isScreenOn: Boolean,
    startTimeMs: Long,
    durationMs: Long,
    isServiceRunning: Boolean,
    onSimulateToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val activeColor by animateColorAsState(
        targetValue = if (isScreenOn) NeonCyan else ElectricViolet,
        label = "active_color"
    )

    val digitalClock = TimeFormatter.formatDigitalClock(durationMs)
    val formattedDuration = TimeFormatter.formatDurationChinese(durationMs)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        activeColor.copy(alpha = 0.12f),
                        SurfaceElevatedDark.copy(alpha = 0.85f),
                        SurfaceDark
                    ),
                    center = Offset(0.5f, 0.2f),
                    radius = 800f
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        activeColor.copy(alpha = 0.6f),
                        BorderDark.copy(alpha = 0.4f)
                    )
                ),
                shape = RoundedCornerShape(28.dp)
            )
            .padding(24.dp)
            .testTag("hero_pulse_card")
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar: Status Badge & Service Guard Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Screen Status Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(30.dp))
                        .background(activeColor.copy(alpha = 0.15f))
                        .border(1.dp, activeColor.copy(alpha = 0.35f), RoundedCornerShape(30.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(activeColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isScreenOn) "⚡ 屏幕点亮进行中" else "🌙 屏幕休眠熄屏中",
                        color = activeColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Guard Service Status
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(30.dp))
                        .background(
                            if (isServiceRunning) NeonEmerald.copy(alpha = 0.12f)
                            else AmberWarning.copy(alpha = 0.12f)
                        )
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Service guard",
                        tint = if (isServiceRunning) NeonEmerald else AmberWarning,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isServiceRunning) "后台守护中" else "守护未开启",
                        color = if (isServiceRunning) NeonEmerald else AmberWarning,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Pulse Ring Canvas with Glowing Center Timer
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(190.dp)
            ) {
                Canvas(modifier = Modifier.size(190.dp)) {
                    val center = Offset(size.width / 2, size.height / 2)
                    val baseRadius = size.width / 2 - 16.dp.toPx()

                    // Outer pulse wave ring
                    drawCircle(
                        color = activeColor.copy(alpha = pulseAlpha * 0.25f),
                        radius = baseRadius * pulseScale,
                        center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )

                    // Secondary static glow track
                    drawCircle(
                        color = BorderDark,
                        radius = baseRadius,
                        center = center,
                        style = Stroke(width = 3.dp.toPx())
                    )

                    // Active animated glow arc
                    drawCircle(
                        brush = Brush.sweepGradient(
                            listOf(
                                activeColor.copy(alpha = 0.1f),
                                activeColor,
                                activeColor.copy(alpha = 0.3f)
                            )
                        ),
                        radius = baseRadius,
                        center = center,
                        style = Stroke(width = 3.5.dp.toPx())
                    )

                    // Inner soft radial bloom
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                activeColor.copy(alpha = 0.18f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = baseRadius * 0.9f
                        ),
                        center = center,
                        radius = baseRadius * 0.9f
                    )
                }

                // Center Display Time & State Icon
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (isScreenOn) Icons.Default.WbSunny else Icons.Default.Bedtime,
                        contentDescription = null,
                        tint = activeColor,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = digitalClock,
                        color = TextPrimary,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = "本次状态持续",
                        color = TextTertiary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Duration in words & start timestamp
            Text(
                text = formattedDuration,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "自 ${TimeFormatter.formatTime(startTimeMs)} 开始计时",
                color = TextSecondary,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Interactive simulation toggle button for rapid testing / reviewer experience
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceElevatedDark)
                    .border(1.dp, BorderDark, RoundedCornerShape(16.dp))
                    .clickable { onSimulateToggle() }
                    .padding(horizontal = 16.dp, vertical = 9.dp)
                    .testTag("simulate_toggle_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Simulate state toggle",
                    tint = activeColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isScreenOn) "模拟屏幕熄屏状态" else "模拟屏幕唤醒状态",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
