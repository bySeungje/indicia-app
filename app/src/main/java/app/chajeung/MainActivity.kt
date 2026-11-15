package app.chajeung

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val missing = arrayOf("android.car.permission.CAR_SPEED").filter { checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 1)
        // 내비게이션 위 표시 창 권한. 없으면 설정 화면을 연다(차 설정에 그 화면이 없으면 넘어간다)
        if (!android.provider.Settings.canDrawOverlays(this)) runCatching {
            startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName")))
        }
        Drive.start(this)
        intent.getStringExtra("llm")?.let { LlmProbe.run(this, it) }
        setContent { ChTheme { App() } }
        // 주행이 시작되면 앱은 뒤로 물러나 내비게이션을 보이게 한다. 주행 중 표시와 질문은 내비 위 작은 창(Overlay)이 맡는다
        lifecycleScope.launch { Drive.live.map { it.driving }.distinctUntilChanged().collect { if (it) moveTaskToBack(true) } }
    }

    override fun onResume() { super.onResume(); Overlay.appInFront.value = true }
    override fun onPause() { super.onPause(); Overlay.appInFront.value = false }
}

internal enum class Screen { Today, Home, Body, Breath, Eyes, Wait, Stuck }

@Composable
private fun App() {
    val pal = LocalPalette.current
    val live by Drive.live.collectAsStateWithLifecycle()
    var screen by remember { mutableStateOf(Screen.Today) }
    var resumes by remember { mutableIntStateOf(0) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) { resumes++; onPauseOrDispose {} }
    // 앱이 앞으로 올 때마다, 주행이 끝날 때마다 다시 센다
    val month = remember(resumes, live.driving) { Month(Drive.store().trips()) }
    val key = remember(screen, resumes) { Any() } // 화면을 열 때마다 움직임을 처음부터
    // 옛 차증의 안전주행, 업무주행 화면은 2026-11-15 메뉴에서 뺐다. 모든 화면은 오늘로 돌아간다
    BackHandler(screen != Screen.Today) { screen = Screen.Today }
    Box(Modifier.fillMaxSize().background(pal.bg)) {
        Box(Modifier.fillMaxSize().tiltIn(key).padding(horizontal = 56.dp, vertical = 44.dp)) {
            when (screen) {
                Screen.Today -> Today(live, key) { screen = it }
                Screen.Home -> Home(month, key) { screen = Screen.Today }
                else -> Retouches(screen, live, key) { screen = Screen.Today }
            }
        }
    }
}

private val T get() = TextStyle(fontFamily = null)

@Composable
internal fun Top(title: String, sub: String, right: String, key: Any, back: (() -> Unit)? = null, extra: @Composable () -> Unit = {}) {
    val pal = LocalPalette.current
    Row(Modifier.fillMaxWidth().appear(0, key).padding(bottom = 28.dp), verticalAlignment = Alignment.Bottom) {
        if (back != null) {
            Text("←", fontSize = 40.sp, color = pal.sub, modifier = Modifier.clickable(onClick = back).padding(end = 20.dp))
        }
        Text(title, fontSize = 44.sp, fontWeight = FontWeight.Bold, color = pal.tx)
        Spacer(Modifier.width(18.dp))
        Text(sub, fontSize = 24.sp, color = pal.sub, modifier = Modifier.padding(bottom = 6.dp))
        Spacer(Modifier.weight(1f))
        extra()
        Text(right, fontSize = 24.sp, color = pal.dim, modifier = Modifier.padding(bottom = 6.dp, start = 20.dp))
    }
}

@Composable
internal fun Panel(modifier: Modifier = Modifier, pad: Int = 36, content: @Composable ColumnScope.() -> Unit) {
    val pal = LocalPalette.current
    Column(modifier.background(pal.s1, RoundedCornerShape(16.dp)).border(1.dp, pal.ln, RoundedCornerShape(16.dp)).padding(pad.dp), content = content)
}

@Composable
internal fun Go(label: String, primary: Boolean, modifier: Modifier = Modifier, key: Any = Unit, onClick: () -> Unit) {
    val pal = LocalPalette.current
    val sheen = progress(900, 1400, key)
    Box(
        modifier.height(96.dp)
            .drawBehind { if (primary) drawRoundRect(pal.ac.copy(alpha = .30f * pal.glow), Offset(-10f, -6f), Size(size.width + 20f, size.height + 18f), CornerRadius(22f)) }
            .background(if (primary) pal.ac else pal.s2, RoundedCornerShape(12.dp))
            .border(1.dp, if (primary) pal.ac else pal.ln, RoundedCornerShape(12.dp))
            .drawBehind {
                if (primary && sheen in 0.01f..0.99f) {
                    val x = size.width * (sheen * 1.7f - .5f)
                    drawRect(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = .45f), Color.Transparent), x, x + size.width * .4f))
                }
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = if (primary) pal.onAc else pal.tx) }
}

internal fun big(color: Color) = TextStyle(fontSize = 136.sp, fontWeight = FontWeight.Bold, letterSpacing = (-3).sp, color = color, lineHeight = 136.sp)

/* ---------- 홈 ---------- */

@Composable
private fun Home(m: Month, key: Any, back: () -> Unit) {
    val pal = LocalPalette.current
    val date = SimpleDateFormat("M월 d일 EEEE", Locale.KOREA).format(Calendar.getInstance().time)
    Box(Modifier.fillMaxSize()) {
        Ember(Modifier.offset((-260).dp, 300.dp))
        Column(Modifier.fillMaxSize()) {
            Top("오늘의 드라이브", "오늘 주행 기록", date, key, back)
            Panel(Modifier.fillMaxWidth().appear(1, key), pad = 30) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Orb(pal.ac); Spacer(Modifier.width(28.dp))
                    Column {
                        // 오늘 달린 것이 있으면 오늘 한 줄, 없으면 이번 달 한 줄
                        val letter = remember(m) { dayLetter(Drive.store().trips()) }
                        Text(if (letter != null) "오늘 주행" else "이번 달 주행 ${m.trips.size}회", fontSize = 22.sp, color = pal.sub)
                        Spacer(Modifier.height(8.dp))
                        val (a, b) = m.headline()
                        Words(letter ?: (a + (b ?: "")), key)
                    }
                }
            }
        }
    }
}

/** 낱말이 하나씩 흐림에서 또렷하게 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun Words(text: String, key: Any) {
    val pal = LocalPalette.current
    val ws = text.split(" ")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ws.forEachIndexed { i, w ->
            val p = progress(400, 1000 + i * 70, key)
            Text(w, fontSize = 36.sp, fontWeight = FontWeight.Bold, color = pal.tx, modifier = Modifier.alpha(p))
        }
    }
}

@Composable
internal fun Toggle(title: String, sub: String, on: Boolean, set: (Boolean) -> Unit) {
    val pal = LocalPalette.current
    Row(Modifier.fillMaxWidth().clickable { set(!on) }.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 28.sp, color = pal.tx, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp)); Text(sub, fontSize = 22.sp, color = pal.sub)
        }
        Switch(checked = on, onCheckedChange = set, colors = SwitchDefaults.colors(checkedTrackColor = pal.ac, checkedThumbColor = pal.onAc))
    }
}
