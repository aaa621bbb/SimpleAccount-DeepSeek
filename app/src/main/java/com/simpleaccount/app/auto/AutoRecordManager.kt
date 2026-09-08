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
 * 无感记账：支付通知 → 当前账本。
 *
 * v2.31.0：
 * - 去重：60 秒窗口 + 当天账本同金额同商家（含支付通知+后续到账通知）；
 * - 低置信（商家兜底名等）→ 写入 SOURCE_DRAFT 草稿，不进统计；
 * - 执行授权与 AI 管家共用：confirm 模式一律草稿；auto 模式高置信才直接 SOURCE_AUTO。
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
        /** 低于此置信 → 草稿 */
        const val CONFIDENCE_AUTO_THRESHOLD = 0.75f
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
            // 草稿与已入账都参与去重，避免支付通知 + 到账通知双记
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
        val sub = com.simpleaccount.app.util.KeywordRules.classifySub(merchant, category).orEmpty()

        // 执行授权 + 置信度：confirm 或低置信 → 草稿
        val autoAuth = settingsRepository.isAgentAutoExecute()
        val highConf = parsed.confidence >= CONFIDENCE_AUTO_THRESHOLD
        val asDraft = !autoAuth || !highConf
        val source = if (asDraft) Transaction.SOURCE_DRAFT else Transaction.SOURCE_AUTO
        val note = if (asDraft) {
            "待确认 · 无感记账 · conf=${"%.2f".format(parsed.confidence)} · ${parsed.source}"
        } else {
            "无感记账 · ${parsed.source}"
        }

        val id = accountRepository.insert(
            Transaction(
                amount = parsed.amountFen,
                type = parsed.type,
                category = category,
                subCategory = sub,
                date = today,
                time = time,
                merchant = merchant,
                product = "",
                note = note,
                source = source,
            )
        )
        val dir = if (parsed.type == Transaction.TYPE_EXPENSE) "支出" else "收入"
        val tag = if (asDraft) "草稿待确认" else "已入账"
        AppLog.i(
            "无感记账: $tag #$id $dir ${parsed.amountFen / 100.0} 元 · $merchant · $category " +
                "（${parsed.source} conf=${"%.2f".format(parsed.confidence)} auth=${if (autoAuth) "auto" else "confirm"}）"
        )
        return true
    }

    /** 确认草稿：升为 SOURCE_AUTO，清空待确认备注前缀。 */
    suspend fun confirmDraft(id: Long): Boolean {
        val t = accountRepository.getById(id) ?: return false
        if (t.source != Transaction.SOURCE_DRAFT) return false
        val note = t.note.removePrefix("待确认 · ").trim()
        accountRepository.update(
            t.copy(
                source = Transaction.SOURCE_AUTO,
                note = note.ifBlank { "无感记账" },
                updatedAt = System.currentTimeMillis(),
            )
        )
        return true
    }

    /** 丢弃草稿。 */
    suspend fun discardDraft(id: Long): Boolean {
        val t = accountRepository.getById(id) ?: return false
        if (t.source != Transaction.SOURCE_DRAFT) return false
        accountRepository.delete(t.id)
        return true
    }

    suspend fun listDrafts(): List<Transaction> =
        accountRepository.getAll().filter { it.source == Transaction.SOURCE_DRAFT }
            .sortedByDescending { it.createdAt }

    /** 观测用：开关当前是否开。 */
    suspend fun isEnabled(): Boolean = settingsRepository.isAutoRecordEnabled()
}
