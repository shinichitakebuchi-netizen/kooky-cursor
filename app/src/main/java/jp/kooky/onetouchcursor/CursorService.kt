package jp.kooky.onetouchcursor

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PointF
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 本体サービス。
 *  - カーソル（全画面・タッチ不可オーバーレイ）
 *  - 操作パネル（画面下部・タッチ可オーバーレイ）
 *  - dispatchGesture でカーソル位置にタップ等を送る
 *
 * オーバーレイは TYPE_ACCESSIBILITY_OVERLAY を使うので SYSTEM_ALERT_WINDOW 権限は不要。
 */
class CursorService : AccessibilityService(),
    SharedPreferences.OnSharedPreferenceChangeListener,
    TrackpadView.Listener {

    private lateinit var wm: WindowManager
    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    private var s = Settings()

    private var screenW = 0
    private var screenH = 0
    private var cx = 0f
    private var cy = 0f

    private var cursorView: CursorOverlayView? = null
    private var padRoot: LinearLayout? = null
    private var padParams: WindowManager.LayoutParams? = null
    private var trackpad: TrackpadView? = null
    private var bubble: TextView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null

    private val idleRunnable = Runnable {
        cursorView?.let { it.active = false; it.invalidate() }
    }

    // ───────── ライフサイクル ─────────

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = Settings.prefs(this)
        prefs.registerOnSharedPreferenceChangeListener(this)
        s = Settings.load(prefs)
        updateScreenSize()
        cx = screenW / 2f
        cy = screenH / 2f
        addCursorWindow()
        addPadWindow()
        addBubbleWindow()
        applySettings()
        wakeCursor()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (::prefs.isInitialized) prefs.unregisterOnSharedPreferenceChangeListener(this)
        handler.removeCallbacksAndMessages(null)
        listOf<View?>(cursorView, padRoot, bubble).forEach { v ->
            v?.let { runCatching { wm.removeView(it) } }
        }
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateScreenSize()
        clampCursor()
        cursorView?.setPos(cx, cy)
    }

    override fun onSharedPreferenceChanged(p: SharedPreferences?, key: String?) {
        s = Settings.load(prefs)
        applySettings()
    }

    // ───────── ウィンドウ構築 ─────────

    private fun dp(v: Number): Int = (v.toFloat() * resources.displayMetrics.density).toInt()

    private fun updateScreenSize() {
        val b = wm.currentWindowMetrics.bounds
        screenW = b.width()
        screenH = b.height()
    }

    private fun overlayParams(w: Int, h: Int, flags: Int) = WindowManager.LayoutParams(
        w, h,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        flags,
        PixelFormat.TRANSLUCENT
    )

    private fun addCursorWindow() {
        val v = CursorOverlayView(this)
        val lp = overlayParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        wm.addView(v, lp)
        v.setPos(cx, cy)
        cursorView = v
    }

    private fun barButton(text: String, onClick: () -> Unit) = TextView(this).apply {
        this.text = text
        setTextColor(Color.argb(220, 255, 255, 255))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setPadding(dp(12), dp(4), dp(12), dp(4))
        background = GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor(Color.argb(60, 255, 255, 255))
        }
        setOnClickListener { onClick() }
    }

    private fun addPadWindow() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(Color.parseColor("#E6141E33"))      // ネイビー
                setStroke(dp(1), Color.argb(70, 255, 138, 43)) // オレンジの細枠
            }
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        bar.addView(barButton("◎ 中央") { resetCursor() })
        bar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        bar.addView(barButton("隠す") { setPadVisible(false) })
        root.addView(bar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(28)))

        val pad = TrackpadView(this, this)
        root.addView(pad, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(4) })

        val lp = overlayParams(
            dp(s.padWidthDp), dp(s.padHeightDp + 36),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        )
        wm.addView(root, lp)
        padRoot = root
        padParams = lp
        trackpad = pad
    }

    private fun addBubbleWindow() {
        val b = TextView(this).apply {
            text = "●"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#FF8A2B"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#CC141E33"))
            }
            setOnClickListener { setPadVisible(true) }
        }
        val lp = overlayParams(
            dp(44), dp(44),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        )
        wm.addView(b, lp)
        bubble = b
        bubbleParams = lp
    }

    // ───────── 設定反映 ─────────

    private fun applySettings() {
        val side = if (s.rightHand) Gravity.END else Gravity.START

        padParams?.let { lp ->
            lp.width = dp(s.padWidthDp)
            lp.height = dp(s.padHeightDp + 36)
            lp.gravity = Gravity.BOTTOM or side
            lp.x = dp(s.padSideDp)
            lp.y = dp(s.padBottomDp)
            padRoot?.let { wm.updateViewLayout(it, lp) }
        }
        padRoot?.alpha = (1f - s.transparency / 100f).coerceIn(0f, 1f)
        padRoot?.visibility = if (s.padVisible) View.VISIBLE else View.GONE

        bubbleParams?.let { lp ->
            lp.gravity = Gravity.BOTTOM or side
            lp.x = dp(s.padSideDp)
            lp.y = dp(s.padBottomDp)
            bubble?.let { wm.updateViewLayout(it, lp) }
        }
        bubble?.visibility = if (s.padVisible) View.GONE else View.VISIBLE
        // パネルを隠している間はカーソルも消す
        cursorView?.visibility = if (s.padVisible) View.VISIBLE else View.GONE

        trackpad?.apply {
            speed = s.speed
            dragThresholdPx = dp(s.dragThresholdDp).toFloat()
            doubleClickEnabled = s.doubleClick
            longPressEnabled = s.longPress
        }
        cursorView?.apply {
            radiusDp = when (s.cursorSize) { 0 -> 9f; 2 -> 20f; else -> 14f }
            color = s.cursorColor
            invalidate()
        }
    }

    private fun setPadVisible(v: Boolean) {
        s = s.copy(padVisible = v)
        s.save(prefs) // → onSharedPreferenceChanged → applySettings
    }

    // ───────── カーソル ─────────

    private fun clampCursor() {
        cx = cx.coerceIn(0f, (screenW - 1).toFloat())
        cy = cy.coerceIn(0f, (screenH - 1).toFloat())
    }

    private fun resetCursor() {
        cx = screenW / 2f
        cy = screenH / 2f
        cursorView?.setPos(cx, cy)
        wakeCursor()
    }

    private fun wakeCursor() {
        handler.removeCallbacks(idleRunnable)
        cursorView?.let { it.active = true; it.invalidate() }
        handler.postDelayed(idleRunnable, 2000L)
    }

    override fun cursorPos() = PointF(cx, cy)

    override fun moveCursor(dx: Float, dy: Float) {
        cx += dx
        cy += dy
        clampCursor()
        cursorView?.setPos(cx, cy)
        wakeCursor()
    }

    override fun onTouchStart() {
        handler.removeCallbacks(idleRunnable)
        cursorView?.let { it.active = true; it.invalidate() }
    }

    override fun onTouchEnd() = wakeCursor()

    override fun setDragging(on: Boolean) {
        cursorView?.let { it.dragging = on; it.invalidate() }
    }

    override fun haptic(strong: Boolean) {
        if (!s.vibrate) return
        val vm = getSystemService(VibratorManager::class.java) ?: return
        val effect = VibrationEffect.createPredefined(
            if (strong) VibrationEffect.EFFECT_HEAVY_CLICK else VibrationEffect.EFFECT_TICK
        )
        vm.defaultVibrator.vibrate(effect)
    }

    // ───────── ジェスチャー送信 ─────────

    private fun pointInPad(x: Float, y: Float): Boolean {
        val root = padRoot ?: return false
        if (root.visibility != View.VISIBLE) return false
        val loc = IntArray(2)
        root.getLocationOnScreen(loc)
        return x >= loc[0] && x <= loc[0] + root.width &&
            y >= loc[1] && y <= loc[1] + root.height
    }

    /** パネル自身の上をタップするとき、送ったタップをパネルが受け取らないよう一時的に透過させる */
    private fun setPadTouchable(touchable: Boolean) {
        val lp = padParams ?: return
        val root = padRoot ?: return
        lp.flags = if (touchable) {
            lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        runCatching { wm.updateViewLayout(root, lp) }
    }

    private fun dispatch(
        gesture: GestureDescription,
        points: List<PointF>,
        onCompleted: (() -> Unit)? = null,
    ) {
        val passThrough = points.any { pointInPad(it.x, it.y) }
        if (passThrough) setPadTouchable(false)
        val cb = object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                if (passThrough) setPadTouchable(true)
                onCompleted?.invoke()
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                if (passThrough) setPadTouchable(true)
            }
        }
        val ok = dispatchGesture(gesture, cb, handler)
        if (!ok && passThrough) setPadTouchable(true)
    }

    private fun pointPath(x: Float, y: Float) = Path().apply { moveTo(x, y) }

    override fun click(x: Float, y: Float) {
        val stroke = GestureDescription.StrokeDescription(pointPath(x, y), 0L, 40L)
        dispatch(GestureDescription.Builder().addStroke(stroke).build(), listOf(PointF(x, y)))
    }

    override fun doubleClick(x: Float, y: Float) {
        val s1 = GestureDescription.StrokeDescription(pointPath(x, y), 0L, 40L)
        val s2 = GestureDescription.StrokeDescription(pointPath(x, y), 120L, 40L)
        dispatch(
            GestureDescription.Builder().addStroke(s1).addStroke(s2).build(),
            listOf(PointF(x, y))
        )
    }

    override fun longClick(x: Float, y: Float) {
        val stroke = GestureDescription.StrokeDescription(pointPath(x, y), 0L, 700L)
        dispatch(GestureDescription.Builder().addStroke(stroke).build(), listOf(PointF(x, y)))
    }

    /**
     * ドラッグ：指を離した時点で、記録したカーソル軌跡をまとめて再生する。
     * 先に開始点で長押し（アイコン移動などに必要）→ 続けて軌跡をなぞる。
     */
    override fun drag(points: List<PointF>, durationMs: Long) {
        if (points.isEmpty()) return
        val start = points.first()
        val hold = GestureDescription.StrokeDescription(
            pointPath(start.x, start.y), 0L, 400L, true
        )
        val movePath = Path().apply {
            moveTo(start.x, start.y)
            if (points.size == 1) lineTo(start.x + 1f, start.y)
            for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
        }
        val moveDur = durationMs.coerceIn(150L, 8000L)
        dispatch(GestureDescription.Builder().addStroke(hold).build(), points) {
            val move = hold.continueStroke(movePath, 0L, moveDur, false)
            dispatch(GestureDescription.Builder().addStroke(move).build(), points)
        }
    }

    /** 2本指スクロール：カーソル位置から指の動きと同じ方向へスワイプ */
    override fun scroll(fromX: Float, fromY: Float, dx: Float, dy: Float) {
        if (kotlin.math.hypot(dx, dy) < dp(8)) return
        val toX = (fromX + dx).coerceIn(1f, screenW - 2f)
        val toY = (fromY + dy).coerceIn(1f, screenH - 2f)
        val path = Path().apply { moveTo(fromX, fromY); lineTo(toX, toY) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 280L)
        dispatch(
            GestureDescription.Builder().addStroke(stroke).build(),
            listOf(PointF(fromX, fromY), PointF(toX, toY))
        )
    }
}
