package app.chajeung

import org.junit.Test

class RetouchTest {
    @Test fun selfCheck() = Retouch.selfCheck()
}

class DayLetterTest {
    @Test fun letter() {
        val now = System.currentTimeMillis()
        val t = Trip(now - 3_600_000, now, 12f, 1, 0, 0, false, false, work = true)
        val s = dayLetter(listOf(t), now)!!
        check(s == "오늘 1번, 12km 달렸어요. 업무 주행 1번. 급제동 1번.") { s }
        check(dayLetter(emptyList(), now) == null)
    }
}
