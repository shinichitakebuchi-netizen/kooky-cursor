package jp.kooky.onetouchcursor

import android.content.Context
import android.content.SharedPreferences

/** 設定値。SharedPreferences に保存し、サービス側は変更を即時反映する。 */
data class Settings(
    // ── トリガー ──
    val edgeMode: Int = EDGE_RIGHT,       // 0=左端 / 1=右端 / 2=両方
    val triggerPosPercent: Int = 55,      // トリガー中心の高さ（画面上端=0% / 下端=100%）
    val triggerLengthDp: Int = 180,       // トリガーの長さ
    val triggerWidthDp: Int = 20,         // トリガーの太さ（触れる幅）
    val triggerColor: Int = 0xFFF35336.toInt(),
    val barStyle: Int = 0,                // BarStyles の番号
    val triggerTransparency: Int = 40,    // 0%=不透明 / 100%=完全に透明
    val holdMs: Int = 250,                // 長押し判定の時間
    val enabled: Boolean = true,          // トリガー表示 ON/OFF
    val showArea: Boolean = true,         // 触れる範囲を常にうっすら表示

    // ── カーソル ──
    val speed: Float = 2.5f,              // 0.5〜5.0
    val cursorSize: Int = 1,              // 0=小 1=中 2=大
    val cursorColor: Int = 0xFFF35336.toInt(),
    val cursorShape: Int = 0,             // CursorShapes の番号
    // カーソルの初期位置
    val startFixed: Boolean = false,      // false=指の近く / true=画面の決まった位置
    val startOffsetXDp: Int = 70,         // 指の近く：指から内側へ
    val startOffsetYDp: Int = 40,         // 指の近く：指から上へ（マイナスで下）
    val startXPercent: Int = 50,          // 決まった位置：左端=0% / 右端=100%
    val startYPercent: Int = 40,          // 決まった位置：上端=0% / 下端=100%

    // ── ドック ──
    val dockEnabled: Boolean = true,
    val dockCols: Int = 4,
    val dockRows: Int = 4,
    val dockItems: String = DockItems.DEFAULT,
    val dockCellDp: Int = 76,             // ドックの1マスの大きさ
    val dockLabels: Boolean = true,       // アプリ名を表示
    val dockFixed: Boolean = false,       // 位置：true=決まった高さ / false=指の高さ
    val dockYPercent: Int = 50,           // 決まった高さ（ドックの中心、上端=0%）
    val dockGapDp: Int = 36,              // 端からの距離

    // ── 文字・色 ──
    val font: Int = 0,                    // AppFonts の番号
    val theme: Int = Theme.DARK,          // Theme の番号（ダーク／ライト／システム）

    // ── 操作 ──
    val vibrate: Boolean = true,
    val cancelOnReturn: Boolean = true,   // トリガー上に指を戻して離すとキャンセル
) {
    val leftOn get() = edgeMode == EDGE_LEFT || edgeMode == EDGE_BOTH
    val rightOn get() = edgeMode == EDGE_RIGHT || edgeMode == EDGE_BOTH

    fun save(prefs: SharedPreferences) {
        prefs.edit()
            .putInt(K_EDGE, edgeMode)
            .putInt(K_POS, triggerPosPercent)
            .putInt(K_LEN, triggerLengthDp)
            .putInt(K_WIDTH, triggerWidthDp)
            .putInt(K_TCOLOR, triggerColor)
            .putInt(K_BAR, barStyle)
            .putInt(K_TTRANS, triggerTransparency)
            .putInt(K_HOLD, holdMs)
            .putBoolean(K_ENABLED, enabled)
            .putBoolean(K_AREA, showArea)
            .putFloat(K_SPEED, speed)
            .putInt(K_SIZE, cursorSize)
            .putInt(K_COLOR, cursorColor)
            .putInt(K_SHAPE, cursorShape)
            .putBoolean(K_SFIXED, startFixed)
            .putInt(K_SOX, startOffsetXDp)
            .putInt(K_SOY, startOffsetYDp)
            .putInt(K_SXP, startXPercent)
            .putInt(K_SYP, startYPercent)
            .putBoolean(K_VIB, vibrate)
            .putInt(K_FONT, font)
            .putInt(K_THEME, theme)
            .putBoolean(K_DOCK, dockEnabled)
            .putInt(K_DCOLS, dockCols)
            .putInt(K_DROWS, dockRows)
            .putString(K_DITEMS, dockItems)
            .putInt(K_DCELL, dockCellDp)
            .putBoolean(K_DLABEL, dockLabels)
            .putBoolean(K_DFIXED, dockFixed)
            .putInt(K_DY, dockYPercent)
            .putInt(K_DGAP, dockGapDp)
            .putBoolean(K_CANCEL, cancelOnReturn)
            .apply()
    }

    companion object {
        const val PREFS_NAME = "onetouch_settings_v2"
        const val K_RIGHT = "right_edge"   // 旧設定（読み込みのみ）
        const val K_EDGE = "edge_mode"
        const val EDGE_LEFT = 0
        const val EDGE_RIGHT = 1
        const val EDGE_BOTH = 2
        const val K_POS = "trigger_pos"
        const val K_LEN = "trigger_len"
        const val K_WIDTH = "trigger_width"
        const val K_TCOLOR = "trigger_color"
        const val K_BAR = "bar_style"
        const val K_TTRANS = "trigger_trans"
        const val K_HOLD = "hold_ms"
        const val K_ENABLED = "enabled"
        const val K_AREA = "show_area"
        const val K_SPEED = "speed"
        const val K_SIZE = "cursor_size"
        const val K_COLOR = "cursor_color"
        const val K_SHAPE = "cursor_shape"
        const val K_SFIXED = "start_fixed"
        const val K_SOX = "start_offset_x"
        const val K_SOY = "start_offset_y"
        const val K_SXP = "start_x_pct"
        const val K_SYP = "start_y_pct"
        const val K_VIB = "vibrate"
        const val K_FONT = "font"
        const val K_THEME = "theme"
        const val K_DOCK = "dock_enabled"
        const val K_DCOLS = "dock_cols"
        const val K_DROWS = "dock_rows"
        const val K_DITEMS = "dock_items"
        const val K_DCELL = "dock_cell"
        const val K_DLABEL = "dock_labels"
        const val K_DFIXED = "dock_fixed"
        const val K_DY = "dock_y"
        const val K_DGAP = "dock_gap"
        const val K_CANCEL = "cancel_on_return"

        fun prefs(ctx: Context): SharedPreferences =
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        fun load(p: SharedPreferences): Settings {
            val d = Settings()
            return Settings(
                edgeMode = if (p.contains(K_EDGE)) p.getInt(K_EDGE, d.edgeMode)
                else if (p.getBoolean(K_RIGHT, true)) EDGE_RIGHT else EDGE_LEFT,
                triggerPosPercent = p.getInt(K_POS, d.triggerPosPercent),
                triggerLengthDp = p.getInt(K_LEN, d.triggerLengthDp),
                triggerWidthDp = p.getInt(K_WIDTH, d.triggerWidthDp),
                triggerColor = p.getInt(K_TCOLOR, d.triggerColor),
                barStyle = p.getInt(K_BAR, d.barStyle),
                triggerTransparency = p.getInt(K_TTRANS, d.triggerTransparency),
                holdMs = p.getInt(K_HOLD, d.holdMs),
                enabled = p.getBoolean(K_ENABLED, d.enabled),
                showArea = p.getBoolean(K_AREA, d.showArea),
                speed = p.getFloat(K_SPEED, d.speed),
                cursorSize = p.getInt(K_SIZE, d.cursorSize),
                cursorColor = p.getInt(K_COLOR, d.cursorColor),
                cursorShape = p.getInt(K_SHAPE, d.cursorShape),
                startFixed = p.getBoolean(K_SFIXED, d.startFixed),
                startOffsetXDp = p.getInt(K_SOX, d.startOffsetXDp),
                startOffsetYDp = p.getInt(K_SOY, d.startOffsetYDp),
                startXPercent = p.getInt(K_SXP, d.startXPercent),
                startYPercent = p.getInt(K_SYP, d.startYPercent),
                vibrate = p.getBoolean(K_VIB, d.vibrate),
                font = p.getInt(K_FONT, d.font),
                theme = p.getInt(K_THEME, d.theme),
                dockEnabled = p.getBoolean(K_DOCK, d.dockEnabled),
                dockCols = p.getInt(K_DCOLS, d.dockCols),
                dockRows = p.getInt(K_DROWS, d.dockRows),
                dockItems = p.getString(K_DITEMS, d.dockItems) ?: d.dockItems,
                dockCellDp = p.getInt(K_DCELL, d.dockCellDp),
                dockLabels = p.getBoolean(K_DLABEL, d.dockLabels),
                dockFixed = p.getBoolean(K_DFIXED, d.dockFixed),
                dockYPercent = p.getInt(K_DY, d.dockYPercent),
                dockGapDp = p.getInt(K_DGAP, d.dockGapDp),
                cancelOnReturn = p.getBoolean(K_CANCEL, d.cancelOnReturn),
            )
        }
    }
}
