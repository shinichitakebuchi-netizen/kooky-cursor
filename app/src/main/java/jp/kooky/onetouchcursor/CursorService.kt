package jp.kooky.onetouchcursor

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.VibratorManager
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

/**
 * 本体サービス。
 *  - 画面端の縦長トリガー（タッチ可オーバーレイ）
 *  - カーソル（全画面・タッチ不可オーバーレイ、操作中だけ表示）
 *  - 指を離した位置に dispatchGesture でタップを送る
 *
 * オーバーレイは TYPE_ACCESSIBILITY_OVERLAY なので SYSTEM_ALERT_WINDOW 権限は不要。
 */
class CursorService : AccessibilityService(),
    SharedPreferences.OnSharedPreferenceChangeListener,
    TriggerView.Listener {

    companion object {
        private var instance: CursorService? = null
        private var previewOn = false

        /** 設定画面の表示中だけ、触れる範囲を濃く表示する（同じアプリ内からの呼び出し） */
        fun setPreview(on: Boolean) {
            previewOn = on
            instance?.let { svc ->
                svc.triggers.forEach { (t, _) -> t.preview = on; t.alpha = svc.triggerAlpha(); t.invalidate() }
            }
        }
    }

    private lateinit var wm: WindowManager
    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    private var s = Settings()

    private var screenW = 0
    private var screenH = 0
    private var cx = 0f
    private var cy = 0f

    private var cursorView: CursorOverlayView? = null
    private var dockView: DockView? = null
    /** [0]=左端 / [1]=右端 のトリガー。表示するかは設定で切り替える */
    private val triggers = ArrayList<Pair<TriggerView, WindowManager.LayoutParams>>()

    // ───────── ライフサイクル ─────────

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = Settings.prefs(this)
        prefs.registerOnSharedPreferenceChangeListener(this)
        s = Settings.load(prefs)
        instance = this
        updateScreenSize()
        addCursorWindow()
        addDockWindow()
        addTriggerWindow(right = false)
        addTriggerWindow(right = true)
        applySettings()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (::prefs.isInitialized) prefs.unregisterOnSharedPreferenceChangeListener(this)
        handler.removeCallbacksAndMessages(null)
        if (instance === this) instance = null
        cursorView?.let { runCatching { wm.removeView(it) } }
        dockView?.let { runCatching { wm.removeView(it) } }
        triggers.forEach { (t, _) -> runCatching { wm.removeView(t) } }
        triggers.clear()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateScreenSize()
        applySettings() // 回転時にトリガー位置を再計算
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
    ).apply {
        layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    }

    private fun addDockWindow() {
        val v = DockView(this)
        val lp = overlayParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        ).apply { gravity = Gravity.TOP or Gravity.START }
        v.visibility = View.GONE
        wm.addView(v, lp)
        dockView = v
    }

    private fun addCursorWindow() {
        val v = CursorOverlayView(this)
        val lp = overlayParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        ).apply { gravity = Gravity.TOP or Gravity.START }
        v.visibility = View.GONE   // 操作中だけ表示
        wm.addView(v, lp)
        cursorView = v
    }

    private fun addTriggerWindow(right: Boolean) {
        val t = TriggerView(this, this).apply { rightEdge = right }
        val lp = overlayParams(
            dp(s.triggerWidthDp), dp(s.triggerLengthDp),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        wm.addView(t, lp)
        triggers.add(t to lp)
    }

    // ───────── 設定反映 ─────────

    private fun applySettings() {
        val h = dp(s.triggerLengthDp)
        val center = screenH * s.triggerPosPercent / 100
        triggers.forEach { (t, lp) ->
            val right = t.rightEdge
            lp.width = dp(s.triggerWidthDp)
            lp.height = h
            lp.gravity = Gravity.TOP or (if (right) Gravity.END else Gravity.START)
            lp.x = 0
            lp.y = (center - h / 2).coerceIn(0, (screenH - h).coerceAtLeast(0))
            runCatching { wm.updateViewLayout(t, lp) }
            t.apply {
                val on = s.enabled && (if (right) s.rightOn else s.leftOn)
                visibility = if (on) View.VISIBLE else View.GONE
                alpha = triggerAlpha()
                holdMs = s.holdMs.toLong()
                speed = s.speed
                cancelOnReturn = s.cancelOnReturn
                barColor = s.triggerColor
                barStyle = s.barStyle
                dockEnabled = s.dockEnabled
                showArea = s.showArea
                preview = previewOn
                invalidate()
            }
        }
        cursorView?.apply {
            radiusDp = when (s.cursorSize) { 0 -> 9f; 2 -> 20f; else -> 14f }
            color = s.cursorColor
            shape = s.cursorShape
            invalidate()
        }
    }

    /** 設定画面を開いている間は透明度に関係なく見えるようにする */
    private fun triggerAlpha(): Float =
        if (previewOn) 1f else (1f - s.triggerTransparency / 100f).coerceIn(0.05f, 1f)

    // ───────── TriggerView.Listener ─────────

    override fun showCursorNear(rawX: Float, rawY: Float, fromRight: Boolean) {
        if (s.startFixed) {
            // 画面の決まった位置
            cx = screenW * s.startXPercent / 100f
            cy = screenH * s.startYPercent / 100f
        } else {
            // 指の近く（内側へ startOffsetX、上へ startOffsetY）
            val inward = dp(s.startOffsetXDp).toFloat()
            cx = if (fromRight) rawX - inward else rawX + inward
            cy = rawY - dp(s.startOffsetYDp).toFloat()
        }
        clampCursor()
        cursorView?.apply {
            active = true
            dragging = false
            setPos(cx, cy)
            visibility = View.VISIBLE
        }
    }

    override fun moveCursor(dx: Float, dy: Float) {
        cx += dx
        cy += dy
        clampCursor()
        cursorView?.setPos(cx, cy)
    }

    override fun release(rawX: Float, rawY: Float, cancelled: Boolean) {
        cursorView?.visibility = View.GONE
        // 設定画面を開いている間はカーソルの動きだけ確認でき、タップは送らない
        if (!cancelled && !previewOn) tapAt(cx, cy)
    }

    // ───────── ドック ─────────

    override fun dockStart(rawX: Float, rawY: Float, fromRight: Boolean) {
        val v = dockView ?: return
        v.cols = s.dockCols
        v.rows = s.dockRows
        v.cellDp = s.dockCellDp.toFloat()
        v.showLabels = s.dockLabels
        v.fontIndex = s.font
        v.pal = Theme.pal(this, s.theme)
        v.posFixed = s.dockFixed
        v.posYPercent = s.dockYPercent
        v.gapDp = s.dockGapDp.toFloat()
        v.editMode = false
        v.show(DockItems.parse(s.dockItems), rawX, rawY, fromRight, screenW, screenH)
        haptic(false)
    }

    override fun dockMove(rawX: Float, rawY: Float) {
        val v = dockView ?: return
        val h = v.hit(rawX, rawY)
        if (v.setHovered(h) && (h >= 0 || h == DockView.HIT_HEADER)) haptic(false)
    }

    override fun dockRelease(rawX: Float, rawY: Float) {
        val v = dockView ?: return
        val h = v.hit(rawX, rawY)
        val key = v.keyAt(h)
        v.hide()
        // 設定画面を開いている間は見た目の確認だけ（起動しない）
        if (previewOn) return
        if (h == DockView.HIT_HEADER) {
            // 「⚙ 設定」→ ドックの設置画面を開く
            runCatching {
                startActivity(
                    android.content.Intent(this, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_EDIT_DOCK, true)
                        .addFlags(
                            android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                                android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                        )
                )
            }
            return
        }
        if (key != null) DockItems.run(this, key)
    }

    override fun dockAbort() {
        dockView?.hide()
    }

    override fun abort() {
        cursorView?.visibility = View.GONE
    }

    override fun haptic(strong: Boolean) {
        if (!s.vibrate) return
        val vm = getSystemService(VibratorManager::class.java) ?: return
        vm.defaultVibrator.vibrate(
            VibrationEffect.createPredefined(
                if (strong) VibrationEffect.EFFECT_HEAVY_CLICK else VibrationEffect.EFFECT_TICK
            )
        )
    }

    private fun clampCursor() {
        cx = cx.coerceIn(0f, (screenW - 1).toFloat())
        cy = cy.coerceIn(0f, (screenH - 1).toFloat())
    }

    // ───────── タップ送信 ─────────

    private fun pointInTrigger(x: Float, y: Float): Boolean {
        val loc = IntArray(2)
        return triggers.any { (t, _) ->
            if (t.visibility != View.VISIBLE) return@any false
            t.getLocationOnScreen(loc)
            x >= loc[0] && x <= loc[0] + t.width && y >= loc[1] && y <= loc[1] + t.height
        }
    }

    /** トリガー自身の上をタップするとき、送ったタップを受け取らないよう一時的に透過させる */
    private fun setTriggerTouchable(touchable: Boolean) {
        triggers.forEach { (t, lp) ->
            lp.flags = if (touchable) {
                lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            } else {
                lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            }
            runCatching { wm.updateViewLayout(t, lp) }
        }
    }

    private fun tapAt(x: Float, y: Float) {
        val passThrough = pointInTrigger(x, y)
        if (passThrough) setTriggerTouchable(false)
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 40L))
            .build()
        val cb = object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                if (passThrough) setTriggerTouchable(true)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                if (passThrough) setTriggerTouchable(true)
            }
        }
        val ok = dispatchGesture(gesture, cb, handler)
        if (!ok && passThrough) setTriggerTouchable(true)
    }
}
