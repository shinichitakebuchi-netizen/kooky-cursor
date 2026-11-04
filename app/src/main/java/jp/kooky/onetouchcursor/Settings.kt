package jp.kooky.onetouchcursor

import android.content.Context
import android.content.SharedPreferences

/** 設定値。SharedPreferences に保存し、サービス側は変更を即時反映する。 */
data class Settings(
    // ── トリガー ──
    val rightEdge: Boolean = true,        // 右端 / 左端
    val triggerPosPercent: Int = 55,      // トリガー中心の高さ（画面上端=0% / 下端=100%）
    val triggerLengthDp: Int = 180,       // トリガーの長さ
    val triggerWidthDp: Int = 20,         // トリガーの太さ（触れる幅）
    val triggerColor: Int = 0xFFFF8A2B.toInt(),
    val triggerTransparency: Int = 40,    // 0%=不透明 / 100%=完全に透明
    val holdMs: Int = 250,                // 長押し判定の時間
    val enabled: Boolean = true,          // トリガー表示 ON/OFF
    val showArea: Boolean = true,         // 触れる範囲を常にうっすら表示

    // ── カーソル ──
    val speed: Float = 2.5f,              // 0.5〜5.0
    val cursorSize: Int = 1,              // 0=小 1=中 2=大
    val cursorColor: Int = 0xFFFF8A2B.toInt(),
    val cursorShape: Int = 0,             // CursorShapes の番号
    // カーソルの初期位置
    val startFixed: Boolean = false,      // false=指の近く / true=画面の決まった位置
    val startOffsetXDp: Int = 70,         // 指の近く：指から内側へ
    val startOffsetYDp: Int = 40,         // 指の近く：指から上へ（マイナスで下）
    val startXPercent: Int = 50,          // 決まった位置：左端=0% / 右端=100%
    val startYPercent: Int = 40,          // 決まった位置：上端=0% / 下端=100%

    // ── 操作 ──
    val vibrate: Boolean = true,
    val cancelOnReturn: Boolean = true,   // トリガー上に指を戻して離すとキャンセル
) {
    fun save(prefs: SharedPreferences) {
        prefs.edit()
            .putBoolean(K_RIGHT, rightEdge)
            .putInt(K_POS, triggerPosPercent)
            .putInt(K_LEN, triggerLengthDp)
            .putInt(K_WIDTH, triggerWidthDp)
            .putInt(K_TCOLOR, triggerColor)
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
            .putBoolean(K_CANCEL, cancelOnReturn)
            .apply()
    }

    companion object {
        const val PREFS_NAME = "onetouch_settings_v2"
        const val K_RIGHT = "right_edge"
        const val K_POS = "trigger_pos"
        const val K_LEN = "trigger_len"
        const val K_WIDTH = "trigger_width"
        const val K_TCOLOR = "trigger_color"
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
        const val K_CANCEL = "cancel_on_return"

        fun prefs(ctx: Context): SharedPreferences =
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        fun load(p: SharedPreferences): Settings {
            val d = Settings()
            return Settings(
                rightEdge = p.getBoolean(K_RIGHT, d.rightEdge),
                triggerPosPercent = p.getInt(K_POS, d.triggerPosPercent),
                triggerLengthDp = p.getInt(K_LEN, d.triggerLengthDp),
                triggerWidthDp = p.getInt(K_WIDTH, d.triggerWidthDp),
                triggerColor = p.getInt(K_TCOLOR, d.triggerColor),
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
                cancelOnReturn = p.getBoolean(K_CANCEL, d.cancelOnReturn),
            )
        }
    }
}
