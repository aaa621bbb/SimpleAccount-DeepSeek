package com.simpleaccount.app.auto

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 通知监听服务：抓取微信/支付宝的支付通知 → 解析 → 自动入账。
 * 需要用户在系统设置授予「通知使用权」（App 内有无感记账设置页引导）。
 * 仅处理能解析出金额的支付类通知；开关关闭时直接忽略。
 */
@AndroidEntryPoint
class PaymentNotificationListenerService : NotificationListenerService() {

    @Inject
    lateinit var autoRecordManager: AutoRecordManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val n = sbn ?: return
        val pkg = n.packageName ?: return
        // 诊断日志：监听服务收到"任何"通知都记录一条（证明服务活着/被绑定），
        // 之后才按包名过滤 —— 排障时在日志里能看到系统到底有没有把通知送进来
        com.simpleaccount.app.util.AppLog.d(
            "无感记账: onNotificationPosted pkg=${pkg.substringAfterLast('.')} " +
                "title=${n.notification?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.take(20)}"
        )
        // 快速过滤：只看微信/支付宝/云闪付
        val pkgLower = pkg.lowercase()
        if (!pkgLower.contains("tencent.mm") && !pkgLower.contains("alipay") && !pkgLower.contains("unionpay")) return

        val extras = n.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        if (title.isBlank() && text.isBlank()) return

        scope.launch {
            val parsed = runCatching { NotificationParser.parse(pkg, title, text) }.getOrNull()
            // 诊断日志：每条微信/支付宝/云闪付通知都记录解析结论（排障用，可在日志页查看/分享）
            com.simpleaccount.app.util.AppLog.d(
                "无感记账: 收到通知 pkg=${pkg.substringAfterLast('.')} " +
                    "title=${title.take(24)} text=${text.take(48)} → " +
                    (parsed?.let { "解析成功 ${it.type} ${it.amountFen / 100.0}元 ${it.merchant}" }
                        ?: "忽略（未匹配到支付金额/模式）")
            )
            if (parsed != null) {
                runCatching { autoRecordManager.onPaymentParsed(parsed) }
                    .onFailure { android.util.Log.w("SimpleAccount", "auto record failed", it) }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
