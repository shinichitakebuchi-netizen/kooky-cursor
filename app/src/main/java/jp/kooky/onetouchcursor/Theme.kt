package jp.kooky.onetouchcursor

import android.content.Context
import android.content.res.Configuration

/**
 * 画面の色。メインはダーク＝墨（#313131）、ライト＝白練（#F3F3F2）。
 * 差し色は紅緋、補足と罫線は甕覗・利休鼠（どちらのテーマでも伝統色の中から）。
 */
object Theme {
    const val DARK = 0
    const val LIGHT = 1
    const val SYSTEM = 2
    val names = listOf("墨", "白練", "移ろい")   // 移ろい＝端末のダーク/ライト設定に合わせて移り変わる
    val order = listOf(DARK, LIGHT, SYSTEM)

    data class Pal(
        val bg: Int,       // 背景
        val text: Int,     // 文字（主）
        val sub: Int,      // 文字（補足）
        val line: Int,     // 罫線・枠
        val accent: Int,   // 見出し・強調
        val ok: Int,       // 稼働中
        val area: Int,     // 一段沈んだ面（ドックの置き場など）
        val raised: Int,   // 持ち上げた行など
    )

    private const val SUMI = 0xFF313131.toInt()       // 墨
    private const val SHIRONERI = 0xFFF3F3F2.toInt()  // 白練
    private const val KAME = 0xFFC6C2B6.toInt()       // 甕覗
    private const val RIKYU = 0xFF656255.toInt()      // 利休鼠
    private const val BENIHI = 0xFFF35336.toInt()     // 紅緋
    private const val WAKATAKE = 0xFF6B9362.toInt()   // 若竹色

    val darkPal = Pal(
        bg = SUMI, text = SHIRONERI, sub = 0xB3C6C2B6.toInt(), line = RIKYU,
        accent = BENIHI, ok = WAKATAKE, area = 0xFF262626.toInt(), raised = 0xFF3E3E3E.toInt(),
    )
    val lightPal = Pal(
        bg = SHIRONERI, text = SUMI, sub = RIKYU, line = KAME,
        accent = BENIHI, ok = WAKATAKE, area = 0xFFE7E7E5.toInt(), raised = 0xFFFFFFFF.toInt(),
    )

    fun isDark(ctx: Context, mode: Int): Boolean = when (mode) {
        DARK -> true
        LIGHT -> false
        else -> (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }

    fun pal(ctx: Context, mode: Int): Pal = if (isDark(ctx, mode)) darkPal else lightPal
}
