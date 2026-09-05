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
 * 通知监听：抓取支付类通知 → 解析 → 自动入账。
 * 需要用户授予「通知使用权」。
 *
 * 真机常见坑：
 * - 正文在 EXTRA_BIG_TEXT / TEXT_LINES / ticker，不只在 EXTRA_TEXT；
 * - 分组摘要通知没有金额，要跳过；
 * - 服务被系统解绑后要能重新绑定。
 */
@AndroidEntryPoint
class PaymentNotificationListenerService : NotificationListenerService() {

    @Inject
    lateinit var autoRecordManager: AutoRecordManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        super.onListenerConnected()
        com.simpleaccount.app.util.AppLog.i("无感记账: 通知监听已连接")
    }

    override fun onListenerDisconnected() {
        com.simpleaccount.app.util.AppLog.w("无感记账: 通知监听被断开，请求重新绑定")
        runCatching { requestRebind(android.content.ComponentName(this, javaClass)) }
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val n = sbn ?: return
        val pkg = n.packageName ?: return
        if (!::autoRecordManager.isInitialized) return

        // 分组摘要通常没有单笔金额
        if ((n.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        if (n.isOngoing) return

        val (title, text) = extractBody(n.notification)
        com.simpleaccount.app.util.AppLog.d(
            "无感记账: onNotificationPosted pkg=${pkg.substringAfterLast('.')} title=${title.take(20)}"
        )

        val pkgLower = pkg.lowercase()
        val interesting = pkgLower.contains("tencent.mm") ||
            pkgLower.contains("alipay") ||
            pkgLower.contains("unionpay") ||
            pkgLower.contains("mipay") ||
            pkgLower.contains("xiaomi.payment") ||
            pkgLower.contains("huawei.wallet") ||
            pkgLower.contains("huawei.payment") ||
            pkgLower.contains("bank") ||
            pkgLower.contains("wallet")
        if (!interesting) return
        if (title.isBlank() && text.isBlank()) return

        scope.launch {
            val parsed = runCatching { NotificationParser.parse(pkg, title, text) }.getOrNull()
            com.simpleaccount.app.util.AppLog.d(
                "无感记账: 收到通知 pkg=${pkg.substringAfterLast('.')} " +
                    "title=${title.take(24)} text=${text.take(64)} → " +
                    (parsed?.let { "解析成功 ${it.type} ${it.amountFen / 100.0}元 ${it.merchant}" }
                        ?: "忽略（未匹配到支付金额/模式）")
            )
            if (parsed != null) {
                runCatching { autoRecordManager.onPaymentParsed(parsed) }
                    .onFailure { android.util.Log.w("SimpleAccount", "auto record failed", it) }
            }
        }
    }

    /** 把通知里所有可能承载正文的字段拼起来，避免只读 EXTRA_TEXT 漏掉真机支付通知 */
    private fun extractBody(notification: Notification): Pair<String, String> {
        val extras = notification.extras
        val title = listOf(
            extras.getCharSequence(Notification.EXTRA_TITLE),
            extras.getCharSequence(Notification.EXTRA_TITLE_BIG),
            notification.tickerText,
        ).mapNotNull { it?.toString()?.trim() }.firstOrNull { it.isNotBlank() } ?: ""

        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.mapNotNull { it?.toString()?.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()
        val parts = listOf(
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString(),
            lines.joinToString("\n").ifBlank { null },
            notification.tickerText?.toString(),
        ).mapNotNull { it?.trim() }.filter { it.isNotBlank() }.distinct()

        return title to parts.joinToString("\n")
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
