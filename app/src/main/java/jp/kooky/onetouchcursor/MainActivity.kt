package jp.kooky.onetouchcursor

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ───────── 色（墨・生成り・朱） ─────────
private val Sumi = Color(0xFF1E2024)       // 背景
private val Kinari = Color(0xFFECE7DC)     // 文字
private val Usuzumi = Color(0xFF8C8F96)    // 補足の文字
private val Line = Color(0xFF3A3D44)       // 罫線
private val Shu = Color(0xFFE0632F)        // 朱（見出し・強調）

private val Mincho = FontFamily.Serif      // 端末の明朝体（Noto Serif CJK）

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
                    primary = Kinari,
                    background = Sumi,
                    surface = Sumi,
                    onPrimary = Sumi,
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
            .background(Sumi)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp)
    ) {
        // ── 表題 ──
        Spacer(Modifier.height(36.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("縁", color = Kinari, fontSize = 44.sp, fontFamily = Mincho, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(12.dp))
            Text("へり", color = Usuzumi, fontSize = 13.sp, letterSpacing = 3.sp,
                modifier = Modifier.padding(bottom = 8.dp))
            Spacer(Modifier.weight(1f))
            Text(
                if (serviceOn) "稼働中" else "停止中",
                color = Usuzumi, fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier.padding(bottom = 13.dp).size(8.dp).clip(CircleShape)
                    .background(if (serviceOn) Color(0xFF3DA35D) else Shu)
            )
        }
        Spacer(Modifier.height(10.dp))
        Note("画面の端を長押しして、そのまま指を滑らせるとカーソルが出ます。指を離した場所を押します。")

        // ── 停止中の案内 ──
        if (!serviceOn) {
            Section("はじめに")
            Note(
                "ユーザー補助で「縁」を入にしてください。\n" +
                    "入にできないときは、アプリ情報の右上「︙」から「制限付き設定を許可」を選んでから、もう一度お試しください。"
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Choice("ユーザー補助を開く", true, onClick = openA11y)
                Choice("アプリ情報を開く", false, onClick = openAppInfo)
            }
        }

        // ── 触れる帯 ──
        Section("触れる帯")
        ToggleRow("表示する", st.enabled) { update(st.copy(enabled = it)) }
        Label("置く場所")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Choice("左", st.edgeMode == Settings.EDGE_LEFT) { update(st.copy(edgeMode = Settings.EDGE_LEFT)) }
            Choice("右", st.edgeMode == Settings.EDGE_RIGHT) { update(st.copy(edgeMode = Settings.EDGE_RIGHT)) }
            Choice("両方", st.edgeMode == Settings.EDGE_BOTH) { update(st.copy(edgeMode = Settings.EDGE_BOTH)) }
        }
        Label("形")
        BarPicker(st.barStyle, st.triggerColor) { update(st.copy(barStyle = it)) }
        SliderRow("上下の位置", st.triggerPosPercent.toFloat(), 0f..100f, "${st.triggerPosPercent}%") {
            update(st.copy(triggerPosPercent = it.toInt()))
        }
        SliderRow("長さ", st.triggerLengthDp.toFloat(), 60f..500f, "${st.triggerLengthDp}dp") {
            update(st.copy(triggerLengthDp = it.toInt()))
        }
        SliderRow("太さ", st.triggerWidthDp.toFloat(), 8f..48f, "${st.triggerWidthDp}dp") {
            update(st.copy(triggerWidthDp = it.toInt()))
        }
        SliderRow("透明度", st.triggerTransparency.toFloat(), 0f..100f, "${st.triggerTransparency}%") {
            update(st.copy(triggerTransparency = it.toInt()))
        }
        SliderRow("長押しの時間", st.holdMs.toFloat(), 100f..800f, "${st.holdMs}ms") {
            update(st.copy(holdMs = (it / 10).toInt() * 10))
        }
        ToggleRow("触れる範囲をうっすら見せる", st.showArea) { update(st.copy(showArea = it)) }
        Label("色")
        ColorPicker(st.triggerColor) { update(st.copy(triggerColor = it)) }

        // ── カーソル ──
        Section("カーソル")
        SliderRow("速さ", st.speed, 0.5f..5f, String.format("%.1f", st.speed)) {
            update(st.copy(speed = (it * 10).toInt() / 10f))
        }
        Label("形")
        ShapePicker(st.cursorShape, st.cursorColor) { update(st.copy(cursorShape = it)) }
        Label("大きさ")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("小", "中", "大").forEachIndexed { i, t ->
                Choice(t, st.cursorSize == i) { update(st.copy(cursorSize = i)) }
            }
        }
        Label("色")
        ColorPicker(st.cursorColor) { update(st.copy(cursorColor = it)) }
        Label("出てくる位置")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Choice("指の近く", !st.startFixed) { update(st.copy(startFixed = false)) }
            Choice("決まった位置", st.startFixed) { update(st.copy(startFixed = true)) }
        }
        if (!st.startFixed) {
            SliderRow("指から内側へ", st.startOffsetXDp.toFloat(), 0f..300f, "${st.startOffsetXDp}dp") {
                update(st.copy(startOffsetXDp = it.toInt()))
            }
            SliderRow("指から上へ", st.startOffsetYDp.toFloat(), -200f..400f, "${st.startOffsetYDp}dp") {
                update(st.copy(startOffsetYDp = it.toInt()))
            }
        } else {
            SliderRow("横", st.startXPercent.toFloat(), 0f..100f, "${st.startXPercent}%") {
                update(st.copy(startXPercent = it.toInt()))
            }
            SliderRow("縦", st.startYPercent.toFloat(), 0f..100f, "${st.startYPercent}%") {
                update(st.copy(startYPercent = it.toInt()))
            }
        }

        // ── 操作 ──
        Section("操作")
        ToggleRow("振動", st.vibrate) { update(st.copy(vibrate = it)) }
        ToggleRow("帯に指を戻して離すと取り消し", st.cancelOnReturn) {
            update(st.copy(cancelOnReturn = it))
        }

        Spacer(Modifier.height(28.dp))
        Note("この画面を開いている間は、カーソルは出ますが指を離しても押しません。")

        // ── 更新 ──
        Spacer(Modifier.height(48.dp))
        Text(
            "更新する　v$version",
            color = Usuzumi, fontSize = 13.sp, letterSpacing = 2.sp,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clickable(onClick = openUpdate)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )
        Spacer(Modifier.height(32.dp))
    }
}

// ───────── 部品 ─────────

/** 見出し：明朝・朱・字間あり＋細い罫線 */
@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(40.dp))
    Text(title, color = Shu, fontSize = 15.sp, fontFamily = Mincho, letterSpacing = 4.sp)
    Spacer(Modifier.height(10.dp))
    HRule()
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun HRule(thin: Boolean = false) {
    Box(Modifier.fillMaxWidth().height(if (thin) 0.5.dp else 1.dp).background(Line))
}

@Composable
private fun Note(t: String) {
    Text(t, color = Usuzumi, fontSize = 12.sp, lineHeight = 20.sp)
}

@Composable
private fun Label(t: String) {
    Text(t, color = Kinari, fontSize = 15.sp, modifier = Modifier.padding(top = 18.dp, bottom = 10.dp))
}

/** 入／切：行全体を押して切り替える */
@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onChange(!checked) }
    ) {
        Row(Modifier.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Kinari, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text("入", color = if (checked) Shu else Line, fontSize = 14.sp)
            Text("　/　", color = Line, fontSize = 12.sp)
            Text("切", color = if (checked) Line else Kinari, fontSize = 14.sp)
        }
        HRule(thin = true)
    }
}

/** 細い線と白い丸だけのスライダー */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: String,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(top = 16.dp)) {
        Row {
            Text(label, color = Kinari, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(display, color = Usuzumi, fontSize = 14.sp)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.height(36.dp),
            thumb = {
                Box(
                    Modifier
                        .size(16.dp)
                        .background(Sumi, CircleShape)
                        .border(1.5.dp, Kinari, CircleShape)
                )
            },
            track = { state ->
                val span = state.valueRange.endInclusive - state.valueRange.start
                val f = if (span == 0f) 0f else ((state.value - state.valueRange.start) / span).coerceIn(0f, 1f)
                Canvas(Modifier.fillMaxWidth().height(2.dp)) {
                    val y = size.height / 2
                    drawLine(Line, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
                    drawLine(Kinari, Offset(0f, y), Offset(size.width * f, y), strokeWidth = 2.dp.toPx())
                }
            }
        )
    }
}

/** 選択肢：選択中は塗り、それ以外は細い枠 */
@Composable
private fun Choice(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(2.dp)
    Box(
        Modifier
            .clip(shape)
            .then(
                if (selected) Modifier.background(Kinari, shape)
                else Modifier.border(1.dp, Line, shape)
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(text, color = if (selected) Sumi else Kinari, fontSize = 14.sp)
    }
}

@Composable
private fun BarPicker(current: Int, color: Int, onPick: (Int) -> Unit) {
    val items = BarStyles.names.indices.toList()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { i ->
                    val selected = i == current
                    Column(
                        Modifier
                            .weight(1f)
                            .border(1.dp, if (selected) Kinari else Line, RoundedCornerShape(2.dp))
                            .clickable { onPick(i) }
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Canvas(Modifier.size(width = 30.dp, height = 70.dp)) {
                            drawIntoCanvas {
                                BarStyles.draw(
                                    it.nativeCanvas, size.width / 2, 0f, size.height,
                                    i, color, false, density
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            BarStyles.names[i], color = if (selected) Kinari else Usuzumi,
                            fontSize = 10.sp, maxLines = 1
                        )
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ShapePicker(current: Int, color: Int, onPick: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        CursorShapes.names.forEachIndexed { i, name ->
            val selected = i == current
            val shape = RoundedCornerShape(2.dp)
            Column(
                Modifier
                    .weight(1f)
                    .border(1.dp, if (selected) Kinari else Line, shape)
                    .clickable { onPick(i) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Canvas(Modifier.size(40.dp)) {
                    val r = 8.dp.toPx()
                    // 矢印は先端がタップ位置なので少し左上に寄せて全体を枠内に収める
                    val ox = if (i == CursorShapes.ARROW) size.width * 0.3f else size.width / 2
                    val oy = if (i == CursorShapes.ARROW) size.height * 0.18f else size.height / 2
                    drawIntoCanvas {
                        CursorShapes.draw(it.nativeCanvas, ox, oy, i, r, color, 255)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(name, color = if (selected) Kinari else Usuzumi, fontSize = 10.sp, maxLines = 1)
            }
        }
    }
}

/** 日本の伝統色から */
private val presetColors = listOf(
    "朱" to 0xFFE0632F, "柿" to 0xFFED8A3F, "山吹" to 0xFFF2B33D, "生成り" to 0xFFF3EEE3,
    "若竹" to 0xFF5BAE7A, "浅葱" to 0xFF3AA6B9, "藍" to 0xFF4A6FA5, "桜" to 0xFFF2A7B5,
).map { it.first to it.second.toInt() }

@Composable
private fun ColorPicker(current: Int, onPick: (Int) -> Unit) {
    var hex by remember(current) {
        mutableStateOf(String.format("%06X", current and 0xFFFFFF))
    }
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        presetColors.forEach { (name, c) ->
            val selected = c == current
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onPick(c) }
            ) {
                Box(
                    Modifier
                        .size(26.dp)
                        .border(1.dp, if (selected) Kinari else Color.Transparent, CircleShape)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(Color(c))
                )
                Spacer(Modifier.height(4.dp))
                Text(name, color = if (selected) Kinari else Usuzumi, fontSize = 9.sp, maxLines = 1)
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("#", color = Usuzumi, fontSize = 14.sp)
        Spacer(Modifier.width(6.dp))
        BasicTextField(
            value = hex,
            onValueChange = { hex = it.uppercase().filter { ch -> ch in "0123456789ABCDEF" }.take(6) },
            singleLine = true,
            textStyle = TextStyle(color = Kinari, fontSize = 14.sp, letterSpacing = 2.sp),
            cursorBrush = SolidColor(Kinari),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Column {
                    inner()
                    Spacer(Modifier.height(6.dp))
                    HRule(thin = true)
                }
            }
        )
        Spacer(Modifier.width(14.dp))
        Choice("適用", false) {
            if (hex.length == 6) onPick((0xFF000000 or hex.toLong(16)).toInt())
        }
    }
}
