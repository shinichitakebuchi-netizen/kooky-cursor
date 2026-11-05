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
 * ドック（アプリの並んだ板）。全画面・タッチ不可のオーバーレイに描き、
 * 指の位置（スクリーン座標）で、どの項目の上にいるかを判定する。
 */
class DockView(context: Context) : View(context) {

    private val d = resources.displayMetrics.density
    private val loc = IntArray(2)

    // 配色（伝統色）
    private val cAi = 0xFF264348.toInt()        // 藍色
    private val cTorinoko = 0xFFE2BE9F.toInt()  // 鳥の子色
    private val cKame = 0xFFC6C2B6.toInt()      // 甕覗
    private val cRikyu = 0xFF656255.toInt()     // 利休鼠
    private val cBenihi = 0xFFF35336.toInt()    // 紅緋

    private var keys: List<String> = emptyList()
    private val labels = HashMap<String, String>()
    private val icons = HashMap<String, Drawable?>()
    var cols = 4
    var rows = 4
    var cellDp = 76f          // 1マスの大きさ
    var showLabels = true     // アプリ名を表示するか

    private val panel = RectF()
    private var hovered = -1

    private val cell get() = cellDp * d
    private val pad get() = 12f * d

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
        val shownRows = if (keys.isEmpty()) 1 else ((keys.size + cols - 1) / cols).coerceAtMost(rows)
        val w = cols * cell + pad * 2
        val h = shownRows * cell + pad * 2
        // 指から少し内側に離して置く（指はドックの外から入っていく）
        val gapFromEdge = 36f * d
        val left = if (fromRight) screenW - gapFromEdge - w else gapFromEdge
        val top = (rawY - h / 2).coerceIn(24f * d, (screenH - h - 24f * d).coerceAtLeast(24f * d))
        panel.set(left.coerceAtLeast(8f * d), top, left.coerceAtLeast(8f * d) + w, top + h)
        hovered = -1
        visibility = VISIBLE
        invalidate()
    }

    fun hide() {
        visibility = GONE
        hovered = -1
    }

    /** 指の位置にある項目の番号（なければ -1） */
    fun hit(rawX: Float, rawY: Float): Int {
        val x = rawX - panel.left - pad
        val y = rawY - panel.top - pad
        if (x < 0 || y < 0) return -1
        val c = (x / cell).toInt()
        val r = (y / cell).toInt()
        if (c >= cols || r >= rows) return -1
        val i = r * cols + c
        return if (i < keys.size) i else -1
    }

    /** 選択中を更新。変わったら true */
    fun setHovered(i: Int): Boolean {
        if (i == hovered) return false
        hovered = i
        invalidate()
        return true
    }

    fun keyAt(i: Int): String? = keys.getOrNull(i)

    /** 指が離れても選べるように、ドックの外側にいるかどうか */
    fun isOverPanel(rawX: Float, rawY: Float): Boolean {
        return panel.contains(rawX, rawY)
    }

    override fun onDraw(canvas: Canvas) {
        // panel はスクリーン座標なので、Viewの画面上の位置だけずらして描く
        getLocationOnScreen(loc)
        canvas.save()
        canvas.translate(-loc[0].toFloat(), -loc[1].toFloat())
        drawPanel(canvas)
        canvas.restore()
    }

    private fun drawPanel(canvas: Canvas) {
        // 板
        bg.color = cAi; bg.alpha = 245
        canvas.drawRoundRect(panel, 4f * d, 4f * d, bg)
        line.color = cRikyu; line.strokeWidth = 1f * d
        canvas.drawRoundRect(panel, 4f * d, 4f * d, line)

        if (keys.isEmpty()) {
            text.color = cKame; text.textSize = 12f * d
            canvas.drawText("設定の「ドック」で項目を追加", panel.centerX(), panel.centerY() + 4f * d, text)
            return
        }

        val k0 = cell / (76f * d)               // 標準(76dp)に対する倍率
        val iconSize = cell * (if (showLabels) 0.53f else 0.62f)
        keys.forEachIndexed { i, k ->
            val c = i % cols
            val r = i / cols
            val cx = panel.left + pad + c * cell + cell / 2
            val top = panel.top + pad + r * cell
            val on = i == hovered

            if (on) {
                tmp.set(cx - cell / 2 + 2f * d, top + 2f * d, cx + cell / 2 - 2f * d, top + cell - 2f * d)
                bg.color = cTorinoko; bg.alpha = 40
                canvas.drawRoundRect(tmp, 3f * d, 3f * d, bg)
                line.color = cBenihi; line.strokeWidth = 1f * d
                canvas.drawRoundRect(tmp, 3f * d, 3f * d, line)
            }

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
                val g = DockItems.action(k)?.glyph ?: "?"
                canvas.drawText(g, cx, tmp.centerY() + glyph.textSize * 0.35f, glyph)
            }

            if (showLabels) {
                text.color = if (on) cTorinoko else cKame
                text.textSize = 10f * d * k0.coerceIn(0.8f, 1.3f)
                val label = TextUtils.ellipsize(labels[k] ?: "", text, cell - 8f * d, TextUtils.TruncateAt.END)
                canvas.drawText(label, 0, label.length, cx, iTop + iconSize + text.textSize * 1.4f, text)
            }
        }
    }
}
