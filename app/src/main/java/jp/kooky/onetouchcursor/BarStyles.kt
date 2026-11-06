package jp.kooky.onetouchcursor

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/**
 * 触れる帯の見た目。トリガー本体と設定画面のプレビューで同じ描画を使う。
 * cx = 帯の中心のx、top..bottom = 帯の上下。d = density。
 * inward = 画面の内側の向き（右端の帯なら -1、左端なら +1）。笹の葉などの向きに使う。
 */
object BarStyles {
    const val LINE = 0      // 線
    const val BRUSH = 1     // ① 筆：入りの筆先・太り・掠れ
    const val BAMBOO = 2    // ② 竹：膨らむ稈・節の二本線・笹の葉
    const val SASHIKO = 3   // ③ 刺し子：一目刺し・花刺し（十字）・下糸
    const val MIZUHIKI = 4  // ④ 水引：ねじれ合う二本の紐・あわじ結び風の輪
    const val NIJIMI = 5    // ⑤ 滲み：にじむ淡い層・墨の粒
    const val SHIORI = 6    // ⑥ 栞紐：撚りの紐・結び玉・房

    val names = listOf("線", "筆", "竹", "刺し子", "水引", "滲み", "栞紐")

    /** 形の名前に合わせた初期の色（色名, 色） */
    val defaults = listOf(
        "紅緋" to 0xFFF35336.toInt(),    // 線
        "墨" to 0xFF313131.toInt(),      // 筆：墨で書く
        "若竹色" to 0xFF6B9362.toInt(),  // 竹
        "白練" to 0xFFF3F3F2.toInt(),    // 刺し子：白い木綿糸
        "韓紅花" to 0xFFC91F37.toInt(),  // 水引：紅白の紅
        "藍色" to 0xFF264348.toInt(),    // 滲み：藍染めのにじみ
        "紫苑" to 0xFF976E9A.toInt(),    // 栞紐：紫の栞紐
    )
    fun defaultColor(style: Int): Int = defaults.getOrElse(style) { defaults[0] }.second
    fun defaultColorName(style: Int): String = defaults.getOrElse(style) { defaults[0] }.first

    private const val KAME = 0xFFC6C2B6.toInt()   // 甕覗（水引のもう一本）

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()
    private val path = Path()

    private fun Paint.c(color: Int, alpha: Float = 1f): Paint {
        shader = null
        this.color = color
        this.alpha = (alpha * 255).toInt().coerceIn(0, 255)
        return this
    }

    private fun Paint.w(width: Float): Paint { strokeWidth = width; return this }

    private fun bar(canvas: Canvas, cx: Float, top: Float, bottom: Float, w: Float, p: Paint) {
        rect.set(cx - w / 2, top, cx + w / 2, bottom)
        canvas.drawRoundRect(rect, w / 2, w / 2, p)
    }

    fun draw(
        canvas: Canvas, cx: Float, top: Float, bottom: Float,
        style: Int, color: Int, active: Boolean, d: Float, inward: Float = -1f,
    ) {
        val h = bottom - top
        if (h <= 0f) return
        val u = d * (if (active) 1.25f else 1f)   // 反応中は少し太く
        stroke.strokeCap = Paint.Cap.BUTT
        when (style) {
            BRUSH -> brush(canvas, cx, top, bottom, h, u, d, color)
            BAMBOO -> bamboo(canvas, cx, top, bottom, h, u, d, color, inward)
            SASHIKO -> sashiko(canvas, cx, top, bottom, u, d, color)
            MIZUHIKI -> mizuhiki(canvas, cx, top, bottom, h, u, d, color)
            NIJIMI -> nijimi(canvas, cx, top, bottom, h, u, d, color)
            SHIORI -> shiori(canvas, cx, top, bottom, h, u, d, color)
            else -> bar(canvas, cx, top, bottom, 4f * u, fill.c(color))
        }
    }

    private fun brush(canvas: Canvas, cx: Float, top: Float, bottom: Float, h: Float, u: Float, d: Float, color: Int) {
        val hw = 3.2f * u
        path.reset()
        path.moveTo(cx - 0.6f * u, top + 2f * d)
        path.cubicTo(cx + hw * 1.4f, top - 1f * d, cx + hw * 1.3f, top + h * .25f, cx + hw, top + h * .5f)
        path.cubicTo(cx + hw * .8f, top + h * .68f, cx + hw * .45f, top + h * .8f, cx + .4f * u, top + h * .86f)
        path.cubicTo(cx - hw * .3f, top + h * .78f, cx - hw * .55f, top + h * .55f, cx - hw * .5f, top + h * .4f)
        path.cubicTo(cx - hw * .45f, top + h * .2f, cx - hw * .9f, top + h * .05f, cx - 0.6f * u, top + 2f * d)
        path.close()
        canvas.drawPath(path, fill.c(color))
        // 入り：斜めに置いた筆先
        canvas.save()
        canvas.translate(cx + .8f * u, top + 3.4f * d)
        canvas.rotate(-34f)
        canvas.scale(1.5f, 0.9f)
        canvas.drawCircle(0f, 0f, 2.3f * u, fill)
        canvas.restore()
        // 掠れ：抜けの部分に途切れ途切れの細い筋
        val rnd = Random(7)
        stroke.strokeCap = Paint.Cap.ROUND
        listOf(-1.4f, -0.2f, 1.0f, 2.0f).forEachIndexed { k, off ->
            var y = top + h * (.62f + k * .02f)
            val end = top + h * (.9f + rnd.nextFloat() * .09f)
            stroke.strokeWidth = (0.7f + rnd.nextFloat() * .5f) * u
            while (y < end) {
                val seg = (4f + rnd.nextFloat() * 10f) * d
                val gap = (1.5f + rnd.nextFloat() * 4f) * d * (1f + (y - top) / h)
                val x = cx + off * u * (1f - (y - top - h * .6f) / h * .8f)
                stroke.c(color, (0.9f - (y - top) / h * .5f)).strokeWidth = stroke.strokeWidth
                canvas.drawLine(x, y, x + .25f * d, min(end, y + seg), stroke)
                y += seg + gap
            }
        }
        stroke.strokeCap = Paint.Cap.BUTT
    }

    private fun bamboo(canvas: Canvas, cx: Float, top: Float, bottom: Float, h: Float, u: Float, d: Float, color: Int, inward: Float) {
        val n = 4
        val gap = 2.4f * d
        val seg = (h - gap * (n - 1)) / n
        val w = 4.6f * u
        for (i in 0 until n) {
            val y = top + i * (seg + gap)
            path.reset()
            path.moveTo(cx - w * .42f, y)
            path.cubicTo(cx - w * .56f, y + seg * .3f, cx - w * .56f, y + seg * .7f, cx - w * .45f, y + seg)
            path.lineTo(cx + w * .45f, y + seg)
            path.cubicTo(cx + w * .56f, y + seg * .7f, cx + w * .56f, y + seg * .3f, cx + w * .42f, y)
            path.close()
            canvas.drawPath(path, fill.c(color))
            // 稈の光
            canvas.drawLine(cx - w * .12f, y + seg * .15f, cx - w * .12f, y + seg * .85f,
                stroke.c(0xFFFFFFFF.toInt(), .22f).w(.6f * d))
            // 節の二本線
            if (i > 0) {
                val ny = y - gap / 2
                stroke.c(color).w(.8f * d)
                canvas.drawLine(cx - w * .62f, ny - .7f * d, cx + w * .62f, ny - .7f * d, stroke)
                canvas.drawLine(cx - w * .62f, ny + .7f * d, cx + w * .62f, ny + .7f * d, stroke)
            }
        }
        // 笹の葉：上の節から内側へ
        val ly = top + seg + gap / 2
        val s = inward
        val bx = cx + s * w * .5f
        path.reset()
        path.moveTo(bx, ly)
        path.cubicTo(bx + s * 4f * u, ly - 2f * d, bx + s * 9f * u, ly - 7f * d, bx + s * 12f * u, ly - 14f * d)
        path.cubicTo(bx + s * 6f * u, ly - 10f * d, bx + s * 2f * u, ly - 5f * d, bx, ly)
        path.close()
        canvas.drawPath(path, fill.c(color, .9f))
    }

    private fun sashiko(canvas: Canvas, cx: Float, top: Float, bottom: Float, u: Float, d: Float, color: Int) {
        val dash = 6f * d
        val gap = 3.5f * d
        var y = top
        var i = 0
        stroke.strokeCap = Paint.Cap.ROUND
        while (y + dash <= bottom + .5f) {
            if (i % 4 == 3) {
                // 花刺し（十字）
                val my = y + dash / 2
                stroke.c(color).w(1.6f * u)
                canvas.drawLine(cx, my - 3.4f * u, cx, my + 3.4f * u, stroke)
                canvas.drawLine(cx - 3.4f * u, my, cx + 3.4f * u, my, stroke)
            } else {
                stroke.c(color).w(2.2f * u)
                canvas.drawLine(cx, y + 1.1f * d, cx, y + dash - 1.1f * d, stroke)
            }
            // 脇の下糸
            val side = if (i % 2 == 0) 2.8f * u else -2.8f * u
            stroke.c(color, .45f).w(.7f * d)
            canvas.drawLine(cx + side, y + dash * .3f, cx + side, y + dash * .7f, stroke)
            y += dash + gap
            i++
        }
        stroke.strokeCap = Paint.Cap.BUTT
    }

    private fun mizuhiki(canvas: Canvas, cx: Float, top: Float, bottom: Float, h: Float, u: Float, d: Float, color: Int) {
        val amp = 1.7f * u
        val period = 16f * d
        val my = (top + bottom) / 2
        val knot = 9f * d
        fun cord(phase: Float, c: Int, alpha: Float) {
            stroke.c(c, alpha).w(1.6f * u)
            for ((y0, y1) in listOf(top to my - knot, my + knot to bottom)) {
                path.reset()
                path.moveTo(cx + amp * sin(phase + (y0 - top) / period * 2f * PI.toFloat()), y0)
                var yy = y0
                while (yy < y1) {
                    yy = min(y1, yy + d)
                    path.lineTo(cx + amp * sin(phase + (yy - top) / period * 2f * PI.toFloat()), yy)
                }
                canvas.drawPath(path, stroke)
            }
        }
        cord(0f, KAME, .9f)
        cord(PI.toFloat(), color, 1f)
        // 結び：左右の輪＋中央の小さな輪
        for ((sx, c) in listOf(-1f to color, 1f to KAME)) {
            canvas.save()
            canvas.translate(cx + sx * 3.2f * u, my)
            canvas.scale(1f, 1.5f)
            canvas.drawCircle(0f, 0f, 3.2f * u, stroke.c(c).w(1.6f * u / 1.2f))
            canvas.restore()
        }
        canvas.drawCircle(cx, my, 2f * u, stroke.c(color).w(1.6f * u))
    }

    private fun nijimi(canvas: Canvas, cx: Float, top: Float, bottom: Float, h: Float, u: Float, d: Float, color: Int) {
        val rgb = color and 0x00FFFFFF
        for ((wdt, al) in listOf(14f * u to .08f, 9f * u to .14f, 5.5f * u to .55f, 3f * u to .9f)) {
            val a = (al * 255).toInt() shl 24
            fill.shader = LinearGradient(
                0f, top, 0f, bottom,
                intArrayOf(rgb, rgb or a, rgb or a, rgb),
                floatArrayOf(0f, .25f, .55f, 1f),
                Shader.TileMode.CLAMP
            )
            fill.alpha = 255
            rect.set(cx - wdt / 2, top, cx + wdt / 2, bottom)
            canvas.drawRoundRect(rect, wdt / 2, wdt / 2, fill)
        }
        fill.shader = null
        val rnd = Random(3)
        for (k in 0 until 6) {
            val yy = if (k < 3) top + h * (.04f + rnd.nextFloat() * .12f) else bottom - h * (.04f + rnd.nextFloat() * .14f)
            val xx = cx + (rnd.nextFloat() - .5f) * 6f * u
            canvas.drawCircle(xx, yy, (.5f + rnd.nextFloat() * .8f) * d, fill.c(color, .5f))
        }
    }

    private fun shiori(canvas: Canvas, cx: Float, top: Float, bottom: Float, h: Float, u: Float, d: Float, color: Int) {
        val tassel = min(24f * d, h * .4f)
        val cordEnd = bottom - tassel - 5f * d
        val w = 2.8f * u
        bar(canvas, cx, top, cordEnd, w, fill.c(color))
        // 撚り
        stroke.c(0xFFFFFFFF.toInt(), .28f).w(.5f * d)
        var y = top + 2f * d
        while (y < cordEnd - 2f * d) {
            canvas.drawLine(cx - w / 2, y + 1.6f * d, cx + w / 2, y, stroke)
            y += 3f * d
        }
        // 結び玉
        canvas.drawCircle(cx, cordEnd + 2.4f * d, 3f * u, fill.c(color))
        // 房
        val tt = cordEnd + 5f * d
        for (k in -3..3) {
            val x0 = cx + k * .5f * u
            val x1 = cx + k * 1.5f * u
            path.reset()
            path.moveTo(x0, tt)
            path.cubicTo(x0, tt + tassel * .3f, x1, tt + tassel * .6f, x1, tt + tassel - abs(k) * .6f * d)
            canvas.drawPath(path, stroke.c(color, 1f - abs(k) * .08f).w(.8f * d))
        }
        canvas.drawLine(cx - 2.2f * u, tt + 2.2f * d, cx + 2.2f * u, tt + 2.2f * d, stroke.c(color).w(1.4f * d))
    }
}
