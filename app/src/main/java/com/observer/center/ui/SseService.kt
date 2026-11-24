package com.observer.center.ui

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.observer.center.api.Api
import com.observer.center.api.GsonHolder
import com.observer.center.api.Notify
import com.observer.center.api.Thread
import okhttp3.Call

/**
 * 常驻前台服务：独立维持 SSE 长连，访客来信时发本地通知。
 * 主界面在前台时由界面自己发通知，服务只负责后台。
 */
class SseService : Service() {
    private var call: Call? = null
    private val notified = HashMap<Int, Long>()
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        const val CHANNEL = "chat"
        const val ONGOING_ID = 42
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = ongoingNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(ONGOING_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(ONGOING_ID, n)
        }
        startSse()
        return START_STICKY
    }

    private fun ongoingNotification(): Notification {
        Notify.ensure(this)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("观测中心")
            .setContentText("实时监听访客消息中")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun startSse() {
        call?.cancel()
        call = Api.sse(
            "/plugin/ObservationCenter/stream/admin",
            onEvent = { event, data -> handle(event, data) },
            onFail = {
                handler.postDelayed({ startSse() }, 3000)
            }
        )
    }

    private fun handle(event: String, data: String) {
        if (event != "thread") return
        runCatching {
            val th = GsonHolder.gson.fromJson(data, Thread::class.java) ?: return
            val last = th.last ?: return
            if (last.from != "visitor") return
            if (Foreground.active) return
            val ts = last.ts ?: 0L
            if (ts > (notified[th.id] ?: 0L)) {
                notified[th.id] = ts
                val name = th.name.ifEmpty { "访客" }
                Notify.push(this, "新消息 · $name", last.body, th.id)
            }
        }
    }

    override fun onDestroy() {
        call?.cancel()
        super.onDestroy()
    }
}

object Foreground {
    @Volatile
    var active: Boolean = false
}
