package app.chajeung

import java.util.Calendar

/** 이번 달 기록을 화면 숫자와 습관 문장으로 바꾼다. 차가 잰 값만 센다 */
class Month(all: List<Trip>, now: Calendar = Calendar.getInstance()) {
    private fun cal(ms: Long) = Calendar.getInstance().apply { timeInMillis = ms }
    private fun sameMonth(ms: Long, ref: Calendar) = cal(ms).let { it.get(Calendar.YEAR) == ref.get(Calendar.YEAR) && it.get(Calendar.MONTH) == ref.get(Calendar.MONTH) }
    private val prevRef = (now.clone() as Calendar).apply { add(Calendar.MONTH, -1) }

    val trips = all.filter { sameMonth(it.startMs, now) }
    private val prev = all.filter { sameMonth(it.startMs, prevRef) }
    val work = trips.filter { it.work }.sortedByDescending { it.startMs }

    val km = trips.sumOf { it.km.toDouble() }
    val kept = trips.count { it.kept }
    val keptPct = if (trips.isEmpty()) 0 else kept * 100 / trips.size
    val hardBrakes = trips.sumOf { it.hardBrakes }
    val nights = trips.count { it.night }
    val workKm = work.sumOf { it.km.toDouble() }
    val pending = work.count { !it.submitted }

    /** 요일(월=0) x 시간(6~23시) 급제동 횟수 */
    val heat: Array<IntArray> = Array(7) { IntArray(18) }.also { h ->
        trips.flatMap { it.hbEvents }.forEach { e ->
            val c = cal(e.ms); val hr = c.get(Calendar.HOUR_OF_DAY)
            if (hr in 6..23) h[(c.get(Calendar.DAY_OF_WEEK) + 5) % 7][hr - 6]++
        }
    }

    /** 이번 달 주별(1~4주, 5주 이후는 4주에 넣는다) 부드러운 감속 비율. 밟은 기록이 없는 주는 null */
    val smoothByWeek: List<Int?> = (0..3).map { w ->
        val t = trips.filter { minOf(3, (cal(it.startMs).get(Calendar.DAY_OF_MONTH) - 1) / 7) == w }
        val b = t.sumOf { it.brakes }
        if (b == 0) null else ((b - t.sumOf { it.hardBrakes }.coerceAtMost(b)) * 100 / b)
    }

    private fun avgLead(ts: List<Trip>) = ts.flatMap { it.leads }.takeIf { it.isNotEmpty() }?.average()

    /** 가장 눈에 띈 것 한 줄. 급제동이 한 시간대에 몰렸으면 그것, 아니면 잘한 것 */
    fun headline(): Pair<String, String?> {
        val events = trips.flatMap { it.hbEvents }
        if (events.isEmpty()) return (if (trips.isEmpty()) "이번 달 주행이 아직 없어요" else "이번 달은 급제동 없이 부드럽게 멈췄어요") to null
        val byHour = events.groupBy { cal(it.ms).get(Calendar.HOUR_OF_DAY) }.maxBy { it.value.size }
        val weekday = byHour.value.all { cal(it.ms).get(Calendar.DAY_OF_WEEK) in Calendar.MONDAY..Calendar.FRIDAY }
        val h = byHour.key
        val slot = (if (weekday) "평일 " else "") + (if (h < 12) "오전 " else "오후 ") + "${if (h % 12 == 0) 12 else h % 12}시대"
        val place = byHour.value.groupBy { it.place }.maxBy { it.value.size }.takeIf { it.key.isNotBlank() && it.value.size >= 2 }?.key
        val tail = place?.let { ", $it 근처였어요" } ?: "였어요"
        val lead = if (byHour.value.size == events.size) "급제동 ${events.size}번이 모두 " else "급제동 ${events.size}번 중 ${byHour.value.size}번이 "
        return lead to "$slot$tail"
    }

    /** 습관 세 줄: 제목과 근거 숫자 */
    fun patterns(c: Conditions): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        avgLead(trips)?.let { now ->
            val before = avgLead(prev)
            val diff = before?.let { now - it }
            val title = when {
                diff == null -> "방향지시등을 회전 %.1f초 전에 켜요".format(now)
                diff >= 0.3 -> "방향지시등을 켜는 때가 %.1f초 빨라졌어요".format(diff)
                diff <= -0.3 -> "방향지시등을 켜는 때가 %.1f초 늦어졌어요".format(-diff)
                else -> "방향지시등을 지난달처럼 켜요"
            }
            out += title to ("회전 지점 평균 %.1f초 전".format(now) + (before?.let { ", 지난달 %.1f초 전".format(it) } ?: ""))
        }
        if (c.noNight && trips.isNotEmpty()) out += (if (nights == 0) "해 지기 전에 ${trips.size}번 모두 마쳤어요" else "해 진 뒤에 ${nights}번 운전했어요") to "주행 ${trips.size}회 중 야간 ${nights}회"
        val asked = trips.count { it.highwayAsked }
        if (c.noHighway && asked > 0) {
            val avoided = trips.count { it.highwayAsked && !it.tollRoad }
            out += (if (avoided == asked) "고속도로 없이 다녔어요" else "고속도로를 ${asked - avoided}번 지났어요") to "고속도로 경로 안내 ${asked}번 중 ${avoided}번 피함"
        }
        return out.take(3)
    }

    companion object {
        /** 자체 점검: 급제동 시간대 묶기와 주별 비율 */
        fun selfCheck() {
            val now = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 9, 15, 0) }
            fun at(d: Int, h: Int) = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, d, h, 10) }.timeInMillis
            val t = listOf(
                Trip(at(2, 18), at(2, 19), 10f, 2, 1, 0, false, false, brakes = 8, hbEvents = listOf(HardBrake(at(2, 18), "수정구청 사거리"), HardBrake(at(2, 18), "수정구청 사거리"))),
                Trip(at(3, 9), at(3, 10), 5f, 1, 0, 0, false, true, brakes = 4, hbEvents = listOf(HardBrake(at(3, 9), "판교IC")), highwayAsked = true),
            )
            val m = Month(t, now)
            check(m.headline() == ("급제동 3번 중 2번이 " to "평일 오후 6시대, 수정구청 사거리 근처였어요")) { m.headline().toString() }
            check(m.smoothByWeek[0] == 75) { m.smoothByWeek.toString() } // 밟음 12, 급제동 3
            check(m.keptPct == 50 && m.heat[0][12] == 2)
        }
    }
}
