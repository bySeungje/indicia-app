package app.chajeung

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

// 움직임 값(시안 2와 같다): 화면 진입 700ms, 요소 진입 400ms 80ms 간격, 숫자 1200ms, 고리 1200ms,
// 열 지도 훑기 1000ms, 막대 900ms, 경로 1400ms, 숨쉬는 빛 5000ms. 곡선은 Ez

/** 0에서 1로 한 번 가는 진행값. delayMs 뒤에 시작 */
@Composable
fun progress(ms: Int, delayMs: Int = 0, key: Any = Unit): Float {
    val a = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { delay(delayMs.toLong()); a.animateTo(1f, tween(ms, easing = Ez)) }
    return a.value
}

/** 화면 진입: 아래에서 기울어진 채 떠올라 선다 */
@Composable
fun Modifier.tiltIn(key: Any): Modifier {
    val p = progress(700, key = key)
    return graphicsLayer {
        alpha = p; rotationX = 14f * (1 - p); translationY = 60f * (1 - p)
        scaleX = .96f + .04f * p; scaleY = scaleX; cameraDistance = 18 * density
        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(.5f, 1f)
    }
}

/** 요소 진입: 차례로 떠오른다 */
@Composable
fun Modifier.appear(order: Int, key: Any = Unit): Modifier {
    val p = progress(400, order * 80, key)
    return graphicsLayer { alpha = p; translationY = 24f * (1 - p) }
}

/** 숫자가 0에서 올라간다 */
@Composable
fun CountUp(value: Int, style: TextStyle, delayMs: Int = 450, key: Any = Unit) {
    val p = progress(1200, delayMs, key to value)
    Text((value * p).toInt().toString(), style = style)
}

/** 큰 숫자 뒤에서 숨 쉬는 빛 */
@Composable
fun Ember(modifier: Modifier, size: Dp = 1400.dp) {
    val pal = LocalPalette.current
    val t = rememberInfiniteTransition(label = "ember")
    val k by t.animateFloat(1f, .55f, infiniteRepeatable(tween(2500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "k")
    Box(modifier.size(size).graphicsLayer { alpha = k * pal.glow; scaleX = 1.06f - .06f * k; scaleY = scaleX }
        .background(Brush.radialGradient(0f to pal.ac.copy(alpha = .22f), .3f to pal.wn.copy(alpha = .10f), .62f to Color.Transparent)))
}

/** 숨 쉬는 점: 지금 살펴보고 있다는 표시 */
@Composable
fun Orb(color: Color) {
    val t = rememberInfiniteTransition(label = "orb")
    val r by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearOutSlowInEasing)), label = "r")
    Canvas(Modifier.size(22.dp)) {
        drawCircle(color.copy(alpha = .6f * (1 - r)), radius = size.minDimension / 2 + 22.dp.toPx() * r)
        drawCircle(color)
    }
}

/** 빛이 두 바퀴 도는 테두리. 주 행동이 있는 카드 하나에만 */
fun Modifier.litBorder(turn: Float, color: Color, radius: Dp = 16.dp) = drawBehind {
    if (turn <= 0f || turn >= 1f) return@drawBehind
    // 사각형을 돌리면 테두리가 글자 위를 가로지른다. 사각형은 두고 빛의 자리만 테두리를 따라 돈다
    val h = ((turn * 2f) % 1f).coerceIn(.16f, .97f)
    val brush = Brush.sweepGradient(0f to Color.Transparent, h - .15f to Color.Transparent, h to color, h + .02f to Color.Transparent, 1f to Color.Transparent, center = center)
    drawRoundRect(brush, cornerRadius = CornerRadius(radius.toPx()), style = Stroke(3.dp.toPx()), size = size)
}

/** 조건 준수 고리: 진행값만큼 그린다 */
@Composable
fun Ring(pct: Int, key: Any, modifier: Modifier) {
    val pal = LocalPalette.current
    val p = progress(1200, 400, key)
    Canvas(modifier) {
        val w = 26.dp.toPx(); val inset = w / 2 + 8.dp.toPx()
        val sz = Size(size.width - inset * 2, size.height - inset * 2)
        drawArc(pal.s2, 0f, 360f, false, Offset(inset, inset), sz, style = Stroke(w))
        val sweep = 360f * pct / 100f * p
        drawArc(pal.ac.copy(alpha = .25f * pal.glow), -90f, sweep, false, Offset(inset, inset), sz, style = Stroke(w + 18.dp.toPx(), cap = StrokeCap.Round))
        drawArc(Brush.sweepGradient(listOf(pal.acDeep, pal.ac, pal.acDeep)), -90f, sweep, false, Offset(inset, inset), sz, style = Stroke(w, cap = StrokeCap.Round))
    }
}

/** 막대 하나: 아래에서 솟는다 */
@Composable
fun Bar(frac: Float, color: Color, delayMs: Int, key: Any, modifier: Modifier, glow: Boolean = false) {
    val pal = LocalPalette.current
    val p = progress(900, delayMs, key)
    Box(modifier, contentAlignment = androidx.compose.ui.Alignment.BottomCenter) {
        Box(Modifier.fillMaxWidth().fillMaxHeight((frac * p).coerceIn(0f, 1f))
            .drawBehind { if (glow) drawRoundRect(pal.ac.copy(alpha = .35f * pal.glow), Offset(-8.dp.toPx(), -8.dp.toPx()), Size(size.width + 16.dp.toPx(), size.height + 16.dp.toPx()), CornerRadius(14.dp.toPx())) }
            .background(color, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 4.dp, bottomEnd = 4.dp)))
    }
}

/** 출발지에서 도착지로 빛나는 점이 달리며 선을 긋는다 */
@Composable
fun RouteLine(delayMs: Int, key: Any, modifier: Modifier) {
    val pal = LocalPalette.current
    val p = progress(1400, delayMs, key)
    Canvas(modifier) {
        val y = size.height / 2
        drawLine(pal.ln, Offset(0f, y), Offset(size.width, y), 3.dp.toPx(), StrokeCap.Round)
        drawLine(Brush.horizontalGradient(listOf(pal.acDeep, pal.ac)), Offset(0f, y), Offset(size.width * p, y), 3.dp.toPx(), StrokeCap.Round)
        if (p < 1f) {
            val x = size.width * p
            drawCircle(pal.ac.copy(alpha = .45f * pal.glow), 18.dp.toPx(), Offset(x, y))
            drawCircle(Color.White, 7.dp.toPx(), Offset(x, y))
        }
    }
}
