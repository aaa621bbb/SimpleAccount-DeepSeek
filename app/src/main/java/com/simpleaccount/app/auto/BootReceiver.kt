package com.simpleaccount.app.auto

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.simpleaccount.app.util.AppLog
import dagger.hilt.android.EntryPointAccessors

/**
 * 开机 / 快速开机 / 覆盖安装后，若无感记账开关开着，把保活 + 监听重绑拉起来。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val ok = action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON"
        if (!ok) return
        val pending = goAsync()
        try {
            AppLog.i("无感记账: 系统广播 $action")
            val ep = EntryPointAccessors.fromApplication(
                context.applicationContext,
                AutoRecordEntryPoint::class.java,
            )
            val enabled = runCatching { ep.settings().isAutoRecordEnabled() }.getOrDefault(false)
            if (enabled) {
                AutoRecordKeepAliveService.start(context)
                ep.runtime().ensurePipeline()
            }
        } catch (t: Throwable) {
            AppLog.w("无感记账: BootReceiver 失败 ${t.message}")
        } finally {
            pending.finish()
        }
    }
}
