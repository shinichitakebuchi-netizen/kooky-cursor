package jp.kooky.onetouchcursor

import android.content.Context
import android.content.SharedPreferences

/** 設定値。SharedPreferences に保存し、サービス側は変更を即時反映する。 */
data class Settings(
    // ── トリガー ──
    val edgeMode: Int = EDGE_RIGHT,       // 0=左端 / 1=右端 / 2=両方
    val left: Bar = Bar(),                // 左端の帯
    val right: Bar = Bar(),               // 右端の帯
    val holdMs: Int = 250,                // 長押し判定の時間
    val enabled: Boolean = true,          // トリガー表示 ON/OFF
    val showArea: Boolean = true,         // 触れる範囲を常にうっすら表示

    // ── カーソル ──
    val cursorSize: Int = 1,              // 0=小 1=中 2=大
    val cursorColor: Int = 0xFFF35336.toInt(),
    val cursorShape: Int = 0,             // CursorShapes の番号
    // 左右それぞれの帯から出したときの、カーソルの速さと出てくる位置
    val leftCursor: CursorSide = CursorSide(),
    val rightCursor: CursorSide = CursorSide(),

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
            .putInt(K_HOLD, holdMs)
            .putBoolean(K_ENABLED, enabled)
            .putBoolean(K_AREA, showArea)
            .putInt(K_SIZE, cursorSize)
            .putInt(K_COLOR, cursorColor)
            .putInt(K_SHAPE, cursorShape)
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
            .also { e ->
                left.save(e, "L_"); right.save(e, "R_")
                leftCursor.save(e, "LC_"); rightCursor.save(e, "RC_")
            }
            .apply()
    }

    fun bar(rightSide: Boolean): Bar = if (rightSide) right else left
    fun withBar(rightSide: Boolean, b: Bar): Settings = if (rightSide) copy(right = b) else copy(left = b)
    fun cursor(rightSide: Boolean): CursorSide = if (rightSide) rightCursor else leftCursor
    fun withCursor(rightSide: Boolean, c: CursorSide): Settings =
        if (rightSide) copy(rightCursor = c) else copy(leftCursor = c)

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
                left = Bar.load(p, "L_"),
                right = Bar.load(p, "R_"),
                leftCursor = CursorSide.load(p, "LC_"),
                rightCursor = CursorSide.load(p, "RC_"),
                holdMs = p.getInt(K_HOLD, d.holdMs),
                enabled = p.getBoolean(K_ENABLED, d.enabled),
                showArea = p.getBoolean(K_AREA, d.showArea),
                cursorSize = p.getInt(K_SIZE, d.cursorSize),
                cursorColor = p.getInt(K_COLOR, d.cursorColor),
                cursorShape = p.getInt(K_SHAPE, d.cursorShape),
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

/** 片側の帯の設定。color は customColor=true のときだけ使う（false なら形に合わせた色） */
data class Bar(
    val style: Int = BarStyles.LINE,
    val customColor: Boolean = false,
    val color: Int = BarStyles.defaultColor(BarStyles.LINE),
    val posPercent: Int = 55,             // 帯の中心の高さ（上端=0% / 下端=100%）
    val lengthDp: Int = 180,
    val widthDp: Int = 20,                // 触れる幅
    val transparency: Int = 40,           // 0%=不透明 / 100%=ほぼ透明
) {
    val shownColor: Int get() = if (customColor) color else BarStyles.defaultColor(style)

    fun save(e: SharedPreferences.Editor, k: String) {
        e.putInt(k + "style", style)
            .putBoolean(k + "custom", customColor)
            .putInt(k + "color", color)
            .putInt(k + "pos", posPercent)
            .putInt(k + "len", lengthDp)
            .putInt(k + "width", widthDp)
            .putInt(k + "trans", transparency)
    }

    companion object {
        /** 左右別の設定が無ければ、前の共通の設定から引き継ぐ（色は形の色に戻す） */
        fun load(p: SharedPreferences, k: String): Bar {
            val d = Bar()
            return Bar(
                style = p.getInt(k + "style", p.getInt(Settings.K_BAR, d.style)),
                customColor = p.getBoolean(k + "custom", false),
                color = p.getInt(k + "color", d.color),
                posPercent = p.getInt(k + "pos", p.getInt(Settings.K_POS, d.posPercent)),
                lengthDp = p.getInt(k + "len", p.getInt(Settings.K_LEN, d.lengthDp)),
                widthDp = p.getInt(k + "width", p.getInt(Settings.K_WIDTH, d.widthDp)),
                transparency = p.getInt(k + "trans", p.getInt(Settings.K_TTRANS, d.transparency)),
            )
        }
    }
}

/** 片側の帯から出したときのカーソル（速さ・出てくる位置） */
data class CursorSide(
    val speed: Float = 2.5f,              // 0.5〜5.0
    val startFixed: Boolean = false,      // false=指の近く / true=画面の決まった位置
    val offsetXDp: Int = 70,              // 指の近く：指から内側へ
    val offsetYDp: Int = 40,              // 指の近く：指から上へ（マイナスで下）
    val xPercent: Int = 50,               // 決まった位置：左端=0% / 右端=100%
    val yPercent: Int = 40,               // 決まった位置：上端=0% / 下端=100%
) {
    fun save(e: SharedPreferences.Editor, k: String) {
        e.putFloat(k + "speed", speed)
            .putBoolean(k + "fixed", startFixed)
            .putInt(k + "ox", offsetXDp)
            .putInt(k + "oy", offsetYDp)
            .putInt(k + "xp", xPercent)
            .putInt(k + "yp", yPercent)
    }

    companion object {
        /** 左右別の設定が無ければ、前の共通の設定から引き継ぐ */
        fun load(p: SharedPreferences, k: String): CursorSide {
            val d = CursorSide()
            return CursorSide(
                speed = p.getFloat(k + "speed", p.getFloat(Settings.K_SPEED, d.speed)),
                startFixed = p.getBoolean(k + "fixed", p.getBoolean(Settings.K_SFIXED, d.startFixed)),
                offsetXDp = p.getInt(k + "ox", p.getInt(Settings.K_SOX, d.offsetXDp)),
                offsetYDp = p.getInt(k + "oy", p.getInt(Settings.K_SOY, d.offsetYDp)),
                xPercent = p.getInt(k + "xp", p.getInt(Settings.K_SXP, d.xPercent)),
                yPercent = p.getInt(k + "yp", p.getInt(Settings.K_SYP, d.yPercent)),
            )
        }
    }
}
