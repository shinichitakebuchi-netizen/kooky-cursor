package jp.kooky.onetouchcursor

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.PointerInputChange
import kotlin.math.roundToInt
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

// ───────── 色（日本の内装材料辞典「伝統色」掲載の色名から） ─────────
private val Sumi = Color(0xFF264348)       // 背景：藍色
private val Kinari = Color(0xFFE2BE9F)     // 文字：鳥の子色
private val Usuzumi = Color(0xB3C6C2B6)    // 補足の文字：甕覗（70%）
private val Line = Color(0xFF656255)       // 罫線：利休鼠
private val Shu = Color(0xFFF35336)        // 見出し・強調：紅緋
private val Wakatake = Color(0xFF6B9362)   // 稼働中：若竹色

/** 選んだフォント（設定の「文字」） */
private val LocalAppFont = staticCompositionLocalOf<FontFamily> { FontFamily.Serif }
private val Mincho: FontFamily
    @Composable @ReadOnlyComposable get() = LocalAppFont.current

private const val RELEASES_URL =
    "https://github.com/shinichitakebuchi-netizen/kooky-cursor/releases/latest"

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_EDIT_DOCK = "edit_dock"
    }

    private val serviceOn = mutableStateOf(false)
    /** 表示中の画面："main" / "editor"（ドックの中身） / "overview"（ドックの設置） / "picker"（項目の追加） */
    private val page = mutableStateOf("main")
    private var pickerBack = "main"

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_EDIT_DOCK, false)) page.value = "editor"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Settings.prefs(this)
        if (intent?.getBooleanExtra(EXTRA_EDIT_DOCK, false) == true) page.value = "editor"
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
                val fontFamily = AppFonts.family(st.font)
                CompositionLocalProvider(LocalAppFont provides fontFamily) {
                ProvideTextStyle(TextStyle(fontFamily = fontFamily)) {
                when (page.value) {
                    "picker" -> {
                        BackHandler { page.value = pickerBack }
                        DockPicker(st, update) { page.value = pickerBack }
                        return@ProvideTextStyle
                    }
                    "overview" -> {
                        BackHandler { page.value = "main" }
                        DockOverview(st, update) { page.value = "main" }
                        return@ProvideTextStyle
                    }
                    "editor" -> {
                        BackHandler { page.value = "main" }
                        DockEditor(
                            st, update,
                            openPicker = { pickerBack = "editor"; page.value = "picker" },
                            close = { page.value = "main" }
                        )
                        return@ProvideTextStyle
                    }
                }
                Screen(
                    openPicker = { pickerBack = "main"; page.value = "picker" },
                    openEditor = { page.value = "editor" },
                    openOverview = { page.value = "overview" },
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
    openPicker: () -> Unit,
    openEditor: () -> Unit,
    openOverview: () -> Unit,
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
                    .background(if (serviceOn) Wakatake else Shu)
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

        // ── ドック ──
        Section("ドック")
        Note("帯を長押しせずにそのまま滑らせるとドックが出ます。項目の上で指を離すと起動します。")
        ToggleRow("滑らせてドックを出す", st.dockEnabled) { update(st.copy(dockEnabled = it)) }
        Spacer(Modifier.height(14.dp))
        Choice("ドックを設置する（画面全体を見ながら位置を決める）", true, onClick = openOverview)
        Spacer(Modifier.height(10.dp))
        Choice("ドックの中身（アプリの追加・並び・大きさ）", false, onClick = openEditor)
        Spacer(Modifier.height(4.dp))
        Note("ドックの上の「⚙ 設定」で指を離すと、ドックの中身の画面が開きます。")

        // ── 文字 ──
        Section("文字")
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AppFonts.order.forEach { i ->
                val name = AppFonts.names[i]
                val sel = st.font == i
                Row(
                    Modifier
                        .fillMaxWidth()
                        .border(1.dp, if (sel) Kinari else Line, RoundedCornerShape(2.dp))
                        .clickable { update(st.copy(font = i)) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("縁　へり", color = Kinari, fontSize = 22.sp, fontFamily = AppFonts.family(i))
                    Spacer(Modifier.weight(1f))
                    Text(name, color = if (sel) Kinari else Usuzumi, fontSize = 12.sp, fontFamily = FontFamily.Default)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Note("Kaisei Tokumin・Yuji Syuku は SIL Open Font License 1.1 の無料フォントです。")

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

/** 日本の内装材料辞典「伝統色」掲載の色（数値は公開資料より。丁子色・葡萄色は数値未確認のため除外） */
private val presetColors = listOf(
    "一斤染" to 0xFFF08F90, "鴇色" to 0xFFF3A696, "韓紅花" to 0xFFC91F37,
    "紅緋" to 0xFFF35336, "柿渋色" to 0xFF934337, "鬱金色" to 0xFFE69B3A,
    "玉子色" to 0xFFFFA631, "鳥の子色" to 0xFFE2BE9F, "鶸色" to 0xFFBDA928,
    "萌黄" to 0xFF5B8930, "松葉色" to 0xFF454D32, "若竹色" to 0xFF6B9362,
    "新橋色" to 0xFF006C7F, "甕覗" to 0xFFC6C2B6, "藍色" to 0xFF264348,
    "利休鼠" to 0xFF656255, "紫苑" to 0xFF976E9A, "桔梗色" to 0xFF5D3F6A,
).map { it.first to it.second.toInt() }

@Composable
private fun ColorPicker(current: Int, onPick: (Int) -> Unit) {
    var hex by remember(current) {
        mutableStateOf(String.format("%06X", current and 0xFFFFFF))
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        presetColors.chunked(6).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { (name, c) ->
                    val selected = c == current
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f).clickable { onPick(c) }
                    ) {
                        Box(
                            Modifier
                                .size(28.dp)
                                .border(1.dp, if (selected) Kinari else Color.Transparent, CircleShape)
                                .padding(4.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .border(0.5.dp, Line, CircleShape)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(name, color = if (selected) Kinari else Usuzumi, fontSize = 9.sp, maxLines = 1)
                    }
                }
                repeat(6 - row.size) { Spacer(Modifier.weight(1f)) }
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

// ───────── ドックの項目 ─────────

/** 置く項目の一覧。頭に「行-列」を表示し、長押しで浮かせて上下に動かして並べ替える */
@Composable
private fun DockItemList(st: Settings, update: (Settings) -> Unit, openPicker: () -> Unit) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val items = DockItems.parse(st.dockItems)
    val cols = st.dockCols
    val cap = cols * st.dockRows
    val rowH = 52.dp
    val rowPx = with(LocalDensity.current) { rowH.toPx() }

    var dragIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val target = if (dragIndex >= 0)
        (dragIndex + (dragOffset / rowPx).roundToInt()).coerceIn(0, items.lastIndex) else -1

    Label("置く項目（${items.size} / $cap）")
    Note("長押しで持ち上げて、上下に動かすと並べ替えできます。番号は ドックの「行-列」です。")
    if (items.size > cap) Note("並びに入りきらない ${items.size - cap} 個（「—」）は表示されません。")
    Spacer(Modifier.height(8.dp))

    Column {
        items.forEachIndexed { i, key ->
            // 並べ替え後に何番目になるか（番号の表示用）
            val pos = when {
                dragIndex < 0 -> i
                i == dragIndex -> target
                dragIndex < target && i in (dragIndex + 1)..target -> i - 1
                dragIndex > target && i in target until dragIndex -> i + 1
                else -> i
            }
            val shift = when {
                dragIndex < 0 -> 0f
                i == dragIndex -> dragOffset
                else -> (pos - i) * rowPx
            }
            val lifted = i == dragIndex
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(rowH)
                    .zIndex(if (lifted) 1f else 0f)
                    .graphicsLayer { translationY = shift }
                    .background(if (lifted) Color(0xFF30525A) else Sumi)
                    .then(if (lifted) Modifier.border(1.dp, Kinari) else Modifier)
                    .pointerInput(key, i) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                dragIndex = i; dragOffset = 0f
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount.y
                            },
                            onDragEnd = {
                                val from = dragIndex
                                val to = (from + (dragOffset / rowPx).roundToInt()).coerceIn(0, items.lastIndex)
                                dragIndex = -1; dragOffset = 0f
                                if (from >= 0 && from != to) {
                                    val l = items.toMutableList(); l.add(to, l.removeAt(from))
                                    update(st.copy(dockItems = DockItems.join(l)))
                                }
                            },
                            onDragCancel = { dragIndex = -1; dragOffset = 0f }
                        )
                    }
            ) {
                Row(
                    Modifier.weight(1f).padding(horizontal = if (lifted) 8.dp else 0.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (pos < cap) "${pos / cols + 1}-${pos % cols + 1}" else "—",
                        color = if (pos < cap) Shu else Line,
                        fontSize = 12.sp, fontFamily = Mincho,
                        modifier = Modifier.width(34.dp)
                    )
                    ItemIcon(key, 26)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        DockItems.label(ctx, key) ?: "（見つかりません）",
                        color = Kinari, fontSize = 14.sp, maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    Text("外す", color = Usuzumi, fontSize = 12.sp,
                        modifier = Modifier.clickable {
                            update(st.copy(dockItems = DockItems.join(items - key)))
                        }.padding(horizontal = 8.dp, vertical = 6.dp))
                    Text("≡", color = Line, fontSize = 16.sp, modifier = Modifier.padding(start = 4.dp))
                }
                HRule(thin = true)
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    Choice("項目を追加する", false, onClick = openPicker)
}

@Composable
private fun ItemIcon(key: String, sizeDp: Int) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, key) {
        value = withContext(Dispatchers.IO) {
            DockItems.icon(ctx, key)?.toBitmap(96, 96)?.asImageBitmap()
        }
    }
    val b = bmp
    if (b != null) {
        Image(b, null, Modifier.size(sizeDp.dp))
    } else {
        Box(
            Modifier.size(sizeDp.dp).border(1.dp, Line, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(DockItems.action(key)?.glyph ?: "", color = Kinari, fontSize = (sizeDp / 2).sp)
        }
    }
}

private data class AppEntry(val key: String, val label: String)

/** 項目を選ぶ画面（操作＋インストール済みアプリ） */
@Composable
private fun DockPicker(st: Settings, update: (Settings) -> Unit, close: () -> Unit) {
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    val apps by produceState<List<AppEntry>?>(null) {
        value = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(intent, 0)
                .map {
                    val cn = android.content.ComponentName(it.activityInfo.packageName, it.activityInfo.name)
                    AppEntry(DockItems.appKey(cn), it.loadLabel(pm).toString())
                }
                .filter { it.key != "app:" + ctx.packageName + "/" + MainActivity::class.java.name }
                .sortedBy { it.label }
        }
    }
    val chosen = DockItems.parse(st.dockItems)
    fun toggle(key: String) {
        val l = if (key in chosen) chosen - key else chosen + key
        update(st.copy(dockItems = DockItems.join(l)))
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Sumi)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 28.dp)
    ) {
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("項目を追加", color = Kinari, fontSize = 24.sp, fontFamily = Mincho, modifier = Modifier.weight(1f))
            Choice("完了", true, onClick = close)
        }
        Spacer(Modifier.height(18.dp))
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = TextStyle(color = Kinari, fontSize = 15.sp),
            cursorBrush = SolidColor(Kinari),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Column {
                    Box {
                        if (query.isEmpty()) Text("さがす", color = Usuzumi, fontSize = 15.sp)
                        inner()
                    }
                    Spacer(Modifier.height(8.dp))
                    HRule()
                }
            }
        )
        val list = apps
        LazyColumn(Modifier.fillMaxSize()) {
            if (query.isEmpty()) {
                item { Section("操作") }
                items(DockItems.actions) { a ->
                    PickRow(DockItems.actionKey(a.id), a.label, DockItems.actionKey(a.id) in chosen) {
                        toggle(DockItems.actionKey(a.id))
                    }
                }
            }
            item { Section("アプリ") }
            if (list == null) {
                item { Note("読み込み中…") }
            } else {
                items(list.filter { query.isEmpty() || it.label.contains(query, ignoreCase = true) }, key = { it.key }) { a ->
                    PickRow(a.key, a.label, a.key in chosen) { toggle(a.key) }
                }
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

@Composable
private fun PickRow(key: String, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            ItemIcon(key, 32)
            Spacer(Modifier.width(14.dp))
            Text(label, color = Kinari, fontSize = 15.sp, maxLines = 1, modifier = Modifier.weight(1f))
            Text(if (selected) "置く" else "", color = Shu, fontSize = 13.sp)
        }
        HRule(thin = true)
    }
}

// ───────── ドックの中身の画面 ─────────

/**
 * 上にドック、下に設定。ドックの中身（アプリ）と見た目を整える画面。
 *  - 項目を長押し → 持ち上げて動かす → 離した所へ移動
 *  - 項目右上の「×」→ 外す ／ 帯の右「＋ 追加」→ 項目を追加
 * ドックを置く場所は「設置」（画面全体を縮小した図）で決める。
 */
@Composable
private fun DockEditor(st: Settings, update: (Settings) -> Unit, openPicker: () -> Unit, close: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val items = DockItems.parse(st.dockItems)
    var dockRef by remember { mutableStateOf<DockView?>(null) }
    var area by remember { mutableStateOf(androidx.compose.ui.unit.IntSize(0, 0)) }
    val stNow by rememberUpdatedState(st)

    Column(
        Modifier
            .fillMaxSize()
            .background(Sumi)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // ── 上：ドック ──
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF1B3136))
                .onSizeChanged { area = it }
        ) {
            AndroidView(
                factory = { ctx -> DockView(ctx).also { dockRef = it } },
                modifier = Modifier.fillMaxSize(),
                update = { v ->
                    v.editMode = true
                    v.fontIndex = st.font
                    v.cols = st.dockCols
                    v.rows = st.dockRows
                    v.cellDp = st.dockCellDp.toFloat()
                    v.showLabels = st.dockLabels
                    v.show(items, 0f, 0f, true, area.width, area.height)
                }
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(area) {
                        awaitEachGesture {
                            val dock = dockRef ?: return@awaitEachGesture
                            val down = awaitFirstDown()
                            val p0 = down.position
                            val hit0 = dock.hit(p0.x, p0.y)

                            // 長押しを待つ（その前に離したらタップ）
                            var up: PointerInputChange? = null
                            val released = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                up = waitForUpOrCancellation(); true
                            }
                            if (released == true) {
                                val u = up ?: return@awaitEachGesture
                                val rm = dock.hitRemove(u.position.x, u.position.y)
                                val h = dock.hit(u.position.x, u.position.y)
                                val cur = DockItems.parse(stNow.dockItems)
                                when {
                                    rm >= 0 -> update(stNow.copy(dockItems = DockItems.join(cur.filterIndexed { i, _ -> i != rm })))
                                    h == DockView.HIT_ADD -> openPicker()
                                }
                                return@awaitEachGesture
                            }

                            // 長押し → 項目を持ち上げる
                            if (hit0 < 0) return@awaitEachGesture
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            dock.dragFrom = hit0
                            dock.dragX = p0.x; dock.dragY = p0.y
                            dock.dropAt = hit0
                            dock.invalidate()
                            var lastPos = p0
                            while (true) {
                                val ev = awaitPointerEvent()
                                val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                                if (!c.pressed) { lastPos = c.position; break }
                                c.consume()
                                lastPos = c.position
                                dock.dragX = c.position.x; dock.dragY = c.position.y
                                val ci = dock.cellIndex(c.position.x, c.position.y)
                                if (ci >= 0) dock.dropAt = ci
                                dock.invalidate()
                            }
                            val from = dock.dragFrom
                            val ci = dock.cellIndex(lastPos.x, lastPos.y)
                            val to = (if (ci >= 0) ci else dock.dropAt).coerceIn(0, (items.size - 1).coerceAtLeast(0))
                            dock.dragFrom = -1; dock.dropAt = -1
                            dock.invalidate()
                            val cur = DockItems.parse(stNow.dockItems).toMutableList()
                            if (from in cur.indices && from != to) {
                                cur.add(to, cur.removeAt(from))
                                update(stNow.copy(dockItems = DockItems.join(cur)))
                            }
                        }
                    }
            )
        }

        // ── 下：設定 ──
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("ドックの中身", color = Kinari, fontSize = 18.sp, fontFamily = Mincho, modifier = Modifier.weight(1f))
                Choice("完了", true, onClick = close)
            }
            Spacer(Modifier.height(6.dp))
            Note("長押しで持ち上げて移動／「×」で外す／右上の「＋ 追加」で追加")
            Label("横の数")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(3, 4, 5).forEach { c -> Choice("$c", st.dockCols == c) { update(st.copy(dockCols = c)) } }
            }
            Label("縦の数（最大）")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(2, 3, 4, 5, 6).forEach { r -> Choice("$r", st.dockRows == r) { update(st.copy(dockRows = r)) } }
            }
            SliderRow("大きさ", st.dockCellDp.toFloat(), 52f..110f, "${st.dockCellDp}dp") {
                update(st.copy(dockCellDp = it.toInt()))
            }
            ToggleRow("アプリ名を表示", st.dockLabels) { update(st.copy(dockLabels = it)) }
        }
    }
}

// ───────── 位置の調整（画面全体を縮小して見る） ─────────

@Composable
private fun DockOverview(st: Settings, update: (Settings) -> Unit, close: () -> Unit) {
    val ctx = LocalContext.current
    val d = LocalDensity.current.density
    val bounds = remember {
        (ctx.getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager)
            .currentWindowMetrics.bounds
    }
    val sw = bounds.width()
    val sh = bounds.height()
    val fromRight = st.edgeMode != Settings.EDGE_LEFT
    val count = DockItems.parse(st.dockItems).size.coerceAtMost(st.dockCols * st.dockRows)
    val stNow by rememberUpdatedState(st)

    // 指で動かしている途中の位置（px、実寸）
    var centerY by remember { mutableFloatStateOf(sh * st.dockYPercent / 100f) }
    var gapPx by remember { mutableFloatStateOf(st.dockGapDp * d) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Sumi)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 28.dp)
    ) {
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ドックの設置", color = Kinari, fontSize = 20.sp, fontFamily = Mincho, modifier = Modifier.weight(1f))
            Choice("完了", true, onClick = close)
        }
        Spacer(Modifier.height(6.dp))
        Note("画面全体を縮めて表示しています。ドックを指で動かして置き場所を決めてください。")
        Spacer(Modifier.height(4.dp))
        Text(
            "高さ ${st.dockYPercent}%　・　端から ${st.dockGapDp}dp" +
                if (!st.dockFixed) "　（今は「指の高さ」なので高さは指に合わせて変わります）" else "",
            color = Usuzumi, fontSize = 11.sp
        )
        Spacer(Modifier.height(12.dp))

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val availW = constraints.maxWidth.toFloat()
            val availH = constraints.maxHeight.toFloat()
            val scale = minOf(availW / sw, availH / sh) * 0.95f
            val fw = sw * scale
            val fh = sh * scale

            Canvas(
                Modifier
                    .size(width = (fw / d).dp, height = (fh / d).dp)
                    .pointerInput(scale) {
                        detectDragGestures(
                            onDrag = { change, amount ->
                                change.consume()
                                centerY = (centerY + amount.y / scale).coerceIn(0f, sh.toFloat())
                                gapPx = (gapPx + (if (fromRight) -amount.x else amount.x) / scale)
                                    .coerceIn(0f, 300f * d)
                                update(
                                    stNow.copy(
                                        dockFixed = true,
                                        dockYPercent = (centerY / sh * 100f).toInt().coerceIn(0, 100),
                                        dockGapDp = (gapPx / d).toInt().coerceIn(0, 300)
                                    )
                                )
                            }
                        )
                    }
            ) {
                val r = 22f * d * scale * 2f
                // 画面の枠
                drawRoundRect(
                    color = Color(0xFF1B3136),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r)
                )
                drawRoundRect(
                    color = Line, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r)
                )
                // ホーム画面のアプリのイメージ（4列 × 6段の薄い四角）
                val ic = fw / 4f
                for (row in 0 until 6) for (col in 0 until 4) {
                    val s0 = ic * 0.5f
                    drawRoundRect(
                        color = Line.copy(alpha = 0.35f),
                        topLeft = Offset(col * ic + (ic - s0) / 2, fh * 0.12f + row * ic * 1.15f),
                        size = androidx.compose.ui.geometry.Size(s0, s0),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(s0 * 0.25f, s0 * 0.25f)
                    )
                }
                // 触れる帯
                val bh = st.triggerLengthDp * d * scale
                val by = (fh * st.triggerPosPercent / 100f - bh / 2).coerceIn(0f, (fh - bh).coerceAtLeast(0f))
                if (st.rightOn) drawLine(Shu, Offset(fw - 3.dp.toPx(), by), Offset(fw - 3.dp.toPx(), by + bh), 3.dp.toPx())
                if (st.leftOn) drawLine(Shu, Offset(3.dp.toPx(), by), Offset(3.dp.toPx(), by + bh), 3.dp.toPx())

                // ドック（実寸で計算して縮小）
                val (w, h) = DockView.size(st.dockCols, st.dockRows, count, st.dockCellDp.toFloat(), d)
                val (left, top) = DockView.origin(w, h, fromRight, gapPx / d, centerY, sw, sh, d)
                val tl = Offset(left * scale, top * scale)
                val dsz = androidx.compose.ui.geometry.Size(w * scale, h * scale)
                drawRect(Sumi, tl, dsz)
                drawRect(Kinari, tl, dsz, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                // 上の帯とマス
                val hh = DockView.HEADER_DP * d * scale
                drawLine(Line, Offset(tl.x, tl.y + hh), Offset(tl.x + dsz.width, tl.y + hh), 1f)
                val cell = st.dockCellDp * d * scale
                val pad = DockView.PAD_DP * d * scale
                for (i in 0 until count) {
                    val cx = tl.x + pad + (i % st.dockCols) * cell + cell / 2
                    val cy = tl.y + hh + pad + (i / st.dockCols) * cell + cell / 2
                    drawCircle(Kinari.copy(alpha = 0.7f), radius = cell * 0.22f, center = Offset(cx, cy))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("出す高さ", color = Kinari, fontSize = 14.sp)
            Spacer(Modifier.width(4.dp))
            Choice("指の高さ", !st.dockFixed) { update(st.copy(dockFixed = false)) }
            Choice("決まった高さ", st.dockFixed) { update(st.copy(dockFixed = true)) }
        }
        Spacer(Modifier.height(16.dp))
    }
}
