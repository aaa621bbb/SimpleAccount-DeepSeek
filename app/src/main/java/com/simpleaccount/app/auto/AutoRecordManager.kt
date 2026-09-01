package com.simpleaccount.app.auto

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
 * 无感记账：把解析出的支付通知写入主表（source=auto），接入现有分类体系。
 * 60 秒内同方向同金额的多路径重复抓取自动去重。
 */
@Singleton
class AutoRecordManager @Inject constructor(
    private val accountRepository: AccountRepository,
    private val classificationService: ClassificationService,
    private val categoryRepository: CategoryRepository,
    private val settingsRepository: SettingsRepository,
) {

    companion object {
        /** 60 秒内同方向同金额视为同一笔（通知重复推送/多路径抓取） */
        private const val DEDUP_WINDOW_MS = 60_000L
    }

    /** key = "type|amountFen" → 上次入库时间 */
    private val lastInsertAt = ConcurrentHashMap<String, Long>()

    /** 返回 true = 已入账 */
    suspend fun onPaymentParsed(parsed: NotificationParser.ParsedPayment): Boolean {
        if (!settingsRepository.isAutoRecordEnabled()) return false

        val now = System.currentTimeMillis()
        val key = "${parsed.type}|${parsed.amountFen}"
        val last = lastInsertAt[key]
        if (last != null && now - last < DEDUP_WINDOW_MS) {
            AppLog.d("无感记账: 60秒内重复(${key})，跳过")
            return false
        }
        lastInsertAt[key] = now

        val validNames = categoryRepository.getAll().map { it.name }.toSet()
        val category = classificationService.classifyForImport(
            parsed.merchant, "", "", validNames, parsed.type
        )
        val id = accountRepository.insert(
            Transaction(
                amount = parsed.amountFen,
                type = parsed.type,
                category = category,
                date = DateUtil.today(),
                merchant = parsed.merchant,
                product = "",
                source = Transaction.SOURCE_AUTO,
            )
        )
        val dir = if (parsed.type == Transaction.TYPE_EXPENSE) "支出" else "收入"
        AppLog.d("无感记账: 已入账 #$id $dir ${parsed.amountFen / 100.0} 元 · ${parsed.merchant} · $category（${parsed.source}）")
        return true
    }
}
