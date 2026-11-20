package com.cricstudio.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SportsCricket
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cricstudio.app.ui.theme.BroadcastDisplayFamily
import com.cricstudio.app.ui.theme.CricBlue
import com.cricstudio.app.ui.theme.CricGreen
import com.cricstudio.app.ui.theme.CricRed
import com.cricstudio.app.ui.theme.CricYellow

@Composable
fun CricLogo(compact: Boolean = false, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 30.dp else 34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.linearGradient(listOf(CricBlue, Color(0xFF0D47A1)))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.SportsCricket,
                contentDescription = "CricStudio Logo",
                tint = Color.White,
                modifier = Modifier.size(if (compact) 18.dp else 20.dp),
            )
        }
        if (!compact) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Cric",
                    fontFamily = BroadcastDisplayFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Studio",
                    fontFamily = BroadcastDisplayFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
fun LivePulseDot(
    color: Color = CricRed,
    size: Dp = 8.dp,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "live_pulse")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(650),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_alpha",
    )
    Box(
        modifier = modifier
            .size(size)
            .alpha(alpha)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
fun BallChip(label: String, compact: Boolean = false) {
    val bg = when {
        label == "W" -> CricRed
        label == "6" -> Color(0xFF9334E6)
        label == "4" -> CricGreen
        label.contains("wd") || label.contains("nb") -> CricYellow
        label == "•" -> Color.White.copy(alpha = 0.16f)
        else -> Color(0xFF1A73E8)
    }
    val fg = if (bg == CricYellow) Color(0xFF1F1F1F) else Color.White
    Box(
        modifier = Modifier
            .size(if (compact) 22.dp else 28.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = if (compact) 9.sp else 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}
