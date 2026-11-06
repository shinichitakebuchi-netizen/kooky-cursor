package jp.kooky.onetouchcursor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.text.TextUtils
import android.view.View

/**
 * ドック（アプリの並んだ板）。
 *  - 実際の操作：全画面・タッチ不可のオーバーレイに描き、指の位置で項目を判定する
 *  - 設置画面：同じViewを設定アプリ内に置き、editMode で「×」「＋」や持ち上げた項目を描く
 * 座標は panel も含めてすべてスクリーン座標（設置画面は全画面に置くので画面内の座標と一致する）。
 */
class DockView(context: Context) : View(context) {

    companion object {
        const val HIT_NONE = -1
        const val HIT_HEADER = -2   // 上の「設定」ボタン（設置画面では位置調整のつまみ）
        const val HIT_ADD = -3      // 設置画面の「＋ 追加」

        const val HEADER_DP = 30f
        const val PAD_DP = 12f
        const val MARGIN_DP = 8f

        /** ドックの大きさ(px)。count=項目数 */
        fun size(cols: Int, rows: Int, count: Int, cellDp: Float, d: Float): Pair<Float, Float> {
            val cell = cellDp * d
            val shownRows = if (count == 0) 1 else ((count + cols - 1) / cols).coerceAtMost(rows)
            return (cols * cell + PAD_DP * d * 2) to (HEADER_DP * d + shownRows * cell + PAD_DP * d * 2)
        }

        /** ドックの左上(px) */
        fun origin(
            w: Float, h: Float, fromRight: Boolean, gapDp: Float, centerY: Float,
            screenW: Int, screenH: Int, d: Float,
        ): Pair<Float, Float> {
            val gap = gapDp * d
            val left = (if (fromRight) screenW - gap - w else gap).coerceIn(0f, (screenW - w).coerceAtLeast(0f))
            val m = MARGIN_DP * d
            val top = (centerY - h / 2).coerceIn(m, (screenH - h - m).coerceAtLeast(m))
            return left to top
        }
    }

    private val d = resources.displayMetrics.density
    private val loc = IntArray(2)

    // 配色（テーマ：ダーク＝墨／ライト＝白練）
    var pal: Theme.Pal = Theme.darkPal
    private val cAi get() = pal.bg          // 板の色
    private val cTorinoko get() = pal.text  // 文字（主）
    private val cKame get() = pal.sub or 0xFF000000.toInt()  // 文字（補足）
    private val cRikyu get() = pal.line     // 罫線
    private val cBenihi get() = pal.accent  // 紅緋

    private var keys: List<String> = emptyList()
    private val labels = HashMap<String, String>()
    private val icons = HashMap<String, Drawable?>()

    var cols = 4
    var rows = 4
    var cellDp = 76f          // 1マスの大きさ
    var showLabels = true     // アプリ名を表示するか
    var posFixed = false      // true=決まった高さ / false=指の高さ
    var posYPercent = 50      // 決まった高さ（画面の上端=0% / 下端=100%、ドックの中心）
    var gapDp = 36f           // 端からの距離
    var fontIndex = -1        // AppFonts の番号（文字のフォント）
        set(v) {
            if (field != v) {
                field = v
                text.typeface = AppFonts.typeface(context, v)
            }
        }

    // 設置画面用
    var editMode = false      // true のとき、渡された範囲の中央に置き、座標はView内の座標として扱う
    var dragFrom = -1         // 持ち上げている項目
    var dragX = 0f
    var dragY = 0f
    var dropAt = -1           // 落とす位置（マス番号）

    private val panel = RectF()
    private var hovered = HIT_NONE

    private val cell get() = cellDp * d
    private val pad get() = PAD_DP * d
    private val headerH get() = HEADER_DP * d

    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val tmp = RectF()

    private fun cap() = (cols * rows).coerceAtLeast(1)

    /** 表示する。fromRight=右端から出したか。(rawX, rawY)=指を置いた位置 */
    fun show(items: List<String>, rawX: Float, rawY: Float, fromRight: Boolean, screenW: Int, screenH: Int) {
        keys = items.take(cap())
        keys.forEach { k ->
            if (!labels.containsKey(k)) labels[k] = DockItems.label(context, k) ?: "（不明）"
            if (!icons.containsKey(k)) icons[k] = DockItems.icon(context, k)
        }
        val (w, h) = size(cols, rows, keys.size, cellDp, d)
        val (left, top) = if (editMode) {
            ((screenW - w) / 2).coerceAtLeast(0f) to ((screenH - h) / 2).coerceAtLeast(MARGIN_DP * d)
        } else {
            val centerY = if (posFixed) screenH * posYPercent / 100f else rawY
            origin(w, h, fromRight, gapDp, centerY, screenW, screenH, d)
        }
        panel.set(left, top, left + w, top + h)
        hovered = HIT_NONE
        visibility = VISIBLE
        invalidate()
    }

    fun hide() {
        visibility = GONE
        hovered = HIT_NONE
    }

    fun panelRect(): RectF = RectF(panel)

    /** マス番号（項目の有無に関係なく）。外なら -1 */
    fun cellIndex(x: Float, y: Float): Int {
        val gx = x - panel.left - pad
        val gy = y - panel.top - headerH - pad
        if (gx < 0 || gy < 0) return -1
        val c = (gx / cell).toInt()
        val r = (gy / cell).toInt()
        if (c >= cols || r >= rows) return -1
        return r * cols + c
    }

    /** 指の位置にあるもの：項目の番号 / HIT_HEADER / HIT_ADD / HIT_NONE */
    fun hit(x: Float, y: Float): Int {
        if (!panel.contains(x, y)) return HIT_NONE
        if (y < panel.top + headerH) {
            // 設置画面：帯の右側は「＋ 追加」
            if (editMode && keys.size < cap() && x > panel.right - 84f * d) return HIT_ADD
            return HIT_HEADER
        }
        val i = cellIndex(x, y)
        if (i in keys.indices) return i
        return HIT_NONE
    }

    /** 設置画面：項目右上の「×」を押したか。押した項目の番号 or -1 */
    fun hitRemove(x: Float, y: Float): Int {
        val i = cellIndex(x, y)
        if (i < 0 || i >= keys.size) return -1
        val (cx, top) = cellOrigin(i)
        val bx = cx + cell / 2 - 12f * d
        val by = top + 12f * d
        return if (x > bx - 14f * d && y < by + 14f * d) i else -1
    }

    /** 選択中を更新。変わったら true */
    fun setHovered(i: Int): Boolean {
        if (i == hovered) return false
        hovered = i
        invalidate()
        return true
    }

    fun keyAt(i: Int): String? = keys.getOrNull(i)

    private fun cellOrigin(i: Int): Pair<Float, Float> {
        val c = i % cols
        val r = i / cols
        val cx = panel.left + pad + c * cell + cell / 2
        val top = panel.top + headerH + pad + r * cell
        return cx to top
    }

    override fun onDraw(canvas: Canvas) {
        // panel はスクリーン座標なので、Viewの画面上の位置だけずらして描く
        canvas.save()
        if (!editMode) {
            getLocationOnScreen(loc)
            canvas.translate(-loc[0].toFloat(), -loc[1].toFloat())
        }
        drawPanel(canvas)
        canvas.restore()
    }

    private fun drawPanel(canvas: Canvas) {
        val k0 = cell / (76f * d)   // 標準(76dp)に対する倍率

        // 板
        bg.color = cAi; bg.alpha = 245
        canvas.drawRoundRect(panel, 4f * d, 4f * d, bg)
        line.color = cRikyu; line.strokeWidth = 1f * d
        canvas.drawRoundRect(panel, 4f * d, 4f * d, line)

        // 上の帯：設定ボタン（設置画面では位置調整のつまみ）
        val hb = panel.top + headerH
        val headOn = hovered == HIT_HEADER
        if (headOn) {
            tmp.set(panel.left + 2f * d, panel.top + 2f * d, panel.right - 2f * d, hb)
            bg.color = cTorinoko; bg.alpha = 40
            canvas.drawRoundRect(tmp, 3f * d, 3f * d, bg)
        }
        canvas.drawLine(panel.left + pad, hb, panel.right - pad, hb, line.apply { color = cRikyu; strokeWidth = 0.6f * d })
        text.color = if (headOn) cTorinoko else cKame
        text.textSize = 11f * d
        if (editMode) {
            text.textAlign = Paint.Align.LEFT
            canvas.drawText("≡ 項目は長押しで移動", panel.left + pad, panel.top + headerH / 2 + 4f * d, text)
            if (keys.size < cap()) {
                text.textAlign = Paint.Align.RIGHT
                text.color = cBenihi
                canvas.drawText("＋ 追加", panel.right - pad, panel.top + headerH / 2 + 4f * d, text)
            }
            text.textAlign = Paint.Align.CENTER
        } else {
            canvas.drawText("⚙　設定", panel.centerX(), panel.top + headerH / 2 + 4f * d, text)
        }

        if (keys.isEmpty()) {
            text.color = cKame; text.textSize = 12f * d
            canvas.drawText(if (editMode) "右上の「＋ 追加」から項目を追加" else "⚙ から項目を追加", panel.centerX(), hb + (panel.bottom - hb) / 2 + 4f * d, text)
            return
        }

        // 持ち上げ中は、落とす位置に向けて他の項目をずらして見せる
        val order = keys.indices.toMutableList()
        if (editMode && dragFrom in keys.indices && dropAt >= 0) {
            val to = dropAt.coerceIn(0, keys.lastIndex)
            order.add(to, order.removeAt(dragFrom))
        }

        order.forEachIndexed { slot, i ->
            if (editMode && i == dragFrom) {
                // 空いたマスに落とし先の枠
                val (cx, top) = cellOrigin(slot)
                tmp.set(cx - cell / 2 + 4f * d, top + 4f * d, cx + cell / 2 - 4f * d, top + cell - 4f * d)
                line.color = cBenihi; line.strokeWidth = 1f * d
                canvas.drawRoundRect(tmp, 3f * d, 3f * d, line)
                return@forEachIndexed
            }
            val (cx, top) = cellOrigin(slot)
            drawItem(canvas, keys[i], cx, top, i == hovered, k0)
            if (editMode) drawRemoveBadge(canvas, cx, top)
        }

        // 持ち上げた項目を指の位置に
        if (editMode && dragFrom in keys.indices) {
            val cx = dragX
            val top = dragY - cell / 2
            tmp.set(cx - cell / 2, top, cx + cell / 2, top + cell)
            bg.color = cAi; bg.alpha = 255
            canvas.drawRoundRect(tmp, 3f * d, 3f * d, bg)
            line.color = cTorinoko; line.strokeWidth = 1f * d
            canvas.drawRoundRect(tmp, 3f * d, 3f * d, line)
            drawItem(canvas, keys[dragFrom], cx, top, true, k0)
        }
    }

    private fun drawRemoveBadge(canvas: Canvas, cx: Float, top: Float) {
        val bx = cx + cell / 2 - 12f * d
        val by = top + 12f * d
        bg.color = cRikyu; bg.alpha = 255
        canvas.drawCircle(bx, by, 8f * d, bg)
        glyph.color = cTorinoko; glyph.textSize = 11f * d
        canvas.drawText("×", bx, by + 4f * d, glyph)
    }

    private fun drawItem(canvas: Canvas, k: String, cx: Float, top: Float, on: Boolean, k0: Float) {
        if (on) {
            tmp.set(cx - cell / 2 + 2f * d, top + 2f * d, cx + cell / 2 - 2f * d, top + cell - 2f * d)
            bg.color = cTorinoko; bg.alpha = 40
            canvas.drawRoundRect(tmp, 3f * d, 3f * d, bg)
            line.color = cBenihi; line.strokeWidth = 1f * d
            canvas.drawRoundRect(tmp, 3f * d, 3f * d, line)
        }
        val iconSize = cell * (if (showLabels) 0.53f else 0.62f)
        val iTop = if (showLabels) top + 8f * d * k0 else top + (cell - iconSize) / 2
        val icon = icons[k]
        if (icon != null) {
            icon.setBounds(
                (cx - iconSize / 2).toInt(), iTop.toInt(),
                (cx + iconSize / 2).toInt(), (iTop + iconSize).toInt()
            )
            icon.draw(canvas)
        } else {
            // 操作の項目：細い枠の中に記号
            tmp.set(cx - iconSize / 2, iTop, cx + iconSize / 2, iTop + iconSize)
            line.color = if (on) cTorinoko else cRikyu; line.strokeWidth = 1f * d
            canvas.drawRoundRect(tmp, iconSize / 2, iconSize / 2, line)
            glyph.color = cTorinoko; glyph.textSize = iconSize * 0.45f
            canvas.drawText(DockItems.action(k)?.glyph ?: "?", cx, tmp.centerY() + glyph.textSize * 0.35f, glyph)
        }
        if (showLabels) {
            text.color = if (on) cTorinoko else cKame
            text.textSize = 10f * d * k0.coerceIn(0.8f, 1.3f)
            val label = TextUtils.ellipsize(labels[k] ?: "", text, cell - 8f * d, TextUtils.TruncateAt.END)
            canvas.drawText(label, 0, label.length, cx, iTop + iconSize + text.textSize * 1.4f, text)
        }
    }
}
