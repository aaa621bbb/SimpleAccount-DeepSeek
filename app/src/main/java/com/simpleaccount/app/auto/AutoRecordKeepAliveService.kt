package com.simpleaccount.app.auto

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.simpleaccount.app.MainActivity
import com.simpleaccount.app.R
import com.simpleaccount.app.util.AppLog
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import javax.inject.Inject

/**
 * 无感记账保活：小米/华为会把纯 NotificationListener 杀掉。
 * 开着自动记账时拉起一条低优先级前台通知，让进程不被清掉；
 * 每 4 分钟 requestRebind 一次，并把心跳写进 [AutoRecordRuntime]。
 */
@AndroidEntryPoint
class AutoRecordKeepAliveService : Service() {

    @Inject
    lateinit var runtime: AutoRecordRuntime

    private val handler = Handler(Looper.getMainLooper())
    private val rebindTick = object : Runnable {
        override fun run() {
            resolveRuntime().requestRebind()
            resolveRuntime().markKeepAlive(true)
            handler.postDelayed(this, REBIND_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        resolveRuntime().markKeepAlive(true)
        resolveRuntime().requestRebind()
        handler.postDelayed(rebindTick, REBIND_MS)
        AppLog.i("无感记账: 保活服务已启动")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        resolveRuntime().markKeepAlive(true)
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // 用户划掉任务卡片后仍要活着
        start(this)
    }

    private fun startAsForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL, "无感记账", NotificationManager.IMPORTANCE_MIN)
            ch.setShowBadge(false)
            ch.enableVibration(false)
            ch.enableLights(false)
            nm.createNotificationChannel(ch)
        }
        val launch = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
            .setContentTitle("无感记账运行中")
            .setContentText("正在监听支付通知，点按可打开应用")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(launch)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun resolveRuntime(): AutoRecordRuntime {
        if (::runtime.isInitialized) return runtime
        return EntryPointAccessors.fromApplication(applicationContext, AutoRecordEntryPoint::class.java).runtime()
    }

    override fun onDestroy() {
        handler.removeCallbacks(rebindTick)
        if (::runtime.isInitialized) runtime.markKeepAlive(false)
        super.onDestroy()
        val stillOn = runCatching {
            EntryPointAccessors.fromApplication(applicationContext, AutoRecordEntryPoint::class.java)
                .settings().isAutoRecordEnabled()
        }.getOrDefault(false)
        if (stillOn) {
            AppLog.w("无感记账: 保活被销毁，开关仍开，尝试拉起")
            start(applicationContext)
        }
    }

    companion object {
        private const val CHANNEL = "auto_record_keepalive"
        private const val NOTIF_ID = 42
        private const val REBIND_MS = 4 * 60_000L

        fun start(context: Context) {
            val i = Intent(context, AutoRecordKeepAliveService::class.java)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i)
            else context.startService(i)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AutoRecordKeepAliveService::class.java))
        }
    }
}
