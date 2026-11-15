package app.chajeung

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/** 길에 보태기: 운전자가 켰을 때만 몸짓을 칸, 종류, 10분 시각, 달마다 바뀌는 차 가명으로 줄여 보낸다 */
object City {
    // shortcut: 에뮬레이터에서 맥북의 길 서버로만 간다. 실서비스 주소와 TLS 는 서버를 공개할 때 정한다
    private const val URL_POST = "http://10.0.2.2:8090/g"

    fun payload(st: Store, g: Gesture): JSONObject = JSONObject()
        .put("cell", g.cell).put("kind", g.kind)
        .put("t10", g.ms / 600_000 * 600_000)
        .put("car", Retouch.alias(st.aliasSeed, SimpleDateFormat("yyyy-MM", Locale.KOREA).format(Date(g.ms))))

    /** 길 서버가 세는 몸짓만 보낸다 */
    private val counted = setOf("창문 올림", "바깥 공기 막음", "와이퍼 짧게", "와이퍼 계속")

    fun send(st: Store, g: Gesture) {
        if (!st.cityOn || g.kind !in counted) return
        // 흉내 값(시험 위치)으로 만든 몸짓은 실제 차 한 대로 세지 않게 보내지 않는다
        if (g.src == "흉내") { Log.i("Chajeung", "INDICIA 길 보냄 생략(흉내) ${g.kind}"); return }
        val body = payload(st, g).toString()
        thread {
            runCatching {
                (URL(URL_POST).openConnection() as HttpURLConnection).run {
                    requestMethod = "POST"; doOutput = true; connectTimeout = 3000; readTimeout = 3000
                    setRequestProperty("Content-Type", "application/json")
                    outputStream.use { it.write(body.toByteArray()) }
                    Log.i("Chajeung", "INDICIA 길 보냄 $responseCode $body"); disconnect()
                }
            }.onFailure { Log.w("Chajeung", "city send", it) }
        }
    }
}
