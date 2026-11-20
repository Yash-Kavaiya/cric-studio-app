package com.cricstudio.app.compositor

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import com.cricstudio.app.model.BackgroundId
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renders 16 continuously animated TV-broadcast backgrounds in virtual 1280x720 coordinates.
 */
object AnimatedBackgrounds {
    const val W = 1280f
    const val H = 720f

    private fun rnd(i: Int): Float {
        val x = sin(i * 12.9898 + 78.233) * 43758.5453
        return (x - kotlin.math.floor(x)).toFloat()
    }

    private fun withAlpha(color: Int, alpha: Float): Int {
        val a = (alpha.coerceIn(0f, 1f) * 255f).toInt()
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
    }

    fun draw(canvas: Canvas, bg: BackgroundId, accentColor: Int, nowMs: Long) {
        val t = (nowMs % 600_000L) / 1000f
        val c1 = bg.primaryHex.toInt()
        val c2 = bg.secondaryHex.toInt()

        // Base directional gradient
        val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, W * 0.3f, H, c1, c2, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, W, H, basePaint)

        when (bg) {
            BackgroundId.STADIUM_NIGHT -> drawStadiumNight(canvas, accentColor, t)
            BackgroundId.PITCH_GREEN -> drawPitchGreen(canvas, accentColor, t)
            BackgroundId.LIQUID_WAVES -> drawLiquidWaves(canvas, accentColor, t)
            BackgroundId.HEX_TECH -> drawHexTech(canvas, accentColor, t)
            BackgroundId.PRISM -> drawPrism(canvas, accentColor, t)
            BackgroundId.SEAM_LINES -> drawSeamLines(canvas, accentColor, t)
            BackgroundId.WORLD_CUP_23,
            BackgroundId.IPL_NIGHT,
            BackgroundId.WTC_FINAL,
            BackgroundId.ASHES -> drawTournamentBeams(canvas, accentColor, c2, t)
            BackgroundId.BBL_NEON -> drawNeonArena(canvas, accentColor, t)
            BackgroundId.LORDS_SCOREBOARD -> drawHeritageBoard(canvas, accentColor, t)
            BackgroundId.GOOGLE_MATERIAL,
            BackgroundId.CLAUDE_PAPER -> drawLightOrbs(canvas, accentColor, t)
            BackgroundId.MICROSOFT_FLUENT,
            BackgroundId.X_MONO -> drawSleekMesh(canvas, accentColor, t)
        }

        // Subtle broadcast vignette on dark themes
        if (!bg.isLight) {
            val vig = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    W / 2f,
                    H / 2f,
                    H * 0.98f,
                    intArrayOf(Color.TRANSPARENT, Color.argb(135, 0, 0, 0)),
                    floatArrayOf(0.45f, 1f),
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawRect(0f, 0f, W, H, vig)
        }
    }

    private fun drawStadiumNight(canvas: Canvas, accent: Int, t: Float) {
        val towers = floatArrayOf(140f, 410f, W - 410f, W - 140f)
        val beamPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val path = Path()

        towers.forEachIndexed { idx, x ->
            val y = if (idx == 0 || idx == 3) 48f else 22f
            val sway = sin(t * 0.8f + idx * 1.7f) * 55f
            val flick = 0.85f + 0.15f * sin(t * 2.1f + idx * 2.3f)
            val tx = W / 2f + (x - W / 2f) * 0.25f + sway

            beamPaint.shader = LinearGradient(
                x, y, tx, H,
                Color.argb((78 * flick).toInt(), 215, 232, 255),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
            path.reset()
            path.moveTo(x - 14f, y)
            path.lineTo(x + 14f, y)
            path.lineTo(tx + 260f, H)
            path.lineTo(tx - 260f, H)
            path.close()
            canvas.drawPath(path, beamPaint)

            // Lamp glow
            val lampPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    x, y, 95f,
                    Color.argb((140 * flick).toInt(), 255, 255, 245),
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawCircle(x, y, 95f, lampPaint)
        }

        // Shimmering crowd bokeh
        val bokehPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until 75) {
            val bx = rnd(i) * W
            val by = H * 0.56f + rnd(i + 201) * (H * 0.34f)
            val r = 2.5f + rnd(i + 77) * 5.5f
            val twinkle = 0.35f + 0.65f * (0.5f + 0.5f * sin(t * (1.2f + rnd(i + 15) * 2.5f) + i))
            val col = when {
                rnd(i + 5) > 0.72f -> Color.rgb(255, 215, 130)
                rnd(i + 6) > 0.45f -> accent
                else -> Color.WHITE
            }
            bokehPaint.color = withAlpha(col, 0.35f * twinkle)
            canvas.drawCircle(bx, by, r, bokehPaint)
        }

        // Outfield emerald glow at bottom
        val turfPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                W / 2f, H + 210f, 680f,
                Color.argb(120, 52, 168, 83),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, H * 0.48f, W, H, turfPaint)
    }

    private fun drawPitchGreen(canvas: Canvas, accent: Int, t: Float) {
        // Manicured mowing stripes
        val stripePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val stripes = 12
        val sw = W / stripes
        val shift = (t * 14f) % (sw * 2f)
        for (i in -2..stripes + 1) {
            if (i % 2 == 0) {
                stripePaint.color = Color.argb(26, 255, 255, 255)
                canvas.drawRect(i * sw + shift - sw * 2f, 0f, (i + 1) * sw + shift - sw * 2f, H, stripePaint)
            }
        }
        // Moving overhead spotlight
        val spotX = W * 0.5f + sin(t * 0.45f) * 280f
        val spot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                spotX, H * 0.25f, 520f,
                withAlpha(accent, 0.32f),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, W, H, spot)
    }

    private fun drawLiquidWaves(canvas: Canvas, accent: Int, t: Float) {
        val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val path = Path()
        for (layer in 0 until 4) {
            val baseY = H * (0.42f + layer * 0.14f)
            val amp = 28f + layer * 12f
            val speed = (0.7f + layer * 0.25f) * if (layer % 2 == 0) 1f else -1f
            path.reset()
            path.moveTo(0f, H)
            var x = 0f
            while (x <= W + 40f) {
                val y = baseY + sin(x * 0.0045f + t * speed + layer * 1.4f) * amp +
                    cos(x * 0.0022f - t * speed * 0.6f) * (amp * 0.5f)
                path.lineTo(x, y)
                x += 40f
            }
            path.lineTo(W, H)
            path.close()
            wavePaint.color = withAlpha(if (layer % 2 == 0) accent else Color.WHITE, 0.09f + layer * 0.035f)
            canvas.drawPath(path, wavePaint)
        }
    }

    private fun drawHexTech(canvas: Canvas, accent: Int, t: Float) {
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.4f
            color = withAlpha(accent, 0.18f)
        }
        val r = 46f
        val dx = r * 1.5f
        val dy = r * 1.732f
        val path = Path()
        var col = 0
        var cx = 0f
        while (cx < W + r) {
            var cy = if (col % 2 == 0) 0f else dy / 2f
            while (cy < H + r) {
                path.reset()
                for (k in 0 until 6) {
                    val ang = (k * 60.0) * PI / 180.0
                    val px = cx + (cos(ang) * r).toFloat()
                    val py = cy + (sin(ang) * r).toFloat()
                    if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
                canvas.drawPath(path, gridPaint)
                cy += dy
            }
            cx += dx
            col++
        }
        // Horizontal radar scanline
        val scanY = ((t * 95f) % (H + 140f)) - 70f
        val scanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, scanY - 70f, 0f, scanY + 70f,
                intArrayOf(Color.TRANSPARENT, withAlpha(accent, 0.30f), Color.TRANSPARENT),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, scanY - 70f, W, scanY + 70f, scanPaint)
    }

    private fun drawPrism(canvas: Canvas, accent: Int, t: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val path = Path()
        for (i in 0 until 5) {
            val offset = ((t * (26f + i * 9f) + i * 270f) % (W + 460f)) - 230f
            path.reset()
            path.moveTo(offset, 0f)
            path.lineTo(offset + 140f, 0f)
            path.lineTo(offset - 120f, H)
            path.lineTo(offset - 260f, H)
            path.close()
            paint.color = withAlpha(if (i % 2 == 0) accent else Color.rgb(180, 120, 255), 0.12f)
            canvas.drawPath(path, paint)
        }
    }

    private fun drawSeamLines(canvas: Canvas, accent: Int, t: Float) {
        val cx = W * 0.78f
        val cy = H * 0.52f
        val rad = 410f
        val orb = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx - 90f, cy - 90f, rad * 1.2f,
                withAlpha(accent, 0.35f),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawCircle(cx, cy, rad, orb)

        val stitch = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.argb(85, 255, 240, 220)
        }
        canvas.save()
        canvas.rotate(t * 7f, cx, cy)
        for (k in -1..1 step 2) {
            canvas.drawArc(cx - rad, cy - rad, cx + rad, cy + rad, 18f * k - 35f, 70f, false, stitch)
            canvas.drawArc(cx - rad + 18f, cy - rad + 18f, cx + rad - 18f, cy + rad - 18f, 18f * k - 35f, 70f, false, stitch)
        }
        canvas.restore()
    }

    private fun drawTournamentBeams(canvas: Canvas, accent: Int, secondary: Int, t: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until 4) {
            val x = W * (0.15f + i * 0.24f) + sin(t * 0.6f + i * 1.5f) * 90f
            val y = H * (0.25f + (i % 2) * 0.45f) + cos(t * 0.5f + i) * 50f
            val r = 280f + (i % 2) * 110f
            paint.shader = RadialGradient(
                x, y, r,
                withAlpha(if (i % 2 == 0) accent else secondary, 0.30f),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(x, y, r, paint)
        }
        // Diagonal kinetic broadcast ribbon
        val ribbonX = ((t * 65f) % (W + 500f)) - 250f
        val path = Path().apply {
            moveTo(ribbonX, 0f)
            lineTo(ribbonX + 90f, 0f)
            lineTo(ribbonX - 180f, H)
            lineTo(ribbonX - 270f, H)
            close()
        }
        paint.shader = null
        paint.color = withAlpha(accent, 0.14f)
        canvas.drawPath(path, paint)
    }

    private fun drawNeonArena(canvas: Canvas, accent: Int, t: Float) {
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(accent, 0.20f)
            strokeWidth = 1.5f
        }
        // Perspective floor grid
        val horizon = H * 0.62f
        for (i in 0..8) {
            val frac = i / 8f
            val y = horizon + frac * frac * (H - horizon)
            canvas.drawLine(0f, y, W, y, linePaint)
        }
        val shift = (t * 45f) % 120f
        var x = -240f + shift
        while (x < W + 240f) {
            canvas.drawLine(W / 2f + (x - W / 2f) * 0.25f, horizon, x, H, linePaint)
            x += 120f
        }
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(W / 2f, horizon, 420f, withAlpha(accent, 0.28f), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, W, H, glow)
    }

    private fun drawHeritageBoard(canvas: Canvas, accent: Int, t: Float) {
        val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = withAlpha(accent, 0.30f + 0.08f * sin(t))
        }
        canvas.drawRoundRect(22f, 22f, W - 22f, H - 22f, 16f, 16f, frame)
        canvas.drawRoundRect(30f, 30f, W - 30f, H - 30f, 12f, 12f, frame)
    }

    private fun drawLightOrbs(canvas: Canvas, accent: Int, t: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until 3) {
            val x = W * (0.2f + i * 0.32f) + sin(t * 0.35f + i * 2f) * 110f
            val y = H * (0.3f + (i % 2) * 0.35f) + cos(t * 0.4f + i) * 70f
            paint.shader = RadialGradient(x, y, 360f, withAlpha(accent, 0.14f), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawCircle(x, y, 360f, paint)
        }
    }

    private fun drawSleekMesh(canvas: Canvas, accent: Int, t: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(accent, 0.12f)
            strokeWidth = 1.2f
        }
        val offset = (t * 20f) % 64f
        var x = offset
        while (x < W) {
            canvas.drawLine(x, 0f, x, H, paint)
            x += 64f
        }
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                W * 0.8f + sin(t * 0.4f) * 80f, H * 0.25f, 440f,
                withAlpha(accent, 0.26f), Color.TRANSPARENT, Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, W, H, glow)
    }
}
