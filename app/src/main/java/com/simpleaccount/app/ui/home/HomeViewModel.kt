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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class HomeSort { TIME, AMOUNT }

/** 首页「再记一笔」芯片：复用最近商家+金额+分类 */
data class QuickRepeat(
    val merchant: String,
    val amount: Long,
    val category: String,
    val type: String,
)

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
    val monthTx: List<Transaction> = emptyList(),
    val ledgers: List<Ledger> = emptyList(),
    val currentLedgerName: String = Ledger.DEFAULT_NAME,
    val todayFen: Long = 0L,
    val projectedFen: Long = 0L,
    /** 剩余预算按剩余天数摊，今天建议上限 */
    val todayCapFen: Long = 0L,
    val todayDupes: List<String> = emptyList(),
    val quickRepeats: List<QuickRepeat> = emptyList(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val settingsRepository: SettingsRepository,
    private val ledgerRepository: LedgerRepository,
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

    private val _snack = MutableStateFlow<String?>(null)
    val snack = _snack.asStateFlow()
    private var lastRepeatId: Long = 0L

    /** 一键再记：今天此时，同样商家/分类/金额。可立刻撤销。 */
    fun repeatQuick(q: QuickRepeat) {
        viewModelScope.launch {
            val now = java.time.LocalTime.now()
            val id = accountRepository.insert(
                Transaction(
                    amount = q.amount,
                    type = q.type,
                    category = q.category,
                    date = DateUtil.today(),
                    time = "%02d:%02d".format(now.hour, now.minute),
                    merchant = q.merchant,
                    source = Transaction.SOURCE_MANUAL,
                )
            )
            lastRepeatId = id
            _snack.value = "已再记 ${q.merchant} ¥${com.simpleaccount.app.util.MoneyUtil.fenToYuan(q.amount)}"
        }
    }

    fun undoRepeat() {
        val id = lastRepeatId
        if (id <= 0) {
            _snack.value = null
            return
        }
        viewModelScope.launch {
            accountRepository.delete(id)
            lastRepeatId = 0L
            _snack.value = "已撤销"
        }
    }

    fun dismissSnack() {
        _snack.value = null
    }

    /** 当月流水 + 分类 + 排序 + 条数，合并为 UI 状态 */
    fun switchLedger(id: Long) = ledgerRepository.switchTo(id)

    private data class Extra(
        val count: Int,
        val sort: HomeSort,
        val ledgers: List<Ledger>,
        val lid: Long,
    )

    val uiState: StateFlow<HomeUiState> =
        combine(
            accountRepository.observeAll(),
            categoriesFlow,
            budgetFlow,
            combine(recentCountFlow, sortFlow, ledgerRepository.observeAll(), ledgerRepository.currentIdFlow) { count, sort, ledgers, lid ->
                Extra(count, sort, ledgers, lid)
            }
        ) { all, categories, budget, q ->
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
            val quick = transactions
                .filter { it.type == Transaction.TYPE_EXPENSE && it.merchant.isNotBlank() }
                .sortedWith(compareByDescending<Transaction> { it.date }.thenByDescending { it.time })
                .distinctBy { it.merchant }
                .take(6)
                .map { QuickRepeat(it.merchant, it.amount, it.category, it.type) }
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
                insightReport = if (all.isEmpty()) "" else InsightsEngine.toMarkdown(health),
                evidenceTips = health.evidenceTips,
                monthTx = transactions,
                ledgers = ledgers,
                currentLedgerName = ledgers.firstOrNull { it.id == lid }?.name ?: Ledger.DEFAULT_NAME,
                todayFen = health.todayFen,
                projectedFen = health.projectedFen,
                todayCapFen = todayCap,
                todayDupes = health.todayDupes,
                quickRepeats = quick,
            )
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())
}
