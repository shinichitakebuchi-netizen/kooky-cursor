package jp.kooky.onetouchcursor

import android.content.Context
import android.content.SharedPreferences

/** 設定値。SharedPreferences に保存し、サービス側は変更を即時反映する。 */
data class Settings(
    val rightHand: Boolean = true,      // 操作位置 右/左
    val padWidthDp: Int = 220,          // 操作エリア 幅
    val padHeightDp: Int = 150,         // 操作エリア 高さ
    val padBottomDp: Int = 56,          // 操作エリアの位置（下からの距離）
    val padSideDp: Int = 12,            // 操作エリアの位置（端からの距離）
    val speed: Float = 2.0f,            // カーソル速度 0.5〜5.0
    val cursorSize: Int = 1,            // 0=小 1=中 2=大
    val cursorColor: Int = 0xFFFF8A2B.toInt(),
    val transparency: Int = 30,         // 0%=不透明 / 100%=完全に透明
    val vibrate: Boolean = true,
    val dragThresholdDp: Int = 10,      // この距離以上動いたら「移動した」とみなす
    val doubleClick: Boolean = true,
    val longPress: Boolean = true,
    val padVisible: Boolean = true,
) {
    fun save(prefs: SharedPreferences) {
        prefs.edit()
            .putBoolean(K_RIGHT, rightHand)
            .putInt(K_PAD_W, padWidthDp)
            .putInt(K_PAD_H, padHeightDp)
            .putInt(K_PAD_BOTTOM, padBottomDp)
            .putInt(K_PAD_SIDE, padSideDp)
            .putFloat(K_SPEED, speed)
            .putInt(K_SIZE, cursorSize)
            .putInt(K_COLOR, cursorColor)
            .putInt(K_TRANS, transparency)
            .putBoolean(K_VIB, vibrate)
            .putInt(K_DRAG, dragThresholdDp)
            .putBoolean(K_DOUBLE, doubleClick)
            .putBoolean(K_LONG, longPress)
            .putBoolean(K_VISIBLE, padVisible)
            .apply()
    }

    companion object {
        const val PREFS_NAME = "onetouch_settings"
        const val K_RIGHT = "right_hand"
        const val K_PAD_W = "pad_w"
        const val K_PAD_H = "pad_h"
        const val K_PAD_BOTTOM = "pad_bottom"
        const val K_PAD_SIDE = "pad_side"
        const val K_SPEED = "speed"
        const val K_SIZE = "cursor_size"
        const val K_COLOR = "cursor_color"
        const val K_TRANS = "transparency"
        const val K_VIB = "vibrate"
        const val K_DRAG = "drag_threshold"
        const val K_DOUBLE = "double_click"
        const val K_LONG = "long_press"
        const val K_VISIBLE = "pad_visible"

        fun prefs(ctx: Context): SharedPreferences =
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        fun load(p: SharedPreferences): Settings {
            val d = Settings()
            return Settings(
                rightHand = p.getBoolean(K_RIGHT, d.rightHand),
                padWidthDp = p.getInt(K_PAD_W, d.padWidthDp),
                padHeightDp = p.getInt(K_PAD_H, d.padHeightDp),
                padBottomDp = p.getInt(K_PAD_BOTTOM, d.padBottomDp),
                padSideDp = p.getInt(K_PAD_SIDE, d.padSideDp),
                speed = p.getFloat(K_SPEED, d.speed),
                cursorSize = p.getInt(K_SIZE, d.cursorSize),
                cursorColor = p.getInt(K_COLOR, d.cursorColor),
                transparency = p.getInt(K_TRANS, d.transparency),
                vibrate = p.getBoolean(K_VIB, d.vibrate),
                dragThresholdDp = p.getInt(K_DRAG, d.dragThresholdDp),
                doubleClick = p.getBoolean(K_DOUBLE, d.doubleClick),
                longPress = p.getBoolean(K_LONG, d.longPress),
                padVisible = p.getBoolean(K_VISIBLE, d.padVisible),
            )
        }
    }
}
