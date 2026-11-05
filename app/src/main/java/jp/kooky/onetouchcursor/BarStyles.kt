package jp.kooky.onetouchcursor

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader

/**
 * 触れる帯の見た目。トリガー本体と設定画面のプレビューで同じ描画を使う。
 * cx = 帯の中心のx、top..bottom = 帯の上下。d = density。
 */
object BarStyles {
    const val LINE = 0      // 線（従来）
    const val BRUSH = 1     // ① 筆の一筆
    const val BAMBOO = 2    // ② 竹の節
    const val SASHIKO = 3   // ③ 刺し子の点線
    const val MIZUHIKI = 4  // ④ 水引
    const val NIJIMI = 5    // ⑤ 墨の滲み
    const val SHIORI = 6    // ⑥ 栞紐

    val names = listOf("線", "筆", "竹", "刺し子", "水引", "滲み", "栞紐")

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()
    private val path = Path()

    private fun Paint.c(color: Int, alpha: Int = 255): Paint {
        shader = null
        this.color = color
        this.alpha = alpha
        return this
    }

    private fun bar(canvas: Canvas, cx: Float, top: Float, bottom: Float, w: Float, p: Paint) {
        rect.set(cx - w / 2, top, cx + w / 2, bottom)
        canvas.drawRoundRect(rect, w / 2, w / 2, p)
    }

    fun draw(
        canvas: Canvas, cx: Float, top: Float, bottom: Float,
        style: Int, color: Int, active: Boolean, d: Float,
    ) {
        val h = bottom - top
        if (h <= 0f) return
        when (style) {
            BRUSH -> {
                // 入り（上）は細く、中ほどで太り、抜き（下）で細くなる
                val hw = (if (active) 4.5f else 3f) * d
                path.reset()
                path.moveTo(cx, top)
                path.cubicTo(cx + hw * 0.8f, top + h * 0.11f, cx + hw * 1.2f, top + h * 0.33f, cx + hw, top + h * 0.55f)
                path.cubicTo(cx + hw * 0.8f, top + h * 0.78f, cx + hw * 0.4f, top + h * 0.92f, cx + 0.3f * d, bottom)
                path.cubicTo(cx - hw * 0.2f, top + h * 0.92f, cx - hw * 0.4f, top + h * 0.67f, cx - hw * 0.4f, top + h * 0.5f)
                path.cubicTo(cx - hw * 0.4f, top + h * 0.28f, cx - hw * 0.4f, top + h * 0.11f, cx, top)
                path.close()
                canvas.drawPath(path, fill.c(color))
            }

            BAMBOO -> {
                val w = (if (active) 6.5f else 5f) * d
                val gap = 3.5f * d
                val n = 4
                val seg = (h - gap * (n - 1)) / n
                for (i in 0 until n) {
                    val t = top + i * (seg + gap)
                    bar(canvas, cx, t, t + seg, w, fill.c(color))
                }
            }

            SASHIKO -> {
                val w = (if (active) 4f else 3f) * d
                val dash = 9f * d
                val gap = 6f * d
                var t = top
                while (t + dash <= bottom + 0.5f) {
                    bar(canvas, cx, t, t + dash, w, fill.c(color))
                    t += dash + gap
                }
            }

            MIZUHIKI -> {
                val w = (if (active) 2.6f else 2f) * d
                bar(canvas, cx - 3f * d, top, bottom, w, fill.c(color))
                bar(canvas, cx + 3f * d, top, bottom, w, fill.c(0xFFF3EEE3.toInt(), 215))
                // 中央の結び
                stroke.c(color).strokeWidth = 2f * d
                val my = (top + bottom) / 2
                canvas.drawCircle(cx, my, 5f * d, stroke)
            }

            NIJIMI -> {
                val w = (if (active) 8f else 6f) * d
                val clear = color and 0x00FFFFFF
                fill.shader = LinearGradient(
                    0f, top, 0f, bottom,
                    intArrayOf(clear, color, color, clear),
                    floatArrayOf(0f, 0.3f, 0.7f, 1f),
                    Shader.TileMode.CLAMP
                )
                fill.alpha = 255
                rect.set(cx - w / 2, top, cx + w / 2, bottom)
                canvas.drawRoundRect(rect, w / 2, w / 2, fill)
                fill.shader = null
            }

            SHIORI -> {
                val w = (if (active) 4f else 3f) * d
                val tassel = (22f * d).coerceAtMost(h * 0.4f)
                val tw = 6.5f * d
                rect.set(cx - w / 2, top, cx + w / 2, bottom - tassel)
                canvas.drawRect(rect, fill.c(color))
                // 先の房（燕尾）
                path.reset()
                path.moveTo(cx - tw, bottom - tassel)
                path.lineTo(cx + tw, bottom - tassel)
                path.lineTo(cx + tw, bottom)
                path.lineTo(cx, bottom - tassel * 0.32f)
                path.lineTo(cx - tw, bottom)
                path.close()
                canvas.drawPath(path, fill.c(color))
            }

            else -> { // LINE
                bar(canvas, cx, top, bottom, (if (active) 6f else 4f) * d, fill.c(color))
            }
        }
    }
}
