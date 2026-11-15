package app.chajeung

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log

/** 안내 문장을 차례로 들려준다. 문장 목록과 녹음은 voice/lines.tsv 가 정본 */
class Voice(private val ctx: Context) {
    private val queue = ArrayDeque<String>()
    private var playing: MediaPlayer? = null

    val lines = mapOf(
        "v_before_sunset" to "해 지기 전에 도착해요.",
        "v_after_sunset" to "도착이 해 진 뒤예요. 오늘은 가까운 곳에서 멈추는 게 좋겠어요.",
        "v_highway_q" to "고속도로를 지나는 길이에요. 고속도로를 피하는 길로 바꿀까요?",
        "v_changed" to "고속도로를 피하는 무료 도로 길로 바꿨어요.",
        "v_keep" to "알겠어요. 이번 주행은 기록에 남겨 둘게요.",
        "v_night_start" to "지금은 해가 진 뒤예요. 오늘 주행은 야간 운전으로 기록돼요.",
        "v_end_ok" to "주행을 기록했어요. 고른 조건을 모두 지켰어요.",
        "v_end_ng" to "주행을 기록했어요. 조건을 벗어난 구간이 있었어요.",
        "v_hb1" to "급제동이 한 번 있었어요.",
        "v_hb2" to "급제동이 두 번 있었어요.",
        "v_hb3" to "급제동이 세 번 넘게 있었어요.",
        "v_requested" to "내비게이션에 고속도로를 피하는 길을 요청했어요.",
        "v_work_q" to "업무 주행이었나요?",
        "v_work_saved" to "업무 주행으로 기록했어요.",
    )

    fun say(vararg keys: String) {
        synchronized(queue) { queue.addAll(keys) }
        if (playing == null) next()
    }

    private fun next() {
        val key = synchronized(queue) { queue.removeFirstOrNull() } ?: run { playing = null; return }
        val id = ctx.resources.getIdentifier(key, "raw", ctx.packageName)
        // 영상 편집에서 소리를 같은 시점에 맞추려고 재생 시각을 남긴다
        Log.i("ChajeungVoice", "PLAY $key ${System.currentTimeMillis()}")
        playing = MediaPlayer.create(ctx, id)?.apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE).build())
            setOnCompletionListener { it.release(); playing = null; next() }
            start()
        }
        if (playing == null) next()
    }
}
