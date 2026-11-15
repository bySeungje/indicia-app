package app.chajeung

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 운전자가 고른 조건 */
data class Conditions(
    val noNight: Boolean = true,
    val noHighway: Boolean = true,
)

/** 급제동 한 번: 언제, 어디서 */
data class HardBrake(val ms: Long, val place: String)

/** 주행 한 번의 기록. 차에서 나온 값만 담는다 */
data class Trip(
    val startMs: Long,
    val endMs: Long,
    val km: Float,
    val hardBrakes: Int,
    val signals: Int,
    val warnings: Int,
    val night: Boolean,
    val tollRoad: Boolean,
    val from: String = "",
    val to: String = "",
    val brakes: Int = 0,                       // 시속 10 이상에서 브레이크를 밟은 횟수
    val hbEvents: List<HardBrake> = emptyList(),
    val leads: List<Float> = emptyList(),      // 방향지시등을 켠 때 회전까지 남은 초
    val highwayAsked: Boolean = false,
    val work: Boolean = false,
    val submitted: Boolean = false,
) {
    val kept get() = !night && !tollRoad

    fun json(): JSONObject = JSONObject().put("s", startMs).put("e", endMs).put("km", km.toDouble())
        .put("hb", hardBrakes).put("sig", signals).put("w", warnings).put("n", night).put("t", tollRoad)
        .put("from", from).put("to", to).put("br", brakes)
        .put("hbe", JSONArray(hbEvents.map { JSONArray().put(it.ms).put(it.place) }))
        .put("lead", JSONArray(leads.map { it.toDouble() }))
        .put("ha", highwayAsked).put("work", work).put("sub", submitted)

    companion object {
        // 옛 기록(새 칸이 없는 것)도 읽는다
        fun of(o: JSONObject) = Trip(
            o.getLong("s"), o.getLong("e"), o.getDouble("km").toFloat(), o.getInt("hb"),
            o.getInt("sig"), o.optInt("w"), o.getBoolean("n"), o.getBoolean("t"),
            o.optString("from"), o.optString("to"), o.optInt("br"),
            o.optJSONArray("hbe")?.let { a -> (0 until a.length()).map { a.getJSONArray(it).let { e -> HardBrake(e.getLong(0), e.getString(1)) } } } ?: emptyList(),
            o.optJSONArray("lead")?.let { a -> (0 until a.length()).map { a.getDouble(it).toFloat() } } ?: emptyList(),
            o.optBoolean("ha"), o.optBoolean("work"), o.optBoolean("sub"),
        )
    }
}

/** 몸짓 한 건. 값의 보증은 Gestures 한 곳에서 한다. src 는 "차" 또는 "흉내" */
data class Gesture(val kind: String, val ms: Long, val cell: String, val kph: Float, val src: String) {
    fun json(): JSONObject = JSONObject().put("k", kind).put("t", ms).put("c", cell).put("v", kph.toDouble()).put("src", src)
    companion object {
        fun of(o: JSONObject) = Gesture(o.getString("k"), o.getLong("t"), o.getString("c"), o.getDouble("v").toFloat(), o.getString("src"))
    }
}

class Store(ctx: Context) {
    private val sp = ctx.getSharedPreferences("chajeung", Context.MODE_PRIVATE)

    var conditions: Conditions
        get() = Conditions(sp.getBoolean("noNight", true), sp.getBoolean("noHighway", true))
        set(c) = sp.edit().putBoolean("noNight", c.noNight).putBoolean("noHighway", c.noHighway).apply()

    fun trips(): List<Trip> {
        val a = JSONArray(sp.getString("trips", "[]"))
        return (0 until a.length()).map { Trip.of(a.getJSONObject(it)) }
    }

    private fun save(list: List<Trip>) = sp.edit().putString("trips", JSONArray(list.map { it.json() }).toString()).apply()

    fun add(t: Trip) = save(trips() + t)

    /** 주행을 시작 시각으로 찾아 고친다(업무 표시, 제출 표시) */
    fun update(startMs: Long, f: (Trip) -> Trip) = save(trips().map { if (it.startMs == startMs) f(it) else it })

    fun rawTrips(): String = sp.getString("trips", "[]")!!

    // 몸짓: 최근 300건만 둔다(기억한 자리는 places 에 따로 쌓인다)
    fun gestures(): List<Gesture> = JSONArray(sp.getString("gestures", "[]")).let { a -> (0 until a.length()).map { Gesture.of(a.getJSONObject(it)) } }
    fun addGesture(g: Gesture) = sp.edit().putString("gestures", JSONArray((gestures() + g).takeLast(300).map { it.json() }).toString()).apply()

    // 기억한 자리: 다듬기 이름(숨, 눈) 아래 칸마다 몇 번
    private fun placesJson() = JSONObject(sp.getString("places", "{}")!!)
    fun places(kind: String): Map<String, Int> = placesJson().optJSONObject(kind)?.let { o -> o.keys().asSequence().associateWith { o.getInt(it) } } ?: emptyMap()
    fun remember(kind: String, cell: String) {
        val all = placesJson(); val o = all.optJSONObject(kind) ?: JSONObject()
        all.put(kind, o.put(cell, o.optInt(cell) + 1)); sp.edit().putString("places", all.toString()).apply()
    }
    /** 한 번 쌓은 것을 되돌린다(비 온 날로 밝혀진 짧은 와이퍼) */
    fun unremember(kind: String, cell: String) {
        val all = placesJson(); val o = all.optJSONObject(kind) ?: return
        val n = o.optInt(cell) - 1; if (n > 0) o.put(cell, n) else o.remove(cell)
        sp.edit().putString("places", all.toString()).apply()
    }
    fun forget(kind: String, cell: String) {
        val all = placesJson(); all.optJSONObject(kind)?.remove(cell); sp.edit().putString("places", all.toString()).apply()
    }

    // 좌석 흐름: 날짜마다 그날 마지막 주차 때 앞뒤와 등받이, 출처
    data class SeatDay(val date: String, val seat: Retouch.Seat, val src: String)
    fun seatDays(): List<SeatDay> = JSONObject(sp.getString("seat_days", "{}")!!).let { o ->
        o.keys().asSequence().sorted().map { d -> o.getJSONArray(d).let { SeatDay(d, Retouch.Seat(it.getInt(0), it.getInt(1)), it.getString(2)) } }.toList()
    }
    fun putSeatDay(d: SeatDay) = sp.edit().putString("seat_days",
        JSONObject(sp.getString("seat_days", "{}")!!).put(d.date, JSONArray().put(d.seat.slide).put(d.seat.recline).put(d.src)).toString()).apply()

    /** 몸 살피기. 켠 날이 기준일의 시작이다(그날 이후 처음 남긴 좌석이 기준) */
    var bodyOn: Boolean
        get() = sp.getBoolean("body_on", false)
        set(v) {
            val since = if (!v) "" else bodySince.ifEmpty { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.KOREA).format(java.util.Date()) }
            sp.edit().putBoolean("body_on", v).putString("body_since", since).apply()
        }
    var bodySince: String
        get() = sp.getString("body_since", "")!!
        set(v) = sp.edit().putString("body_since", v).apply()
    var cityOn: Boolean
        get() = sp.getBoolean("city_on", false)
        set(v) = sp.edit().putBoolean("city_on", v).apply()
    var bodySeen: String
        get() = sp.getString("body_seen", "")!!
        set(v) = sp.edit().putString("body_seen", v).apply()

    /** 차 가명의 씨앗. 설치 뒤 처음 부를 때 만든다. 기기 밖으로 나가지 않는다 */
    val aliasSeed: String
        get() = sp.getString("car_alias", null) ?: java.util.UUID.randomUUID().toString().also { sp.edit().putString("car_alias", it).apply() }
}
