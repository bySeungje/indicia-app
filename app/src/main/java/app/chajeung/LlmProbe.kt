package app.chajeung

import android.content.Context
import android.util.Log
import ai.pleos.playground.llm.LLM
import ai.pleos.playground.llm.constant.Mode
import ai.pleos.playground.llm.listener.ResultListener

/** 시험용: 차 안 LLM 이 답하는지 본다. am start ... --es llm on|server */
object LlmProbe {
    fun run(ctx: Context, which: String) {
        val mode = if (which == "server") Mode.SERVER else Mode.ON_DEVICE
        val llm = runCatching { LLM(ctx.applicationContext, mode).also { it.initialize() } }
            .onFailure { Log.e("ChajeungLLM", "init $mode", it) }.getOrNull() ?: return
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            runCatching {
                llm.generateContent("운전 습관을 한 문장으로 말해 줘: 이번 달 급제동 3번이 모두 평일 오후 6시대에 있었다.", object : ResultListener {
                    override fun onResponse(s: String, done: Boolean) { Log.i("ChajeungLLM", "resp done=$done $s") }
                    override fun onError(e: String) { Log.e("ChajeungLLM", "error $e") }
                })
            }.onFailure { Log.e("ChajeungLLM", "gen", it) }
        }, 3000)
    }
}
