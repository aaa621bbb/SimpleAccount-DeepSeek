package com.simpleaccount.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simpleaccount.app.data.entity.Transaction
import com.simpleaccount.app.data.insights.InsightTip
import com.simpleaccount.app.data.insights.InsightsEngine
import com.simpleaccount.app.data.entity.Ledger
import com.simpleaccount.app.data.repository.AccountRepository
import com.simpleaccount.app.data.repository.CategoryRepository
import com.simpleaccount.app.data.repository.LedgerRepository
import com.simpleaccount.app.data.repository.SettingsRepository
import com.simpleaccount.app.ui.components.RowUi
import com.simpleaccount.app.util.DateUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class HomeSort { TIME, AMOUNT }

data class HomeUiState(
    val month: String = DateUtil.thisMonth(),
    val expense: Long = 0L,
    val income: Long = 0L,
    val balance: Long = 0L,
    /** 每月预算（分），0 = 未设置 */
    val budgetFen: Long = 0L,
    val recent: List<RowUi> = emptyList(),
    val sortByAmount: Boolean = false,
    val insightHeadline: String = "",
    val insightSub: String = "",
    val insightReport: String = "",
    val evidenceTips: List<InsightTip> = emptyList(),
    val insightScore: Int = 0,
    val insightGrade: String = "",
    val monthTx: List<Transaction> = emptyList(),
    val ledgers: List<Ledger> = emptyList(),
    val currentLedgerName: String = Ledger.DEFAULT_NAME,
    val todayFen: Long = 0L,
    val projectedFen: Long = 0L,
    /** 剩余预算按剩余天数摊，今天建议上限 */
    val todayCapFen: Long = 0L,
    val todayDupes: List<String> = emptyList(),
    val pendingMerchants: Int = 0,
    val importFailures: Int = 0,
    /** 首页版式：simple 简约风 / dense 信息密集风。 */
    val homeLayout: String = SettingsRepository.HOME_SIMPLE,
    /** 条目标题字段：merchant 商家名 / product 商品名。 */
    val titleField: String = SettingsRepository.TITLE_MERCHANT,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val settingsRepository: SettingsRepository,
    private val ledgerRepository: LedgerRepository,
    private val merchantRepository: com.simpleaccount.app.data.repository.MerchantRepository,
    private val importFailureDao: com.simpleaccount.app.data.dao.ImportFailureDao,
) : ViewModel() {

    private val categoriesFlow = categoryRepository.observeAll()
    private val budgetFlow = MutableStateFlow(settingsRepository.monthlyBudget())
    private val recentCountFlow = settingsRepository.recentCountFlow
    private val sortFlow = MutableStateFlow(HomeSort.TIME)

    init {
        refreshBudget()
    }

    fun refreshBudget() {
        viewModelScope.launch { budgetFlow.value = settingsRepository.monthlyBudget() }
    }

    /** 设置每月预算（分）；传 0 = 清除预算 */
    fun setMonthlyBudget(fen: Long) {
        viewModelScope.launch {
            settingsRepository.setMonthlyBudget(fen)
            budgetFlow.value = fen
        }
    }

    /** 切换最近记录排序：时间 ↔ 金额 */
    fun toggleSort() {
        sortFlow.value = if (sortFlow.value == HomeSort.TIME) HomeSort.AMOUNT else HomeSort.TIME
    }

    private val pendingMerchantsFlow = merchantRepository
        .observeByStatus(com.simpleaccount.app.data.entity.Merchant.STATUS_PENDING)
        .map { it.size }

    private val importFailureCountFlow = importFailureDao.observeCount()

    /** 当月流水 + 分类 + 排序 + 条数，合并为 UI 状态 */
    fun switchLedger(id: Long) = ledgerRepository.switchTo(id)

    private data class Extra(
        val count: Int,
        val sort: HomeSort,
        val ledgers: List<Ledger>,
        val lid: Long,
    )

    private data class Follow(
        val pending: Int,
        val failures: Int,
    )

    private data class Prefs(
        val layout: String,
        val title: String,
    )

    val uiState: StateFlow<HomeUiState> =
        combine(
            accountRepository.observeAll(),
            categoriesFlow,
            budgetFlow,
            combine(recentCountFlow, sortFlow, ledgerRepository.observeAll(), ledgerRepository.currentIdFlow) { count, sort, ledgers, lid ->
                Extra(count, sort, ledgers, lid)
            },
            combine(
                combine(pendingMerchantsFlow, importFailureCountFlow) { p, f -> Follow(p, f) },
                combine(settingsRepository.homeLayoutFlow, settingsRepository.titleFieldFlow) { l, t ->
                    Prefs(l, t)
                }
            ) { follow, prefs -> follow to prefs },
        ) { all, categories, budget, q, tail ->
            val (follow, prefs) = tail
            val count = q.count
            val sort = q.sort
            val ledgers = q.ledgers
            val lid = q.lid
            val month = DateUtil.thisMonth()
            val transactions = all.filter { it.date.startsWith(month) }
            val catMap = categories.associateBy { it.name }
            var expense = 0L
            var income = 0L
            transactions.forEach { t ->
                if (t.amount > 0) {
                    when (t.type) {
                        Transaction.TYPE_EXPENSE -> expense += t.amount
                        Transaction.TYPE_INCOME -> income += t.amount
                    }
                }
            }
            val sorted = if (sort == HomeSort.AMOUNT) {
                transactions.sortedWith(
                    compareByDescending<Transaction> { it.amount }
                        .thenByDescending { it.date }
                        .thenByDescending { it.time }
                )
            } else {
                // 按时间排序要精确到分钟：date + time 组合排序，同一天内按时间
                transactions.sortedWith(
                    compareByDescending<Transaction> { it.date }
                        .thenByDescending { it.time }
                        .thenByDescending { it.id }
                )
            }
            val health = InsightsEngine.compute(all, budget, month)
            val day = java.time.LocalDate.now().dayOfMonth
            val days = java.time.YearMonth.now().lengthOfMonth()
            val remainDays = (days - day).coerceAtLeast(1)
            val todayCap = if (budget > 0) (budget - expense).coerceAtLeast(0L) / remainDays else 0L
            HomeUiState(
                month = month,
                expense = expense,
                income = income,
                balance = income - expense,
                budgetFen = budget,
                sortByAmount = sort == HomeSort.AMOUNT,
                recent = sorted.take(count).map { RowUi(it, catMap[it.category]) },
                insightHeadline = health.headline,
                insightSub = health.subline,
                insightReport = if (transactions.isEmpty()) "" else InsightsEngine.toMarkdown(health),
                evidenceTips = health.evidenceTips,
                insightScore = health.score,
                insightGrade = health.grade,
                monthTx = transactions,
                ledgers = ledgers,
                currentLedgerName = ledgers.firstOrNull { it.id == lid }?.name ?: Ledger.DEFAULT_NAME,
                todayFen = health.todayFen,
                projectedFen = health.projectedFen,
                todayCapFen = todayCap,
                todayDupes = health.todayDupes,
                pendingMerchants = follow.pending,
                importFailures = follow.failures,
                homeLayout = prefs.layout,
                titleField = prefs.title,
            )
        }
            // 聚合计算（排序/体检/分组）在 Default 线程做，避免大数据量时阻塞主线程掉帧
            .flowOn(kotlinx.coroutines.Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())
}
