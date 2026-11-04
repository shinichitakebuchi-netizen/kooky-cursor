package jp.kooky.onetouchcursor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

/**
 * トラックパッド本体。
 *
 * 操作ルール（MVP）
 *  - 1本指で触れて動かす → カーソル移動（相対座標）
 *  - 指を離す → その時点のカーソル位置をタップ（動かさずに離してもタップ）
 *  - 動かさずに2回素早くタップ → ダブルクリック（設定ON時）
 *  - 動かさずに長押し → 振動 → そのまま離すと「長押し」、動かすと「ドラッグ」
 *  - 2本指で動かす → 離したときにカーソル位置からスクロール（スワイプ）
 *
 * 指がパッドの外に出ても、触れ始めがパッド内なら追跡し続ける（Androidの通常動作）。
 */
class TrackpadView(context: Context, private val listener: Listener) : View(context) {

    interface Listener {
        fun cursorPos(): PointF
        fun moveCursor(dx: Float, dy: Float)
        fun onTouchStart()
        fun onTouchEnd()
        fun click(x: Float, y: Float)
        fun doubleClick(x: Float, y: Float)
        fun longClick(x: Float, y: Float)
        fun drag(points: List<PointF>, durationMs: Long)
        fun scroll(fromX: Float, fromY: Float, dx: Float, dy: Float)
        fun setDragging(on: Boolean)
        fun haptic(strong: Boolean)
    }

    // サービスから設定を流し込む
    var speed = 2f
    var dragThresholdPx = 30f
    var doubleClickEnabled = true
    var longPressEnabled = true

    private val density = resources.displayMetrics.density
    private val handler = Handler(Looper.getMainLooper())

    private enum class Mode { NONE, MOVE, HOLD, DRAG, SCROLL }

    private var mode = Mode.NONE
    private var activeId = -1
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var lastT = 0L
    private var moved = false

    // ドラッグ記録
    private val dragPoints = ArrayList<PointF>()
    private var dragStartT = 0L

    // 2本指スクロール
    private var scrollFrom = PointF()
    private var scrollDx = 0f
    private var scrollDy = 0f

    // ダブルクリック判定用（動かさないタップを少しだけ保留する）
    private var pendingTap: Runnable? = null
    private var pendingX = 0f
    private var pendingY = 0f
    private val doubleTapWindowMs = 280L

    private val longPressRunnable = Runnable {
        if (mode == Mode.MOVE && !moved) {
            mode = Mode.HOLD
            listener.haptic(true)
            listener.setDragging(true)
        }
    }

    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(110, 255, 255, 255)
        textAlign = Paint.Align.CENTER
        textSize = 12f * resources.displayMetrics.scaledDensity
    }

    override fun onDraw(canvas: Canvas) {
        val label = when (mode) {
            Mode.HOLD -> "長押し中（動かすとドラッグ）"
            Mode.DRAG -> "ドラッグ中"
            Mode.SCROLL -> "スクロール"
            else -> "トラックパッド"
        }
        canvas.drawText(label, width / 2f, height / 2f, hintPaint)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activeId = e.getPointerId(0)
                downX = e.x; downY = e.y
                lastX = e.x; lastY = e.y
                lastT = SystemClock.uptimeMillis()
                moved = false
                mode = Mode.MOVE
                listener.onTouchStart()
                if (longPressEnabled) handler.postDelayed(longPressRunnable, 450L)
                invalidate()
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (e.pointerCount == 2 && (mode == Mode.MOVE)) {
                    handler.removeCallbacks(longPressRunnable)
                    flushPendingTap()
                    mode = Mode.SCROLL
                    scrollFrom = listener.cursorPos()
                    scrollDx = 0f; scrollDy = 0f
                    invalidate()
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                // 追跡中の指が離れたら残りの指に切り替え（座標ジャンプ防止）
                val upIndex = e.actionIndex
                if (e.getPointerId(upIndex) == activeId) {
                    val newIndex = if (upIndex == 0) 1 else 0
                    activeId = e.getPointerId(newIndex)
                    lastX = e.getX(newIndex)
                    lastY = e.getY(newIndex)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                val i = e.findPointerIndex(activeId)
                if (i < 0) return true
                val x = e.getX(i)
                val y = e.getY(i)
                val dx = x - lastX
                val dy = y - lastY
                val now = SystemClock.uptimeMillis()
                val dt = (now - lastT).coerceAtLeast(1L)
                lastX = x; lastY = y; lastT = now

                if (!moved && hypot(x - downX, y - downY) > dragThresholdPx) {
                    moved = true
                    handler.removeCallbacks(longPressRunnable)
                    flushPendingTap()
                    if (mode == Mode.HOLD) {
                        mode = Mode.DRAG
                        dragPoints.clear()
                        dragPoints.add(listener.cursorPos())
                        dragStartT = now
                        invalidate()
                    }
                }

                when (mode) {
                    Mode.MOVE, Mode.DRAG -> {
                        // 速度に応じた加速（ゆっくり=精密、速く=遠くへ）
                        val v = hypot(dx, dy) / dt / density   // dp/ms
                        val accel = 1f + ((v - 0.25f).coerceIn(0f, 2f) * 0.7f)
                        val g = speed * accel
                        listener.moveCursor(dx * g, dy * g)
                        if (mode == Mode.DRAG) {
                            val p = listener.cursorPos()
                            val last = dragPoints.last()
                            if (hypot(p.x - last.x, p.y - last.y) > 3f) dragPoints.add(p)
                        }
                    }
                    Mode.SCROLL -> {
                        scrollDx += dx
                        scrollDy += dy
                    }
                    else -> Unit
                }
            }

            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPressRunnable)
                val p = listener.cursorPos()
                when (mode) {
                    Mode.MOVE -> handleRelease(p, moved)
                    Mode.HOLD -> listener.longClick(p.x, p.y)
                    Mode.DRAG -> {
                        if (dragPoints.last().let { it.x != p.x || it.y != p.y }) dragPoints.add(p)
                        val dur = SystemClock.uptimeMillis() - dragStartT
                        listener.drag(ArrayList(dragPoints), dur)
                    }
                    Mode.SCROLL -> {
                        val k = 1.6f
                        listener.scroll(scrollFrom.x, scrollFrom.y, scrollDx * k, scrollDy * k)
                    }
                    Mode.NONE -> Unit
                }
                endTouch()
            }

            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                endTouch()
            }
        }
        return true
    }

    private fun handleRelease(p: PointF, didMove: Boolean) {
        if (didMove || !doubleClickEnabled) {
            listener.haptic(false)
            listener.click(p.x, p.y)
            return
        }
        // 動かさずに離した → ダブルクリック判定のため少し待つ
        val pending = pendingTap
        if (pending != null) {
            handler.removeCallbacks(pending)
            pendingTap = null
            listener.haptic(false)
            listener.doubleClick(pendingX, pendingY)
        } else {
            pendingX = p.x; pendingY = p.y
            val r = Runnable {
                pendingTap = null
                listener.haptic(false)
                listener.click(pendingX, pendingY)
            }
            pendingTap = r
            handler.postDelayed(r, doubleTapWindowMs)
        }
    }

    /** 保留中のタップがあれば即実行（次の操作で動かし始めたとき等） */
    private fun flushPendingTap() {
        val pending = pendingTap ?: return
        handler.removeCallbacks(pending)
        pending.run()
    }

    private fun endTouch() {
        mode = Mode.NONE
        listener.setDragging(false)
        listener.onTouchEnd()
        invalidate()
    }
}
