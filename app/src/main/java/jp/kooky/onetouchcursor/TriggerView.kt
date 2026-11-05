package jp.kooky.onetouchcursor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

/**
 * 画面端の縦長トリガー。
 *
 *  1. トリガーに触れて長押し（holdMs）→ 振動してカーソル出現
 *  2. そのまま指を動かす → カーソルが指の動き×速度で移動（指は画面中どこへ動いてもOK）
 *  3. 指を離す → カーソル位置をタップしてカーソルを消す
 *     ※ トリガーの上に指を戻して離した場合はキャンセル（設定ON時）
 *  長押し前に指を動かした／すぐ離した場合は何もしない。
 */
class TriggerView(context: Context, private val listener: Listener) : View(context) {

    interface Listener {
        fun showCursorNear(rawX: Float, rawY: Float, fromRight: Boolean)
        fun moveCursor(dx: Float, dy: Float)
        fun release(rawX: Float, rawY: Float, cancelled: Boolean)
        fun abort()
        fun haptic(strong: Boolean)
    }

    var holdMs = 250L
    var speed = 2.5f
    var cancelOnReturn = true
    var rightEdge = true
    var barColor = 0xFFFF8A2B.toInt()
    var showArea = true      // 触れる範囲を常にうっすら表示
    var preview = false      // 設定画面を開いている間は枠付きで濃く表示

    private val density = resources.displayMetrics.density
    private val handler = Handler(Looper.getMainLooper())
    private val slop = 10f * density

    private var active = false
    private var downRawX = 0f
    private var downRawY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var lastT = 0L

    private val holdRunnable = Runnable {
        active = true
        listener.haptic(true)
        listener.showCursorNear(lastRawX, lastRawY, rightEdge)
        invalidate()
    }

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val areaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 8f), 0f)
    }
    private val areaRect = RectF()
    private val barRect = RectF()
    private val loc = IntArray(2)

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        // Androidの「戻る」ジェスチャーよりトリガーを優先させる指定（効くかは端末次第）
        systemGestureExclusionRects = listOf(Rect(0, 0, width, height))
    }

    override fun onDraw(canvas: Canvas) {
        // 触れる範囲（View全体）
        val r = 6f * density
        areaRect.set(0f, 0f, width.toFloat(), height.toFloat())
        if (preview || showArea || active) {
            areaPaint.color = barColor
            areaPaint.alpha = when {
                preview -> 90
                active -> 60
                else -> 28
            }
            canvas.drawRoundRect(areaRect, r, r, areaPaint)
        }
        if (preview) {
            borderPaint.color = barColor
            borderPaint.strokeWidth = 1.5f * density
            areaRect.inset(borderPaint.strokeWidth / 2, borderPaint.strokeWidth / 2)
            canvas.drawRoundRect(areaRect, r, r, borderPaint)
        }
        // 見た目は細い帯（触れる範囲はView全体）
        val barW = (if (active) 6f else 4f) * density
        val left = if (rightEdge) width - barW - 2f * density else 2f * density
        barRect.set(left, 4f * density, left + barW, height - 4f * density)
        barPaint.color = barColor
        canvas.drawRoundRect(barRect, barW / 2, barW / 2, barPaint)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                active = false
                downRawX = e.rawX; downRawY = e.rawY
                lastRawX = e.rawX; lastRawY = e.rawY
                lastT = SystemClock.uptimeMillis()
                handler.postDelayed(holdRunnable, holdMs)
            }

            MotionEvent.ACTION_MOVE -> {
                val x = e.rawX
                val y = e.rawY
                if (!active) {
                    // 長押し前に動いたら無効（誤操作防止）
                    if (hypot(x - downRawX, y - downRawY) > slop) {
                        handler.removeCallbacks(holdRunnable)
                    }
                    lastRawX = x; lastRawY = y
                    return true
                }
                val dx = x - lastRawX
                val dy = y - lastRawY
                val now = SystemClock.uptimeMillis()
                val dt = (now - lastT).coerceAtLeast(1L)
                lastRawX = x; lastRawY = y; lastT = now

                // 速く動かすほど遠くへ（ゆっくり=精密）
                val v = hypot(dx, dy) / dt / density
                val accel = 1f + ((v - 0.25f).coerceIn(0f, 2f) * 0.7f)
                listener.moveCursor(dx * speed * accel, dy * speed * accel)
            }

            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(holdRunnable)
                if (active) {
                    val cancelled = cancelOnReturn && isOverTrigger(e.rawX, e.rawY)
                    if (cancelled) listener.haptic(false)
                    listener.release(e.rawX, e.rawY, cancelled)
                }
                active = false
                invalidate()
            }

            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(holdRunnable)
                if (active) listener.abort()
                active = false
                invalidate()
            }
        }
        return true
    }

    private fun isOverTrigger(rawX: Float, rawY: Float): Boolean {
        getLocationOnScreen(loc)
        val margin = 12f * density
        return rawX >= loc[0] - margin && rawX <= loc[0] + width + margin &&
            rawY >= loc[1] - margin && rawY <= loc[1] + height + margin
    }
}
