package com.simpleaccount.app.auto

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 通知监听：抓取支付类通知 → 解析 → 自动入账。
 *
 * Hilt 注入可能晚于首条通知；一律走 [resolveDeps] 兜底 EntryPoint，避免丢单。
 * 绑定/解绑上报 [AutoRecordRuntime]，由保活服务周期 requestRebind。
 */
@AndroidEntryPoint
class PaymentNotificationListenerService : NotificationListenerService() {

    @Inject
    lateinit var autoRecordManager: AutoRecordManager

    @Inject
    lateinit var runtime: AutoRecordRuntime

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        super.onListenerConnected()
        resolveRuntime().markListenerBound(true)
    }

    override fun onListenerDisconnected() {
        resolveRuntime().markListenerBound(false)
        runCatching { requestRebind(android.content.ComponentName(this, javaClass)) }
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val n = sbn ?: return
        val pkg = n.packageName ?: return

        if ((n.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        if (n.isOngoing) return

        val (title, text) = extractBody(n.notification)
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

        val manager = resolveManager()
        val rt = resolveRuntime()
        scope.launch {
            val parsed = runCatching { NotificationParser.parse(pkg, title, text) }.getOrNull()
            com.simpleaccount.app.util.AppLog.d(
                "无感记账: 收到通知 pkg=${pkg.substringAfterLast('.')} " +
                    "title=${title.take(24)} text=${text.take(64)} → " +
                    (parsed?.let { "解析成功 ${it.type} ${it.amountFen / 100.0}元 ${it.merchant}" }
                        ?: "忽略（未匹配到支付金额/模式）"),
            )
            if (parsed == null) {
                rt.markSkipped("未解析 ${pkg.substringAfterLast('.')} ${title.take(16)}")
                return@launch
            }
            runCatching { manager.onPaymentParsed(parsed) }
                .onSuccess { ok ->
                    if (ok) {
                        rt.markRecorded("${parsed.merchant} ${parsed.amountFen / 100.0}元")
                    } else {
                        rt.markSkipped("未入账 ${parsed.merchant}")
                    }
                }
                .onFailure {
                    android.util.Log.w("SimpleAccount", "auto record failed", it)
                    rt.markError(it.message ?: "入账失败")
                }
        }
    }

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

    private fun resolveManager(): AutoRecordManager {
        if (::autoRecordManager.isInitialized) return autoRecordManager
        return entryPoint().manager()
    }

    private fun resolveRuntime(): AutoRecordRuntime {
        if (::runtime.isInitialized) return runtime
        return entryPoint().runtime()
    }

    private fun entryPoint(): AutoRecordEntryPoint {
        return EntryPointAccessors.fromApplication(applicationContext, AutoRecordEntryPoint::class.java)
    }

    override fun onDestroy() {
        if (::runtime.isInitialized) runtime.markListenerBound(false)
        scope.cancel()
        super.onDestroy()
    }
}
