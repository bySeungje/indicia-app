package app.chajeung

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/* ---------- 오늘: 여섯 다듬기 ---------- */

@Composable
internal fun Today(live: Live, key: Any, go: (Screen) -> Unit) {
    val pal = LocalPalette.current
    val st = Drive.store()
    var bodyOn by remember { mutableStateOf(st.bodyOn) }
    var cityOn by remember { mutableStateOf(st.cityOn) }
    var showSend by remember { mutableStateOf(false) }
    val date = SimpleDateFormat("M월 d일 EEEE", Locale.KOREA).format(Calendar.getInstance().time)
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Date())
    val todayTrips = remember(live.driving) { st.trips().filter { SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Date(it.startMs)) == today } }
    val breath = remember(live.gestures, key) { st.places("숨") }
    val eyes = remember(live.gestures, key) { st.places("눈") }
    val days = remember(live.body, key) { st.seatDays() }

    data class Cell(val s: Screen, val name: String, val noticed: String, val done: String?, val lit: Boolean, val big: String? = null)
    val cells = listOf(
        Cell(Screen.Home, "오늘의 드라이브", if (todayTrips.isEmpty()) "오늘은 아직 주행 기록이 없어요" else "오늘 ${todayTrips.size}번, %.1fkm 달렸어요".format(todayTrips.sumOf { it.km.toDouble() }),
            live.done["하루"], false, if (todayTrips.isEmpty()) null else "%.1f km".format(todayTrips.sumOf { it.km.toDouble() })),
        Cell(Screen.Body, "출발 자세", if (!bodyOn) "시트 위치 기록이 꺼져 있어요" else if (days.size < 2) "주차할 때마다 시트 위치를 기록하고 있어요"
            else "시트 앞뒤 위치: 첫 기록 ${days.first().seat.slide}, 지금 ${days.last().seat.slide}", live.done["몸"], live.body, if (days.size < 2) null else "${days.first().seat.slide} → ${days.last().seat.slide}"),
        Cell(Screen.Breath, "바깥 공기", if (breath.isEmpty()) "창문을 닫거나 외기를 막은 구간이 아직 없어요" else "창문을 닫거나 외기를 막은 구간 ${breath.size}곳을 기록했어요", live.done["숨"], false, if (breath.isEmpty()) null else "${breath.size}곳"),
        Cell(Screen.Eyes, "앞유리", if (eyes.isEmpty()) "비 없이 와이퍼를 쓴 구간이 아직 없어요" else "비 없이 와이퍼를 쓴 구간 ${eyes.size}곳을 기록했어요", live.done["눈"], false, if (eyes.isEmpty()) null else "${eyes.size}곳"),
        Cell(Screen.Wait, "충전 기록", live.receipt?.let { "충전에 %.0f분 걸렸어요".format(it.minutes) } ?: if (live.charging) "충전 중이에요" else "충전하면 걸린 시간을 항목별로 보여 드려요",
            live.done["기다림"], live.receipt != null, live.receipt?.let { "%.0f분".format(it.minutes) }),
        Cell(Screen.Stuck, "정체 중 공조", live.stuck?.let { "지금 공조 그대로 약 %.0f시간 쓸 수 있어요".format(it[0]) } ?: "10분 넘게 정체되면 공조를 쓸 수 있는 시간을 알려 드려요",
            live.done["갇힌 시간"], live.stuck != null, live.stuck?.let { "약 %.0f시간".format(it[0]) }),
    )
    Box(Modifier.fillMaxSize()) {
        Ember(Modifier.offset((-260).dp, 300.dp))
        Column(Modifier.fillMaxSize()) {
            Top("인디시아", "운전하는 동안 차가 공조와 창을 알아서 맞춰요", date, key)
            for (row in cells.chunked(3)) {
                Row(Modifier.fillMaxWidth().weight(1f).padding(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    row.forEachIndexed { i, c ->
                        val turn = progress(4800, 600, key)
                        Panel(Modifier.weight(1f).fillMaxHeight().appear(1 + cells.indexOf(c), key)
                            .then(if (c.lit) Modifier.litBorder(turn, pal.ac) else Modifier).clickable { go(c.s) }, pad = 30) {
                            Text(c.name, fontSize = 38.sp, fontWeight = FontWeight.Bold, color = if (c.lit) pal.ac else pal.tx)
                            Spacer(Modifier.height(14.dp))
                            Text(c.noticed, fontSize = 26.sp, color = pal.sub, lineHeight = 34.sp)
                            Spacer(Modifier.weight(1f))
                            Text(c.big ?: "–", fontSize = 64.sp, fontWeight = FontWeight.Bold, color = if (c.big != null) pal.ac else pal.ln)
                            Spacer(Modifier.weight(1f))
                            Text(c.done ?: "오늘은 아직 동작한 기록이 없어요", fontSize = 23.sp, color = if (c.done != null) pal.ac else pal.dim, lineHeight = 30.sp)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().appear(7, key), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { Toggle("시트 위치 기록", "주차할 때마다 운전석 시트 위치를 저장해요", bodyOn) { bodyOn = it; st.bodyOn = it } }
                Box(Modifier.weight(1f)) { Toggle("도로 정보 공유", "같은 조작이 5대 넘게 모인 구간만 시에 전달돼요", cityOn) { cityOn = it; st.cityOn = it } }
                Text(if (showSend) "닫기" else "보내는 내용 보기", fontSize = 24.sp, color = pal.tx,
                    modifier = Modifier.border(1.dp, pal.ln, RoundedCornerShape(999.dp)).clickable { showSend = !showSend }.padding(horizontal = 22.dp, vertical = 10.dp))
            }
            if (showSend) {
                val last = st.gestures().lastOrNull()
                Text(last?.let { City.payload(st, it).toString(2) } ?: "아직 보낼 기록이 없어요. 보내는 항목은 100m 구역, 조작 종류, 10분 단위 시각, 매달 바뀌는 차량 임시 번호예요",
                    fontSize = 20.sp, color = pal.sub, modifier = Modifier.fillMaxWidth().background(pal.s2, RoundedCornerShape(12.dp)).padding(20.dp))
            }
        }
    }
}

/** 주차 뒤 오늘 주행을 사실로 한 줄. 오늘 주행이 없으면 null */
internal fun dayLetter(trips: List<Trip>, now: Long = System.currentTimeMillis()): String? {
    val f = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA); val d = f.format(Date(now))
    val t = trips.filter { f.format(Date(it.startMs)) == d }.ifEmpty { return null }
    val km = t.sumOf { it.km.toDouble() }; val hb = t.sumOf { it.hardBrakes }; val work = t.count { it.work }
    val parts = mutableListOf("오늘 %d번, %.0fkm 달렸어요.".format(t.size, km))
    if (work > 0) parts += "업무 주행 ${work}번."
    parts += if (hb > 0) "급제동 ${hb}번." else "급제동 없음."
    return parts.joinToString(" ")
}

/* ---------- 칸별 자세한 화면 ---------- */

@Composable
internal fun Retouches(screen: Screen, live: Live, key: Any, back: () -> Unit) {
    val title = mapOf(Screen.Body to "출발 자세", Screen.Breath to "바깥 공기", Screen.Eyes to "앞유리", Screen.Wait to "충전 기록", Screen.Stuck to "정체 중 공조")[screen] ?: ""
    val sub = mapOf(Screen.Body to "평소와 달라진 시트 위치", Screen.Breath to "창문을 닫고 외기를 막은 구간", Screen.Eyes to "비 없이 와이퍼를 쓴 구간",
        Screen.Wait to "충전에 걸린 시간", Screen.Stuck to "남은 전력으로 공조를 쓸 수 있는 시간")[screen] ?: ""
    Column(Modifier.fillMaxSize()) {
        Top(title, sub, "", key, back)
        when (screen) {
            Screen.Body -> Body(live, key, back)
            Screen.Breath -> Places("숨", "창문을 닫거나 외기를 막은 구간", live.air?.let { "지금 공조: $it" } ?: "다음에 지나갈 때 300m 앞에서 외기를 먼저 막아요", key)
            Screen.Eyes -> Places("눈", "비 없이 와이퍼를 잠깐 쓴 구간", "다음에 지나갈 때 300m 앞에서 미리 알려 드려요", key)
            Screen.Wait -> Wait(live, key, back)
            Screen.Stuck -> Stuck(live, key)
            else -> {}
        }
    }
}

@Composable
private fun Body(live: Live, key: Any, back: () -> Unit) {
    val pal = LocalPalette.current
    val days = remember(key) { Drive.store().seatDays() }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        Panel(Modifier.weight(1.4f).fillMaxHeight().appear(1, key)) {
            val show = live.body && !live.driving
            Text(if (show) "시트가 평소보다 뒤로 가 있어요. 출발 전에 확인해 보세요" else "평소와 달라진 시트 위치가 없어요",
                fontSize = 32.sp, fontWeight = FontWeight.Bold, color = pal.tx, lineHeight = 42.sp)
            Spacer(Modifier.height(28.dp))
            listOf("등받이는 100~110도로 세워 주세요" to "허리와 어깨가 등받이에 닿으면 페달과 핸들 조작이 안정돼요",
                "핸들과 가슴 사이는 25cm 이상 두세요" to "에어백이 펴질 공간이 필요해요",
                "어깨띠는 어깨 가운데, 허리띠는 골반 위로" to "벨트가 목에 닿거나 느슨하면 제 역할을 못 해요").forEachIndexed { i, (a, b) ->
                HorizontalDivider(color = pal.ln)
                Column(Modifier.padding(vertical = 18.dp).appear(2 + i, key)) {
                    Text(a, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = if (show) pal.ac else pal.tx)
                    Text(b, fontSize = 22.sp, color = pal.sub)
                }
            }
            Spacer(Modifier.weight(1f))
            Go("확인", true, Modifier.fillMaxWidth(), key) { Gestures.bodyConfirmed(); back() }
        }
        Panel(Modifier.weight(1f).fillMaxHeight().appear(2, key)) {
            Text("시트 위치 기록", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = pal.tx)
            Spacer(Modifier.height(16.dp))
            if (days.isEmpty()) Text("시트 위치 기록을 켜면 주차할 때마다 쌓여요", fontSize = 24.sp, color = pal.dim)
            days.takeLast(8).forEach { d ->
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    Text(d.date.substring(5).replace("-", "월 ") + "일", fontSize = 24.sp, color = pal.sub, modifier = Modifier.width(140.dp))
                    Text("앞뒤 ${d.seat.slide}  등받이 ${d.seat.recline}", fontSize = 24.sp, color = pal.tx)
                }
            }
        }
    }
}

@Composable
private fun Places(kind: String, what: String, then: String, key: Any) {
    val pal = LocalPalette.current
    val st = Drive.store()
    var places by remember(key) { mutableStateOf(st.places(kind)) }
    Panel(Modifier.fillMaxSize().appear(1, key)) {
        Text(what, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = pal.tx)
        Text(then, fontSize = 24.sp, color = pal.sub)
        Spacer(Modifier.height(20.dp))
        if (places.isEmpty()) Text("아직 기록한 구간이 없어요", fontSize = 26.sp, color = pal.dim)
        Column(Modifier.verticalScroll(rememberScrollState())) {
            places.entries.sortedByDescending { it.value }.forEach { (cell, n) ->
                HorizontalDivider(color = pal.ln)
                Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    val (la, lo) = Retouch.center(cell)
                    Text("%.4f, %.4f 주변 100m".format(la, lo), fontSize = 26.sp, color = pal.tx, modifier = Modifier.weight(1f))
                    Text("${n}번", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = pal.ac, modifier = Modifier.padding(end = 28.dp))
                    Text("삭제", fontSize = 24.sp, color = pal.tx, modifier = Modifier.border(1.dp, pal.ln, RoundedCornerShape(999.dp))
                        .clickable { st.forget(kind, cell); places = st.places(kind) }.padding(horizontal = 26.dp, vertical = 10.dp))
                }
            }
        }
    }
}

@Composable
private fun Wait(live: Live, key: Any, back: () -> Unit) {
    val pal = LocalPalette.current
    val r = live.receipt
    Panel(Modifier.fillMaxSize().appear(1, key)) {
        if (r == null) {
            Text(if (live.charging) "충전 중이에요. 끝나면 걸린 시간을 항목별로 보여 드려요" else "충전하면 걸린 시간을 항목별로 보여 드려요", fontSize = 32.sp, color = pal.tx)
            return@Panel
        }
        val ideal = r.minutes - r.chargerMin - r.batteryMin
        Text("이번 충전 기록", fontSize = 30.sp, color = pal.sub)
        Row(verticalAlignment = Alignment.Bottom) {
            CountUp(r.minutes.toInt(), big(pal.tx), key = key)
            Text("분", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = pal.sub, modifier = Modifier.padding(start = 8.dp, bottom = 14.dp))
        }
        Text("평균 %.0fkW 로 충전했어요. 이 충전기의 최대 출력은 %.0fkW 예요".format(r.actualKw, r.ratedKw), fontSize = 26.sp, color = pal.tx)
        Spacer(Modifier.height(28.dp))
        listOf(Triple("최대 출력이었다면", ideal, pal.ln), Triple("충전기 출력이 낮아서", r.chargerMin, pal.wn), Triple("80% 이후 배터리 보호", r.batteryMin, pal.ac))
            .forEachIndexed { i, (k, v, c) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(k, fontSize = 26.sp, color = pal.tx, modifier = Modifier.width(420.dp))
                    val grow = progress(900, 400 + i * 200, key)
                    Box(Modifier.weight(1f).height(28.dp).background(pal.s2, RoundedCornerShape(6.dp))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth((v / r.minutes).toFloat().coerceIn(.02f, 1f) * grow).background(c, RoundedCornerShape(6.dp)))
                    }
                    Text("%.0f분".format(v), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = pal.tx, modifier = Modifier.padding(start = 20.dp).width(100.dp))
                }
            }
        Spacer(Modifier.height(16.dp))
        Text("80% 이후에는 배터리 보호를 위해 충전 속도가 줄어요", fontSize = 24.sp, color = pal.sub)
        Spacer(Modifier.weight(1f))
        Go("닫기", false, Modifier.fillMaxWidth(), key) { Drive.live.update { it.copy(receipt = null) }; back() }
    }
}

@Composable
private fun Stuck(live: Live, key: Any) {
    val pal = LocalPalette.current
    val h = live.stuck
    if (h == null) {
        Panel(Modifier.fillMaxSize().appear(1, key)) { Text("기어 D 로 10분 넘게 멈춰 있으면 남은 전력으로 공조를 쓸 수 있는 시간을 알려 드려요", fontSize = 32.sp, color = pal.tx) }
        return
    }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        listOf("지금 설정" to "공조를 그대로 둬요", "약하게" to "풍량을 절반으로 줄여요", "끄기" to "공조를 꺼요").forEachIndexed { i, (name, how) ->
            Panel(Modifier.weight(1f).fillMaxHeight().appear(1 + i, key)) {
                Text(name, fontSize = 36.sp, fontWeight = FontWeight.Bold, color = pal.tx)
                Text(how, fontSize = 24.sp, color = pal.sub)
                Spacer(Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    CountUp(h[i].toInt(), big(if (i == 0) pal.tx else pal.ac), key = key)
                    Text("시간", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = pal.sub, modifier = Modifier.padding(start = 8.dp, bottom = 14.dp))
                }
                Text("사용 가능", fontSize = 26.sp, color = pal.sub)
                Spacer(Modifier.weight(1f))
                Go("이 설정으로 바꾸기", i == 1, Modifier.fillMaxWidth(), key) { Gestures.chooseAir(i) }
            }
        }
    }
}

