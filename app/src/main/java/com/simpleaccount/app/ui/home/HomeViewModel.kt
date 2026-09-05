package com.simpleaccount.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simpleaccount.app.data.entity.Transaction
import com.simpleaccount.app.data.insights.InsightsEngine
import com.simpleaccount.app.data.repository.AccountRepository
import com.simpleaccount.app.data.repository.CategoryRepository
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
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val settingsRepository: SettingsRepository,
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

    /** 当月流水 + 分类 + 排序 + 条数，合并为 UI 状态 */
    val uiState: StateFlow<HomeUiState> =
        combine(
            accountRepository.observeAll(),
            categoriesFlow,
            budgetFlow,
            combine(recentCountFlow, sortFlow) { count, sort -> count to sort }
        ) { all, categories, budget, (count, sort) ->
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
            )
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())
}
