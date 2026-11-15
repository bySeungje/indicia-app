package app.chajeung

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 차를 켜면 앱을 열지 않아도 기록과 표시 창을 시작한다 */
// shortcut: 프로세스를 붙잡는 것은 늘 떠 있는 표시 창뿐이다. 실차에서 꺼지는 일이 보이면 포그라운드 서비스로 옮긴다
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) Drive.start(ctx)
    }
}
