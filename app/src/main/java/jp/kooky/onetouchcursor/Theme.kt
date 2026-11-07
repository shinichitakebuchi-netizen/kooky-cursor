package jp.kooky.onetouchcursor

import android.content.Context

/**
 * 画面の色（庵と同じ作り）。
 *  墨（ダーク）：背景 墨・文字 鳥の子色・補足 甕覗70%・罫線 利休鼠
 *  白練（ライト）：背景 白練・文字 藍色・補足 利休鼠・罫線 利休鼠
 *  移ろい：日の出から日の入りまで白練、それ以外は墨
 * 見出し・強調は「差し色」（18色から選ぶ。初期値は紅緋）。
 */
object Theme {
    const val DARK = 0
    const val LIGHT = 1
    const val SHIFT = 2
    val names = listOf("墨", "白練", "移ろい")
    val order = listOf(DARK, LIGHT, SHIFT)

    data class Pal(
        val bg: Int,       // 背景
        val text: Int,     // 文字（主）
        val sub: Int,      // 文字（補足）
        val line: Int,     // 罫線・枠
        val accent: Int,   // 見出し・強調（差し色）
        val ok: Int,       // 稼働中
        val area: Int,     // 一段沈んだ面（ドックの置き場など）
        val raised: Int,   // 持ち上げた行など
    )

    private const val SUMI = 0xFF313131.toInt()       // 墨
    private const val SHIRONERI = 0xFFF3F3F2.toInt()  // 白練
    private const val TORINOKO = 0xFFE2BE9F.toInt()   // 鳥の子色
    private const val AI = 0xFF264348.toInt()         // 藍色
    private const val KAME70 = 0xB3C6C2B6.toInt()     // 甕覗 70%
    private const val RIKYU = 0xFF656255.toInt()      // 利休鼠
    private const val WAKATAKE = 0xFF6B9362.toInt()   // 若竹色

    // 差し色の18色（庵と同じ並び・6色×3行）
    val PICK_NAMES = listOf(
        "一斤染", "鴇色", "韓紅花", "紅緋", "柿渋色", "鬱金色",
        "玉子色", "鳥の子色", "鶸色", "萌黄", "松葉色", "若竹色",
        "新橋色", "甕覗", "藍色", "利休鼠", "紫苑", "桔梗色",
    )
    val PICK = listOf(
        0xFFF08F90, 0xFFF3A696, 0xFFC91F37, 0xFFF35336, 0xFF934337, 0xFFE69B3A,
        0xFFFFA631, 0xFFE2BE9F, 0xFFBDA928, 0xFF5B8930, 0xFF454D32, 0xFF6B9362,
        0xFF006C7F, 0xFFC6C2B6, 0xFF264348, 0xFF656255, 0xFF976E9A, 0xFF5D3F6A,
    ).map { it.toInt() }
    const val DEFAULT_ACCENT = 3   // 紅緋

    fun accentColor(i: Int): Int = PICK.getOrElse(i) { PICK[DEFAULT_ACCENT] }

    fun isLight(mode: Int): Boolean = when (mode) {
        LIGHT -> true
        SHIFT -> ThemeClock.isLightNow()
        else -> false
    }

    fun palette(light: Boolean, accentIndex: Int): Pal {
        val acc = accentColor(accentIndex)
        return if (light) Pal(
            bg = SHIRONERI, text = AI, sub = RIKYU, line = RIKYU,
            accent = acc, ok = WAKATAKE, area = 0xFFE7E7E5.toInt(), raised = 0xFFFFFFFF.toInt(),
        ) else Pal(
            bg = SUMI, text = TORINOKO, sub = KAME70, line = RIKYU,
            accent = acc, ok = WAKATAKE, area = 0xFF262626.toInt(), raised = 0xFF3E3E3E.toInt(),
        )
    }

    val darkPal = palette(false, DEFAULT_ACCENT)

    @Suppress("UNUSED_PARAMETER")
    fun pal(ctx: Context, mode: Int, accentIndex: Int = DEFAULT_ACCENT): Pal =
        palette(isLight(mode), accentIndex)
}
