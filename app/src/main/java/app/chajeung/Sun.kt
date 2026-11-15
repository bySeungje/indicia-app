package app.chajeung

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.*

/** 위치와 날짜로 일몰 시각(그날 0시부터 분)을 구한다. NOAA 근사식 */
object Sun {
    fun sunsetMinutes(lat: Double, lon: Double, cal: Calendar = Calendar.getInstance()): Int {
        val n = cal.get(Calendar.DAY_OF_YEAR)
        val g = 2 * PI / 365 * (n - 1)
        val eq = 229.18 * (0.000075 + 0.001868 * cos(g) - 0.032077 * sin(g) - 0.014615 * cos(2 * g) - 0.040849 * sin(2 * g))
        val decl = 0.006918 - 0.399912 * cos(g) + 0.070257 * sin(g) - 0.006758 * cos(2 * g) +
            0.000907 * sin(2 * g) - 0.002697 * cos(3 * g) + 0.00148 * sin(3 * g)
        val latR = Math.toRadians(lat)
        val ha = Math.toDegrees(acos(cos(Math.toRadians(90.833)) / (cos(latR) * cos(decl)) - tan(latR) * tan(decl)))
        val utcMin = 720 - 4 * (lon - ha) - eq
        val offMin = TimeZone.getDefault().getOffset(cal.timeInMillis) / 60000
        return ((utcMin + offMin).roundToInt() + 1440) % 1440
    }

    fun hhmm(min: Int) = "%d시 %02d분".format(min / 60, min % 60)
}
