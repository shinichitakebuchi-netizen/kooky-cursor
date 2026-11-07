package jp.kooky.onetouchcursor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/**
 * 全画面・タッチ不可のオーバーレイにカーソルを描くだけのView。
 * 座標はスクリーン座標で受け取り、Viewの画面上の位置を差し引いて描画する。
 */
class CursorOverlayView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density
    private val loc = IntArray(2)

    var posX = 0f
        private set
    var posY = 0f
        private set

    var radiusDp = 14f
    var color = 0xFFFF8A2B.toInt()
    var shape = CursorShapes.ENSO
    var active = true          // 操作中は濃く、放置で薄く
    var dragging = false

    fun setPos(x: Float, y: Float) {
        posX = x
        posY = y
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        getLocationOnScreen(loc)
        CursorShapes.draw(
            canvas, posX - loc[0], posY - loc[1],
            shape, radiusDp * density, color, if (active) 255 else 90
        )
    }
}

/**
 * カーソルの形（和の6種類）。設定画面のプレビューでも同じ描画を使う。
 * (x, y) が常に「タップされる位置」（矢は鏃の先、それ以外は中心）。
 * 寸法は r=14 を基準にした単位で書き、u で拡大縮小する。
 */
object CursorShapes {
    const val ENSO = 0       // 円相
    const val JANOME = 1     // 蛇の目
    const val SAKURA = 2     // 桜
    const val HISHI = 3      // 菱
    const val ARROW = 4      // 矢（鏃の先がタップ位置）
    const val KUTSUWA = 5    // 丸に十字

    val names = listOf("円相", "蛇の目", "桜", "菱", "矢", "丸に十字")

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()
    private val oval = android.graphics.RectF()

    private fun Paint.set(c: Int, a: Int, w: Float = strokeWidth): Paint {
        color = c
        alpha = a.coerceIn(0, 255)
        strokeWidth = w
        return this
    }

    fun draw(canvas: Canvas, x: Float, y: Float, shape: Int, r: Float, color: Int, alpha: Int) {
        val u = r / 14f
        val sh = (alpha * 0.35f).toInt()
        val white = Color.WHITE
        stroke.strokeCap = Paint.Cap.BUTT
        stroke.strokeJoin = Paint.Join.MITER
        canvas.save()
        canvas.translate(x, y)
        when (shape) {
            JANOME -> {
                canvas.drawCircle(0f, 0f, 13f * u, stroke.set(Color.BLACK, sh, 10f * u))
                canvas.drawCircle(0f, 0f, 13f * u, stroke.set(color, alpha, 7f * u))
                canvas.drawCircle(0f, 0f, 16.6f * u, stroke.set(white, (alpha * .9f).toInt(), 1.2f * u))
                canvas.drawCircle(0f, 0f, 2f * u, fill.set(color, alpha))
            }

            SAKURA -> {
                fun petal() {
                    path.reset()
                    path.moveTo(0f, -3.2f * u)
                    path.cubicTo(-6.5f * u, -5.5f * u, -7f * u, -12.5f * u, -3f * u, -16f * u)
                    path.lineTo(0f, -13.6f * u)
                    path.lineTo(3f * u, -16f * u)
                    path.cubicTo(7f * u, -12.5f * u, 6.5f * u, -5.5f * u, 0f, -3.2f * u)
                    path.close()
                }
                for (pass in 0..1) {
                    for (i in 0 until 5) {
                        canvas.save()
                        canvas.rotate(i * 72f)
                        petal()
                        if (pass == 0) {
                            canvas.drawPath(path, stroke.set(Color.BLACK, sh, 3f * u))
                        } else {
                            canvas.drawPath(path, fill.set(color, (alpha * .92f).toInt()))
                            canvas.drawPath(path, stroke.set(white, alpha, 1.1f * u))
                        }
                        canvas.restore()
                    }
                }
                canvas.drawCircle(0f, 0f, 2.2f * u, fill.set(white, alpha))
            }

            HISHI -> {
                stroke.strokeJoin = Paint.Join.ROUND
                for ((sz, w) in listOf(15f to 3.2f, 8.5f to 2.2f)) {
                    path.reset()
                    path.moveTo(0f, -sz * u); path.lineTo(sz * .72f * u, 0f)
                    path.lineTo(0f, sz * u); path.lineTo(-sz * .72f * u, 0f); path.close()
                    canvas.drawPath(path, stroke.set(Color.BLACK, sh, (w + 3f) * u))
                    canvas.drawPath(path, stroke.set(color, alpha, w * u))
                }
                canvas.drawCircle(0f, 0f, 2f * u, fill.set(white, alpha))
            }

            ARROW -> {
                canvas.rotate(-35f)   // 左上を指す
                // 矢柄
                canvas.drawLine(0f, 6f * u, 0f, 30f * u, stroke.set(Color.BLACK, sh, 4.5f * u))
                canvas.drawLine(0f, 6f * u, 0f, 30f * u, stroke.set(color, alpha, 2.2f * u))
                // 鏃
                path.reset()
                path.moveTo(0f, 0f); path.lineTo(4.2f * u, 9f * u); path.lineTo(0f, 7f * u)
                path.lineTo(-4.2f * u, 9f * u); path.close()
                canvas.drawPath(path, stroke.set(Color.BLACK, sh, 2.5f * u))
                canvas.drawPath(path, fill.set(color, alpha))
                canvas.drawPath(path, stroke.set(white, alpha, 1f * u))
                // 矢羽根
                for (sx in listOf(-1f, 1f)) {
                    path.reset()
                    path.moveTo(0f, 20f * u); path.lineTo(sx * 3.2f * u, 22.5f * u)
                    path.lineTo(sx * 3.2f * u, 31f * u); path.lineTo(0f, 28.5f * u); path.close()
                    canvas.drawPath(path, stroke.set(Color.BLACK, (sh * .85f).toInt(), 2f * u))
                    canvas.drawPath(path, fill.set(color, (alpha * .8f).toInt()))
                    canvas.drawPath(path, stroke.set(white, alpha, .9f * u))
                }
            }

            KUTSUWA -> {
                stroke.strokeCap = Paint.Cap.ROUND
                canvas.drawCircle(0f, 0f, 14f * u, stroke.set(Color.BLACK, sh, 5.6f * u))
                canvas.drawCircle(0f, 0f, 14f * u, stroke.set(color, alpha, 2.6f * u))
                val segs = listOf(
                    floatArrayOf(0f, -10f, 0f, -3f), floatArrayOf(0f, 3f, 0f, 10f),
                    floatArrayOf(-10f, 0f, -3f, 0f), floatArrayOf(3f, 0f, 10f, 0f),
                )
                for (pass in 0..1) for (sg in segs) {
                    if (pass == 0) stroke.set(Color.BLACK, sh, 5.6f * u) else stroke.set(color, alpha, 2.6f * u)
                    canvas.drawLine(sg[0] * u, sg[1] * u, sg[2] * u, sg[3] * u, stroke)
                }
                canvas.drawCircle(0f, 0f, 1.8f * u, fill.set(white, alpha))
            }

            else -> { // ENSO：入りは太く、抜けは細く、少し途切れる
                stroke.strokeCap = Paint.Cap.ROUND
                val rr = 15f * u
                oval.set(-rr, -rr, rr, rr)
                val start = -60f
                val sweep = 330f
                val steps = 40
                for (pass in 0..1) {
                    for (k in 0 until steps) {
                        val w = (5.5f - 4f * k / steps) * u
                        if (pass == 0) stroke.set(Color.BLACK, (alpha * .25f).toInt(), w + 2.5f * u)
                        else stroke.set(color, alpha, w)
                        canvas.drawArc(oval, start + sweep * k / steps, sweep / steps + .6f, false, stroke)
                    }
                }
                canvas.drawCircle(0f, 0f, 2.2f * u, fill.set(color, alpha))
            }
        }
        canvas.restore()
    }
}
