package com.cricstudio.app.compositor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.cricstudio.app.R
import com.cricstudio.app.model.BannerKind
import com.cricstudio.app.model.CompositorSnapshot
import java.util.Locale
import kotlin.math.sin

/**
 * Unified 1280x720 TV Broadcast Compositor.
 * Used identically for both the live on-screen Compose Monitor and the hardware MediaCodec H.264 encoder,
 * ensuring WYSIWYG parity between Studio preview and YouTube Live output.
 */
class BroadcastCanvasRenderer(context: Context) {

    private val displayFont: Typeface = runCatching {
        Typeface.create(ResourcesCompat.getFont(context, R.font.barlow_condensed), Typeface.BOLD)
    }.getOrDefault(Typeface.DEFAULT_BOLD)

    private val bodyFont: Typeface = runCatching {
        ResourcesCompat.getFont(context, R.font.roboto)
    }.getOrNull() ?: Typeface.SANS_SERIF

    companion object {
        private const val VW = 1280f
        private const val VH = 720f
        private const val RED = 0xFFEA4335.toInt()
        private const val GREEN = 0xFF34A853.toInt()
        private const val YELLOW = 0xFFFBBC04.toInt()
    }

    fun drawFrame(
        canvas: Canvas,
        width: Int,
        height: Int,
        snapshot: CompositorSnapshot,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        if (width <= 0 || height <= 0) return
        canvas.save()
        canvas.scale(width / VW, height / VH)

        val studio = snapshot.studio
        val bgSpec = studio.brand.background
        val accent = studio.brand.accentHex.toInt()
        val onBg = if (bgSpec.isLight) Color.rgb(20, 24, 31) else Color.rgb(245, 247, 250)
        val subText = if (bgSpec.isLight) Color.argb(180, 20, 24, 31) else Color.argb(185, 245, 247, 250)
        val cardFill = if (bgSpec.isLight) Color.argb(24, 0, 0, 0) else Color.argb(115, 8, 12, 24)
        val cardStroke = if (bgSpec.isLight) Color.argb(42, 0, 0, 0) else Color.argb(55, 255, 255, 255)

        // 1. Animated Broadcast Background
        AnimatedBackgrounds.draw(canvas, bgSpec, accent, nowMs)

        // 2. Top Broadcast Header Bar
        drawTopChrome(canvas, snapshot, accent, onBg, bgSpec.isLight, nowMs)

        // 3. Active Broadcast Scene
        val enter = ((nowMs - snapshot.sceneChangedAt).coerceAtLeast(0L) / 520f).coerceIn(0f, 1f)
        val style = BroadcastSceneDrawers.Style(
            accent = accent,
            onBg = onBg,
            subText = subText,
            cardFill = cardFill,
            cardStroke = cardStroke,
            displayFont = displayFont,
            bodyFont = bodyFont,
            enter = enter,
            nowMs = nowMs,
        )
        BroadcastSceneDrawers.draw(canvas, snapshot, style)

        // 4. Animated Lower-Third Banner (Sponsor / Quick Score)
        drawLowerThirdBanner(canvas, snapshot, accent, nowMs)

        // 5. Live Commentary Captions Strip
        var tickerBottom = VH
        if (studio.brand.showTicker && studio.brand.ticker.isNotBlank()) {
            drawScrollingTicker(canvas, studio.brand.ticker, accent, nowMs)
            tickerBottom = VH - 46f
        }
        if (studio.brand.captions && snapshot.commentary != null) {
            drawCaptionBar(canvas, snapshot.commentary.text, tickerBottom, nowMs)
        }

        // 6. Full-Frame Kinetic Event Celebration Overlay
        studio.overlay?.let { ov ->
            if (ov.until > nowMs) {
                KineticCelebrations.draw(canvas, ov, accent, displayFont, bodyFont, nowMs)
            }
        }

        canvas.restore()
    }

    private fun drawTopChrome(
        canvas: Canvas,
        snapshot: CompositorSnapshot,
        accent: Int,
        onBg: Int,
        isLight: Boolean,
        nowMs: Long,
    ) {
        val studio = snapshot.studio
        val barBg = Paint().apply {
            color = if (isLight) Color.argb(28, 0, 0, 0) else Color.argb(135, 4, 8, 18)
        }
        canvas.drawRect(0f, 0f, VW, 58f, barBg)

        // ON AIR / PREVIEW pill
        val live = studio.isLive || snapshot.isStreaming
        val pillRect = RectF(24f, 12f, if (live) 190f else 142f, 46f)
        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (live) RED else Color.rgb(60, 64, 70)
        }
        canvas.drawRoundRect(pillRect, 9f, 9f, pillPaint)

        if (live) {
            val pulseAlpha = (140 + 115 * (0.5f + 0.5f * sin(nowMs / 240.0).toFloat())).toInt()
            canvas.drawCircle(42f, 29f, 6.5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(pulseAlpha, 255, 255, 255) })
            val dur = formatClock(snapshot.elapsedSeconds)
            drawLabel(canvas, "LIVE  $dur", 58f, 36f, 21f, Color.WHITE, displayFont)
        } else {
            drawLabel(canvas, "PREVIEW", 83f, 36f, 21f, Color.WHITE, displayFont, Paint.Align.CENTER)
        }

        // Show title
        val titleStart = pillRect.right + 18f
        drawLabel(
            canvas,
            studio.brand.title.ifBlank { studio.name },
            titleStart,
            37f,
            24f,
            onBg,
            bodyFont,
        )

        // Right-side AUTO + Scene pills
        var rightCursor = VW - 24f
        val sceneTag = studio.scene.shortName.uppercase()
        val sceneW = 134f
        val sceneRect = RectF(rightCursor - sceneW, 12f, rightCursor, 46f)
        canvas.drawRoundRect(sceneRect, 8f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(95, Color.red(accent), Color.green(accent), Color.blue(accent))
        })
        drawLabel(canvas, sceneTag, sceneRect.centerX(), 36f, 20f, onBg, displayFont, Paint.Align.CENTER)
        rightCursor -= sceneW + 10f

        if (studio.autopilot.enabled) {
            val autoRect = RectF(rightCursor - 94f, 12f, rightCursor, 46f)
            canvas.drawRoundRect(autoRect, 8f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(65, Color.red(GREEN), Color.green(GREEN), Color.blue(GREEN))
            })
            drawLabel(canvas, "★ AUTO", autoRect.centerX(), 36f, 19f, GREEN, displayFont, Paint.Align.CENTER)
        }
    }

    private fun drawLowerThirdBanner(canvas: Canvas, snapshot: CompositorSnapshot, accent: Int, nowMs: Long) {
        val banner = snapshot.studio.banner ?: return
        val slide = ((nowMs - banner.since).coerceAtLeast(0L) / 300f).coerceIn(0f, 1f)
        val yOffset = (1f - slide) * 44f
        val match = snapshot.match
        val curInn = match?.innings?.firstOrNull { it.isCurrent } ?: match?.innings?.lastOrNull()
        val text = when (banner.kind) {
            BannerKind.SPONSOR -> snapshot.studio.brand.sponsors.firstOrNull() ?: "Powered by CricStudio Broadcast"
            BannerKind.QUICK_SCORE -> if (curInn != null) {
                "${curInn.teamAbbr} ${curInn.runs}/${curInn.wickets} (${curInn.overs} ov)  ·  ${match?.summary.orEmpty()}"
            } else {
                match?.title ?: snapshot.studio.brand.title
            }
        }
        val r = RectF(160f, 562f + yOffset, VW - 160f, 610f + yOffset)
        canvas.drawRoundRect(r, 12f, 12f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(235, Color.red(accent), Color.green(accent), Color.blue(accent))
        })
        drawLabel(canvas, text.take(80), r.centerX(), r.top + 33f, 25f, Color.WHITE, displayFont, Paint.Align.CENTER)
    }

    private fun drawCaptionBar(canvas: Canvas, text: String, bottomY: Float, nowMs: Long) {
        val barTop = bottomY - 46f
        canvas.drawRect(0f, barTop, VW, bottomY, Paint().apply { color = Color.argb(215, 9, 13, 22) })

        // Animated 3-bar voice indicator
        val eqP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = YELLOW }
        for (i in 0 until 3) {
            val h = 8f + 14f * (0.5f + 0.5f * sin(nowMs / 130.0 + i * 1.4).toFloat())
            canvas.drawRoundRect(RectF(26f + i * 9f, bottomY - 12f - h, 32f + i * 9f, bottomY - 12f), 3f, 3f, eqP)
        }
        drawLabel(canvas, text.take(102), 66f, bottomY - 15f, 22f, Color.WHITE, bodyFont)
    }

    private fun drawScrollingTicker(canvas: Canvas, ticker: String, accent: Int, nowMs: Long) {
        val top = VH - 46f
        canvas.drawRect(0f, top, VW, VH, Paint().apply {
            color = Color.argb(238, Color.red(accent), Color.green(accent), Color.blue(accent))
        })

        // Continuous seamless horizontal marquee at 105 px/sec
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = bodyFont
            textSize = 23f
            color = Color.WHITE
        }
        val unit = "   ★   $ticker   "
        val unitW = p.measureText(unit).coerceAtLeast(320f)
        val offset = ((nowMs * 0.105f) % unitW)

        canvas.save()
        canvas.clipRect(136f, top, VW, VH)
        var x = 136f - offset
        while (x < VW + unitW) {
            canvas.drawText(unit, x, VH - 14f, p)
            x += unitW
        }
        canvas.restore()

        // Left static LIVE TICKER tag
        canvas.drawRect(0f, top, 136f, VH, Paint().apply { color = Color.rgb(15, 20, 32) })
        drawLabel(canvas, "CRIC LIVE", 68f, VH - 14f, 22f, YELLOW, displayFont, Paint.Align.CENTER)
    }

    private fun drawLabel(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        size: Float,
        color: Int,
        font: Typeface,
        align: Paint.Align = Paint.Align.LEFT,
    ) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = font
            textSize = size
            this.color = color
            textAlign = align
        }
        canvas.drawText(text, x, y, p)
    }

    private fun formatClock(totalSec: Long): String {
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%02d:%02d", m, s)
    }
}
