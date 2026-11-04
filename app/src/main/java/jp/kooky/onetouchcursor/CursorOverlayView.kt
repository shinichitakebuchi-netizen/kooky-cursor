package jp.kooky.onetouchcursor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/**
 * 全画面・タッチ不可のオーバーレイに円形カーソルを描くだけのView。
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
    var active = true          // 操作中は濃く、放置で薄く
    var dragging = false       // ドラッグ中は中心に白い点

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ringWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
    }
    private val ringDark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.BLACK
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    fun setPos(x: Float, y: Float) {
        posX = x
        posY = y
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        getLocationOnScreen(loc)
        val x = posX - loc[0]
        val y = posY - loc[1]
        val r = radiusDp * density
        val alpha = if (active) 255 else 90

        // 外側の黒リング（明るい背景でも見える）
        ringDark.strokeWidth = 2.5f * density
        ringDark.alpha = (alpha * 0.55f).toInt()
        canvas.drawCircle(x, y, r + 2f * density, ringDark)

        // 本体
        fill.color = color
        fill.alpha = (alpha * (if (active) 0.85f else 0.6f)).toInt()
        canvas.drawCircle(x, y, r, fill)

        // 白リング（暗い背景でも見える）
        ringWhite.strokeWidth = (if (active) 2f else 1.2f) * density
        ringWhite.alpha = alpha
        canvas.drawCircle(x, y, r, ringWhite)

        // 中心点（クリック位置）
        dot.alpha = alpha
        canvas.drawCircle(x, y, (if (dragging) 4f else 2f) * density, dot)
    }
}
