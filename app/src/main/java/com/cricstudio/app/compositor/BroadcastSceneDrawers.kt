package com.cricstudio.app.compositor

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.cricstudio.app.engine.BroadcastAnalytics
import com.cricstudio.app.model.CompositorSnapshot
import com.cricstudio.app.model.MatchDetail
import com.cricstudio.app.model.MatchStateCode
import com.cricstudio.app.model.SceneId
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/**
 * Dynamic, TV-network-grade 1280x720 scene drawers covering all 40+ CricStudio scenes.
 * Every scene animates smoothly on entry (`enterProgress`) and reacts continuously to `nowMs`.
 */
object BroadcastSceneDrawers {
    private const val W = 1280f
    private const val TOP = 68f
    private const val BOTTOM = 612f
    private const val GREEN = 0xFF34A853.toInt()
    private const val RED = 0xFFEA4335.toInt()
    private const val YELLOW = 0xFFFBBC04.toInt()
    private const val PURPLE = 0xFF9334E6.toInt()

    data class Style(
        val accent: Int,
        val onBg: Int,
        val subText: Int,
        val cardFill: Int,
        val cardStroke: Int,
        val displayFont: Typeface,
        val bodyFont: Typeface,
        val enter: Float,
        val nowMs: Long,
    )

    private fun easeOut(x: Float): Float = 1f - (1f - x.coerceIn(0f, 1f)).pow(3)

    private fun withAlpha(c: Int, a: Float): Int =
        Color.argb((a.coerceIn(0f, 1f) * 255f).toInt(), Color.red(c), Color.green(c), Color.blue(c))

    private fun card(canvas: Canvas, r: RectF, s: Style, radius: Float = 18f, stroke: Int = s.cardStroke) {
        val fillP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = s.cardFill }
        val strokeP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.6f
            color = stroke
        }
        canvas.drawRoundRect(r, radius, radius, fillP)
        canvas.drawRoundRect(r, radius, radius, strokeP)
    }

    private fun text(
        canvas: Canvas,
        str: String,
        x: Float,
        y: Float,
        size: Float,
        color: Int,
        font: Typeface,
        align: Paint.Align = Paint.Align.LEFT,
        maxWidth: Float = 0f,
    ) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = font
            textSize = size
            this.color = color
            textAlign = align
        }
        var out = str
        if (maxWidth > 0f && p.measureText(out) > maxWidth) {
            while (out.length > 3 && p.measureText("$out…") > maxWidth) {
                out = out.dropLast(1)
            }
            out = "$out…"
        }
        canvas.drawText(out, x, y, p)
    }

    private fun ballChip(canvas: Canvas, label: String, cx: Float, cy: Float, radius: Float, s: Style, glow: Boolean = false) {
        val bg = when {
            label == "W" -> RED
            label == "6" -> PURPLE
            label == "4" -> GREEN
            label.contains("wd") || label.contains("nb") -> YELLOW
            label == "•" -> withAlpha(s.onBg, 0.22f)
            else -> s.accent
        }
        if (glow) {
            val gPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(bg, 0.42f) }
            canvas.drawCircle(cx, cy, radius + 7f, gPaint)
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg }
        canvas.drawCircle(cx, cy, radius, fill)
        val fg = if (bg == YELLOW) Color.rgb(22, 24, 28) else Color.WHITE
        text(canvas, label, cx, cy + radius * 0.36f, radius * 0.92f, fg, s.displayFont, Paint.Align.CENTER)
    }

    private fun noMatch(canvas: Canvas, label: String, s: Style) {
        val r = RectF(260f, 250f, W - 260f, 440f)
        card(canvas, r, s, 22f, withAlpha(s.accent, 0.5f))
        text(canvas, "LIVE MATCH DATA STANDBY", W / 2f, 326f, 44f, s.accent, s.displayFont, Paint.Align.CENTER)
        text(canvas, label, W / 2f, 382f, 24f, s.subText, s.bodyFont, Paint.Align.CENTER)
    }

    fun draw(canvas: Canvas, snapshot: CompositorSnapshot, s: Style) {
        val studio = snapshot.studio
        val match = snapshot.match
        when (studio.scene) {
            SceneId.SCORE_HERO -> drawScoreHero(canvas, match, s)
            SceneId.BALL_BY_BALL -> drawBallByBall(canvas, match, s)
            SceneId.BATTING_CARD -> drawBattingCard(canvas, match, s)
            SceneId.BOWLING_CARD -> drawBowlingCard(canvas, match, s)
            SceneId.BATTERS, SceneId.PARTNERSHIPS -> drawCreaseAndPartnership(canvas, match, s)
            SceneId.BOWLER_SPOTLIGHT, SceneId.BOWLING_ANALYSIS -> drawBowlerSpotlight(canvas, match, s)
            SceneId.MANHATTAN -> drawManhattan(canvas, match, s)
            SceneId.OVER_HEATMAP -> drawOverHeatmap(canvas, match, s)
            SceneId.WORM, SceneId.RUN_RATE_GRAPH, SceneId.INNINGS_COMPARISON -> drawWormAndCompare(canvas, match, studio.scene, s)
            SceneId.FALL_OF_WICKETS, SceneId.DISMISSAL_ANALYSIS, SceneId.KEY_MOMENTS -> drawFallOfWickets(canvas, match, s)
            SceneId.WIN_PREDICTOR, SceneId.MOMENTUM -> drawWinPredictor(canvas, match, s)
            SceneId.PROJECTED_SCORE, SceneId.CHASE_CALCULATOR -> drawProjectedScore(canvas, match, s)
            SceneId.PHASE_ANALYSIS, SceneId.SCORING_BREAKDOWN, SceneId.BOUNDARY_TRACKER, SceneId.EXTRAS_DISCIPLINE ->
                drawPhaseAndScoring(canvas, match, s)
            SceneId.WAGON_WHEEL, SceneId.GROUND_3D -> drawWagonWheel3D(canvas, match, s)
            SceneId.STAR_PERFORMER, SceneId.MATCHUP, SceneId.BATTING_IMPACT -> drawStarPerformer(canvas, match, s)
            SceneId.PLAYING_XI -> drawPlayingXi(canvas, match, s)
            SceneId.AROUND_GROUNDS, SceneId.UPCOMING_MATCHES -> drawAroundGrounds(canvas, snapshot, studio.scene == SceneId.UPCOMING_MATCHES, s)
            SceneId.FAN_POLL -> drawFanPoll(canvas, snapshot, s)
            SceneId.HOST_SOLO, SceneId.PANEL, SceneId.COMMENTARY_DESK, SceneId.SCREEN_SHARE -> drawCameraStage(canvas, snapshot, s)
            SceneId.STARTING_SOON, SceneId.INTRO_3D -> drawStartingSoon(canvas, snapshot, s)
            SceneId.MESSAGE -> drawAnnouncement(canvas, snapshot, s)
            SceneId.BREAK -> drawBreak(canvas, match, s)
            SceneId.RESULT, SceneId.MATCH_REPORT -> drawResult(canvas, match, s)
        }
    }

    private fun drawScoreHero(canvas: Canvas, match: MatchDetail?, s: Style) {
        if (match == null) {
            noMatch(canvas, "Pick a live match or Demo T20I replay in the Match tab", s)
            return
        }
        val cur = match.innings.firstOrNull { it.isCurrent } ?: match.innings.lastOrNull()
        val p = easeOut(s.enter)
        val slideY = (1f - p) * 28f

        // Hero Main Scoreboard Panel
        val heroRect = RectF(44f, TOP + 18f + slideY, W - 44f, 342f + slideY)
        card(canvas, heroRect, s, 24f, withAlpha(s.accent, 0.65f))

        // Left accent stripe
        val accentStrip = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = s.accent }
        canvas.drawRoundRect(RectF(44f, TOP + 18f + slideY, 56f, 342f + slideY), 10f, 10f, accentStrip)

        // Series / Venue top line
        val meta = listOf(match.series, match.description, match.venue).filter { it.isNotBlank() }.joinToString("  ·  ")
        text(canvas, meta.uppercase(), 80f, TOP + 56f + slideY, 21f, s.subText, s.bodyFont, maxWidth = 860f)

        // Team + Giant Live Score
        val teamAbbr = cur?.teamAbbr ?: match.teams.firstOrNull()?.abbr ?: "LIVE"
        val scoreStr = if (cur != null) "${cur.runs}/${cur.wickets}" else match.teams.firstOrNull()?.score.orEmpty()
        text(canvas, teamAbbr, 80f, TOP + 178f + slideY, 96f, s.accent, s.displayFont)
        text(canvas, scoreStr, 265f, TOP + 184f + slideY, 132f, s.onBg, s.displayFont)
        if (cur != null) {
            text(canvas, "(${cur.overs} ov)", 585f, TOP + 174f + slideY, 44f, s.subText, s.displayFont)
        }

        // Right CRR / RRR / Partnership metrics box
        val rightX = W - 76f
        val crrPill = RectF(W - 350f, TOP + 46f + slideY, rightX, TOP + 110f + slideY)
        card(canvas, crrPill, s, 14f, withAlpha(s.accent, 0.5f))
        text(canvas, "CRR", crrPill.left + 18f, crrPill.top + 42f, 24f, s.subText, s.displayFont)
        text(canvas, String.format(Locale.US, "%.2f", match.crr), crrPill.right - 18f, crrPill.top + 46f, 40f, s.onBg, s.displayFont, Paint.Align.RIGHT)

        val rrrVal = match.rrr
        val secondPill = RectF(W - 350f, TOP + 124f + slideY, rightX, TOP + 188f + slideY)
        card(canvas, secondPill, s, 14f, withAlpha(if (rrrVal != null) YELLOW else GREEN, 0.55f))
        if (rrrVal != null) {
            text(canvas, "REQ RATE", secondPill.left + 18f, secondPill.top + 42f, 24f, YELLOW, s.displayFont)
            text(canvas, String.format(Locale.US, "%.2f", rrrVal), secondPill.right - 18f, secondPill.top + 46f, 40f, YELLOW, s.displayFont, Paint.Align.RIGHT)
        } else {
            text(canvas, "STAND", secondPill.left + 18f, secondPill.top + 42f, 24f, GREEN, s.displayFont)
            text(canvas, "${match.partnership.runs} (${match.partnership.balls})", secondPill.right - 18f, secondPill.top + 46f, 36f, s.onBg, s.displayFont, Paint.Align.RIGHT)
        }

        // Summary / Chase equation bar inside hero card
        val summaryText = match.summary.ifBlank { match.statusText }
        if (summaryText.isNotBlank()) {
            val sumBar = RectF(80f, TOP + 206f + slideY, W - 76f, TOP + 254f + slideY)
            val sumPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(s.accent, 0.24f) }
            canvas.drawRoundRect(sumBar, 12f, 12f, sumPaint)
            text(canvas, summaryText, sumBar.left + 18f, sumBar.top + 33f, 24f, s.onBg, s.bodyFont, maxWidth = sumBar.width() - 36f)
        }

        // Bottom dual panels: Batters at Crease + Bowler & This Over
        val batRect = RectF(44f, 364f + slideY * 0.7f, 654f, BOTTOM - 10f)
        val bowlRect = RectF(674f, 364f + slideY * 0.7f, W - 44f, BOTTOM - 10f)
        card(canvas, batRect, s, 20f)
        card(canvas, bowlRect, s, 20f)

        text(canvas, "AT THE CREASE", batRect.left + 22f, batRect.top + 36f, 24f, s.accent, s.displayFont)
        match.currentBatters.take(2).forEachIndexed { idx, b ->
            val rowY = batRect.top + 92f + idx * 72f
            if (idx == 0) {
                val hl = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(s.accent, 0.16f) }
                canvas.drawRoundRect(RectF(batRect.left + 16f, rowY - 36f, batRect.right - 16f, rowY + 22f), 12f, 12f, hl)
            }
            text(canvas, (if (idx == 0) "★ " else "  ") + b.name, batRect.left + 26f, rowY, 27f, s.onBg, s.bodyFont, maxWidth = 310f)
            text(canvas, "${b.runs}", batRect.right - 185f, rowY + 4f, 44f, if (idx == 0) s.accent else s.onBg, s.displayFont, Paint.Align.RIGHT)
            text(canvas, "(${b.balls})  SR ${String.format(Locale.US, "%.0f", b.sr)}", batRect.right - 24f, rowY, 22f, s.subText, s.bodyFont, Paint.Align.RIGHT)
        }

        text(canvas, "CURRENT BOWLER & THIS OVER", bowlRect.left + 22f, bowlRect.top + 36f, 24f, YELLOW, s.displayFont)
        match.currentBowler?.let { bw ->
            text(canvas, bw.name, bowlRect.left + 22f, bowlRect.top + 88f, 30f, s.onBg, s.bodyFont, maxWidth = 340f)
            text(canvas, "${bw.wickets}/${bw.conceded} (${bw.overs} ov)", bowlRect.right - 22f, bowlRect.top + 90f, 36f, YELLOW, s.displayFont, Paint.Align.RIGHT)
        }
        val thisOver = match.recentBalls.filter { it.innings == (cur?.number ?: 1) }.let { balls ->
            val ov = balls.lastOrNull()?.overNumber ?: return@let emptyList()
            balls.filter { it.overNumber == ov }.takeLast(8)
        }
        val pulseLatest = (s.nowMs / 350L) % 2L == 0L
        thisOver.forEachIndexed { idx, b ->
            val cx = bowlRect.left + 48f + idx * 66f
            val cy = bowlRect.bottom - 58f
            ballChip(canvas, b.label, cx, cy, 24f, s, glow = idx == thisOver.lastIndex && pulseLatest)
        }
    }

    private fun drawBallByBall(canvas: Canvas, match: MatchDetail?, s: Style) {
        if (match == null) { noMatch(canvas, "Ball-by-ball feed standby", s); return }
        text(canvas, "BALL BY BALL COMMENTARY", 48f, TOP + 44f, 42f, s.accent, s.displayFont)
        val recent = match.recentBalls.takeLast(6).reversed()
        recent.forEachIndexed { idx, b ->
            val itemP = easeOut((s.enter * 1.4f - idx * 0.1f).coerceIn(0f, 1f))
            val y = TOP + 68f + idx * 78f + (1f - itemP) * 24f
            val r = RectF(48f, y, W - 48f, y + 66f)
            card(canvas, r, s, 14f, if (idx == 0) withAlpha(s.accent, 0.7f) else s.cardStroke)
            text(canvas, "OV ${b.over}", r.left + 20f, y + 42f, 26f, s.subText, s.displayFont)
            ballChip(canvas, b.label, r.left + 150f, y + 33f, 21f, s, glow = idx == 0)
            text(canvas, b.short.ifBlank { b.text }, r.left + 195f, y + 42f, 24f, s.onBg, s.bodyFont, maxWidth = W - 275f)
        }
    }

    private fun drawBattingCard(canvas: Canvas, match: MatchDetail?, s: Style) {
        val inn = match?.innings?.firstOrNull { it.isCurrent } ?: match?.innings?.lastOrNull()
        if (inn == null) { noMatch(canvas, "Batting scorecard unavailable", s); return }
        text(canvas, "${inn.teamName.uppercase()} BATTING", 48f, TOP + 44f, 42f, s.accent, s.displayFont)
        text(canvas, "${inn.runs}/${inn.wickets} (${inn.overs} ov)", W - 48f, TOP + 46f, 44f, s.onBg, s.displayFont, Paint.Align.RIGHT)

        val panel = RectF(48f, TOP + 64f, W - 48f, BOTTOM - 10f)
        card(canvas, panel, s, 20f)
        val headers = listOf("BATTER" to 76f, "R" to 760f, "B" to 855f, "4s" to 945f, "6s" to 1030f, "SR" to 1185f)
        headers.forEachIndexed { idx, (lbl, x) ->
            text(canvas, lbl, x, panel.top + 38f, 22f, s.subText, s.displayFont, if (idx == 0) Paint.Align.LEFT else Paint.Align.RIGHT)
        }
        inn.batting.take(8).forEachIndexed { idx, b ->
            val y = panel.top + 88f + idx * 52f
            if (b.notOut) {
                val hl = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(s.accent, 0.14f) }
                canvas.drawRoundRect(RectF(64f, y - 30f, W - 64f, y + 14f), 10f, 10f, hl)
            }
            text(canvas, b.name + if (b.notOut) " *" else "", 76f, y, 24f, if (b.notOut) s.accent else s.onBg, s.bodyFont, maxWidth = 560f)
            text(canvas, "${b.runs}", 760f, y + 2f, 30f, s.onBg, s.displayFont, Paint.Align.RIGHT)
            text(canvas, "${b.balls}", 855f, y, 24f, s.subText, s.bodyFont, Paint.Align.RIGHT)
            text(canvas, "${b.fours}", 945f, y, 24f, GREEN, s.bodyFont, Paint.Align.RIGHT)
            text(canvas, "${b.sixes}", 1030f, y, 24f, PURPLE, s.bodyFont, Paint.Align.RIGHT)
            text(canvas, String.format(Locale.US, "%.1f", b.sr), 1185f, y, 24f, s.subText, s.bodyFont, Paint.Align.RIGHT)
        }
    }

    private fun drawBowlingCard(canvas: Canvas, match: MatchDetail?, s: Style) {
        val inn = match?.innings?.firstOrNull { it.isCurrent } ?: match?.innings?.lastOrNull()
        if (inn == null) { noMatch(canvas, "Bowling figures unavailable", s); return }
        text(canvas, "BOWLING ATTACK", 48f, TOP + 44f, 42f, s.accent, s.displayFont)
        val panel = RectF(48f, TOP + 64f, W - 48f, BOTTOM - 10f)
        card(canvas, panel, s, 20f)
        val headers = listOf("BOWLER" to 76f, "O" to 740f, "M" to 835f, "R" to 925f, "W" to 1020f, "ECON" to 1185f)
        headers.forEachIndexed { idx, (lbl, x) ->
            text(canvas, lbl, x, panel.top + 38f, 22f, s.subText, s.displayFont, if (idx == 0) Paint.Align.LEFT else Paint.Align.RIGHT)
        }
        inn.bowling.take(7).forEachIndexed { idx, bw ->
            val y = panel.top + 94f + idx * 58f
            text(canvas, bw.name, 76f, y, 26f, s.onBg, s.bodyFont, maxWidth = 540f)
            text(canvas, "${bw.overs}", 740f, y, 25f, s.subText, s.bodyFont, Paint.Align.RIGHT)
            text(canvas, "${bw.maidens}", 835f, y, 25f, s.subText, s.bodyFont, Paint.Align.RIGHT)
            text(canvas, "${bw.conceded}", 925f, y, 25f, s.onBg, s.bodyFont, Paint.Align.RIGHT)
            text(canvas, "${bw.wickets}", 1020f, y + 3f, 34f, if (bw.wickets > 0) YELLOW else s.onBg, s.displayFont, Paint.Align.RIGHT)
            text(canvas, String.format(Locale.US, "%.2f", bw.econ), 1185f, y, 25f, s.accent, s.bodyFont, Paint.Align.RIGHT)
        }
    }

    private fun drawCreaseAndPartnership(canvas: Canvas, match: MatchDetail?, s: Style) {
        if (match == null) { noMatch(canvas, "No active partnership", s); return }
        text(canvas, "AT THE CREASE & PARTNERSHIP", 48f, TOP + 44f, 42f, s.accent, s.displayFont)
        val pCard = RectF(48f, TOP + 68f, W - 48f, TOP + 178f)
        card(canvas, pCard, s, 18f, withAlpha(YELLOW, 0.7f))
        text(canvas, "CURRENT STAND", 80f, pCard.top + 48f, 24f, YELLOW, s.displayFont)
        text(canvas, "${match.partnership.runs} RUNS (${match.partnership.balls} BALLS)", 80f, pCard.top + 96f, 52f, s.onBg, s.displayFont)

        match.currentBatters.take(2).forEachIndexed { idx, b ->
            val left = if (idx == 0) 48f else W / 2f + 14f
            val right = if (idx == 0) W / 2f - 14f else W - 48f
            val r = RectF(left, TOP + 202f, right, BOTTOM - 10f)
            card(canvas, r, s, 22f, if (idx == 0) withAlpha(s.accent, 0.65f) else s.cardStroke)
            text(canvas, b.name, r.left + 28f, r.top + 56f, 34f, s.onBg, s.bodyFont, maxWidth = r.width() - 56f)
            text(canvas, "${b.runs}", r.left + 28f, r.top + 178f, 118f, if (idx == 0) s.accent else YELLOW, s.displayFont)
            text(canvas, "off ${b.balls} balls", r.left + 195f, r.top + 170f, 30f, s.subText, s.bodyFont)
            text(canvas, "${b.fours} Fours   ·   ${b.sixes} Sixes   ·   SR ${String.format(Locale.US, "%.1f", b.sr)}", r.left + 28f, r.bottom - 38f, 24f, s.onBg, s.bodyFont)
        }
    }

    private fun drawBowlerSpotlight(canvas: Canvas, match: MatchDetail?, s: Style) {
        val bw = match?.currentBowler ?: match?.innings?.lastOrNull()?.bowling?.maxByOrNull { it.wickets }
        if (bw == null) { noMatch(canvas, "No active bowler", s); return }
        text(canvas, "BOWLER SPOTLIGHT", 48f, TOP + 44f, 42f, s.accent, s.displayFont)
        val r = RectF(48f, TOP + 74f, W - 48f, BOTTOM - 14f)
        card(canvas, r, s, 24f, withAlpha(YELLOW, 0.6f))
        text(canvas, bw.name.uppercase(), 92f, r.top + 100f, 64f, s.onBg, s.displayFont)
        text(canvas, "${bw.wickets}/${bw.conceded}", W - 92f, r.top + 138f, 128f, YELLOW, s.displayFont, Paint.Align.RIGHT)
        val stats = listOf(
            "OVERS" to "${bw.overs}",
            "ECONOMY" to String.format(Locale.US, "%.2f", bw.econ),
            "MAIDENS" to "${bw.maidens}",
            "DOT BALLS" to "${bw.dots}",
        )
        stats.forEachIndexed { idx, (lbl, v) ->
            val bx = 92f + idx * 276f
            val box = RectF(bx, r.bottom - 175f, bx + 246f, r.bottom - 34f)
            card(canvas, box, s, 16f)
            text(canvas, lbl, box.centerX(), box.top + 42f, 22f, s.subText, s.displayFont, Paint.Align.CENTER)
            text(canvas, v, box.centerX(), box.bottom - 34f, 56f, s.accent, s.displayFont, Paint.Align.CENTER)
        }
    }

    private fun drawManhattan(canvas: Canvas, match: MatchDetail?, s: Style) {
        val inn = match?.innings?.firstOrNull { it.isCurrent } ?: match?.innings?.lastOrNull()
        val overs = inn?.overSummaries.orEmpty()
        if (overs.isEmpty()) { noMatch(canvas, "Waiting for completed overs", s); return }
        val p = easeOut(s.enter)
        text(canvas, "MANHATTAN · ${inn?.teamAbbr.orEmpty()} RUNS PER OVER", 48f, TOP + 44f, 40f, s.accent, s.displayFont)

        val chart = RectF(48f, TOP + 68f, W - 48f, BOTTOM - 10f)
        card(canvas, chart, s, 20f)

        val maxRuns = max(14, overs.maxOf { it.runs })
        val padL = 78f
        val padR = 36f
        val padT = 46f
        val padB = 56f
        val plotW = chart.width() - padL - padR
        val plotH = chart.height() - padT - padB
        val baseY = chart.bottom - padB

        // Horizontal grid lines
        val gridP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(s.onBg, 0.12f); strokeWidth = 1.5f }
        for (g in 0..4) {
            val gy = baseY - (g / 4f) * plotH
            canvas.drawLine(chart.left + padL, gy, chart.right - padR, gy, gridP)
            text(canvas, "${(maxRuns * g / 4)}", chart.left + padL - 14f, gy + 7f, 18f, s.subText, s.bodyFont, Paint.Align.RIGHT)
        }

        val slot = plotW / max(1, overs.size)
        val barW = (slot * 0.66f).coerceAtMost(42f)
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val wktPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RED }

        overs.forEachIndexed { idx, ov ->
            val barP = easeOut((p * 1.3f - idx * 0.025f).coerceIn(0f, 1f))
            val bh = (ov.runs.toFloat() / maxRuns) * plotH * barP
            val x = chart.left + padL + idx * slot + (slot - barW) / 2f
            val col = if (ov.runs >= 14) YELLOW else s.accent
            barPaint.shader = LinearGradient(x, baseY - bh, x, baseY, col, withAlpha(col, 0.45f), Shader.TileMode.CLAMP)
            canvas.drawRoundRect(RectF(x, baseY - bh, x + barW, baseY), 8f, 8f, barPaint)
            repeat(ov.wickets) { w ->
                canvas.drawCircle(x + barW / 2f, (baseY - bh - 14f - w * 22f).coerceAtLeast(chart.top + 20f), 9f, wktPaint)
            }
            if (overs.size <= 20 || idx % 2 == 0) {
                text(canvas, "${ov.over}", x + barW / 2f, baseY + 28f, 18f, s.subText, s.bodyFont, Paint.Align.CENTER)
            }
        }
    }

    private fun drawOverHeatmap(canvas: Canvas, match: MatchDetail?, s: Style) {
        val inn = match?.innings?.firstOrNull { it.isCurrent } ?: match?.innings?.lastOrNull()
        val overs = inn?.overSummaries.orEmpty()
        if (overs.isEmpty()) { noMatch(canvas, "Over heatmap awaiting overs", s); return }
        text(canvas, "OVER INTENSITY HEATMAP · ${inn?.teamAbbr.orEmpty()}", 48f, TOP + 44f, 40f, s.accent, s.displayFont)

        val cols = 5
        val cellW = (W - 96f - (cols - 1) * 14f) / cols
        val cellH = 104f
        overs.take(20).forEachIndexed { idx, ov ->
            val c = idx % cols
            val row = idx / cols
            val x = 48f + c * (cellW + 14f)
            val y = TOP + 72f + row * (cellH + 14f)
            val intensity = (ov.runs / 18f).coerceIn(0.15f, 1f)
            val tileColor = when {
                ov.wickets > 0 -> withAlpha(RED, 0.55f)
                ov.runs >= 14 -> withAlpha(YELLOW, 0.65f)
                else -> withAlpha(s.accent, 0.22f + intensity * 0.58f)
            }
            val r = RectF(x, y, x + cellW, y + cellH)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tileColor }
            canvas.drawRoundRect(r, 14f, 14f, p)
            text(canvas, "OV ${ov.over}", x + 16f, y + 32f, 20f, s.onBg, s.displayFont)
            text(canvas, "${ov.runs}r" + if (ov.wickets > 0) " · ${ov.wickets}W" else "", x + 16f, y + 80f, 38f, s.onBg, s.displayFont)
        }
    }

    private fun drawWormAndCompare(canvas: Canvas, match: MatchDetail?, scene: SceneId, s: Style) {
        val innings = match?.innings?.filter { it.overSummaries.isNotEmpty() }.orEmpty()
        if (innings.isEmpty()) { noMatch(canvas, "Not enough overs for graph", s); return }
        val p = easeOut(s.enter)
        val title = when (scene) {
            SceneId.RUN_RATE_GRAPH -> "RUN RATE PROGRESSION"
            SceneId.INNINGS_COMPARISON -> "INNINGS COMPARISON · AT THIS STAGE"
            else -> "WORM · CUMULATIVE RUNS"
        }
        text(canvas, title, 48f, TOP + 44f, 40f, s.accent, s.displayFont)

        val chart = RectF(48f, TOP + 68f, W - 48f, BOTTOM - 10f)
        card(canvas, chart, s, 20f)
        val maxOvers = max(6, innings.maxOf { it.overSummaries.size })
        val maxRuns = max(40, innings.maxOf { i -> i.overSummaries.maxOf { it.cumulative } })
        val colors = listOf(s.accent, YELLOW, GREEN)

        val padL = 78f; val padR = 40f; val padT = 56f; val padB = 54f
        val plotW = chart.width() - padL - padR
        val plotH = chart.height() - padT - padB
        val baseY = chart.bottom - padB

        innings.forEachIndexed { idx, inn ->
            val col = colors[idx % colors.size]
            text(canvas, "● ${inn.teamAbbr} ${inn.runs}/${inn.wickets}", chart.right - 44f - idx * 240f, chart.top + 38f, 26f, col, s.displayFont, Paint.Align.RIGHT)

            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 6f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                color = col
            }
            val dotP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RED }
            val path = Path()
            path.moveTo(chart.left + padL, baseY)
            val visibleCount = max(1, (inn.overSummaries.size * p).toInt())
            inn.overSummaries.take(visibleCount).forEachIndexed { i, ov ->
                val x = chart.left + padL + ((i + 1).toFloat() / maxOvers) * plotW
                val y = baseY - (ov.cumulative.toFloat() / maxRuns) * plotH
                path.lineTo(x, y)
                if (ov.wickets > 0) canvas.drawCircle(x, y, 8.5f, dotP)
            }
            canvas.drawPath(path, stroke)
        }
    }

    private fun drawFallOfWickets(canvas: Canvas, match: MatchDetail?, s: Style) {
        val inn = match?.innings?.firstOrNull { it.isCurrent } ?: match?.innings?.lastOrNull()
        val fows = inn?.fow.orEmpty()
        if (fows.isEmpty()) { noMatch(canvas, "No wickets fallen yet", s); return }
        text(canvas, "FALL OF WICKETS · ${inn?.teamAbbr.orEmpty()}", 48f, TOP + 44f, 40f, RED, s.displayFont)
        fows.takeLast(6).forEachIndexed { idx, f ->
            val y = TOP + 70f + idx * 78f
            val r = RectF(48f, y, W - 48f, y + 66f)
            card(canvas, r, s, 14f, withAlpha(RED, 0.45f))
            text(canvas, "${f.runs}/${f.wicket}", r.left + 26f, y + 45f, 38f, YELLOW, s.displayFont)
            text(canvas, f.batter, r.left + 190f, y + 42f, 27f, s.onBg, s.bodyFont, maxWidth = 760f)
            text(canvas, "OVER ${f.over}", r.right - 26f, y + 42f, 26f, s.subText, s.displayFont, Paint.Align.RIGHT)
        }
    }

    private fun drawWinPredictor(canvas: Canvas, match: MatchDetail?, s: Style) {
        if (match == null) { noMatch(canvas, "Attach a match for Live Win Predictor", s); return }
        val prob = BroadcastAnalytics.winProbability(match)
        val momentum = BroadcastAnalytics.momentumIndex(match.innings.firstOrNull { it.isCurrent })
        val p = easeOut(s.enter)

        text(canvas, "LIVE WIN PROBABILITY & MOMENTUM", 48f, TOP + 44f, 42f, s.accent, s.displayFont)
        val panel = RectF(48f, TOP + 76f, W - 48f, BOTTOM - 14f)
        card(canvas, panel, s, 24f, withAlpha(s.accent, 0.55f))

        text(canvas, prob.teamAAbbr, 92f, panel.top + 96f, 64f, s.accent, s.displayFont)
        text(canvas, "${(prob.teamAPct * p).toInt()}%", 92f, panel.top + 188f, 98f, s.onBg, s.displayFont)

        text(canvas, prob.teamBAbbr, W - 92f, panel.top + 96f, 64f, YELLOW, s.displayFont, Paint.Align.RIGHT)
        text(canvas, "${(prob.teamBPct * p).toInt()}%", W - 92f, panel.top + 188f, 98f, s.onBg, s.displayFont, Paint.Align.RIGHT)

        val barL = 92f; val barR = W - 92f; val barY = panel.top + 236f; val barH = 44f
        val splitX = barL + (barR - barL) * (prob.teamAPct / 100f) * p
        val pA = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = s.accent }
        val pB = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = YELLOW }
        canvas.drawRoundRect(RectF(barL, barY, barR, barY + barH), 22f, 22f, pB)
        canvas.drawRoundRect(RectF(barL, barY, splitX.coerceAtLeast(barL + 32f), barY + barH), 22f, 22f, pA)

        text(canvas, prob.headline, W / 2f, panel.bottom - 94f, 30f, s.onBg, s.bodyFont, Paint.Align.CENTER)
        text(canvas, "BATTING MOMENTUM INDEX: $momentum / 100", W / 2f, panel.bottom - 42f, 28f, GREEN, s.displayFont, Paint.Align.CENTER)
    }

    private fun drawProjectedScore(canvas: Canvas, match: MatchDetail?, s: Style) {
        if (match == null) { noMatch(canvas, "Projected score unavailable", s); return }
        val rows = BroadcastAnalytics.projectedScores(match)
        val p = easeOut(s.enter)
        text(canvas, "PROJECTED TOTAL & CHASE CALCULATOR", 48f, TOP + 44f, 40f, s.accent, s.displayFont)
        val cardW = (W - 96f - (rows.size - 1) * 18f) / max(1, rows.size)
        rows.forEachIndexed { idx, (label, score) ->
            val x = 48f + idx * (cardW + 18f)
            val r = RectF(x, TOP + 96f, x + cardW, BOTTOM - 26f)
            card(canvas, r, s, 20f, if (idx == 0) withAlpha(YELLOW, 0.75f) else s.cardStroke)
            text(canvas, "${(score * p).toInt()}", r.centerX(), r.centerY() + 12f, 108f, if (idx == 0) YELLOW else s.onBg, s.displayFont, Paint.Align.CENTER)
            text(canvas, label.uppercase(), r.centerX(), r.bottom - 48f, 22f, s.subText, s.bodyFont, Paint.Align.CENTER, maxWidth = cardW - 24f)
        }
    }

    private fun drawPhaseAndScoring(canvas: Canvas, match: MatchDetail?, s: Style) {
        val inn = match?.innings?.firstOrNull { it.isCurrent } ?: match?.innings?.lastOrNull()
        val phases = BroadcastAnalytics.phaseBreakdown(inn)
        val slices = BroadcastAnalytics.scoringBreakdown(match?.recentBalls.orEmpty())
        text(canvas, "PHASE ANALYSIS & SCORING BREAKDOWN", 48f, TOP + 44f, 40f, s.accent, s.displayFont)

        phases.forEachIndexed { idx, ph ->
            val w = (W - 96f - 32f) / 3f
            val x = 48f + idx * (w + 16f)
            val r = RectF(x, TOP + 70f, x + w, TOP + 276f)
            card(canvas, r, s, 18f)
            text(canvas, ph.label.uppercase(), x + 22f, r.top + 38f, 24f, s.accent, s.displayFont)
            text(canvas, "${ph.runs}/${ph.wickets}", x + 22f, r.top + 124f, 76f, s.onBg, s.displayFont)
            text(canvas, "${ph.range}  ·  RR ${String.format(Locale.US, "%.1f", ph.runRate)}", x + 22f, r.bottom - 26f, 22f, s.subText, s.bodyFont)
        }

        // Scoring breakdown strip
        val sw = (W - 96f - (slices.size - 1) * 14f) / max(1, slices.size)
        slices.forEachIndexed { idx, sl ->
            val x = 48f + idx * (sw + 14f)
            val r = RectF(x, TOP + 306f, x + sw, BOTTOM - 14f)
            val c = sl.colorHex.toInt()
            card(canvas, r, s, 16f, withAlpha(c, 0.75f))
            text(canvas, sl.label.uppercase(), r.centerX(), r.top + 48f, 24f, c, s.displayFont, Paint.Align.CENTER)
            text(canvas, "${sl.count}", r.centerX(), r.bottom - 48f, 74f, s.onBg, s.displayFont, Paint.Align.CENTER)
        }
    }

    private fun drawWagonWheel3D(canvas: Canvas, match: MatchDetail?, s: Style) {
        val batter = match?.currentBatters?.firstOrNull() ?: match?.innings?.lastOrNull()?.batting?.maxByOrNull { it.runs }
        val zones = batter?.wagon?.takeIf { it.size == 8 } ?: listOf(10, 16, 22, 8, 12, 26, 14, 6)
        val maxZone = max(1, zones.maxOrNull() ?: 1)
        val p = easeOut(s.enter)

        text(canvas, "WAGON WHEEL · 360° SHOT MAP", 48f, TOP + 44f, 40f, s.accent, s.displayFont)

        // Left: Stadium Field Oval with animated radiating shot arcs
        val cx = 360f
        val cy = 368f
        val rx = 265f
        val ry = 215f
        val turf = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(cx, cy, rx, Color.rgb(22, 101, 52), Color.rgb(10, 48, 25), Shader.TileMode.CLAMP)
        }
        canvas.drawOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), turf)
        val ringP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = Color.argb(180, 255, 255, 255) }
        canvas.drawOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), ringP)
        ringP.color = Color.argb(95, 255, 255, 255)
        canvas.drawOval(RectF(cx - rx * 0.55f, cy - ry * 0.55f, cx + rx * 0.55f, cy + ry * 0.55f), ringP)

        val pitchP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(214, 181, 136) }
        canvas.drawRect(cx - 10f, cy - 36f, cx + 10f, cy + 36f, pitchP)

        val shotP = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 7f; strokeCap = Paint.Cap.ROUND }
        zones.forEachIndexed { idx, runs ->
            val ang = (idx * 45.0 - 90.0) * PI / 180.0
            val frac = (runs.toFloat() / maxZone) * p
            val ex = cx + (cos(ang) * (rx - 18f) * frac).toFloat()
            val ey = cy + (sin(ang) * (ry - 18f) * frac).toFloat()
            shotP.color = if (runs >= maxZone * 0.72f) YELLOW else s.accent
            canvas.drawLine(cx, cy, ex, ey, shotP)
            canvas.drawCircle(ex, ey, 7f, shotP)
        }

        // Right: Batter info card
        val infoR = RectF(676f, TOP + 84f, W - 48f, BOTTOM - 18f)
        card(canvas, infoR, s, 22f, withAlpha(s.accent, 0.55f))
        text(canvas, batter?.name ?: "Leading Batter", infoR.left + 32f, infoR.top + 80f, 48f, s.onBg, s.displayFont, maxWidth = infoR.width() - 64f)
        if (batter != null) {
            text(canvas, "${batter.runs} (${batter.balls})", infoR.left + 32f, infoR.top + 190f, 96f, YELLOW, s.displayFont)
            text(canvas, "${batter.fours} Fours  ·  ${batter.sixes} Sixes  ·  SR ${String.format(Locale.US, "%.1f", batter.sr)}", infoR.left + 32f, infoR.top + 252f, 26f, s.onBg, s.bodyFont)
        }
        text(canvas, "Live 8-sector scoring distribution", infoR.left + 32f, infoR.bottom - 42f, 22f, s.subText, s.bodyFont)
    }

    private fun drawStarPerformer(canvas: Canvas, match: MatchDetail?, s: Style) {
        val star = match?.innings?.flatMap { it.batting }?.maxByOrNull { it.runs }
        if (star == null) { noMatch(canvas, "Star performer pending", s); return }
        text(canvas, "STAR PERFORMER OF THE MATCH", 48f, TOP + 44f, 42f, YELLOW, s.displayFont)
        val r = RectF(48f, TOP + 86f, W - 48f, BOTTOM - 20f)
        card(canvas, r, s, 24f, withAlpha(YELLOW, 0.75f))
        text(canvas, star.name.uppercase(), 96f, r.top + 130f, 72f, s.onBg, s.displayFont, maxWidth = 720f)
        text(canvas, "${star.runs} (${star.balls})", W - 96f, r.top + 160f, 128f, s.accent, s.displayFont, Paint.Align.RIGHT)
        text(canvas, "${star.fours} Fours   ·   ${star.sixes} Sixes   ·   Strike Rate ${String.format(Locale.US, "%.1f", star.sr)}", 96f, r.bottom - 76f, 30f, s.subText, s.bodyFont)
    }

    private fun drawPlayingXi(canvas: Canvas, match: MatchDetail?, s: Style) {
        if (match == null || match.teams.isEmpty()) { noMatch(canvas, "Playing XIs unavailable", s); return }
        text(canvas, "PLAYING XIs & TOSS", 48f, TOP + 42f, 38f, s.accent, s.displayFont)
        if (match.toss.isNotBlank()) text(canvas, match.toss, 420f, TOP + 40f, 22f, YELLOW, s.bodyFont, maxWidth = 800f)
        match.teams.take(2).forEachIndexed { idx, team ->
            val left = if (idx == 0) 48f else W / 2f + 12f
            val right = if (idx == 0) W / 2f - 12f else W - 48f
            val r = RectF(left, TOP + 62f, right, BOTTOM - 10f)
            card(canvas, r, s, 18f)
            text(canvas, team.name.uppercase(), r.left + 22f, r.top + 40f, 28f, s.accent, s.displayFont)
            team.squad.take(11).forEachIndexed { pIdx, pl ->
                val tag = (if (pl.captain) " (c)" else "") + (if (pl.keeper) " †" else "")
                text(canvas, "${pIdx + 1}. ${pl.name}$tag", r.left + 22f, r.top + 78f + pIdx * 36f, 22f, s.onBg, s.bodyFont, maxWidth = r.width() - 44f)
            }
        }
    }

    private fun drawAroundGrounds(canvas: Canvas, snapshot: CompositorSnapshot, upcoming: Boolean, s: Style) {
        text(canvas, if (upcoming) "UPCOMING FIXTURES" else "AROUND THE GROUNDS", 48f, TOP + 44f, 40f, s.accent, s.displayFont)
        val list = snapshot.allMatches.filter {
            if (upcoming) it.state == MatchStateCode.PRE else it.state == MatchStateCode.IN || it.isDemo
        }.take(5)
        list.forEachIndexed { idx, m ->
            val y = TOP + 70f + idx * 92f
            val r = RectF(48f, y, W - 48f, y + 80f)
            card(canvas, r, s, 16f)
            val title = m.teams.joinToString("  vs  ") { "${it.abbr} ${it.score}".trim() }.ifBlank { m.name }
            text(canvas, title, r.left + 24f, y + 42f, 30f, s.onBg, s.displayFont, maxWidth = 760f)
            text(canvas, m.leagueName, r.left + 24f, y + 68f, 19f, s.subText, s.bodyFont)
            text(canvas, m.statusText, r.right - 24f, y + 48f, 22f, YELLOW, s.bodyFont, Paint.Align.RIGHT, maxWidth = 340f)
        }
    }

    private fun drawFanPoll(canvas: Canvas, snapshot: CompositorSnapshot, s: Style) {
        val m = snapshot.match
        val t1 = m?.teams?.getOrNull(0)?.name ?: "Team A"
        val t2 = m?.teams?.getOrNull(1)?.name ?: "Team B"
        val prob = m?.let { BroadcastAnalytics.winProbability(it) }
        val p1 = prob?.teamAPct ?: 56
        val p2 = 100 - p1
        val prog = easeOut(s.enter)

        text(canvas, "LIVE FAN POLL · VOTE IN CHAT", 48f, TOP + 44f, 38f, YELLOW, s.displayFont)
        val r = RectF(48f, TOP + 76f, W - 48f, BOTTOM - 16f)
        card(canvas, r, s, 24f, withAlpha(YELLOW, 0.55f))
        text(canvas, snapshot.studio.brand.pollQuestion, 90f, r.top + 96f, 52f, s.onBg, s.displayFont, maxWidth = r.width() - 180f)

        listOf(Triple(t1, p1, s.accent), Triple(t2, p2, GREEN)).forEachIndexed { idx, (name, pct, col) ->
            val by = r.top + 185f + idx * 124f
            text(canvas, name, 90f, by, 30f, s.onBg, s.bodyFont)
            text(canvas, "${(pct * prog).toInt()}%", W - 90f, by + 4f, 42f, col, s.displayFont, Paint.Align.RIGHT)
            val track = RectF(90f, by + 18f, W - 90f, by + 54f)
            val fill = RectF(90f, by + 18f, 90f + track.width() * (pct / 100f) * prog, by + 54f)
            canvas.drawRoundRect(track, 18f, 18f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(s.onBg, 0.14f) })
            canvas.drawRoundRect(fill, 18f, 18f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col })
        }
    }

    private fun drawCameraStage(canvas: Canvas, snapshot: CompositorSnapshot, s: Style) {
        val studio = snapshot.studio
        val onStage = studio.participants.filter { it.onStage }.ifEmpty { studio.participants.take(1) }
        val gap = 18f
        val tileW = (W - 96f - gap * (onStage.size - 1)) / onStage.size
        val tileBottom = if (studio.brand.showScorebug && snapshot.match != null) BOTTOM - 86f else BOTTOM - 14f

        onStage.forEachIndexed { idx, p ->
            val x = 48f + idx * (tileW + gap)
            val r = RectF(x, TOP + 20f, x + tileW, tileBottom)
            card(canvas, r, s, 22f, withAlpha(s.accent, 0.7f))

            // Animated voice waveform ring around active host/speaker
            val speaking = !p.muted && (snapshot.micLevel > 0.05f || p.role == "host")
            val pulse = if (speaking) 1f + 0.18f * (0.5f + 0.5f * sin(s.nowMs / 140.0 + idx).toFloat()) + snapshot.micLevel * 0.25f else 1f
            val cx = r.centerX()
            val cy = r.centerY() - 24f
            if (speaking) {
                canvas.drawCircle(cx, cy, 70f * pulse, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(s.accent, 0.24f) })
            }
            canvas.drawCircle(cx, cy, 58f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(s.accent, 0.4f) })
            text(canvas, p.name.take(2).uppercase(), cx, cy + 20f, 52f, Color.WHITE, s.displayFont, Paint.Align.CENTER)
            text(canvas, p.name, cx, cy + 104f, 32f, s.onBg, s.bodyFont, Paint.Align.CENTER)
            text(canvas, if (p.muted) "MUTED" else p.role.uppercase(), cx, cy + 138f, 22f, if (p.muted) RED else GREEN, s.displayFont, Paint.Align.CENTER)
        }

        if (studio.brand.showScorebug && snapshot.match != null) {
            val cur = snapshot.match.innings.firstOrNull { it.isCurrent } ?: snapshot.match.innings.lastOrNull()
            val bug = RectF(48f, BOTTOM - 72f, W - 48f, BOTTOM - 10f)
            canvas.drawRoundRect(bug, 14f, 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(225, 10, 16, 28) })
            val score = if (cur != null) "${cur.teamAbbr} ${cur.runs}/${cur.wickets} (${cur.overs})" else snapshot.match.title
            text(canvas, score, bug.left + 24f, bug.top + 43f, 36f, Color.WHITE, s.displayFont)
            text(canvas, snapshot.match.summary.ifBlank { snapshot.match.statusText }, bug.right - 24f, bug.top + 40f, 23f, YELLOW, s.bodyFont, Paint.Align.RIGHT, maxWidth = 620f)
        }
    }

    private fun drawStartingSoon(canvas: Canvas, snapshot: CompositorSnapshot, s: Style) {
        val remSec = snapshot.studio.countdownTo?.let { max(0L, (it - s.nowMs) / 1000L) }
        val clock = if (remSec != null) {
            String.format(Locale.US, "%02d:%02d", remSec / 60, remSec % 60)
        } else "LIVE SOON"

        text(canvas, "LIVE CRICKET BROADCAST", W / 2f, 175f, 32f, s.accent, s.displayFont, Paint.Align.CENTER)
        text(canvas, snapshot.studio.brand.title.uppercase(), W / 2f, 255f, 68f, s.onBg, s.displayFont, Paint.Align.CENTER, maxWidth = 1080f)
        text(canvas, clock, W / 2f, 408f, 148f, YELLOW, s.displayFont, Paint.Align.CENTER)
        snapshot.match?.let {
            text(canvas, "${it.title}  ·  ${it.venue}".trim(' ', '·'), W / 2f, 474f, 26f, s.subText, s.bodyFont, Paint.Align.CENTER, maxWidth = 1040f)
        }
        // Animated studio equalizer bars
        val bars = 26
        val eqW = 540f
        val bw = eqW / bars
        val eqPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until bars) {
            val v = 0.22f + 0.78f * kotlin.math.abs(sin(s.nowMs / 320.0 + i * 0.55).toFloat())
            val bh = 56f * v
            eqPaint.color = withAlpha(if (i % 3 == 0) s.accent else s.onBg, 0.65f)
            val bx = (W - eqW) / 2f + i * bw + 3f
            canvas.drawRoundRect(RectF(bx, 582f - bh, bx + bw - 6f, 582f), 4f, 4f, eqPaint)
        }
    }

    private fun drawAnnouncement(canvas: Canvas, snapshot: CompositorSnapshot, s: Style) {
        val ann = snapshot.studio.announcement
        val r = RectF(120f, 180f, W - 120f, 510f)
        card(canvas, r, s, 26f, withAlpha(YELLOW, 0.7f))
        text(canvas, (ann?.title ?: "STUDIO UPDATE").uppercase(), W / 2f, 295f, 68f, YELLOW, s.displayFont, Paint.Align.CENTER, maxWidth = 940f)
        text(canvas, ann?.text ?: "Stay tuned — live cricket coverage continues shortly.", W / 2f, 392f, 32f, s.onBg, s.bodyFont, Paint.Align.CENTER, maxWidth = 920f)
    }

    private fun drawBreak(canvas: Canvas, match: MatchDetail?, s: Style) {
        text(canvas, "INNINGS BREAK", W / 2f, 310f, 108f, YELLOW, s.displayFont, Paint.Align.CENTER)
        text(canvas, match?.summary?.ifBlank { match.title } ?: "We'll be right back!", W / 2f, 390f, 32f, s.onBg, s.bodyFont, Paint.Align.CENTER, maxWidth = 980f)
    }

    private fun drawResult(canvas: Canvas, match: MatchDetail?, s: Style) {
        text(canvas, "MATCH RESULT", W / 2f, 265f, 54f, YELLOW, s.displayFont, Paint.Align.CENTER)
        text(canvas, match?.summary?.ifBlank { match.statusText } ?: "Thanks for watching!", W / 2f, 365f, 52f, s.onBg, s.displayFont, Paint.Align.CENTER, maxWidth = 1040f)
        match?.let { text(canvas, it.title, W / 2f, 435f, 28f, s.subText, s.bodyFont, Paint.Align.CENTER) }
    }
}
