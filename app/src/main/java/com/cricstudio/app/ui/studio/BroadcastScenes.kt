package com.cricstudio.app.ui.studio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SportsCricket
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cricstudio.app.engine.BroadcastAnalytics
import com.cricstudio.app.model.MatchDetail
import com.cricstudio.app.model.MatchListItem
import com.cricstudio.app.model.MatchStateCode
import com.cricstudio.app.model.SceneId
import com.cricstudio.app.model.StudioState
import com.cricstudio.app.ui.components.BallChip
import com.cricstudio.app.ui.theme.BroadcastDisplayFamily
import com.cricstudio.app.ui.theme.CricGreen
import com.cricstudio.app.ui.theme.CricRed
import com.cricstudio.app.ui.theme.CricYellow
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

@Composable
fun BroadcastSceneContent(
    studio: StudioState,
    match: MatchDetail?,
    allMatches: List<MatchListItem>,
    accent: Color,
    onBg: Color,
    cardBg: Color,
) {
    when (studio.scene) {
        SceneId.SCORE_HERO -> ScoreHeroScene(match, accent, onBg, cardBg)
        SceneId.BALL_BY_BALL -> BallByBallScene(match, accent, onBg, cardBg)
        SceneId.BATTING_CARD -> BattingCardScene(match, accent, onBg, cardBg)
        SceneId.BOWLING_CARD -> BowlingCardScene(match, accent, onBg, cardBg)
        SceneId.BATTERS, SceneId.PARTNERSHIPS -> CreaseScene(match, accent, onBg, cardBg)
        SceneId.BOWLER_SPOTLIGHT, SceneId.BOWLING_ANALYSIS -> BowlerSpotlightScene(match, accent, onBg, cardBg)
        SceneId.MANHATTAN, SceneId.OVER_HEATMAP -> ManhattanScene(match, accent, onBg, cardBg)
        SceneId.WORM, SceneId.RUN_RATE_GRAPH, SceneId.INNINGS_COMPARISON -> WormScene(match, accent, onBg, cardBg)
        SceneId.FALL_OF_WICKETS, SceneId.DISMISSAL_ANALYSIS, SceneId.KEY_MOMENTS -> FallOfWicketsScene(match, accent, onBg, cardBg)
        SceneId.WIN_PREDICTOR, SceneId.MOMENTUM -> WinPredictorScene(match, accent, onBg, cardBg)
        SceneId.PROJECTED_SCORE, SceneId.CHASE_CALCULATOR -> ProjectedScoreScene(match, accent, onBg, cardBg)
        SceneId.PHASE_ANALYSIS, SceneId.SCORING_BREAKDOWN, SceneId.BOUNDARY_TRACKER, SceneId.EXTRAS_DISCIPLINE -> PhaseAndScoringScene(match, accent, onBg, cardBg)
        SceneId.WAGON_WHEEL, SceneId.GROUND_3D -> WagonWheelScene(match, accent, onBg, cardBg)
        SceneId.STAR_PERFORMER, SceneId.MATCHUP, SceneId.BATTING_IMPACT -> StarPerformerScene(match, accent, onBg, cardBg)
        SceneId.PLAYING_XI -> PlayingXiScene(match, accent, onBg, cardBg)
        SceneId.AROUND_GROUNDS, SceneId.UPCOMING_MATCHES -> AroundGroundsScene(allMatches, studio.scene == SceneId.UPCOMING_MATCHES, accent, onBg, cardBg)
        SceneId.FAN_POLL -> FanPollScene(studio, match, accent, onBg, cardBg)
        SceneId.HOST_SOLO, SceneId.PANEL, SceneId.COMMENTARY_DESK, SceneId.SCREEN_SHARE -> CameraStageScene(studio, match, accent, onBg, cardBg)
        SceneId.STARTING_SOON, SceneId.INTRO_3D -> StartingSoonScene(studio, match, accent, onBg, cardBg)
        SceneId.MESSAGE -> AnnouncementScene(studio, accent, onBg, cardBg)
        SceneId.BREAK -> BreakScene(match, accent, onBg, cardBg)
        SceneId.RESULT, SceneId.MATCH_REPORT -> ResultScene(match, accent, onBg, cardBg)
    }
}

@Composable
private fun NoMatchPlaceholder(onBg: Color, label: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.SportsCricket, contentDescription = null, tint = onBg.copy(alpha = 0.5f), modifier = Modifier.size(30.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, color = onBg.copy(alpha = 0.7f), fontSize = 12.sp)
    }
}

@Composable
private fun ScoreHeroScene(match: MatchDetail?, accent: Color, onBg: Color, cardBg: Color) {
    if (match == null) {
        NoMatchPlaceholder(onBg, "Attach a match from the Match tab")
        return
    }
    val cur = match.innings.firstOrNull { it.isCurrent } ?: match.innings.lastOrNull()
    val thisOver = match.recentBalls
        .filter { it.innings == (cur?.number ?: 1) }
        .let { balls ->
            val lastOv = balls.lastOrNull()?.overNumber ?: return@let emptyList()
            balls.filter { it.overNumber == lastOv }.takeLast(7)
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${match.series} · ${match.description}".trim(' ', '·'),
                    color = onBg.copy(alpha = 0.72f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = cur?.teamAbbr ?: match.teams.firstOrNull()?.abbr ?: "LIVE",
                        fontFamily = BroadcastDisplayFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp,
                        color = accent,
                    )
                    Text(
                        text = if (cur != null) "${cur.runs}/${cur.wickets}" else match.teams.firstOrNull()?.score.orEmpty(),
                        fontFamily = BroadcastDisplayFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 36.sp,
                        color = onBg,
                    )
                    if (cur != null) {
                        Text(
                            text = "(${cur.overs} ov)",
                            color = onBg.copy(alpha = 0.75f),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(bottom = 5.dp),
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "CRR ${String.format(Locale.US, "%.2f", match.crr)}",
                    color = onBg,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
                match.rrr?.let {
                    Text(
                        text = "RRR ${String.format(Locale.US, "%.2f", it)}",
                        color = CricYellow,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        if (match.summary.isNotBlank()) {
            Surface(color = accent.copy(alpha = 0.22f), shape = RoundedCornerShape(6.dp)) {
                Text(
                    text = match.summary,
                    color = onBg,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(color = cardBg, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1.2f)) {
                Column(modifier = Modifier.padding(7.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    match.currentBatters.forEachIndexed { idx, b ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                text = (if (idx == 0) "★ " else "") + b.name,
                                color = onBg,
                                fontSize = 10.sp,
                                fontWeight = if (idx == 0) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "${b.runs}(${b.balls})",
                                color = onBg,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    if (match.currentBatters.isEmpty()) {
                        Text("Partnership ${match.partnership.runs} (${match.partnership.balls})", color = onBg.copy(alpha = 0.7f), fontSize = 10.sp)
                    }
                }
            }

            Surface(color = cardBg, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(7.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    match.currentBowler?.let { bw ->
                        Text(
                            text = "${bw.name}  ${bw.wickets}/${bw.conceded} (${bw.overs})",
                            color = onBg,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        thisOver.forEach { b -> BallChip(b.label, compact = true) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BallByBallScene(match: MatchDetail?, accent: Color, onBg: Color, cardBg: Color) {
    if (match == null) {
        NoMatchPlaceholder(onBg, "No ball-by-ball feed")
        return
    }
    val recent = match.recentBalls.takeLast(5).reversed()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text("BALL BY BALL", fontFamily = BroadcastDisplayFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = accent)
        recent.forEach { b ->
            Surface(color = cardBg, shape = RoundedCornerShape(7.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("${b.over}", color = onBg.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    BallChip(b.label, compact = true)
                    Text(
                        text = b.short.ifBlank { b.text },
                        color = onBg,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun BattingCardScene(match: MatchDetail?, accent: Color, onBg: Color, cardBg: Color) {
    val inn = match?.innings?.firstOrNull { it.isCurrent } ?: match?.innings?.lastOrNull()
    if (inn == null) {
        NoMatchPlaceholder(onBg, "Scorecard unavailable")
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${inn.teamName.uppercase()} BATTING", fontFamily = BroadcastDisplayFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = accent)
            Text("${inn.runs}/${inn.wickets} (${inn.overs} ov)", fontFamily = BroadcastDisplayFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = onBg)
        }
        Surface(color = cardBg, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("BATTER", color = onBg.copy(alpha = 0.6f), fontSize = 9.sp, modifier = Modifier.weight(2.2f))
                    Text("R", color = onBg.copy(alpha = 0.6f), fontSize = 9.sp, modifier = Modifier.width(26.dp), textAlign = TextAlign.End)
                    Text("B", color = onBg.copy(alpha = 0.6f), fontSize = 9.sp, modifier = Modifier.width(26.dp), textAlign = TextAlign.End)
                    Text("4s", color = onBg.copy(alpha = 0.6f), fontSize = 9.sp, modifier = Modifier.width(22.dp), textAlign = TextAlign.End)
                    Text("6s", color = onBg.copy(alpha = 0.6f), fontSize = 9.sp, modifier = Modifier.width(22.dp), textAlign = TextAlign.End)
                    Text("SR", color = onBg.copy(alpha = 0.6f), fontSize = 9.sp, modifier = Modifier.width(36.dp), textAlign = TextAlign.End)
                }
                inn.batting.take(6).forEach { b ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = b.name + if (b.notOut) "*" else "",
                            color = if (b.notOut) accent else onBg,
                            fontSize = 10.sp,
                            fontWeight = if (b.notOut) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(2.2f),
                        )
                        Text("${b.runs}", color = onBg, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(26.dp), textAlign = TextAlign.End)
                        Text("${b.balls}", color = onBg.copy(alpha = 0.8f), fontSize = 10.sp, modifier = Modifier.width(26.dp), textAlign = TextAlign.End)
                        Text("${b.fours}", color = onBg.copy(alpha = 0.8f), fontSize = 10.sp, modifier = Modifier.width(22.dp), textAlign = TextAlign.End)
                        Text("${b.sixes}", color = onBg.copy(alpha = 0.8f), fontSize = 10.sp, modifier = Modifier.width(22.dp), textAlign = TextAlign.End)
                        Text(String.format(Locale.US, "%.0f", b.sr), color = onBg.copy(alpha = 0.8f), fontSize = 10.sp, modifier = Modifier.width(36.dp), textAlign = TextAlign.End)
                    }
                }
            }
        }
    }
}

@Composable
private fun BowlingCardScene(match: MatchDetail?, accent: Color, onBg: Color, cardBg: Color) {
    val inn = match?.innings?.firstOrNull { it.isCurrent } ?: match?.innings?.lastOrNull()
    if (inn == null) {
        NoMatchPlaceholder(onBg, "Bowling figures unavailable")
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("BOWLING FIGURES", fontFamily = BroadcastDisplayFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = accent)
        Surface(color = cardBg, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("BOWLER", color = onBg.copy(alpha = 0.6f), fontSize = 9.sp, modifier = Modifier.weight(2.2f))
                    Text("O", color = onBg.copy(alpha = 0.6f), fontSize = 9.sp, modifier = Modifier.width(28.dp), textAlign = TextAlign.End)
                    Text("M", color = onBg.copy(alpha = 0.6f), fontSize = 9.sp, modifier = Modifier.width(22.dp), textAlign = TextAlign.End)
                    Text("R", color = onBg.copy(alpha = 0.6f), fontSize = 9.sp, modifier = Modifier.width(26.dp), textAlign = TextAlign.End)
                    Text("W", color = onBg.copy(alpha = 0.6f), fontSize = 9.sp, modifier = Modifier.width(24.dp), textAlign = TextAlign.End)
                    Text("ECON", color = onBg.copy(alpha = 0.6f), fontSize = 9.sp, modifier = Modifier.width(36.dp), textAlign = TextAlign.End)
                }
                inn.bowling.take(6).forEach { bw ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(bw.name, color = onBg, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(2.2f))
                        Text("${bw.overs}", color = onBg.copy(alpha = 0.85f), fontSize = 10.sp, modifier = Modifier.width(28.dp), textAlign = TextAlign.End)
                        Text("${bw.maidens}", color = onBg.copy(alpha = 0.85f), fontSize = 10.sp, modifier = Modifier.width(22.dp), textAlign = TextAlign.End)
                        Text("${bw.conceded}", color = onBg.copy(alpha = 0.85f), fontSize = 10.sp, modifier = Modifier.width(26.dp), textAlign = TextAlign.End)
                        Text("${bw.wickets}", color = if (bw.wickets > 0) CricYellow else onBg, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.width(24.dp), textAlign = TextAlign.End)
                        Text(String.format(Locale.US, "%.1f", bw.econ), color = onBg.copy(alpha = 0.85f), fontSize = 10.sp, modifier = Modifier.width(36.dp), textAlign = TextAlign.End)
                    }
                }
            }
        }
    }
}

@Composable
private fun CreaseScene(match: MatchDetail?, accent: Color, onBg: Color, cardBg: Color) {
    if (match == null) {
        NoMatchPlaceholder(onBg, "No active partnership")
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("AT THE CREASE", fontFamily = BroadcastDisplayFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = accent)
            Text(
                text = "PARTNERSHIP ${match.partnership.runs} (${match.partnership.balls}b)",
                fontFamily = BroadcastDisplayFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = CricYellow,
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            match.currentBatters.take(2).forEach { b ->
                Surface(color = cardBg, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(b.name, color = onBg, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${b.runs}", fontFamily = BroadcastDisplayFamily, fontWeight = FontWeight.Bold, fontSize = 30.sp, color = accent)
                        Text("${b.balls} balls · ${b.fours}×4 · ${b.sixes}×6 · SR ${String.format(Locale.US, "%.1f", b.sr)}", color = onBg.copy(alpha = 0.8f), fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun BowlerSpotlightScene(match: MatchDetail?, accent: Color, onBg: Color, cardBg: Color) {
    val bw = match?.currentBowler ?: match?.innings?.lastOrNull()?.bowling?.maxByOrNull { it.wickets }
    if (bw == null) {
        NoMatchPlaceholder(onBg, "No active bowler")
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("BOWLER SPOTLIGHT", fontFamily = BroadcastDisplayFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = accent)
        Surface(color = cardBg, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(bw.name, fontFamily = BroadcastDisplayFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = onBg)
                    Text("${bw.overs} overs · ${bw.maidens} maidens · ${bw.dots} dots", color = onBg.copy(alpha = 0.75f), fontSize = 11.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${bw.wickets}/${bw.conceded}", fontFamily = BroadcastDisplayFamily, fontWeight = FontWeight.Bold, fontSize = 32.sp, color = CricYellow)
                    Text("ECON ${String.format(Locale.US, "%.2f", bw.econ)}", color = accent, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun ManhattanScene(match: MatchDetail?, accent: Color, onBg: Color, cardBg: Color) {
    val inn = match?.innings?.firstOrNull { it.isCurrent } ?: match?.innings?.lastOrNull()
    val overs = inn?.overSummaries.orEmpty()
    if (overs.isEmpty()) {
        NoMatchPlaceholder(onBg, "No over summaries yet")
        return
    }
    val maxRuns = max(12, overs.maxOf { it.runs })
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("MANHATTAN · ${inn?.teamAbbr.orEmpty()}", fontFamily = BroadcastDisplayFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = accent)
            Text("Max over: $maxRuns runs", color = onBg.copy(alpha = 0.7f), fontSize = 10.sp)
        }
        Surface(color = cardBg, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)) {
                val n = overs.size
                val barSlot = size.width / max(1, n)
                val barW = (barSlot * 0.68f).coerceAtMost(24f)
                overs.forEachIndexed { i, ov ->
                    val h = (ov.runs.toFloat() / maxRuns) * (size.height - 14f)
                    val x = i * barSlot + (barSlot - barW) / 2f
                    val barColor = if (ov.runs >= 12) CricYellow else accent
                    drawRect(
                        color = barColor,
                        topLeft = Offset(x, size.height - h),
                        size = Size(barW, h),
                    )
                    repeat(ov.wickets) { w ->
                        drawCircle(
                            color = CricRed,
                            radius = 5.5f,
                            center = Offset(x + barW / 2f, (size.height - h - 8f - w * 13f).coerceAtLeast(6f)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WormScene(match: MatchDetail?, accent: Color, onBg: Color, cardBg: Color) {
    val innings = match?.innings?.filter { it.overSummaries.isNotEmpty() }.orEmpty()
    if (innings.isEmpty()) {
        NoMatchPlaceholder(onBg, "Not enough overs for Worm graph")
        return
    }
    val maxOvers = max(6, innings.maxOf { it.overSummaries.size })
    val maxRuns = max(40, innings.maxOf { i -> i.overSummaries.maxOf { it.cumulative } })
    val lineColors = listOf(accent, CricYellow, CricGreen)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("WORM · CUMULATIVE RUNS", fontFamily = BroadcastDisplayFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = accent)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                innings.forEachIndexed { idx, inn ->
                    Text(
                        text = "● ${inn.teamAbbr} ${inn.runs}/${inn.wickets}",
                        color = lineColors[idx % lineColors.size],
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Surface(color = cardBg, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)) {
                innings.forEachIndexed { idx, inn ->
                    val path = Path()
                    path.moveTo(0f, size.height)
                    inn.overSummaries.forEachIndexed { i, ov ->
                        val x = ((i + 1).toFloat() / maxOvers) * size.width
                        val y = size.height - (ov.cumulative.toFloat() / maxRuns) * size.height
                        path.lineTo(x, y)
                        if (ov.wickets > 0) {
                            drawCircle(CricRed, radius = 5.5f, center = Offset(x, y))
                        }
                    }
                    drawPath(
                        path = path,
                        color = lineColors[idx % lineColors.size],
                        style = Stroke(width = 4.5f, cap = StrokeCap.Round),
                    )
                }
            }
        }
    }
}

@Composable
private fun FallOfWicketsScene(match: MatchDetail?, accent: Color, onBg: Color, cardBg: Color) {
    val inn = match?.innings?.firstOrNull { it.isCurrent } ?: match?.innings?.lastOrNull()
    val fows = inn?.fow.orEmpty()
    if (fows.isEmpty()) {
        NoMatchPlaceholder(onBg, "No wickets fallen yet")
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("FALL OF WICKETS · ${inn?.teamAbbr.orEmpty()}", fontFamily = BroadcastDisplayFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = CricRed)
        fows.takeLast(5).forEach { f ->
            Surface(color = cardBg, shape = RoundedCornerShape(7.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("${f.runs}/${f.wicket}", color = CricYellow, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text(f.batter, color = onBg, fontSize = 11.sp, modifier = Modifier.weight(1f).padding(horizontal = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Ov ${f.over}", color = onBg.copy(alpha = 0.7f), fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun WinPredictorScene(match: MatchDetail?, accent: Color, onBg: Color, cardBg: Color) {
    if (match == null) {
        NoMatchPlaceholder(onBg, "Attach a match for Win Predictor")
        return
    }
    val prob = BroadcastAnalytics.winProbability(match)
    val momentum = BroadcastAnalytics.momentumIndex(match.innings.firstOrNull { it.isCurrent })
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("LIVE WIN PREDICTOR", fontFamily = BroadcastDisplayFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = accent)
        Surface(color = cardBg, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${prob.teamAAbbr}  ${prob.teamAPct}%", fontFamily = BroadcastDisplayFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = accent)
                    Text("${prob.teamBPct}%  ${prob.teamBAbbr}", fontFamily = BroadcastDisplayFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = CricYellow)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp)),
                ) {
                    Box(modifier = Modifier.weight(prob.teamAPct.toFloat().coerceAtLeast(1f)).fillMaxHeight().background(accent))
                    Box(modifier = Modifier.weight(prob.teamBPct.toFloat().coerceAtLeast(1f)).fillMaxHeight().background(CricYellow))
                }
                Text("${prob.headline} · Batting momentum index: $momentum/100", color = onBg.copy(alpha = 0.8f), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun ProjectedScoreScene(match: MatchDetail?, accent: Color, onBg: Color, cardBg: Color) {
    if (match == null) {
        NoMatchPlaceholder(onBg, "Projected score unavailable")
        return
    }
    val rows = BroadcastAnalytics.projectedScores(match)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("PROJECTED SCORE & CHASE CALCULATOR", fontFamily = BroadcastDisplayFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = accent)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { (label, score) ->
                Surface(color = cardBg, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("$score", fontFamily = BroadcastDisplayFamily, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = CricYellow)
                        Text(label, color = onBg.copy(alpha = 0.78f), fontSize = 9.sp, textAlign = TextAlign.Center, maxLines = 2)
                    }
                }
            }
        }
    }
}

@Composable
private fun PhaseAndScoringScene(match: MatchDetail?, accent: Color, onBg: Color, cardBg: Color) {
    val inn = match?.innings?.firstOrNull { it.isCurrent } ?: match?.innings?.lastOrNull()
    val phases = BroadcastAnalytics.phaseBreakdown(inn)
    val slices = BroadcastAnalytics.scoringBreakdown(match?.recentBalls.orEmpty())
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("PHASE ANALYSIS & SCORING BREAKDOWN", fontFamily = BroadcastDisplayFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = accent)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            phases.forEach { p ->
                Surface(color = cardBg, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(p.label, color = accent, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        Text("${p.runs}/${p.wickets}", fontFamily = BroadcastDisplayFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = onBg)
                        Text("${p.range} · RR ${String.format(Locale.US, "%.1f", p.runRate)}", color = onBg.copy(alpha = 0.7f), fontSize = 9.sp)
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            slices.forEach { s ->
                Surface(color = Color(s.colorHex).copy(alpha = 0.25f), shape = RoundedCornerShape(6.dp), modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(s.label, color = onBg, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text("${s.count}", color = onBg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun WagonWheelScene(match: MatchDetail?, accent: Color, onBg: Color, cardBg: Color) {
    val batter = match?.currentBatters?.firstOrNull() ?: match?.innings?.lastOrNull()?.batting?.maxByOrNull { it.runs }
    val zones = batter?.wagon?.takeIf { it.size == 8 } ?: listOf(8, 14, 18, 6, 10, 22, 12, 4)
    val maxZone = max(1, zones.maxOrNull() ?: 1)

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(modifier = Modifier.size(132.dp)) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val r = size.minDimension / 2f - 4f
            drawCircle(color = Color(0xFF14532D), radius = r, center = c)
            drawCircle(color = Color.White.copy(alpha = 0.35f), radius = r * 0.55f, center = c, style = Stroke(2f))
            drawCircle(color = Color.White.copy(alpha = 0.7f), radius = r, center = c, style = Stroke(2.5f))
            drawRect(color = Color(0xFFD6B588), topLeft = Offset(c.x - 4f, c.y - 16f), size = Size(8f, 32f))
            zones.forEachIndexed { idx, runs ->
                val angle = (idx * 45.0 - 90.0) * PI / 180.0
                val len = (runs.toFloat() / maxZone) * (r - 6f)
                val end = Offset(c.x + (cos(angle) * len).toFloat(), c.y + (sin(angle) * len).toFloat())
                val color = if (runs >= maxZone * 0.75f) CricYellow else accent
                drawLine(color = color, start = c, end = end, strokeWidth = 5f, cap = StrokeCap.Round)
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("WAGON WHEEL", fontFamily = BroadcastDisplayFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = accent)
            Text(batter?.name ?: "Leading Batter", fontFamily = BroadcastDisplayFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = onBg)
            if (batter != null) {
                Text("${batter.runs} (${batter.balls}) · ${batter.fours}×4 · ${batter.sixes}×6", color = CricYellow, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Text("8-sector scoring distribution across off-side & leg-side", color = onBg.copy(alpha = 0.75f), fontSize = 10.sp)
        }
    }
}

@Composable
private fun StarPerformerScene(match: MatchDetail?, accent: Color, onBg: Color, cardBg: Color) {
    val star = match?.innings?.flatMap { it.batting }?.maxByOrNull { it.runs }
    if (star == null) {
        NoMatchPlaceholder(onBg, "Star performer pending")
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("STAR PERFORMER", fontFamily = BroadcastDisplayFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = CricYellow)
        Surface(color = cardBg, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(star.name, fontFamily = BroadcastDisplayFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = onBg)
                    Text("${star.fours} fours · ${star.sixes} sixes · SR ${String.format(Locale.US, "%.1f", star.sr)}", color = onBg.copy(alpha = 0.78f), fontSize = 11.sp)
                }
                Text("${star.runs} (${star.balls})", fontFamily = BroadcastDisplayFamily, fontWeight = FontWeight.Bold, fontSize = 30.sp, color = accent)
            }
        }
    }
}

@Composable
private fun PlayingXiScene(match: MatchDetail?, accent: Color, onBg: Color, cardBg: Color) {
    if (match == null || match.teams.isEmpty()) {
        NoMatchPlaceholder(onBg, "Squads unavailable")
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text("PLAYING XIs & TOSS", fontFamily = BroadcastDisplayFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = accent)
        if (match.toss.isNotBlank()) {
            Text(match.toss, color = CricYellow, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            match.teams.take(2).forEach { team ->
                Surface(color = cardBg, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Column(modifier = Modifier.padding(7.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(team.name, color = accent, fontWeight = FontWeight.Bold, fontSize = 10.sp, maxLines = 1)
                        team.squad.take(6).forEach { p ->
                            val tag = buildString {
                                if (p.captain) append(" (c)")
                                if (p.keeper) append(" †")
                            }
                            Text("${p.name}$tag", color = onBg, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AroundGroundsScene(allMatches: List<MatchListItem>, upcomingOnly: Boolean, accent: Color, onBg: Color, cardBg: Color) {
    val list = allMatches.filter {
        if (upcomingOnly) it.state == MatchStateCode.PRE else it.state == MatchStateCode.IN || it.isDemo
    }.take(4)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Rounded.Public, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
            Text(
                if (upcomingOnly) "UPCOMING MATCHES" else "AROUND THE GROUNDS",
                fontFamily = BroadcastDisplayFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = accent,
            )
        }
        list.forEach { m ->
            Surface(color = cardBg, shape = RoundedCornerShape(7.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        m.teams.joinToString(" v ") { "${it.abbr} ${it.score}".trim() }.ifBlank { m.name },
                        color = onBg,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(m.statusText, color = onBg.copy(alpha = 0.7f), fontSize = 9.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun FanPollScene(studio: StudioState, match: MatchDetail?, accent: Color, onBg: Color, cardBg: Color) {
    val t1 = match?.teams?.getOrNull(0)?.name ?: "Team A"
    val t2 = match?.teams?.getOrNull(1)?.name ?: "Team B"
    val prob = match?.let { BroadcastAnalytics.winProbability(it) }
    val p1 = prob?.teamAPct ?: 54
    val p2 = 100 - p1
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("LIVE FAN POLL", fontFamily = BroadcastDisplayFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = CricYellow)
        Spacer(Modifier.height(4.dp))
        Text(studio.brand.pollQuestion, fontFamily = BroadcastDisplayFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = onBg)
        Spacer(Modifier.height(10.dp))
        listOf(t1 to p1, t2 to p2).forEach { (name, pct) ->
            Column(modifier = Modifier.padding(vertical = 3.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(name, color = onBg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text("$pct%", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                LinearProgressIndicator(
                    progress = { pct / 100f },
                    modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)),
                    color = accent,
                    trackColor = cardBg,
                )
            }
        }
    }
}

@Composable
private fun CameraStageScene(studio: StudioState, match: MatchDetail?, accent: Color, onBg: Color, cardBg: Color) {
    val onStage = studio.participants.filter { it.onStage }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            onStage.ifEmpty { studio.participants.take(1) }.forEach { p ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(10.dp))
                        .background(cardBg)
                        .border(1.5.dp, accent.copy(alpha = 0.7f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.Person, contentDescription = p.name, tint = accent)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(p.name, color = onBg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Text(p.role.uppercase(), color = onBg.copy(alpha = 0.6f), fontSize = 9.sp)
                    }
                    if (p.muted) {
                        Icon(
                            Icons.Rounded.MicOff,
                            contentDescription = "Muted",
                            tint = CricRed,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(14.dp),
                        )
                    }
                }
            }
        }
        if (studio.brand.showScorebug && match != null) {
            val cur = match.innings.firstOrNull { it.isCurrent } ?: match.innings.lastOrNull()
            Spacer(Modifier.height(6.dp))
            Surface(color = Color(0xDD0B1220), shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (cur != null) "${cur.teamAbbr} ${cur.runs}/${cur.wickets} (${cur.overs})" else match.title,
                        fontFamily = BroadcastDisplayFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White,
                    )
                    Text(
                        text = match.summary.ifBlank { match.statusText },
                        color = CricYellow,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun StartingSoonScene(studio: StudioState, match: MatchDetail?, accent: Color, onBg: Color, cardBg: Color) {
    val remainingSec = studio.countdownTo?.let { max(0L, (it - System.currentTimeMillis()) / 1000L) }
    val timeText = if (remainingSec != null) {
        val m = remainingSec / 60
        val s = remainingSec % 60
        String.format(Locale.US, "%02d:%02d", m, s)
    } else "LIVE SOON"
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.Schedule, contentDescription = null, tint = accent, modifier = Modifier.size(26.dp))
        Text("STARTING SOON", fontFamily = BroadcastDisplayFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = accent)
        Text(timeText, fontFamily = BroadcastDisplayFamily, fontWeight = FontWeight.Bold, fontSize = 38.sp, color = onBg)
        Text(match?.title ?: studio.brand.title, color = onBg.copy(alpha = 0.8f), fontSize = 12.sp)
    }
}

@Composable
private fun AnnouncementScene(studio: StudioState, accent: Color, onBg: Color, cardBg: Color) {
    val ann = studio.announcement
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.Campaign, contentDescription = null, tint = CricYellow, modifier = Modifier.size(28.dp))
        Text(ann?.title ?: "STUDIO UPDATE", fontFamily = BroadcastDisplayFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = onBg)
        Spacer(Modifier.height(4.dp))
        Text(ann?.text ?: "Stay tuned — live cricket coverage resumes shortly.", color = onBg.copy(alpha = 0.85f), fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun BreakScene(match: MatchDetail?, accent: Color, onBg: Color, cardBg: Color) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("INNINGS BREAK", fontFamily = BroadcastDisplayFamily, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = CricYellow)
        Spacer(Modifier.height(4.dp))
        Text(match?.summary?.ifBlank { match.title } ?: "We'll be right back!", color = onBg, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ResultScene(match: MatchDetail?, accent: Color, onBg: Color, cardBg: Color) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = CricYellow, modifier = Modifier.size(30.dp))
        Text("MATCH RESULT", fontFamily = BroadcastDisplayFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = CricYellow)
        Text(match?.summary?.ifBlank { match.statusText } ?: "Thanks for watching!", color = onBg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}
