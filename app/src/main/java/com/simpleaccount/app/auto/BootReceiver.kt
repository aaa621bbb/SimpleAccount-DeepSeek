package com.simpleaccount.app.auto

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.simpleaccount.app.util.AppLog

/** 开机后若无感记账是开着的，把保活服务拉起来。 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        AppLog.i("无感记账: 开机广播")
        runCatching { AutoRecordKeepAliveService.start(context) }
    }
}
