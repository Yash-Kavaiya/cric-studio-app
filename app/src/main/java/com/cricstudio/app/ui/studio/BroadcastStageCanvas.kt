package com.cricstudio.app.ui.studio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cricstudio.app.compositor.BroadcastCanvasRenderer
import com.cricstudio.app.model.BroadcastStatus
import com.cricstudio.app.model.CommentaryLine
import com.cricstudio.app.model.CompositorSnapshot
import com.cricstudio.app.model.CricketEventType
import com.cricstudio.app.model.DEMO_MATCH_KEY
import com.cricstudio.app.model.MatchDetail
import com.cricstudio.app.model.MatchListItem
import com.cricstudio.app.model.SceneId
import com.cricstudio.app.model.StreamTelemetry
import com.cricstudio.app.model.StudioState
import com.cricstudio.app.ui.components.LivePulseDot
import com.cricstudio.app.ui.theme.BroadcastDisplayFamily
import com.cricstudio.app.ui.theme.CricGreen
import com.cricstudio.app.ui.theme.CricPurple
import com.cricstudio.app.ui.theme.CricRed
import com.cricstudio.app.ui.theme.CricYellow
import java.util.Locale

/**
 * Live 30fps animated 16:9 Broadcast Monitor backed by the exact same BroadcastCanvasRenderer
 * that feeds MediaCodec H.264 -> YouTube Live RTMP (100% WYSIWYG parity).
 */
@Composable
fun BroadcastStageCanvas(
    studio: StudioState,
    match: MatchDetail?,
    allMatches: List<MatchListItem>,
    commentary: CommentaryLine?,
    sceneChangedAt: Long,
    telemetry: StreamTelemetry,
    onClearOverlay: () -> Unit,
    onExpandFullscreen: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val renderer = remember(context) { BroadcastCanvasRenderer(context) }
    var frameClockMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        var lastTick = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                // Drive compositor at steady ~30fps
                if (frameNanos - lastTick >= 30_000_000L) {
                    lastTick = frameNanos
                    frameClockMs = System.currentTimeMillis()
                }
            }
        }
    }

    val snapshot = CompositorSnapshot(
        studio = studio,
        match = match,
        allMatches = allMatches,
        commentary = commentary,
        sceneChangedAt = sceneChangedAt,
        isStreaming = telemetry.status == BroadcastStatus.LIVE,
        elapsedSeconds = telemetry.elapsedSeconds,
        micLevel = telemetry.micLevel,
    )

    val accent = Color(studio.brand.accentHex)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(14.dp))
            .border(1.5.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
            .testTag("broadcast_stage_canvas"),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val now = frameClockMs
            drawIntoCanvas { composeCanvas ->
                renderer.drawFrame(
                    canvas = composeCanvas.nativeCanvas,
                    width = size.width.toInt(),
                    height = size.height.toInt(),
                    snapshot = snapshot,
                    nowMs = now,
                )
            }
        }

        // Translucent monitor utility actions (overlay dismiss + fullscreen)
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 26.dp, end = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (studio.overlay != null) {
                Surface(
                    color = Color(0xAA0A0E17),
                    shape = CircleShape,
                ) {
                    IconButton(
                        onClick = onClearOverlay,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("dismiss_overlay_button"),
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Dismiss celebration overlay",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            if (onExpandFullscreen != null) {
                Surface(
                    color = Color(0x990A0E17),
                    shape = CircleShape,
                ) {
                    IconButton(
                        onClick = onExpandFullscreen,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("expand_monitor_button"),
                    ) {
                        Icon(
                            Icons.Rounded.Fullscreen,
                            contentDescription = "Full-screen broadcast monitor",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Real-time RTMP stream health telemetry strip (Status, Clock, Bitrate, FPS, Audio VU, Mic Mute, Retry).
 */
@Composable
fun StreamTelemetryStrip(
    telemetry: StreamTelemetry,
    onToggleMic: () -> Unit,
    onRetryStream: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusColor = when (telemetry.status) {
        BroadcastStatus.LIVE -> CricRed
        BroadcastStatus.CONNECTING, BroadcastStatus.RECONNECTING -> CricYellow
        BroadcastStatus.ERROR -> CricRed
        BroadcastStatus.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (telemetry.status == BroadcastStatus.LIVE) CricRed.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outline,
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("stream_telemetry_strip"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Status badge
            Surface(
                color = statusColor.copy(alpha = 0.18f),
                shape = RoundedCornerShape(6.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    if (telemetry.status == BroadcastStatus.LIVE || telemetry.status == BroadcastStatus.CONNECTING) {
                        LivePulseDot(color = statusColor, size = 6.dp)
                    }
                    Text(
                        text = telemetry.status.label,
                        color = statusColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // Live clock + bitrate + FPS
            if (telemetry.status != BroadcastStatus.IDLE) {
                val h = telemetry.elapsedSeconds / 3600
                val m = (telemetry.elapsedSeconds % 3600) / 60
                val s = telemetry.elapsedSeconds % 60
                val clock = if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
                else String.format(Locale.US, "%02d:%02d", m, s)
                Text(
                    text = "$clock · ${telemetry.bitrateKbps} kbps · ${telemetry.fps} fps",
                    fontFamily = BroadcastDisplayFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Text(
                    text = "${telemetry.quality.label} · Ready to stream",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }

            // 5-bar animated Audio VU meter
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.height(16.dp),
            ) {
                for (i in 0 until 5) {
                    val threshold = (i + 1) / 5f
                    val lit = telemetry.micLevel >= threshold * 0.7f
                    val barCol = when {
                        !lit -> MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                        i >= 4 -> CricRed
                        i >= 3 -> CricYellow
                        else -> CricGreen
                    }
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height((5 + i * 2.5f).dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(barCol),
                    )
                }
            }

            // Quick Mic Mute button
            Surface(
                color = if (telemetry.micEnabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else CricRed.copy(alpha = 0.18f),
                shape = CircleShape,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onToggleMic)
                    .testTag("quick_mic_toggle_button"),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (telemetry.micEnabled) Icons.Rounded.Mic else Icons.Rounded.MicOff,
                        contentDescription = if (telemetry.micEnabled) "Mute microphone" else "Unmute microphone",
                        tint = if (telemetry.micEnabled) MaterialTheme.colorScheme.primary else CricRed,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            if (telemetry.status == BroadcastStatus.ERROR || telemetry.status == BroadcastStatus.RECONNECTING) {
                IconButton(
                    onClick = onRetryStream,
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("retry_stream_button"),
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Retry stream", tint = CricYellow, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/**
 * One-tap Quick-Cut Director Dock below the Broadcast Monitor for instant live cuts,
 * kinetic moment triggers (4 / 6 / W), and Demo replay step controls.
 */
@Composable
fun QuickDirectorDock(
    studio: StudioState,
    demoAutoPlay: Boolean,
    onSelectScene: (SceneId) -> Unit,
    onSimulateEvent: (CricketEventType) -> Unit,
    onToggleDemoAutoPlay: () -> Unit,
    onStepDemo: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val quickScenes = remember {
        listOf(
            SceneId.SCORE_HERO,
            SceneId.BALL_BY_BALL,
            SceneId.BATTING_CARD,
            SceneId.MANHATTAN,
            SceneId.WORM,
            SceneId.WIN_PREDICTOR,
            SceneId.WAGON_WHEEL,
            SceneId.PANEL,
        )
    }

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .testTag("quick_director_dock"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Instant kinetic triggers: 4, 6, W
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                QuickMomentPill("4", CricGreen) { onSimulateEvent(CricketEventType.FOUR) }
                QuickMomentPill("6", CricPurple) { onSimulateEvent(CricketEventType.SIX) }
                QuickMomentPill("W", CricRed) { onSimulateEvent(CricketEventType.WICKET) }
            }
        }

        // Demo quick ball controls when on Demo match
        if (studio.matchKey == DEMO_MATCH_KEY) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Surface(
                        onClick = onToggleDemoAutoPlay,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Icon(
                                if (demoAutoPlay) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = "Toggle demo autoplay",
                                modifier = Modifier.size(14.dp),
                            )
                            Text(if (demoAutoPlay) "Pause" else "Play", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Surface(
                        onClick = { onStepDemo(1) },
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.SkipNext, contentDescription = "Next ball", modifier = Modifier.size(14.dp))
                            Text("+1B", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Instant scene cut pills
        items(quickScenes) { sc ->
            val active = studio.scene == sc
            Surface(
                onClick = { onSelectScene(sc) },
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(
                    1.dp,
                    if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                ),
            ) {
                Text(
                    text = sc.shortName,
                    color = if (active) Color.White else MaterialTheme.colorScheme.onSurface,
                    fontSize = 11.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun QuickMomentPill(label: String, color: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = color,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.testTag("quick_moment_${label.lowercase()}"),
    ) {
        Text(
            text = label,
            color = Color.White,
            fontFamily = BroadcastDisplayFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
