package com.simpleaccount.app.auto

import com.simpleaccount.app.data.agent.DedupEngine
import com.simpleaccount.app.data.entity.Transaction
import com.simpleaccount.app.data.repository.AccountRepository
import com.simpleaccount.app.data.repository.CategoryRepository
import com.simpleaccount.app.data.repository.SettingsRepository
import com.simpleaccount.app.data.service.ClassificationService
import com.simpleaccount.app.util.AppLog
import com.simpleaccount.app.util.DateUtil
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 无感记账：支付通知 → 当前账本（source=auto）。
 * 去重：60 秒窗口 + 当天账本里同金额同商家。
 */
@Singleton
class AutoRecordManager @Inject constructor(
    private val accountRepository: AccountRepository,
    private val classificationService: ClassificationService,
    private val categoryRepository: CategoryRepository,
    private val settingsRepository: SettingsRepository,
) {

    companion object {
        private const val DEDUP_WINDOW_MS = 60_000L
    }

    private val lastInsertAt = ConcurrentHashMap<String, Long>()

    suspend fun onPaymentParsed(parsed: NotificationParser.ParsedPayment): Boolean {
        if (!settingsRepository.isAutoRecordEnabled()) {
            AppLog.w("无感记账: 总开关关闭，丢弃 ${parsed.merchant} ${parsed.amountFen}")
            return false
        }

        val now = System.currentTimeMillis()
        val key = "${parsed.type}|${parsed.amountFen}|${parsed.merchant}"
        val last = lastInsertAt[key]
        if (last != null && now - last < DEDUP_WINDOW_MS) {
            AppLog.d("无感记账: 60秒内重复($key)，跳过")
            return false
        }

        val merchant = com.simpleaccount.app.util.MerchantMatcher.stripEllipsis(parsed.merchant)
        val today = DateUtil.today()
        val time = java.time.LocalTime.now().let { "%02d:%02d".format(it.hour, it.minute) }
        val existing = accountRepository.getAll()
        val dup = existing.any { t ->
            DedupEngine.isSameTx(
                t.date, t.amount, t.merchant, t.time,
                today, parsed.amountFen, merchant, time
            )
        }
        if (dup) {
            AppLog.d("无感记账: 当天账本已有同金额同商家，跳过 $merchant ${parsed.amountFen}")
            lastInsertAt[key] = now
            return false
        }

        lastInsertAt[key] = now
        val validNames = categoryRepository.getAll().map { it.name }.toSet()
        val category = classificationService.classifyForImport(
            merchant, "", "", validNames, parsed.type
        )
        val id = accountRepository.insert(
            Transaction(
                amount = parsed.amountFen,
                type = parsed.type,
                category = category,
                subCategory = com.simpleaccount.app.util.KeywordRules.classifySub(merchant, category).orEmpty(),
                date = today,
                time = time,
                merchant = merchant,
                product = "",
                source = Transaction.SOURCE_AUTO,
            )
        )
        val dir = if (parsed.type == Transaction.TYPE_EXPENSE) "支出" else "收入"
        AppLog.i("无感记账: 已入账 #$id $dir ${parsed.amountFen / 100.0} 元 · $merchant · $category（${parsed.source}）")
        return true
    }

    /** 观测用：开关当前是否开。 */
    suspend fun isEnabled(): Boolean = settingsRepository.isAutoRecordEnabled()
}
