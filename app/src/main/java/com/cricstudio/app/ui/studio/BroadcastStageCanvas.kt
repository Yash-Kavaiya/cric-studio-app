package com.cricstudio.app.ui.studio

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cricstudio.app.model.ActiveOverlay
import com.cricstudio.app.model.BannerKind
import com.cricstudio.app.model.CommentaryLine
import com.cricstudio.app.model.MatchDetail
import com.cricstudio.app.model.MatchListItem
import com.cricstudio.app.model.OverlayId
import com.cricstudio.app.model.StudioState
import com.cricstudio.app.ui.components.LivePulseDot
import com.cricstudio.app.ui.theme.BroadcastDisplayFamily
import com.cricstudio.app.ui.theme.CricGreen
import com.cricstudio.app.ui.theme.CricPurple
import com.cricstudio.app.ui.theme.CricRed
import com.cricstudio.app.ui.theme.CricYellow

@Composable
fun BroadcastStageCanvas(
    studio: StudioState,
    match: MatchDetail?,
    allMatches: List<MatchListItem>,
    commentary: CommentaryLine?,
    onClearOverlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgSpec = studio.brand.background
    val primaryBg = Color(bgSpec.primaryHex)
    val secondaryBg = Color(bgSpec.secondaryHex)
    val accent = Color(studio.brand.accentHex)
    val onBg = if (bgSpec.isLight) Color(0xFF14181F) else Color(0xFFF5F7FA)
    val cardBg = if (bgSpec.isLight) Color(0x14000000) else Color(0x38000000)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(primaryBg, secondaryBg)))
            .drawBehind {
                val lineColor = onBg.copy(alpha = 0.05f)
                val step = size.height / 6f
                for (i in 1..5) {
                    val y = step * i
                    drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                }
            }
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .testTag("broadcast_stage_canvas"),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top broadcast header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (bgSpec.isLight) Color(0x1A000000) else Color(0x55000000))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    color = if (studio.isLive) CricRed else Color(0xFF3C4043),
                    shape = RoundedCornerShape(5.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (studio.isLive) LivePulseDot(color = Color.White, size = 5.dp)
                        Text(
                            text = if (studio.isLive) "ON AIR" else "PREVIEW",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Text(
                    text = studio.brand.title.ifBlank { studio.name },
                    color = onBg,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (studio.autopilot.enabled) {
                    Surface(color = CricGreen.copy(alpha = 0.25f), shape = RoundedCornerShape(4.dp)) {
                        Row(
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Icon(
                                Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = CricGreen,
                                modifier = Modifier.size(10.dp),
                            )
                            Text("AUTO", color = CricGreen, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Surface(color = accent.copy(alpha = 0.28f), shape = RoundedCornerShape(4.dp)) {
                    Text(
                        text = studio.scene.shortName.uppercase(),
                        color = onBg,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            // Main broadcast scene content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                AnimatedContent(
                    targetState = studio.scene,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "scene_transition",
                ) { _ ->
                    BroadcastSceneContent(
                        studio = studio,
                        match = match,
                        allMatches = allMatches,
                        accent = accent,
                        onBg = onBg,
                        cardBg = cardBg,
                    )
                }

                // Active lower-third banner
                val banner = studio.banner
                if (banner != null) {
                    val curInn = match?.innings?.firstOrNull { it.isCurrent } ?: match?.innings?.lastOrNull()
                    val bannerText = when (banner.kind) {
                        BannerKind.SPONSOR -> studio.brand.sponsors.firstOrNull() ?: "Powered by CricStudio Broadcast"
                        BannerKind.QUICK_SCORE -> if (curInn != null) {
                            "${curInn.teamAbbr} ${curInn.runs}/${curInn.wickets} (${curInn.overs} ov) · ${match?.summary.orEmpty()}"
                        } else {
                            match?.title ?: studio.brand.title
                        }
                    }
                    Surface(
                        color = accent.copy(alpha = 0.92f),
                        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 12.dp),
                    ) {
                        Text(
                            text = bannerText,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            // Live commentary captions strip
            if (studio.brand.captions && commentary != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xBB0B0E14))
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Rounded.RecordVoiceOver,
                        contentDescription = null,
                        tint = CricYellow,
                        modifier = Modifier.size(11.dp),
                    )
                    Text(
                        text = commentary.text,
                        color = Color.White,
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Ticker strip
            if (studio.brand.showTicker && studio.brand.ticker.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(accent.copy(alpha = 0.88f))
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = studio.brand.ticker,
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Celebration / Event Overlay
        AnimatedVisibility(
            visible = studio.overlay != null,
            enter = fadeIn() + scaleIn(
                initialScale = 0.82f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            ),
            exit = fadeOut() + scaleOut(targetScale = 0.9f),
            modifier = Modifier.fillMaxSize(),
        ) {
            studio.overlay?.let { ov ->
                OverlayCelebrationCard(overlay = ov, accent = accent, onDismiss = onClearOverlay)
            }
        }
    }
}

@Composable
private fun OverlayCelebrationCard(
    overlay: ActiveOverlay,
    accent: Color,
    onDismiss: () -> Unit,
) {
    val badgeColor = when (overlay.id) {
        OverlayId.SIX -> CricPurple
        OverlayId.FOUR -> CricGreen
        OverlayId.WICKET, OverlayId.HATTRICK -> CricRed
        OverlayId.MILESTONE, OverlayId.FIVE_WICKETS, OverlayId.MATCH_END -> CricYellow
        else -> accent
    }
    val titleColor = if (badgeColor == CricYellow) Color(0xFF14181F) else Color.White

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xA6050811))
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = badgeColor,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth(0.88f),
        ) {
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = overlay.title.uppercase(),
                        fontFamily = BroadcastDisplayFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 30.sp,
                        color = titleColor,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    overlay.subtitle?.takeIf { it.isNotBlank() }?.let { sub ->
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = sub,
                            color = titleColor.copy(alpha = 0.9f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(28.dp)
                        .testTag("dismiss_overlay_button"),
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Dismiss overlay",
                        tint = titleColor,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}
