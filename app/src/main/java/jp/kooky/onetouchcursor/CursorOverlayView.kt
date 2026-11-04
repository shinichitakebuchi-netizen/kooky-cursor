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
    var shape = CursorShapes.CIRCLE
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
 * カーソルの形。設定画面のプレビューでも同じ描画を使う。
 * (x, y) が常に「タップされる位置」。デザイン上の寸法は r=14 を基準にした単位で書き、u で拡大縮小する。
 */
object CursorShapes {
    const val CIRCLE = 0      // ① サークル
    const val RING = 1        // ② リング（中が透ける）
    const val CROSSHAIR = 2   // ③ 照準
    const val ARROW = 3       // ④ 矢印（先端がタップ位置）
    const val DOT_RING = 4    // ⑤ ドット＋外リング

    val names = listOf("サークル", "リング", "照準", "矢印", "ドット＋輪")

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()

    private fun Paint.set(c: Int, a: Int, w: Float = strokeWidth): Paint {
        color = c
        alpha = a.coerceIn(0, 255)
        strokeWidth = w
        return this
    }

    fun draw(canvas: Canvas, x: Float, y: Float, shape: Int, r: Float, color: Int, alpha: Int) {
        val u = r / 14f
        val shadow = (alpha * 0.45f).toInt()
        when (shape) {
            RING -> {
                val rr = 15f * u
                canvas.drawCircle(x, y, rr, stroke.set(Color.BLACK, shadow, 6f * u))
                canvas.drawCircle(x, y, rr, stroke.set(color, alpha, 3.5f * u))
                canvas.drawCircle(x, y, 2.5f * u, fill.set(color, alpha))
                canvas.drawCircle(x, y, 2.5f * u, stroke.set(Color.WHITE, alpha, 1f * u))
            }

            CROSSHAIR -> {
                for (pass in 0..1) {
                    val p = if (pass == 0) stroke.set(Color.BLACK, shadow, 5f * u)
                    else stroke.set(color, alpha, 2.5f * u)
                    canvas.drawLine(x, y - 18f * u, x, y - 10f * u, p)
                    canvas.drawLine(x, y + 10f * u, x, y + 18f * u, p)
                    canvas.drawLine(x - 18f * u, y, x - 10f * u, y, p)
                    canvas.drawLine(x + 10f * u, y, x + 18f * u, y, p)
                    canvas.drawCircle(x, y, 12f * u, p)
                }
                canvas.drawCircle(x, y, 2f * u, fill.set(Color.WHITE, alpha))
            }

            ARROW -> {
                path.reset()
                path.moveTo(x, y)
                path.lineTo(x, y + 26f * u)
                path.lineTo(x + 7f * u, y + 19.5f * u)
                path.lineTo(x + 11.5f * u, y + 29f * u)
                path.lineTo(x + 16f * u, y + 27f * u)
                path.lineTo(x + 11.5f * u, y + 17.5f * u)
                path.lineTo(x + 20f * u, y + 17.5f * u)
                path.close()
                canvas.drawPath(path, stroke.set(Color.BLACK, shadow, 4f * u))
                canvas.drawPath(path, fill.set(color, alpha))
                canvas.drawPath(path, stroke.set(Color.WHITE, alpha, 2f * u))
            }

            DOT_RING -> {
                canvas.drawCircle(x, y, 17f * u, fill.set(color, (alpha * 0.18f).toInt()))
                canvas.drawCircle(x, y, 17f * u, stroke.set(color, (alpha * 0.6f).toInt(), 1.5f * u))
                canvas.drawCircle(x, y, 6f * u, stroke.set(Color.BLACK, shadow, 4f * u))
                canvas.drawCircle(x, y, 6f * u, fill.set(color, alpha))
                canvas.drawCircle(x, y, 6f * u, stroke.set(Color.WHITE, alpha, 2f * u))
            }

            else -> { // CIRCLE
                canvas.drawCircle(x, y, r + 2f * u, stroke.set(Color.BLACK, (alpha * 0.55f).toInt(), 2.5f * u))
                canvas.drawCircle(x, y, r, fill.set(color, (alpha * 0.85f).toInt()))
                canvas.drawCircle(x, y, r, stroke.set(Color.WHITE, alpha, 2f * u))
                canvas.drawCircle(x, y, 2f * u, fill.set(Color.WHITE, alpha))
            }
        }
    }
}
