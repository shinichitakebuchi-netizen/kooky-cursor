package jp.kooky.onetouchcursor

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.core.content.res.ResourcesCompat

/**
 * 画面の文字のフォント（3種類）。
 *  0 = 明朝（端末の標準） / 1 = Kaisei Tokumin / 2 = Yuji Syuku
 * Kaisei Tokumin・Yuji Syuku は SIL Open Font License 1.1（assets/licenses に全文）。
 */
object AppFonts {
    val names = listOf("明朝（標準）", "Kaisei Tokumin", "Yuji Syuku")

    private val kaisei = FontFamily(Font(R.font.kaisei_tokumin))
    private val yuji = FontFamily(Font(R.font.yuji_syuku))

    fun family(i: Int): FontFamily = when (i) {
        1 -> kaisei
        2 -> yuji
        else -> FontFamily.Serif
    }

    /** ドック（Canvasで描く文字）用 */
    fun typeface(ctx: Context, i: Int): Typeface = when (i) {
        1 -> ResourcesCompat.getFont(ctx, R.font.kaisei_tokumin)
        2 -> ResourcesCompat.getFont(ctx, R.font.yuji_syuku)
        else -> null
    } ?: Typeface.SERIF
}
