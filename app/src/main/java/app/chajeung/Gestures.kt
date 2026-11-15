package app.chajeung

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.HvacFanSpeedListener
import ai.pleos.playground.vehicle.listener.HvacRecirculationStateListener
import ai.pleos.playground.vehicle.listener.SeatBackrestAnglePositionListener
import ai.pleos.playground.vehicle.listener.SeatForeAftPositionListener
import ai.pleos.playground.vehicle.listener.WindowPositionListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 몸짓 읽기 층. 차 값을 몸짓 한 건으로 바꾸는 곳은 여기 하나다(값의 보증).
 * 다듬기(내기 켜기, 예고 한 줄, 몸 안내, 충전 영수증, 버틸 시간)도 여기서 일어나고 로그를 한 서식으로 남긴다.
 */
object Gestures {
    private const val TAG = "Chajeung"
    private val driver = SeatArea(SeatValue.ROW_1_LEFT)
    // 공조는 앞줄 두 자리를 묶은 영역으로만 받는다(2026-11-10 에뮬레이터 실측: 운전석 하나나 전체는 "not supported")
    private val front = SeatArea(SeatValue.ROW_1_LEFT, SeatValue.ROW_1_RIGHT)
    private val main = Handler(Looper.getMainLooper())
    private lateinit var st: Store
    private var v: Vehicle? = null

    // 지금 위치와 그 출처. 내비가 주면 차, 시험 방송이 주면 흉내
    private var lat = 0.0; private var lon = 0.0; private var locSrc = "차"; private var hasLoc = false
    private var window = -1
    private var recircMine = 0L          // 우리가 켠 내기는 몸짓으로 세지 않는다
    private var wiperOnAt = 0L
    private var rainDay = ""             // 계속 돈 날은 짧은 와이퍼를 쌓지 않는다
    private val todayEyes = mutableListOf<String>()
    private var slide = -1; private var recline = -1; private var seatSrc = "차"
    var fan = 0; private set
    private var soc = -1.0
    private var timescale = 1            // 시연 대본이 시간을 줄일 때만 바뀐다

    /** 다듬기 한 번: 로그 한 줄(서식 고정)과 오늘 화면에 보일 사람 말 한 줄 */
    fun log(name: String, value: String, src: String, said: String? = null) {
        Log.i(TAG, "INDICIA 다듬기=$name 값=$value 출처=$src")
        if (said != null) Drive.live.update { it.copy(done = it.done + (name to said)) }
    }

    fun start(ctx: Context, vehicle: Vehicle, store: Store) {
        st = store; v = vehicle
        fun safe(what: String, f: () -> Unit) = runCatching(f).onFailure { Log.e(TAG, "subscribe $what", it) }
        safe("window") { vehicle.getWindow().registerWindowPosition(WindowArea(WindowValue.ROW_1_LEFT), object : WindowPositionListener {
            override fun onWindowPositionUpdated(p0: Int?) { p0?.let { onWindow(it) } }
            override fun onFailed(p0: Exception) { Log.e(TAG, "window", p0) }
        }) }
        safe("recirc") { vehicle.getHVAC().registerHvacRecirculationState(front, object : HvacRecirculationStateListener {
            override fun onHvacAutoRecirculationStateUpdated(p0: Boolean?) { if (p0 == true) onRecirc() }
            override fun onFailed(p0: Exception) { Log.e(TAG, "recirc", p0) }
        }) }
        safe("fan") { vehicle.getHVAC().registerHvacFanSpeed(front, object : HvacFanSpeedListener {
            override fun onHvacFanSpeedUpdated(p0: Int?) { if (p0 != null) { fan = p0; Log.d(TAG, "fan $p0") } }
            override fun onFailed(p0: Exception) { Log.e(TAG, "fan", p0) }
        }) }
        safe("wiper") { vehicle.getWiper().registerWiperState(WindowArea(WindowValue.FRONT_WINDSHIELD), object : WiperStateListener {
            override fun onWiperStateUpdated(p0: WiperState?) { onWiper(p0 == WiperState.ON) }
            override fun onFailed(p0: Exception) { Log.e(TAG, "wiper", p0) }
        }) }
        safe("seat slide") { vehicle.getSeat().registerSeatForeAftPosition(driver, object : SeatForeAftPositionListener {
            override fun onSeatForeAftPositionUpdated(p0: Int?) { if (p0 != null) { if (slide >= 0 && p0 != slide) add("좌석 앞뒤", null); slide = p0; seatSrc = "차"; Log.d(TAG, "seat slide $p0") } }
            override fun onFailed(p0: Exception) { Log.e(TAG, "slide", p0) }
        }) }
        safe("seat recline") { vehicle.getSeat().registerSeatBackrestAnglePosition(driver, object : SeatBackrestAnglePositionListener {
            override fun onSeatBackrestAnglePositionUpdated(p0: Int?) { if (p0 != null) { if (recline >= 0 && p0 != recline) add("등받이", null); recline = p0; seatSrc = "차"; Log.d(TAG, "seat recline $p0") } }
            override fun onFailed(p0: Exception) { Log.e(TAG, "recline", p0) }
        }) }
        safe("soc") { vehicle.getEvBattery().registerEvBatteryStateOfCharge(object : EvBatteryStateOfChargeListener {
            override fun onEvBatteryStateOfChargeUpdated(p0: Int?) { if (p0 != null) onSoc(p0.toDouble()) }
            override fun onFailed(p0: Exception) { Log.e(TAG, "soc", p0) }
        }) }
        if (BuildConfig.DEBUG) debugChannel(ctx)
        main.post(tick)
    }

    private fun today() = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Date())
    private fun live() = Drive.live.value
    private fun cell() = if (hasLoc) Retouch.cell(lat, lon) else null

    private fun add(kind: String, rememberAs: String?) {
        val c = cell() ?: run { Log.w(TAG, "gesture $kind without location"); return }
        val g = Gesture(kind, System.currentTimeMillis(), c, live().speedKph, if (locSrc == "흉내") "흉내" else "차")
        st.addGesture(g)
        if (rememberAs != null) st.remember(rememberAs, c)
        Log.i(TAG, "INDICIA 몸짓=$kind 칸=$c 출처=${g.src}")
        Drive.live.update { it.copy(gestures = it.gestures + 1) }
        City.send(st, g)
    }

    // 창문: 열려 있다가(2 이상) 끝까지 닫히면 창문 올림(숨 막히던 바깥 공기). 에뮬레이터는 조작판 값의 10분의 1(0~10)로 준다
    private fun onWindow(pos: Int) {
        Log.d(TAG, "window $pos")
        if (window >= 2 && pos == 0 && live().driving) add("창문 올림", "숨")
        window = pos
    }

    private fun onRecirc() {
        if (System.currentTimeMillis() - recircMine < 5000) return
        if (live().driving) add("바깥 공기 막음", "숨")
    }

    private fun onWiper(on: Boolean) {
        val now = System.currentTimeMillis()
        if (on) { if (wiperOnAt == 0L) wiperOnAt = now; return }
        if (wiperOnAt == 0L) return
        val onAt = wiperOnAt; wiperOnAt = 0L
        if (!live().driving) return
        when (Retouch.wipe(onAt, now, rainDay == today())) {
            "비" -> {
                // 비 오는 날로 밝혀지면 오늘 앞서 쌓은 짧은 와이퍼 자리를 되돌린다
                rainDay = today(); add("와이퍼 계속", null)
                todayEyes.forEach { st.unremember("눈", it) }; todayEyes.clear()
            }
            "눈" -> { add("와이퍼 짧게", "눈"); cell()?.let { todayEyes += it } }
            else -> add("와이퍼 짧게", null)
        }
    }

    /* ---------- 숨과 눈: 기억한 자리 앞에서 먼저 ---------- */

    private var debugLocAt = 0L
    private var warned = mutableMapOf<String, Long>() // 같은 칸은 10분에 한 번

    fun onLocation(newLat: Double, newLon: Double, src: String) {
        // 에뮬레이터 내비 위치는 한 점에 고정이라(2026-11-10 실측) 시험 위치가 들어온 뒤 1분은 내비 위치로 덮지 않는다
        if (src == "흉내") debugLocAt = System.currentTimeMillis() else if (System.currentTimeMillis() - debugLocAt < 60_000) return
        Log.d(TAG, "loc $newLat,$newLon $src")
        val prevLat = lat; val prevLon = lon; val had = hasLoc
        lat = newLat; lon = newLon; locSrc = src; hasLoc = true
        if (!had || !live().driving) return
        for (kind in listOf("숨", "눈")) {
            val c = Retouch.ahead(lat, lon, prevLat, prevLon, st.places(kind).keys) ?: continue
            val k = "$kind$c"; val now = System.currentTimeMillis()
            if (now - (warned[k] ?: 0) < 600_000) continue
            warned[k] = now
            if (kind == "숨") breath(c) else eyes(c)
        }
    }

    private fun breath(c: String) {
        recircMine = System.currentTimeMillis()
        // 주행 중 바깥 공기는 작은 창에 띄우지 않는다(말없이). 결과는 오늘 화면과 바깥 공기 화면에만
        val ok = { Drive.live.update { it.copy(air = "외기를 막았어요") }; log("숨", "내기켬 칸=$c", locSrc, "창문을 닫았던 구간이에요. 외기를 막았어요") }
        val ask = { e: Exception -> Log.w(TAG, "recirc set", e); Drive.live.update { it.copy(air = "외기 차단을 요청했어요. 내기 버튼을 눌러 주세요") }; log("숨", "청함 칸=$c", locSrc, "창문을 닫았던 구간이에요. 외기 차단을 요청했어요") }
        runCatching { v!!.getHVAC().setHvacRecirculation(front, true, { _, _ -> main.post(ok) }, { e -> main.post { ask(e) } }) }.onFailure { ask(it as Exception) }
    }

    private fun eyes(c: String) {
        hint("300m 앞, 와이퍼를 자주 쓰던 구간이에요")
        log("눈", "예고 칸=$c", locSrc, "와이퍼를 자주 쓰던 구간 300m 앞에서 알렸어요")
    }

    fun hint(s: String) { // 작은 창 한 줄은 8초 뒤 사라진다
        Drive.live.update { it.copy(hint = s) }
        main.removeCallbacks(clearHint); main.postDelayed(clearHint, 8_000)
    }
    private val clearHint = Runnable { Drive.live.update { it.copy(hint = null) } }

    /* ---------- 몸: 주차하면 오늘 좌석을 남기고 기준과 견준다 ---------- */

    fun onPark() {
        if (!st.bodyOn || slide < 0 || recline < 0) return
        val d = today()
        st.putSeatDay(Store.SeatDay(d, Retouch.Seat(slide, recline), seatSrc))
        checkBody()
    }

    fun checkBody() {
        if (live().driving) return // 몸 안내는 기어 P 인 동안만
        val days = st.seatDays().filter { it.date >= st.bodySince }; val base = days.firstOrNull() ?: return; val now = days.last()
        if (base.date == now.date || !Retouch.bodyAlert(base.seat, now.seat) || st.bodySeen == now.date) return
        val src = if (base.src == "흉내" || now.src == "흉내") "흉내" else "차"
        Drive.live.update { it.copy(body = true) }
        log("몸", "앞뒤 ${base.seat.slide}→${now.seat.slide} 등받이 ${base.seat.recline}→${now.seat.recline} 기준일 ${base.date}", src, "시트가 평소보다 뒤로 가 있어 출발 전에 알렸어요")
    }

    fun bodyConfirmed() { st.bodySeen = today(); Drive.live.update { it.copy(body = false) } }

    fun hardBrake() = add("급제동", null)

    /* ---------- 기다림과 갇힌 시간: 잔량 ---------- */

    private val charge = mutableListOf<Pair<Long, Double>>()
    private val drain = ArrayDeque<Pair<Long, Double>>()
    private var lastRise = 0L
    private var chargeStart = 0L

    // shortcut: 충전기 정격은 시연용 표다. 실차에서는 충전소 정보(충전 사업자 API)로 바꾼다
    private val ratedTable = listOf("창원" to 100.0, "" to 50.0)
    private fun rated() = ratedTable.first { Drive.place().contains(it.first) }.second

    // shortcut: 배터리 용량은 조작판 실측(100% 에서 77.4kWh, 2026-11-10). SDK 의 배터리 양은 잔량을 바꿔도 그대로라 쓰지 않는다. 실차에서는 차량 정보의 용량 값으로 바꾼다
    private fun capacity() = 77.4

    private fun onSoc(s: Double) {
        val now = System.currentTimeMillis()
        Log.d(TAG, "soc $s")
        if (soc >= 0 && s > soc && !live().driving) {
            // 시연 배율은 충전 시간에도 곱한다(실제로 흐른 시간 x 배율)
            if (charge.isEmpty()) { chargeStart = now; charge += 0L to s; add("충전 시작", null) } else charge += (now - chargeStart) * timescale to s
            lastRise = now
            Drive.live.update { it.copy(charging = true) }
        }
        soc = s
        if (live().driving) {
            drain.addLast(now to s)
            while (drain.size > 2 && now - drain.first().first > 15 * 60_000 / timescale) drain.removeFirst()
        }
    }

    private fun endCharge() {
        val r = Retouch.receipt(charge.toList(), capacity(), rated()) ?: return
        charge.clear(); add("충전 끝", null)
        Drive.live.update { it.copy(charging = false, receipt = r) }
        log("기다림", "%.0f분 실제 %.0fkW 정격 %.0fkW(시연 표) 충전기 탓 %.0f분 배터리 탓 %.0f분 배율 %d".format(r.minutes, r.actualKw, r.ratedKw, r.chargerMin, r.batteryMin, timescale), "흉내", "충전 %.0f분을 항목별로 기록했어요".format(r.minutes))
        hint("충전을 마쳤어요. %.0f분 걸렸어요".format(r.minutes))
    }

    private var stuckSince = 0L
    private var stuckShown = false

    private val tick = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            if (charge.isNotEmpty() && now - lastRise > 20_000) endCharge()
            // 와이퍼와 풍량은 듣기로 오지 않아 2초마다 묻는다(2026-11-10 에뮬레이터 실측: 조회는 되고 알림은 없음)
            v?.let { x ->
                runCatching { x.getWiper().getCurrentWiperState(WindowArea(WindowValue.FRONT_WINDSHIELD), { w -> main.post { onWiper(w == WiperState.ON) } }, {}) }
                runCatching { x.getHVAC().getHvacFanSpeed(front, { f -> if (f != null) main.post { if (f != fan && live().driving) add("공조 단계", null); fan = f } }, {}) }
            }
            val l = live()
            if (l.driving && l.speedKph < 1) { if (stuckSince == 0L) { stuckSince = now; add("정차 시작", null) } }
            else if (l.speedKph > 5 || !l.driving) { if (stuckSince > 0) add("정차 끝", null); stuckSince = 0; stuckShown = false }
            if (stuckSince > 0 && !stuckShown && now - stuckSince >= 10 * 60_000 / timescale) { stuckShown = true; stuck() }
            main.postDelayed(this, 2000)
        }
    }

    private fun drainKw(): Double? {
        if (drain.size < 2) return null
        val (t0, s0) = drain.first(); val (t1, s1) = drain.last()
        val hours = (t1 - t0) / 3_600_000.0 * timescale
        return if (hours > 0 && s0 > s1) (s0 - s1) / 100 * capacity() / hours else null
    }

    private fun stuck() {
        val d = drainKw(); val kwh = capacity() * soc / 100
        // 시연 배율이 문턱과 소모량 계산을 바꾸므로 배율이 1 이 아니면 흉내
        val src = if (d == null || soc < 0 || timescale != 1) "흉내" else "차"
        val hours = Retouch.survive(kwh, d ?: 2.0, fan)
        Drive.live.update { it.copy(stuck = hours) }
        log("갇힌 시간", "잔량 %.0f%% 소모 %.1fkW 지금 %.1f시간 약하게 %.1f시간 끔 %.1f시간 배율 %d".format(soc, d ?: 2.0, hours[0], hours[1], hours[2], timescale), src, "정체 중 공조 사용 가능 시간을 알렸어요")
        hint("지금 공조 그대로 약 %.0f시간 쓸 수 있어요".format(hours[0]))
    }

    /** 갇힌 시간에서 고른 조합(0 지금, 1 약하게, 2 끔)대로 풍량을 바꾼다. 막히면 바꿔 달라는 한 줄 */
    fun chooseAir(i: Int) {
        val target = listOf(fan, (fan / 2).coerceAtLeast(1), 0)[i]
        val name = listOf("지금", "약하게", "끔")[i]
        val ask = { e: Exception -> Log.w(TAG, "fan set", e); hint("풍량을 ${target}단으로 바꿔 주세요"); log("갇힌 시간", "청함 $name 풍량 $target", "차") }
        runCatching { v!!.getHVAC().setHvacFanSpeed(front, target, { _, _ -> main.post { log("갇힌 시간", "풍량 $target $name", "차", "풍량을 ${target}단으로 바꿨어요") } }, { e -> main.post { ask(e) } }) }
            .onFailure { ask(it as Exception) }
    }

    /* ---------- 시연 대본용 시험 통로(디버그 빌드에만 있다) ---------- */

    /** 조작판 값이 SDK 조회로 닿는지 재는 시험. 결과는 로그로만 */
    private fun probe() {
        val x = v ?: return
        fun <T> r(n: String): (T) -> Unit = { t -> Log.i(TAG, "probe $n = $t") }
        val e = { n: String -> { ex: Exception -> Log.i(TAG, "probe $n 실패 ${ex.javaClass.simpleName} ${ex.message}"); Unit } }
        x.getSeat().getCurrentSeatForeAftPosition(driver, r<Int?>("slide"), e("slide"))
        x.getSeat().getCurrentSeatBackrestAnglePosition(driver, r<Int?>("recline"), e("recline"))
        x.getHVAC().getHvacFanSpeed(front, r<Int?>("fan"), e("fan"))
        x.getHVAC().getHvacRecirculationState(front, r<Boolean?>("recirc"), e("recirc"))
        x.getWiper().getCurrentWiperState(WindowArea(WindowValue.FRONT_WINDSHIELD), r<WiperState?>("wiper"), e("wiper"))
        x.getWiper().getCurrentWiperSwitch(WindowArea(WindowValue.FRONT_WINDSHIELD), r<Any?>("wiperSwitch"), e("wiperSwitch"))
        x.getWindow().getCurrentWindowPosition(WindowArea(WindowValue.ROW_1_LEFT), r<Int?>("window"), e("window"))
    }

    private fun debugChannel(ctx: Context) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                i.getStringExtra("loc")?.split(",")?.let { (a, b) -> onLocation(a.toDouble(), b.toDouble(), "흉내") }
                i.getStringExtra("seatday")?.split(",")?.let { (d, s, r2) ->
                    st.bodyOn = true; if (d < st.bodySince) st.bodySince = d; st.putSeatDay(Store.SeatDay(d, Retouch.Seat(s.toInt(), r2.toInt()), "흉내")); Log.i(TAG, "debug seatday $d")
                }
                // 좌석: 에뮬레이터 조작판의 좌석 값이 차까지 오지 않아(실측 2026-11-10) 시연은 이 통로로 넣고 흉내로 남긴다
                i.getStringExtra("seat")?.split(",")?.let { (a, b) -> slide = a.toInt(); recline = b.toInt(); seatSrc = "흉내"; Log.i(TAG, "debug seat $a,$b") }
                if (i.hasExtra("timescale")) timescale = i.getIntExtra("timescale", 1).coerceAtLeast(1)
                if (i.getBooleanExtra("clear", false)) { c.getSharedPreferences("chajeung", 0).edit().remove("places").remove("seat_days").remove("gestures").remove("body_seen").remove("body_since").apply(); warned.clear(); rainDay = ""; todayEyes.clear(); charge.clear(); drain.clear()
                    timescale = 1; seatSrc = "차"; stuckSince = 0; stuckShown = false; Drive.live.update { it.copy(done = emptyMap(), receipt = null, stuck = null, body = false, hint = null, air = null) }; Log.i(TAG, "debug clear") }
                if (i.hasExtra("city")) st.cityOn = i.getBooleanExtra("city", false)
                if (i.getBooleanExtra("probe", false)) probe()
                if (i.hasExtra("air")) chooseAir(i.getIntExtra("air", 0))
                i.getStringExtra("hint")?.let { hint(it) } // 작은 창 한 줄을 차 값 없이 시험한다
            }
        }
        val f = IntentFilter("app.chajeung.DEBUG")
        if (Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(r, f, Context.RECEIVER_EXPORTED) else ctx.registerReceiver(r, f)
    }
}
