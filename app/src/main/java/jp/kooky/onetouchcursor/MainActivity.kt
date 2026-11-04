package jp.kooky.onetouchcursor

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Navy = Color(0xFF0F1626)
private val NavyCard = Color(0xFF18233A)
private val Orange = Color(0xFFFF8A2B)
private val Blue = Color(0xFF3D8BFF)
private val TextSub = Color(0xFF9AA6BF)

private const val RELEASES_URL =
    "https://github.com/shinichitakebuchi-netizen/kooky-cursor/releases/latest"

class MainActivity : ComponentActivity() {

    private val serviceOn = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Settings.prefs(this)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Orange,
                    secondary = Blue,
                    background = Navy,
                    surface = NavyCard,
                    onPrimary = Color.Black,
                )
            ) {
                var st by remember { mutableStateOf(Settings.load(prefs)) }
                val update: (Settings) -> Unit = { n -> st = n; n.save(prefs) }
                Screen(
                    st = st,
                    update = update,
                    serviceOn = serviceOn.value,
                    openA11y = {
                        startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    openUpdate = {
                        startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_URL))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    version = runCatching {
                        packageManager.getPackageInfo(packageName, 0).versionName
                    }.getOrNull() ?: "",
                    openAppInfo = {
                        startActivity(
                            Intent(
                                AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", packageName, null)
                            )
                        )
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        serviceOn.value = isServiceEnabled()
        CursorService.setPreview(true)   // 設定画面を開いている間は触れる範囲をはっきり表示
    }

    override fun onPause() {
        CursorService.setPreview(false)
        super.onPause()
    }

    private fun isServiceEnabled(): Boolean {
        val am = getSystemService(AccessibilityManager::class.java) ?: return false
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == packageName }
    }
}

@Composable
private fun Screen(
    st: Settings,
    update: (Settings) -> Unit,
    serviceOn: Boolean,
    openA11y: () -> Unit,
    openAppInfo: () -> Unit,
    openUpdate: () -> Unit,
    version: String,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Navy)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("OneTouch Cursor", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("v$version", color = TextSub, fontSize = 12.sp)
            }
            Button(onClick = openUpdate) { Text("UPDATE") }
        }
        Text("画面端の帯を長押し→スワイプでカーソル、離した位置をタップ", color = TextSub, fontSize = 13.sp)

        // ── 状態 ──
        Card(title = "サービスの状態") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(10.dp).clip(CircleShape)
                        .background(if (serviceOn) Color(0xFF3DDC84) else Color(0xFFFF5A5A))
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (serviceOn) "動作中" else "停止中（ユーザー補助で ON にしてください）",
                    color = Color.White
                )
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = openA11y, modifier = Modifier.fillMaxWidth()) {
                Text("ユーザー補助の設定を開く")
            }
            if (!serviceOn) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "ONにできない（グレーアウトする）場合：\n" +
                        "① 下のボタンでアプリ情報を開く\n" +
                        "② 右上「︙」→「制限付き設定を許可」\n" +
                        "③ もう一度ユーザー補助で ON",
                    color = TextSub, fontSize = 12.sp, lineHeight = 18.sp
                )
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = openAppInfo, modifier = Modifier.fillMaxWidth()) {
                    Text("アプリ情報を開く")
                }
            }
        }

        // ── トリガー ──
        Card(title = "トリガー（画面端の帯）") {
            SwitchRow("トリガーを表示", st.enabled) { update(st.copy(enabled = it)) }
            Label("置く場所")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice("左端", !st.rightEdge) { update(st.copy(rightEdge = false)) }
                Choice("右端", st.rightEdge) { update(st.copy(rightEdge = true)) }
            }
            SliderRow(
                "上下の位置（0%=上端 / 100%=下端）",
                st.triggerPosPercent.toFloat(), 0f..100f, "${st.triggerPosPercent}%"
            ) { update(st.copy(triggerPosPercent = it.toInt())) }
            SliderRow("長さ", st.triggerLengthDp.toFloat(), 60f..500f, "${st.triggerLengthDp}dp") {
                update(st.copy(triggerLengthDp = it.toInt()))
            }
            SwitchRow("触れる範囲を常にうっすら表示", st.showArea) { update(st.copy(showArea = it)) }
            Text(
                "※ この設定画面を開いている間は、触れる範囲を枠付きで濃く表示します",
                color = TextSub, fontSize = 12.sp
            )
            SliderRow("太さ（触れる幅）", st.triggerWidthDp.toFloat(), 8f..48f, "${st.triggerWidthDp}dp") {
                update(st.copy(triggerWidthDp = it.toInt()))
            }
            SliderRow(
                "透明度（0%=不透明 / 100%=ほぼ透明）",
                st.triggerTransparency.toFloat(), 0f..100f, "${st.triggerTransparency}%"
            ) { update(st.copy(triggerTransparency = it.toInt())) }
            Label("色")
            ColorPicker(st.triggerColor) { update(st.copy(triggerColor = it)) }
            SliderRow("長押しの時間", st.holdMs.toFloat(), 100f..800f, "${st.holdMs}ms") {
                update(st.copy(holdMs = (it / 10).toInt() * 10))
            }
        }

        // ── カーソル ──
        Card(title = "カーソル") {
            SliderRow("速度", st.speed, 0.5f..5f, String.format("%.1f", st.speed)) {
                update(st.copy(speed = (it * 10).toInt() / 10f))
            }
            Label("サイズ")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("小", "中", "大").forEachIndexed { i, t ->
                    Choice(t, st.cursorSize == i) { update(st.copy(cursorSize = i)) }
                }
            }
            Label("色")
            ColorPicker(st.cursorColor) { update(st.copy(cursorColor = it)) }
        }

        // ── 操作 ──
        Card(title = "操作") {
            SwitchRow("振動", st.vibrate) { update(st.copy(vibrate = it)) }
            SwitchRow("トリガーに指を戻して離したらキャンセル", st.cancelOnReturn) {
                update(st.copy(cancelOnReturn = it))
            }
        }

        Card(title = "テストエリア") {
            Text("カーソルで下のボタンを押して動作確認", color = TextSub, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            TestButtons()
        }

        Card(title = "アップデート") {
            Text("現在のバージョン：$version", color = TextSub, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Button(onClick = openUpdate, modifier = Modifier.fillMaxWidth()) {
                Text("UPDATE（最新版をダウンロード）")
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "開いたページの app-debug.apk をダウンロード → 開いて上書きインストール",
                color = TextSub, fontSize = 12.sp
            )
        }

        Spacer(Modifier.height(40.dp))
    }
}

// ───────── 部品 ─────────

@Composable
private fun Card(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NavyCard)
            .padding(14.dp)
    ) {
        Text(title, color = Orange, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun Label(t: String) {
    Text(t, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: String,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(top = 6.dp)) {
        Row {
            Text(label, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(display, color = TextSub, fontSize = 13.sp)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun Choice(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Orange else Color(0xFF243150))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(text, color = if (selected) Color.Black else Color.White, fontSize = 13.sp)
    }
}

private val presetColors = listOf(
    0xFFFF8A2B, 0xFF3D8BFF, 0xFFFFFFFF, 0xFF3DDC84,
    0xFFFF4D6D, 0xFFFFD23F, 0xFFB46CFF, 0xFF22D3EE,
).map { it.toInt() }

@Composable
private fun ColorPicker(current: Int, onPick: (Int) -> Unit) {
    var hex by remember(current) {
        mutableStateOf(String.format("%06X", current and 0xFFFFFF))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        presetColors.forEach { c ->
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color(c))
                    .border(
                        if (c == current) 3.dp else 1.dp,
                        if (c == current) Color.White else Color(0x55FFFFFF),
                        CircleShape
                    )
                    .clickable { onPick(c) }
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = hex,
            onValueChange = { hex = it.uppercase().filter { ch -> ch in "0123456789ABCDEF" }.take(6) },
            label = { Text("自由入力（16進 RRGGBB）") },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Button(onClick = {
            if (hex.length == 6) onPick((0xFF000000 or hex.toLong(16)).toInt())
        }) { Text("適用") }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TestButtons() {
    var log by remember { mutableStateOf("まだ操作なし") }
    var count by remember { mutableIntStateOf(0) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        listOf("A", "B", "C").forEach { name ->
            Box(
                Modifier
                    .weight(1f)
                    .height(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF243150))
                    .combinedClickable(
                        onClick = { count++; log = "$name：タップ（$count）" },
                        onDoubleClick = { count++; log = "$name：ダブルタップ（$count）" },
                        onLongClick = { count++; log = "$name：長押し（$count）" },
                    ),
                contentAlignment = Alignment.Center
            ) { Text(name, color = Color.White, fontSize = 18.sp) }
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(log, color = Blue, fontSize = 14.sp)
}
