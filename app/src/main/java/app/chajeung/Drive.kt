package app.chajeung

import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import android.car.Car
import android.car.VehicleGear
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.util.Calendar

/** 지금 차 상태와 이번 주행의 집계. 화면은 이것만 본다 */
data class Live(
    val driving: Boolean = false,
    val speedKph: Float = 0f,
    val brake: Boolean = false,
    val odoKm: Float? = null,
    val tripKm: Float = 0f,
    val hardBrakes: Int = 0,
    val signals: Int = 0,
    val sunsetMin: Int? = null,
    val question: String? = null,
    val qKind: String? = null, // "highway" 또는 "work"
    val lastSaid: String? = null,
    val sources: Set<String> = emptySet(),
    val hint: String? = null,                 // 작은 창의 한 줄. 이것이 있을 때만 작은 창이 뜬다(8초)
    val gestures: Int = 0,                    // 이번 실행에서 읽은 몸짓 수
    val body: Boolean = false,                // 몸 안내를 띄울 때
    val charging: Boolean = false,
    val receipt: Retouch.Receipt? = null,     // 기다림 영수증
    val stuck: List<Double>? = null,
    val air: String? = null,                  // 숨이 공조를 어떻게 했나(막음, 청함). 숨 화면의 공조 표시          // 갇힌 시간: 지금, 약하게, 끔의 버틸 시간
    val done: Map<String, String> = emptyMap(), // 다듬기 이름 → 오늘 다듬은 것 한 줄
)

object Drive {
    private const val TAG = "Chajeung"
    val live = MutableStateFlow(Live())
    private lateinit var st: Store
    private var voice: Voice? = null
    private var navi: NaviHelper? = null
    private var vehicle: Vehicle? = null

    private var lat = 37.4449; private var lon = 127.1389 // 기본: 에뮬레이터 내비 시작 위치(성남 수정구)
    private var tripStartMs = 0L
    private var tripStartOdo: Float? = null
    private var tripToll = false
    private var tripNight = false
    private var lastSignal = TurnSignalValue.OFF
    private var address = ""                 // 지금 위치 주소(NaviHelper)
    private var tripFrom = ""
    private var tripBrakes = 0
    private val tripHb = mutableListOf<HardBrake>()
    private val tripLeads = mutableListOf<Float>()
    private var tripAsked = false
    private var leadPendingAt = 0L           // 방향지시등을 켠 시각. 회전 안내 거리가 오면 남은 초를 잰다
    private var lastTripStart = 0L

    fun start(ctx: Context) {
        if (::st.isInitialized) return
        val app = ctx.applicationContext
        st = Store(app)
        voice = Voice(app)
        startVehicle(app)
        startCarApi(app)
        startNavi(app)
        live.update { it.copy(sunsetMin = Sun.sunsetMinutes(lat, lon)) }
        Overlay.show(app)
    }

    private fun safe(what: String, f: () -> Unit) = runCatching(f).onFailure { Log.e(TAG, "subscribe $what", it) }

    private fun mark(src: String) = live.update { it.copy(sources = it.sources + src) }

    // Pleos Vehicle SDK: 브레이크 페달, 방향지시등, 계기판 거리 (경고등은 제조사 전용 권한이라 받지 않는다)
    private fun startVehicle(ctx: Context) {
        val v = Vehicle(ctx).also { vehicle = it }
        runCatching { v.initialize() }.onFailure { Log.e(TAG, "vehicle init", it); return }
        safe("Brake()") { v.getBrake().registerBrakePedalStatus(object : BrakePedalStatusListener {
            override fun onBrakePedalStatusUpdated(p0: Boolean?) { Log.d(TAG, "brake $p0 at ${live.value.speedKph}"); mark("브레이크"); onBrake(p0 == true) }
            override fun onFailed(p0: Exception) { Log.e(TAG, "brake", p0) }
        }) }
        safe("TurnSignal()") { v.getTurnSignal().registerTurnSignalState(object : TurnSignalStateListener {
            override fun onTurnSignalStateUpdated(p0: TurnSignalValue?) { Log.d(TAG, "signal $p0"); mark("방향지시등"); onSignal(p0 ?: TurnSignalValue.OFF) }
            override fun onFailed(p0: Exception) { Log.e(TAG, "signal", p0) }
        }) }
        safe("Odometer()") { v.getOdometer().registerDistanceDriven(object : DistanceDrivenListener {
            override fun onDistanceDrivenUpdated(p0: Float?) { Log.d(TAG, "odo $p0"); mark("계기판 거리"); onOdo(p0) }
            override fun onFailed(p0: Exception) { Log.e(TAG, "odo", p0) }
        }) }
        Gestures.start(ctx, v, st)
    }

    /** 지금 자리의 이름(목적지가 있으면 목적지). 기다림의 충전기 표가 쓴다 */
    fun place(): String = lastDest?.name ?: address

    // 표준 차량 API: 속도와 기어
    private fun startCarApi(ctx: Context) {
        runCatching {
            val car = Car.createCar(ctx)
            val pm = car.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
            val cb = object : CarPropertyManager.CarPropertyEventCallback {
                override fun onChangeEvent(v: CarPropertyValue<*>) {
                    when (v.propertyId) {
                        VehiclePropertyIds.PERF_VEHICLE_SPEED -> { mark("속도"); onSpeed((v.value as Float) * 3.6f, v.timestamp / 1_000_000) }
                        VehiclePropertyIds.GEAR_SELECTION -> { mark("기어"); onGear(v.value as Int) }
                    }
                }
                override fun onErrorEvent(id: Int, zone: Int) { Log.e(TAG, "car prop error $id") }
            }
            pm.registerCallback(cb, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_FASTEST)
            pm.registerCallback(cb, VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE)
        }.onFailure { Log.e(TAG, "car api", it) }
    }

    // NaviHelper: 경로가 정해지면 조건과 견주어 한 문장 안내
    private fun startNavi(ctx: Context) {
        val n = NaviHelper(ctx).also { navi = it }
        n.initialize({
            n.addListener(object : NaviHelperEventListener {
                override fun onRouteStarted(info: RouteStartInfo) = onRoute(info)
                override fun onRouteOptionChanged(option: RouteOption) {
                    Log.d(TAG, "route option $option")
                }
                override fun onDestinationInfo(info: DestinationInfo) = onDestination(info)
                override fun onTBTInfo(list: List<TBTInfo>) = onTurns(list)
                override fun onCurrentLocationInfo(info: CurrentLocationInfo) {
                    lat = info.latitude; lon = info.longitude
                    if (!info.address.isNullOrBlank()) address = info.address!!
                    Gestures.onLocation(lat, lon, "차")
                    live.update { it.copy(sunsetMin = Sun.sunsetMinutes(lat, lon)) }
                }
            })
            n.getCurrentLocationInfo()
            mark("내비게이션")
        }, { Log.e(TAG, "navi init", it) })
    }

    private fun nowMin(): Int = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }

    // 내비게이션 방송(onRouteStarted)은 이 에뮬레이터에서 외부 앱에 오지 않아, 목적지 정보를 직접 묻는다.
    // 같은 목적지는 한 번만 안내한다
    private var judgedDest: String? = null
    private val main = Handler(Looper.getMainLooper())
    private val poll = object : Runnable {
        override fun run() {
            if (!live.value.driving) return
            navi?.getDestinationInfo()
            navi?.getTBTInfo()
            navi?.getCurrentLocationInfo()
            main.postDelayed(this, 5000)
        }
    }

    private fun onRoute(info: RouteStartInfo) = judge(info.name, info.duration, info.tollFare)

    // shortcut: 이 에뮬레이터는 목적지 통행료를 0 으로 준다. 다음 갈림길 안내에 IC, JC, 고속도로가 있으면 고속도로 구간으로 본다.
    // 실차에서 통행료가 오면 그 값이 우선한다
    private val highwayWords = Regex("IC|JC|고속도로|톨게이트|요금소")
    private var turnsHighway = false
    private var lastDest: DestinationInfo? = null

    private fun onTurns(list: List<TBTInfo>) {
        if (leadPendingAt > 0) {
            // shortcut: 켠 순간의 거리를 바로 묻고 응답 거리/속도로 남은 초를 잰다. 응답 지연(수십 ms)은 무시한다
            val d = list.firstOrNull()?.distance ?: -1.0
            val v = live.value.speedKph / 3.6
            if (d in 0.0..300.0 && v > 1) tripLeads += (d / v).toFloat().also { Log.d(TAG, "signal lead ${"%.1f".format(it)}s at ${d}m") }
            leadPendingAt = 0
        }
        turnsHighway = list.any { highwayWords.containsMatchIn(it.description ?: "") }
        Log.d(TAG, "turns highway=$turnsHighway ${list.take(3).map { it.description }}")
        lastDest?.let { judge(it.name, it.remainTime, if (it.tollFare > 0 || turnsHighway) 1 else 0) }
    }

    private fun onDestination(info: DestinationInfo) {
        Log.d(TAG, "dest ${info.name} remain=${info.remainTime}s toll=${info.tollFare}")
        if (info.name.isNullOrBlank() || info.remainTime <= 0) return
        lastDest = info
    }

    // 내비게이션은 안내를 시작하는 순간에도 경로를 보내 준다. 안내는 출발(기어 D) 뒤 한 번만
    private fun judge(dest: String?, seconds: Int, toll: Int) {
        if (!live.value.driving) return
        if (dest == judgedDest || switching != 0) { if (switching == 0) tripToll = st.conditions.noHighway && toll > 0; return }
        judgedDest = dest
        val c = st.conditions
        val sunset = Sun.sunsetMinutes(lat, lon)
        val arrive = nowMin() + seconds / 60
        val parts = mutableListOf<String>()
        if (c.noNight) parts += if (arrive <= sunset) "v_before_sunset" else "v_after_sunset"
        if (c.noHighway && toll > 0) tripToll = true // 작은 창의 네/아니요 질문은 내렸다(2026-11-15). 통행료 구간 기록만 남긴다
        if (parts.isNotEmpty()) say(*parts.toTypedArray())
    }

    // '네'면 고속도로를 피하는 길을 내비게이션에 요청하고, 경로에서 IC 와 JC 가 사라진 것을 확인한 뒤에만 바뀌었다고 말한다
    private var switching = 0 // 0 없음, 1 옵션 변경 후 확인 중, 2 무료 도로로 새로 요청 후 확인 중

    fun answer(yes: Boolean) {
        val kind = live.value.qKind
        live.update { it.copy(question = null, qKind = null) }
        if (kind == "work") {
            if (yes) { st.update(lastTripStart) { it.copy(work = true) }; say("v_work_saved") }
            return
        }
        if (!yes) { say("v_keep"); return }
        switching = 1
        say("v_requested")
        navi?.changeRouteOption(RouteOption.FREE)
        navi?.requestReRoute()
        main.postDelayed({ checkSwitch() }, 4000)
    }

    private fun checkSwitch() {
        if (switching == 0) return
        navi?.getTBTInfo()
        main.postDelayed({
            if (switching == 0) return@postDelayed
            if (!turnsHighway) { switched(); return@postDelayed }
            if (switching == 1) {
                val d = lastDest ?: return@postDelayed
                Log.d(TAG, "re-request ${d.name} with FREE")
                switching = 2
                navi?.requestRoute(RouteInfo(d.latitude, d.longitude, d.displayLatitude, d.displayLongitude, d.name ?: "", d.poiId ?: "", d.address ?: "", d.poiSubId ?: "", RouteOption.FREE, d.poiProvider ?: "", true))
                main.postDelayed({ checkSwitch() }, 5000)
            } else { switching = 0; Log.d(TAG, "switch failed, still highway") }
        }, 1500)
    }

    private fun switched() {
        switching = 0
        tripToll = false
        say("v_changed")
    }

    private fun onGear(g: Int) {
        Log.d(TAG, "gear $g")
        val drivingNow = g == VehicleGear.GEAR_DRIVE || g == VehicleGear.GEAR_REVERSE
        val was = live.value.driving
        if (drivingNow && !was) beginTrip() else if (g == VehicleGear.GEAR_PARK && was) endTrip()
    }

    private fun beginTrip() {
        tripStartMs = System.currentTimeMillis(); tripStartOdo = live.value.odoKm
        vehicle?.getOdometer()?.getCurrentDistanceDriven({ km -> if (km != null) { tripStartOdo = km; live.update { it.copy(odoKm = km) } } }, { Log.e(TAG, "odo get", it) })
        tripToll = false; dropAt = 0L; brakeOnAt = 0L
        tripFrom = address; tripBrakes = 0; tripHb.clear(); tripLeads.clear(); tripAsked = false; leadPendingAt = 0
        live.update { it.copy(question = null, qKind = null) }
        tripNight = st.conditions.noNight && nowMin() > (live.value.sunsetMin ?: 1440)
        live.update { it.copy(driving = true, tripKm = 0f, hardBrakes = 0, signals = 0, body = false) } // 몸 안내는 기어 P 인 동안만
        if (tripNight) say("v_night_start")
        navi?.getCurrentLocationInfo()
        judgedDest = null
        main.post(poll)
    }

    private fun endTrip() {
        val l = live.value
        val to = lastDest?.name?.takeIf { it.isNotBlank() } ?: address
        val t = Trip(tripStartMs, System.currentTimeMillis(), l.tripKm, l.hardBrakes, l.signals, 0, tripNight, tripToll,
            tripFrom, to, tripBrakes, tripHb.toList(), tripLeads.toList(), tripAsked)
        st.add(t); lastTripStart = t.startMs
        live.update { it.copy(driving = false) }
        val kept = !t.night && !t.tollRoad
        val keys = mutableListOf(if (kept) "v_end_ok" else "v_end_ng")
        if (t.hardBrakes > 0) keys += "v_hb" + t.hardBrakes.coerceAtMost(3)
        say(*keys.toTypedArray())
        Gestures.log("하루", "%.1fkm 급제동 %d회 방향지시등 %d회 %s".format(t.km, t.hardBrakes, t.signals, if (kept) "지킨 날" else "못 지킨 날"), "차", "%.1fkm 주행을 기록했어요".format(t.km))
        dayLetter(st.trips())?.let { Gestures.hint(it) } // 주차 직후 오늘 한 줄
        Gestures.onPark()
    }

    // 급제동: 센서 시각으로 1초 안에 시속 15km 이상 감속(약 0.4g)하고, 그 앞뒤 2초 안에 브레이크 페달이 밟혔을 때 한 번.
    // 속도와 페달 신호는 서로 다른 통로로 늦게 오기도 해서 시각 창으로 맞춘다
    private val speeds = ArrayDeque<Pair<Long, Float>>() // (센서 시각 ms, km/h)
    private var dropAt = 0L
    private var brakeOnAt = 0L

    private fun onSpeed(kph: Float, sensorMs: Long) {
        live.update { it.copy(speedKph = kph) }
        speeds.addLast(sensorMs to kph)
        while (speeds.isNotEmpty() && sensorMs - speeds.first().first > 1000) speeds.removeFirst()
        val peak = speeds.maxOf { it.second }
        if (peak - kph >= 15f && android.os.SystemClock.elapsedRealtime() - dropAt > 3000) {
            dropAt = android.os.SystemClock.elapsedRealtime()
            if (live.value.brake || dropAt - brakeOnAt <= 2000) countHardBrake()
        }
    }

    private fun countHardBrake() {
        if (!live.value.driving) return
        dropAt = 0L.also { brakeOnAt = 0L }
        tripHb += HardBrake(System.currentTimeMillis(), address)
        Gestures.hardBrake()
        live.update { it.copy(hardBrakes = it.hardBrakes + 1) }
        speeds.clear()
    }

    private fun onBrake(on: Boolean) {
        if (on && !live.value.brake) {
            if (live.value.driving && live.value.speedKph >= 10f) tripBrakes++
            brakeOnAt = android.os.SystemClock.elapsedRealtime()
            if (dropAt > 0 && brakeOnAt - dropAt <= 2000) countHardBrake()
        }
        live.update { it.copy(brake = on) }
    }

    private fun onSignal(s: TurnSignalValue) {
        if (s != TurnSignalValue.OFF && lastSignal == TurnSignalValue.OFF && live.value.driving) {
            live.update { it.copy(signals = it.signals + 1) }
            leadPendingAt = System.currentTimeMillis(); navi?.getTBTInfo()
        }
        lastSignal = s
    }

    private fun onOdo(km: Float?) {
        if (km == null) return
        live.update { l ->
            val start = tripStartOdo ?: if (l.driving) km.also { tripStartOdo = it } else null
            l.copy(odoKm = km, tripKm = if (l.driving && start != null) (km - start).coerceAtLeast(0f) else l.tripKm)
        }
    }


    fun say(vararg keys: String) {
        return // 인디시아는 음성으로 말하지 않는다(2026-11-12 원칙: 주행 중 음성 없음). 옛 차증 음성 안내를 끈다
        @Suppress("UNREACHABLE_CODE")
        val v = voice ?: return
        live.update { it.copy(lastSaid = keys.joinToString(" ") { k -> v.lines[k] ?: k }) }
        v.say(*keys)
    }

    fun store() = st
}
