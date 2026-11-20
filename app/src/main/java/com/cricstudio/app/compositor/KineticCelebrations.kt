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
import com.cricstudio.app.model.ActiveOverlay
import com.cricstudio.app.model.OverlayId
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Full-frame kinetic broadcast celebrations for FOUR, SIX, WICKET, MILESTONE, HAT-TRICK, etc.
 * Runs identically in live Compose preview and hardware H.264 RTMP stream.
 */
object KineticCelebrations {
    private const val W = 1280f
    private const val H = 720f

    private fun easeOutBack(x: Float): Float {
        val t = x.coerceIn(0f, 1f)
        val c1 = 1.70158f
        val c3 = c1 + 1f
        return 1f + c3 * (t - 1f).pow(3) + c1 * (t - 1f).pow(2)
    }

    private fun rnd(seed: Int): Float {
        val x = sin(seed * 91.3458 + 47.119) * 47453.5453
        return (x - kotlin.math.floor(x)).toFloat()
    }

    private fun withAlpha(color: Int, alpha: Float): Int {
        val a = (alpha.coerceIn(0f, 1f) * 255f).toInt()
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
    }

    fun draw(
        canvas: Canvas,
        overlay: ActiveOverlay,
        accentColor: Int,
        displayTypeface: Typeface,
        bodyTypeface: Typeface,
        nowMs: Long,
    ) {
        val totalSec = ((overlay.until - overlay.startedAt).coerceAtLeast(1500L)) / 1000f
        val t = ((nowMs - overlay.startedAt).coerceAtLeast(0L)) / 1000f
        val enter = (t / 0.35f).coerceIn(0f, 1f)
        val exit = ((totalSec - t) / 0.4f).coerceIn(0f, 1f)
        val masterAlpha = minOf(enter, exit)
        if (masterAlpha <= 0.01f) return

        val (primary, secondary) = when (overlay.id) {
            OverlayId.FOUR -> Color.rgb(52, 168, 83) to Color.rgb(16, 185, 129)
            OverlayId.SIX -> Color.rgb(147, 52, 230) to Color.rgb(251, 188, 4)
            OverlayId.WICKET, OverlayId.HATTRICK -> Color.rgb(234, 67, 53) to Color.rgb(251, 140, 0)
            OverlayId.MILESTONE, OverlayId.FIVE_WICKETS, OverlayId.MATCH_END -> Color.rgb(251, 188, 4) to Color.rgb(245, 158, 11)
            else -> accentColor to Color.rgb(56, 189, 248)
        }

        // Dramatic cinema backdrop dimmer
        val dimPaint = Paint().apply {
            color = Color.argb((178 * masterAlpha).toInt(), 5, 9, 18)
        }
        canvas.drawRect(0f, 0f, W, H, dimPaint)

        // Radial impact burst glow
        val pulse = 1f + 0.08f * sin(t * 7f)
        val burstPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                W / 2f, H / 2f, 520f * pulse,
                withAlpha(primary, 0.55f * masterAlpha),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, W, H, burstPaint)

        // Event-specific kinetic particle / graphic FX
        when (overlay.id) {
            OverlayId.FOUR -> drawFourStreaks(canvas, primary, secondary, t, masterAlpha)
            OverlayId.SIX -> drawSixFireworks(canvas, primary, secondary, t, masterAlpha)
            OverlayId.WICKET, OverlayId.HATTRICK -> drawWicketSirenAndStumps(canvas, primary, secondary, t, masterAlpha)
            OverlayId.MILESTONE, OverlayId.FIVE_WICKETS, OverlayId.PARTNERSHIP, OverlayId.MATCH_END ->
                drawGoldenConfetti(canvas, primary, secondary, t, masterAlpha)
            else -> drawSweepBeam(canvas, primary, t, masterAlpha)
        }

        // Kinetic Center Broadcast Card
        val scale = 0.72f + 0.28f * easeOutBack((t / 0.45f).coerceIn(0f, 1f))
        canvas.save()
        canvas.translate(W / 2f, H / 2f)
        canvas.scale(scale, scale)

        val cardW = 860f
        val cardH = 260f
        val rect = RectF(-cardW / 2f, -cardH / 2f, cardW / 2f, cardH / 2f)

        val cardBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                rect.left, rect.top, rect.right, rect.bottom,
                withAlpha(primary, 0.94f * masterAlpha),
                withAlpha(secondary, 0.92f * masterAlpha),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRoundRect(rect, 28f, 28f, cardBg)

        // Specular animated sheen sweep across the card
        val sheenX = -cardW + ((t * 680f) % (cardW * 2.2f))
        val sheenPath = Path().apply { addRoundRect(rect, 28f, 28f, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(sheenPath)
        val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((68 * masterAlpha).toInt(), 255, 255, 255)
        }
        val beam = Path().apply {
            moveTo(sheenX, -cardH)
            lineTo(sheenX + 90f, -cardH)
            lineTo(sheenX - 40f, cardH)
            lineTo(sheenX - 130f, cardH)
            close()
        }
        canvas.drawPath(beam, sheenPaint)
        canvas.restore()

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = Color.argb((210 * masterAlpha).toInt(), 255, 255, 255)
        }
        canvas.drawRoundRect(rect, 28f, 28f, borderPaint)

        val darkInk = overlay.id == OverlayId.MILESTONE || overlay.id == OverlayId.FIVE_WICKETS || overlay.id == OverlayId.MATCH_END
        val fgColor = if (darkInk) Color.rgb(18, 22, 30) else Color.WHITE

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = displayTypeface
            textSize = 112f
            textAlign = Paint.Align.CENTER
            color = withAlpha(fgColor, masterAlpha)
            setShadowLayer(12f, 0f, 4f, Color.argb(120, 0, 0, 0))
        }
        val titleText = overlay.title.uppercase().take(24)
        canvas.drawText(titleText, 0f, 18f, titlePaint)

        overlay.subtitle?.takeIf { it.isNotBlank() }?.let { sub ->
            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = bodyTypeface
                textSize = 32f
                textAlign = Paint.Align.CENTER
                color = withAlpha(fgColor, 0.92f * masterAlpha)
            }
            canvas.drawText(sub.take(58), 0f, 84f, subPaint)
        }

        canvas.restore()
    }

    private fun drawFourStreaks(canvas: Canvas, primary: Int, secondary: Int, t: Float, alpha: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
        for (i in 0 until 26) {
            val y = 40f + rnd(i) * (H - 80f)
            val speed = 700f + rnd(i + 19) * 900f
            val len = 90f + rnd(i + 53) * 180f
            val x = ((t * speed + rnd(i + 91) * W) % (W + len * 2f)) - len
            paint.strokeWidth = 3f + rnd(i + 7) * 5f
            paint.color = withAlpha(if (i % 2 == 0) primary else secondary, 0.65f * alpha)
            canvas.drawLine(x, y, x + len, y, paint)
        }
        // Expanding boundary ring
        val ringR = (t * 460f) % 680f
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 8f * (1f - ringR / 680f)
            color = withAlpha(Color.WHITE, (1f - ringR / 680f) * 0.7f * alpha)
        }
        canvas.drawCircle(W / 2f, H / 2f, ringR, ring)
    }

    private fun drawSixFireworks(canvas: Canvas, primary: Int, secondary: Int, t: Float, alpha: Float) {
        val centers = listOf(
            W * 0.22f to H * 0.28f,
            W * 0.78f to H * 0.26f,
            W * 0.50f to H * 0.22f,
            W * 0.18f to H * 0.74f,
            W * 0.82f to H * 0.72f,
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
        centers.forEachIndexed { idx, (cx, cy) ->
            val localT = (t - idx * 0.18f).coerceAtLeast(0f)
            val cycle = localT % 1.6f
            val fade = (1f - cycle / 1.6f).coerceIn(0f, 1f)
            if (fade <= 0f) return@forEachIndexed
            val sparks = 18
            for (s in 0 until sparks) {
                val ang = (s.toFloat() / sparks) * (2.0 * PI) + idx
                val speed = 130f + rnd(idx * 31 + s) * 160f
                val px = cx + (cos(ang) * speed * cycle).toFloat()
                val py = cy + (sin(ang) * speed * cycle + 70f * cycle * cycle).toFloat()
                val tailX = cx + (cos(ang) * speed * (cycle - 0.12f).coerceAtLeast(0f)).toFloat()
                val tailY = cy + (sin(ang) * speed * (cycle - 0.12f).coerceAtLeast(0f) + 70f * (cycle - 0.12f).coerceAtLeast(0f).pow(2)).toFloat()
                paint.strokeWidth = 5f * fade
                paint.color = withAlpha(
                    when (s % 3) {
                        0 -> secondary
                        1 -> primary
                        else -> Color.rgb(56, 189, 248)
                    },
                    fade * alpha,
                )
                canvas.drawLine(tailX, tailY, px, py, paint)
                canvas.drawCircle(px, py, 4.5f * fade, paint)
            }
        }
    }

    private fun drawWicketSirenAndStumps(canvas: Canvas, primary: Int, secondary: Int, t: Float, alpha: Float) {
        // Dual sweeping crimson siren beams
        val beamPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val path = Path()
        for (side in listOf(-1f, 1f)) {
            val ox = if (side < 0) 0f else W
            val sweep = sin(t * 5.2f) * 260f * side
            beamPaint.shader = LinearGradient(
                ox, 0f, W / 2f + sweep, H,
                withAlpha(primary, 0.45f * alpha),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
            path.reset()
            path.moveTo(ox, 0f)
            path.lineTo(W / 2f + sweep - 180f, H)
            path.lineTo(W / 2f + sweep + 180f, H)
            path.close()
            canvas.drawPath(path, beamPaint)
        }

        // Animated flying bails above card
        val bailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(secondary, alpha)
            strokeWidth = 10f
            strokeCap = Paint.Cap.ROUND
        }
        val fly = (t * 2.1f).coerceAtMost(1.8f)
        for (dir in listOf(-1f, 1f)) {
            val bx = W / 2f + dir * (40f + fly * 170f)
            val by = 165f - sin((fly / 1.8f) * PI).toFloat() * 85f + fly * 28f
            canvas.save()
            canvas.translate(bx, by)
            canvas.rotate(dir * fly * 340f)
            canvas.drawLine(-26f, 0f, 26f, 0f, bailPaint)
            canvas.restore()
        }

        // Pulsing danger frame border
        val pulse = 0.4f + 0.6f * (0.5f + 0.5f * sin(t * 11f))
        val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 12f
            color = withAlpha(primary, pulse * alpha)
        }
        canvas.drawRect(6f, 6f, W - 6f, H - 6f, frame)
    }

    private fun drawGoldenConfetti(canvas: Canvas, primary: Int, secondary: Int, t: Float, alpha: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until 54) {
            val x = rnd(i) * W + sin(t * 2.5f + i) * 32f
            val y = ((rnd(i + 33) * H + t * (160f + rnd(i + 71) * 180f)) % (H + 40f)) - 20f
            paint.color = withAlpha(if (i % 3 == 0) Color.WHITE else if (i % 2 == 0) primary else secondary, 0.85f * alpha)
            canvas.save()
            canvas.translate(x, y)
            canvas.rotate(t * (140f + rnd(i + 5) * 180f))
            canvas.drawRect(-7f, -4f, 7f, 4f, paint)
            canvas.restore()
        }
    }

    private fun drawSweepBeam(canvas: Canvas, primary: Int, t: Float, alpha: Float) {
        val r = 220f + ((t * 280f) % 440f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = withAlpha(primary, (1f - (r - 220f) / 440f) * alpha)
        }
        canvas.drawCircle(W / 2f, H / 2f, r, p)
    }
}
