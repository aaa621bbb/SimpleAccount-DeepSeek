package com.simpleaccount.app.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simpleaccount.app.data.dao.AiMessageDao
import com.simpleaccount.app.data.entity.AiMessage
import com.simpleaccount.app.data.entity.Merchant
import com.simpleaccount.app.data.entity.Transaction
import com.simpleaccount.app.data.repository.AccountRepository
import com.simpleaccount.app.data.repository.CategoryRepository
import com.simpleaccount.app.data.repository.MerchantRepository
import com.simpleaccount.app.data.repository.SettingsRepository
import com.simpleaccount.app.data.service.AiService
import com.simpleaccount.app.util.DateUtil
import com.simpleaccount.app.util.MoneyUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

data class AiUiState(
    val messages: List<AiMessage> = emptyList(),
    val loading: Boolean = false,
    val typing: Boolean = false,
    val input: String = "",
    val error: String? = null,
    val pendingCount: Int = 0,
    val enabled: Boolean = false,
)

@HiltViewModel
class AiViewModel @Inject constructor(
    private val aiMessageDao: AiMessageDao,
    private val settingsRepository: SettingsRepository,
    private val aiService: AiService,
    private val merchantRepository: MerchantRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AiUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch { loadInitial() }
    }

    private suspend fun loadInitial() {
        val enabled = settingsRepository.isAiEnabled()
        val msgs = aiMessageDao.getAll()
        val pending = merchantRepository.getByStatus(Merchant.STATUS_PENDING).size
        val welcome = if (msgs.isEmpty()) {
            listOf(
                AiMessage(
                    role = "assistant",
                    content = "你好，我是记账助手，可以帮你归类商家或回答记账问题。",
                    timestamp = System.currentTimeMillis()
                )
            )
        } else msgs
        if (msgs.isEmpty()) {
            aiMessageDao.insert(welcome[0])
        }
        _state.value = _state.value.copy(
            messages = if (msgs.isEmpty()) welcome else msgs,
            enabled = enabled,
            pendingCount = pending,
            loading = false
        )
    }

    fun onInputChange(v: String) {
        _state.value = _state.value.copy(input = v)
    }

    /** 刷新 enabled 状态（页面进入/返回时调用，避免缓存陈旧） */
    fun refreshEnabled() {
        val cur = settingsRepository.isAiEnabled()
        if (cur != _state.value.enabled) {
            _state.value = _state.value.copy(enabled = cur)
        }
    }

    fun sendMessage(content: String) {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return
        // 实时读取启用状态，避免 ViewModel 缓存的 enabled 陈旧导致误报"未开启"
        val enabled = settingsRepository.isAiEnabled()
        if (!enabled) {
            _state.value = _state.value.copy(error = "AI 功能未开启，请到「我的→AI辅助设置」开启并配置")
            return
        }
        viewModelScope.launch {
            val userMsg = AiMessage(role = "user", content = trimmed, timestamp = System.currentTimeMillis())
            aiMessageDao.insert(userMsg)
            val newList = _state.value.messages + userMsg
            aiMessageDao.trimBeyond(200)
            // 保留最近 20 条作为上下文
            val context = newList.takeLast(20)
            _state.value = _state.value.copy(messages = newList, input = "", typing = true, error = null)
            val apiKey = settingsRepository.apiKey()
            if (apiKey.isBlank()) {
                _state.value = _state.value.copy(
                    messages = newList + AiMessage(
                        role = "assistant",
                        content = "未配置 API Key，请先在设置中填写。",
                        timestamp = System.currentTimeMillis()
                    ),
                    typing = false
                )
                return@launch
            }
            val sysContext = buildSystemContext()
            val result = aiService.chat(
                baseUrl = settingsRepository.baseUrl(),
                apiKey = apiKey,
                model = settingsRepository.model(),
                messages = buildList {
                    // 账本上下文作为 system 提示，让 AI 能"看到"账本数据
                    if (sysContext.isNotBlank()) add("system" to sysContext)
                    addAll(context.map { it.role to it.content })
                }
            )
            val replyMsg = AiMessage(
                role = "assistant",
                content = result.error ?: result.content,
                timestamp = System.currentTimeMillis()
            )
            aiMessageDao.insert(replyMsg)
            aiMessageDao.trimBeyond(200)
            _state.value = _state.value.copy(
                messages = _state.value.messages + replyMsg,
                typing = false
            )
        }
    }

    /**
     * 构建账本上下文（system 提示），让 AI 能"看到"当前账本数据。
     * 提供整理后的数据：本月汇总、全部汇总、全部月份分类汇总、最近流水明细。
     * 明细量过大时降级为「按月汇总 + 最近 N 条明细」，保证 AI 能读到远期数据而不爆 token。
     */
    private suspend fun buildSystemContext(): String {
        val month = DateUtil.thisMonth()
        val all = accountRepository.getAll()
        if (all.isEmpty()) return ""  // 没有账本数据就不注入

        val monthSummary = accountRepository.monthSummary(month)
        val allSummary = accountRepository.allSummary()

        val sb = StringBuilder()
        sb.appendLine("你是一个记账助手。以下是用户当前的账本数据（金额单位为元）：")
        sb.appendLine("【本月(${month})汇总】支出=${MoneyUtil.fenToYuan(monthSummary.expense)} 元，收入=${MoneyUtil.fenToYuan(monthSummary.income)} 元，结余=${MoneyUtil.fenToYuan(monthSummary.balance)} 元")
        sb.appendLine("【全部汇总】支出=${MoneyUtil.fenToYuan(allSummary.expense)} 元，收入=${MoneyUtil.fenToYuan(allSummary.income)} 元，记账${all.size}笔")

        // 每月收支汇总（让 AI 能读到各个月份，而不只是最近一个月）
        val dates = all.map { it.date }.distinct().sorted()
        if (dates.isNotEmpty()) {
            val firstMonth = dates.first().substring(0, 7)
            val lastMonth = dates.last().substring(0, 7)
            val months = buildMonthlyRange(firstMonth, lastMonth)
            val perMonth = StringBuilder("【各月收支汇总】")
            months.forEach { m ->
                val exp = all.filter { it.date.startsWith(m) && it.type == Transaction.TYPE_EXPENSE }.sumOf { it.amount }
                val inc = all.filter { it.date.startsWith(m) && it.type == Transaction.TYPE_INCOME }.sumOf { it.amount }
                if (exp != 0L || inc != 0L) {
                    perMonth.append(" $m:支=${MoneyUtil.fenToYuan(exp)} 收=${MoneyUtil.fenToYuan(inc)};")
                }
            }
            sb.appendLine(perMonth.toString())
        }

        // 全部月份的分类统计（支出），让 AI 能回答"哪类花得多"
        val expenseCatsAll = accountRepository.categoryTotalsAll(Transaction.TYPE_EXPENSE)
        if (expenseCatsAll.isNotEmpty()) {
            sb.appendLine("【全部支出分类】" + expenseCatsAll.joinToString("，") { "${it.category}:${MoneyUtil.fenToYuan(it.total ?: 0L)}元" })
        }

        // 明细：优先全量（紧凑），>300 条则只给最近 300 条 + 提醒可按月问
        val sorted = all.sortedByDescending { it.date }
        val detailLimit = 300
        val detail = sorted.take(detailLimit)
        sb.appendLine("【流水明细(最近${detail.size}条${if (all.size > detailLimit) "，共${all.size}条，更早记录可按月份询问" else "，共${all.size}条"}")】")
        detail.forEach { t ->
            val typeName = if (t.type == Transaction.TYPE_EXPENSE) "支出" else "收入"
            sb.appendLine("- ${t.date} $typeName ${MoneyUtil.fenToYuan(t.amount)}元 分类:${t.category} 商家:${t.merchant} 商品:${t.product}")
        }
        return sb.toString()
    }

    /** 生成 [firstMonth, lastMonth]（yyyy-MM）的连续月份列表 */
    private fun buildMonthlyRange(firstMonth: String, lastMonth: String): List<String> {
        val y1 = firstMonth.substring(0, 4).toInt()
        val m1 = firstMonth.substring(5, 7).toInt()
        val y2 = lastMonth.substring(0, 4).toInt()
        val m2 = lastMonth.substring(5, 7).toInt()
        val out = mutableListOf<String>()
        var y = y1; var m = m1
        while (y < y2 || (y == y2 && m <= m2)) {
            out.add("%04d-%02d".format(y, m))
            m++
            if (m > 12) { m = 1; y++ }
        }
        return out
    }

    /** 批量归类 pending 商家（每批 ≤20） */
    fun classifyPendingMerchants() {
        val enabled = _state.value.enabled
        if (!enabled) {
            _state.value = _state.value.copy(error = "AI 未开启，无法批量归类")
            return
        }
        viewModelScope.launch {
            val apiKey = settingsRepository.apiKey()
            if (apiKey.isBlank()) {
                _state.value = _state.value.copy(error = "未配置 API Key")
                return@launch
            }
            _state.value = _state.value.copy(typing = true, error = null)
            val pendings = merchantRepository.getByStatus(Merchant.STATUS_PENDING)
            if (pendings.isEmpty()) {
                _state.value = _state.value.copy(typing = false, error = "当前没有待归类的商家")
                return@launch
            }
            val validCategories = categoryRepository.getAll()
                .map { it.name }
                .filter { it != "其它" }
            var done = 0
            pendings.chunked(20).forEach { batch ->
                val prompt = buildClassifyPrompt(batch, validCategories)
                val result = aiService.chat(
                    baseUrl = settingsRepository.baseUrl(),
                    apiKey = apiKey,
                    model = settingsRepository.model(),
                    messages = listOf("user" to prompt)
                )
                if (result.error != null) {
                    _state.value = _state.value.copy(error = "归类失败：${result.error}")
                    return@launch
                }
                val parsed = parseClassifyResult(result.content, validCategories)
                applyClassification(parsed)
                done += batch.size
            }
            // 更新 pending 数
            val pendingLeft = merchantRepository.getByStatus(Merchant.STATUS_PENDING).size
            _state.value = _state.value.copy(typing = false, pendingCount = pendingLeft, error = "已完成 $done 个商家归类")
        }
    }

    /**
     * AI 输出格式要求：JSON {"merchant":"商家名1":"分类","商家名2":"分类"...}
     * 实际更宽容解析：多行 "商家名=分类" 或 JSON。
     */
    private suspend fun parseClassifyResult(text: String, validCategories: List<String>): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val json = runCatching { JSONObject(text) }.getOrNull()
            ?: runCatching { JSONObject(text.substringAfter("```json").substringBefore("```")) }.getOrNull()
        if (json != null) {
            json.keys().forEach { k ->
                val v = json.optString(k)
                if (v.isNotBlank() && v in validCategories) map[k] = v
            }
        } else {
            text.lines().forEach { line ->
                val parts = line.trim().split(Regex("[=:：]"), limit = 2)
                if (parts.size == 2) {
                    val merchant = parts[0].trim()
                    val cat = Regex("\\s+").replace(parts[1].trim(), "")
                    if (merchant.isNotEmpty() && cat in validCategories) map[merchant] = cat
                }
            }
        }
        return map
    }

    private suspend fun applyClassification(map: Map<String, String>) {
        for ((merchant, category) in map) {
            val existing = merchantRepository.getByMerchant(merchant) ?: continue
            if (existing.status == Merchant.STATUS_USER_SET) continue
            merchantRepository.update(
                existing.copy(category = category, status = Merchant.STATUS_CLASSIFIED, updatedAt = System.currentTimeMillis())
            )
            // 历史追改：只改 source='import'
            accountRepository.getAllImport()
                .filter { it.merchant.trim() == merchant }
                .forEach { t ->
                    accountRepository.update(
                        t.copy(category = category, updatedAt = System.currentTimeMillis())
                    )
                }
        }
    }

    private fun buildClassifyPrompt(batch: List<Merchant>, validCategories: List<String>): String {
        val list = batch.joinToString("\n") { it.merchant }
        return """我是一名记账助手。请为下面的商家名分类（只看商家名推断消费类型）。
合法分类：${validCategories.joinToString("、")}
如果没有把握的，给"其它"。
请严格只返回 JSON 对象，键为商家名，值为分类，例如：
{"瑞幸咖啡":"餐饮"}
商家列表：
$list"""
    }
}
