package app.chajeung

import android.content.Context
import android.content.res.Configuration
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** 색 역할. 강조색은 ac(알아챈 것, 다듬은 것)와 wn(벗어난 것) 둘뿐.
 *  흑요석 바탕에 홍채빛 단색 하나. 앱 아이콘과 같은 색을 쓴다. */
data class Palette(
    val bg: Color, val s1: Color, val s2: Color, val ln: Color,
    val tx: Color, val sub: Color, val dim: Color,
    val ac: Color, val acDeep: Color, val wn: Color, val onAc: Color,
    val glow: Float, // 빛 효과 세기. 밝은 판에서는 빛이 탁해 보여 낮춘다
)

val Dark = Palette(
    bg = Color(0xFF09090C), s1 = Color(0xFF121218), s2 = Color(0xFF1A1A22), ln = Color(0xFF2A2A35),
    tx = Color(0xFFF2F1F7), sub = Color(0xFF8C8B99), dim = Color(0xFF5B5A66),
    ac = Color(0xFFB3A4FF), acDeep = Color(0xFF3E3478), wn = Color(0xFFFF8F70), onAc = Color(0xFF120E2A), glow = 1f,
)

val Light = Palette(
    bg = Color(0xFFF5F4F8), s1 = Color(0xFFFFFFFF), s2 = Color(0xFFECEBF2), ln = Color(0xFFDFDDE8),
    tx = Color(0xFF121218), sub = Color(0xFF696875), dim = Color(0xFF9998A6),
    ac = Color(0xFF6B55E6), acDeep = Color(0xFFC9BFFF), wn = Color(0xFFD9572F), onAc = Color(0xFFFFFFFF), glow = .45f,
)

val LocalPalette = staticCompositionLocalOf { Dark }

/** 차의 밤 모드(시스템 테마)를 따른다 */
@Composable
fun ChTheme(content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalPalette provides if (isSystemInDarkTheme()) Dark else Light, content = content)

fun paletteOf(ctx: Context) =
    if (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) Dark else Light

/** 움직임 곡선: 빠르게 들어와 천천히 선다 */
val Ez = CubicBezierEasing(.16f, 1f, .3f, 1f)
