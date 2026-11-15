package app.chajeung

import java.security.MessageDigest
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sqrt

/** 다듬기의 계산. 안드로이드에 기대지 않아 단위 시험으로 잡는다 */
object Retouch {
    // 100m 칸: 위도 0.0009도 ≈ 100m, 경도는 위도에 맞춰 늘린다(창원 35도에서 약 0.0011도)
    private const val LAT_STEP = 0.0009
    private fun lonStep(lat: Double) = LAT_STEP / cos(Math.toRadians(lat))

    fun cell(lat: Double, lon: Double): String =
        "${floor(lat / LAT_STEP).toLong()}:${floor(lon / lonStep(lat)).toLong()}"

    fun center(cell: String): Pair<Double, Double> {
        val (a, b) = cell.split(":").map { it.toLong() }
        val lat = (a + 0.5) * LAT_STEP
        return lat to (b + 0.5) * lonStep(lat)
    }

    /** 두 점 사이 거리(m). 100m 칸 사이에서는 평면 근사로 충분하다 */
    fun meters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dy = (lat2 - lat1) * 111_000.0
        val dx = (lon2 - lon1) * 111_000.0 * cos(Math.toRadians((lat1 + lat2) / 2))
        return sqrt(dx * dx + dy * dy)
    }

    /** 기억한 칸 가운데 300m 안으로 다가오고 있는 것 하나. 이미 그 칸 안이면 예고가 늦었으므로 내지 않는다 */
    fun ahead(lat: Double, lon: Double, prevLat: Double, prevLon: Double, remembered: Collection<String>): String? {
        val here = cell(lat, lon)
        return remembered.filter { it != here }.map { c ->
            val (clat, clon) = center(c)
            Triple(c, meters(lat, lon, clat, clon), meters(prevLat, prevLon, clat, clon))
        }.filter { (_, d, prev) -> d <= 300 && d < prev }.minByOrNull { it.second }?.first
    }

    /** 와이퍼: 20초 안에 꺼지면 짧게(흐려진 앞길), 넘으면 계속(비) */
    fun shortWipe(onMs: Long, offMs: Long) = offMs - onMs in 0..20_000

    /** 와이퍼 한 번을 끝냈을 때: "비"(계속 돎), "눈"(비 없는 날 짧게, 기억할 자리), null(비 온 날의 짧은 것은 쌓지 않음).
     *  shortcut: SDK 는 켜짐과 꺼짐만 줘서 1~2회는 20초 안에 꺼진 것으로 대신 잰다. 실차에서 와이퍼 횟수가 오면 그것으로 바꾼다 */
    fun wipe(onMs: Long, offMs: Long, rainyToday: Boolean): String? = when {
        !shortWipe(onMs, offMs) -> "비"
        rainyToday -> null
        else -> "눈"
    }

    data class Seat(val slide: Int, val recline: Int)

    /** 몸: 앞뒤가 끝 쪽 90% 를 넘고 기준보다 뒤로, 등받이가 기준보다 5 이상 젖혀짐 */
    fun bodyAlert(base: Seat, today: Seat) =
        today.slide > 90 && today.slide > base.slide && today.recline - base.recline >= 5

    /** 기다림: 충전 시간 가운데 충전기 탓과 배터리(80% 위에서 차가 늦춤) 탓을 가른다. 분 단위 */
    data class Receipt(val minutes: Double, val actualKw: Double, val ratedKw: Double, val chargerMin: Double, val batteryMin: Double)

    fun receipt(samples: List<Pair<Long, Double>>, capacityKwh: Double, ratedKw: Double): Receipt? {
        if (samples.size < 2) return null
        var below = 0.0; var above = 0.0; var eBelow = 0.0; var eAbove = 0.0
        samples.zipWithNext { (t0, s0), (t1, s1) ->
            val min = (t1 - t0) / 60_000.0; val kwh = (s1 - s0).coerceAtLeast(0.0) / 100 * capacityKwh
            if (s0 >= 80) { above += min; eAbove += kwh } else { below += min; eBelow += kwh }
        }
        val total = below + above
        if (total <= 0) return null
        val actual = (eBelow + eAbove) / (total / 60)
        val chargerMin = (below - eBelow / ratedKw * 60).coerceAtLeast(0.0)
        val batteryMin = (above - eAbove / ratedKw * 60).coerceAtLeast(0.0)
        return Receipt(total, actual, ratedKw, chargerMin, batteryMin)
    }

    /** 갇힌 시간: 지금 잔량으로 공조 조합 셋(지금, 약하게, 끔)마다 버틸 시간(시간). drainKw 는 지난 15분 평균 소모 */
    fun hvacKw(fan: Int) = if (fan <= 0) 0.0 else 0.3 + fan * 0.15

    fun survive(kwh: Double, drainKw: Double, fan: Int): List<Double> {
        val base = (drainKw - hvacKw(fan)).coerceAtLeast(0.2) // 공조를 빼도 차는 조금 쓴다
        val weak = (fan / 2).coerceAtLeast(1)
        return listOf(base + hvacKw(fan), base + hvacKw(weak), base).map { kwh / it }
    }

    /** 차 가명: 설치 때 만든 씨앗과 달(yyyy-MM)로 만들어 달마다 바뀐다. 거꾸로 씨앗을 알 수 없다 */
    fun alias(seed: String, month: String): String =
        MessageDigest.getInstance("SHA-256").digest("$seed|$month".toByteArray()).take(6).joinToString("") { "%02x".format(it) }

    fun selfCheck() {
        // 칸 묶기: 같은 칸, 150m 떨어진 다른 칸
        val c = cell(35.2300, 128.6900)
        check(c == cell(35.23001, 128.69001)) { "same cell" }
        check(c != cell(35.2314, 128.6900)) { "150m north is another cell" }
        val (clat, clon) = center(c); check(meters(35.23, 128.69, clat, clon) < 100) { "center within cell" }
        // 다음 칸 예고: 다가오면 내고, 멀어지면 안 낸다
        val target = cell(35.2320, 128.6900); val (tl, tn) = center(target)
        check(ahead(tl - 0.0020, tn, tl - 0.0030, tn, listOf(target)) == target) { "approaching 220m" }
        check(ahead(tl - 0.0020, tn, tl - 0.0010, tn, listOf(target)) == null) { "moving away" }
        check(ahead(tl - 0.0050, tn, tl - 0.0060, tn, listOf(target)) == null) { "too far 550m" }
        // 비 온 날 와이퍼 제외
        check(shortWipe(0, 8_000)); check(!shortWipe(0, 60_000))
        check(wipe(0, 8_000, false) == "눈"); check(wipe(0, 8_000, true) == null); check(wipe(0, 60_000, false) == "비")
        // 몸 문턱
        check(bodyAlert(Seat(60, 30), Seat(92, 36))); check(!bodyAlert(Seat(60, 30), Seat(92, 33))); check(!bodyAlert(Seat(60, 30), Seat(85, 40)))
        // 충전 몫: 50kW 정격, 77kWh. 70→80 을 15분(30.8kW), 80→85 를 10분(23.1kW)
        val r = receipt(listOf(0L to 70.0, 900_000L to 80.0, 1_500_000L to 85.0), 77.0, 50.0)!!
        check(r.minutes == 25.0 && r.chargerMin in 5.7..5.8 && r.batteryMin in 5.3..5.4) { "split $r" }
        // 버틸 시간: 끔이 가장 길고 지금이 가장 짧다
        val s = survive(20.0, 2.0, 4); check(s[0] < s[1] && s[1] < s[2]) { "survive $s" }
        check(alias("a", "2026-11") != alias("a", "2026-12") && alias("a", "2026-11").length == 12)
    }
}
