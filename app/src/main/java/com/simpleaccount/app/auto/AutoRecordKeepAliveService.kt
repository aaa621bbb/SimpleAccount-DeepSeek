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
import android.os.IBinder
import com.simpleaccount.app.MainActivity
import com.simpleaccount.app.R
import com.simpleaccount.app.util.AppLog

/**
 * 无感记账保活：小米/华为会把纯 NotificationListener 杀掉。
 * 开着自动记账时拉起一条低优先级前台通知，让进程不被清掉。
 */
class AutoRecordKeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        AppLog.i("无感记账: 保活服务已启动")
        runCatching {
            requestListenerRebind()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        return START_STICKY
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
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
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

    private fun requestListenerRebind() {
        val cn = android.content.ComponentName(this, PaymentNotificationListenerService::class.java)
        if (Build.VERSION.SDK_INT >= 24) {
            android.service.notification.NotificationListenerService.requestRebind(cn)
        }
    }

    companion object {
        private const val CHANNEL = "auto_record_keepalive"
        private const val NOTIF_ID = 42

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
