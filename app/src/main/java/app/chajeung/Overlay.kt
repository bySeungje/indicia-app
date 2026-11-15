package app.chajeung

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.toArgb
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * 내비게이션 위에 뜨는 작은 창. 평소에는 숨어 있고, 한 줄 안내(hint)가 생겼을 때만 그 줄을 보인다(8초 뒤 Gestures 가 지운다).
 * 주행 중이면 그 위에 주행 상태 한 줄. 지도를 덜 가리게 오른쪽 위에 작게. 우리 앱 화면이 앞에 있을 때는 숨긴다.
 */
object Overlay {
    val appInFront = MutableStateFlow(false)
    private var card: LinearLayout? = null

    fun show(ctx: Context) {
        if (card != null) return
        if (!Settings.canDrawOverlays(ctx)) { Log.w("Chajeung", "overlay not allowed"); return }
        val wm = ctx.getSystemService(WindowManager::class.java)

        fun text(size: Float, color: Int, bold: Boolean = false) = TextView(ctx).apply {
            textSize = size; setTextColor(color); if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        fun round(fill: Int, stroke: Int, radius: Float) = GradientDrawable().apply {
            cornerRadius = radius; setColor(fill); setStroke(3, stroke)
        }

        val status = text(20f, 0, true)
        val hintV = text(22f, 0, true).apply { maxWidth = 620 }
        val c = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(26, 12, 26, 14)
            elevation = 8f
            addView(status); addView(hintV)
            // 주차 중에 누르면 앱을 연다
            setOnClickListener {
                if (!Drive.live.value.driving) ctx.startActivity(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.END; x = 200; y = 110 } // 내비 화면 오른쪽 위, 2D 버튼 왼쪽
        runCatching { wm.addView(c, lp) }.onFailure { Log.e("Chajeung", "overlay", it); return }
        card = c

        // 차의 밤 모드를 따라 앱과 같은 색을 입힌다. 모드가 바뀌면 바로 다시 칠한다
        fun paint() {
            val p = paletteOf(ctx)
            c.background = round(p.s1.toArgb(), p.ln.toArgb(), 24f)
            status.setTextColor(if (Drive.live.value.driving) p.wn.toArgb() else p.tx.toArgb())
            hintV.setTextColor(p.ac.toArgb())
        }
        paint()
        ctx.applicationContext.registerComponentCallbacks(object : android.content.ComponentCallbacks {
            override fun onConfigurationChanged(n: android.content.res.Configuration) = paint()
            @Deprecated("") override fun onLowMemory() {}
        })

        CoroutineScope(Dispatchers.Main).launch {
            Drive.live.combine(appInFront) { l, front -> l to front }.collect { (l, front) ->
                // 지도를 가리지 않게 평소에는 숨긴다. 한 줄 안내가 있을 때만, 주행 중이든 주차 중이든 같다
                c.visibility = if (front || l.hint == null) View.GONE else View.VISIBLE
                status.visibility = if (l.driving) View.VISIBLE else View.GONE
                status.text = "●  주행 기록 중 %.1fkm".format(l.tripKm)
                hintV.text = l.hint ?: ""
                paint()
            }
        }
    }
}
