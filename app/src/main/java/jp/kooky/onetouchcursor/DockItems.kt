package jp.kooky.onetouchcursor

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable

/**
 * ドックに置く項目。設定には文字列キーで保存する。
 *  - "app:<パッケージ/アクティビティ>" … アプリ
 *  - "act:<名前>" ………………………… 戻る・ホームなどの操作
 */
object DockItems {

    data class Action(val id: String, val label: String, val glyph: String, val global: Int)

    val actions = listOf(
        Action("back", "戻る", "←", AccessibilityService.GLOBAL_ACTION_BACK),
        Action("home", "ホーム", "⌂", AccessibilityService.GLOBAL_ACTION_HOME),
        Action("recents", "最近", "▤", AccessibilityService.GLOBAL_ACTION_RECENTS),
        Action("notifications", "通知", "▼", AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS),
        Action("quick", "設定パネル", "≡", AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS),
        Action("screenshot", "スクショ", "◫", AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT),
        Action("lock", "画面ロック", "◉", AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN),
    )

    const val DEFAULT = "act:back\nact:home\nact:recents\nact:notifications"

    fun parse(s: String): List<String> = s.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
    fun join(list: List<String>): String = list.joinToString("\n")

    fun appKey(cn: ComponentName) = "app:" + cn.flattenToString()
    fun actionKey(id: String) = "act:$id"

    fun action(key: String): Action? =
        if (key.startsWith("act:")) actions.firstOrNull { it.id == key.removePrefix("act:") } else null

    private fun component(key: String): ComponentName? =
        if (key.startsWith("app:")) ComponentName.unflattenFromString(key.removePrefix("app:")) else null

    fun label(ctx: Context, key: String): String? {
        action(key)?.let { return it.label }
        val cn = component(key) ?: return null
        return runCatching {
            ctx.packageManager.getActivityInfo(cn, 0).loadLabel(ctx.packageManager).toString()
        }.getOrNull()
    }

    /** アプリのアイコン（操作の項目や、アンインストール済みのアプリは null） */
    fun icon(ctx: Context, key: String): Drawable? {
        val cn = component(key) ?: return null
        return runCatching { ctx.packageManager.getActivityIcon(cn) }.getOrNull()
    }

    /** 実行。成功したら true */
    fun run(service: AccessibilityService, key: String): Boolean {
        action(key)?.let { return service.performGlobalAction(it.global) }
        val cn = component(key) ?: return false
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(cn)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return runCatching { service.startActivity(intent); true }.getOrDefault(false)
    }
}
