package com.simpleaccount.app.ui.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simpleaccount.app.data.entity.Category
import com.simpleaccount.app.data.entity.Transaction
import com.simpleaccount.app.data.repository.AccountRepository
import com.simpleaccount.app.data.repository.CategoryRepository
import com.simpleaccount.app.ui.components.RowUi
import com.simpleaccount.app.util.DateUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 账本筛选状态 */
data class LedgerFilter(
    val month: String? = null,       // null = 全部
    val category: String? = null,    // null = 全部
    val query: String = "",
    /** null = 全部；expense / income */
    val type: String? = null,
)

/** 按月分组的一段账目（分组计算在后台线程完成，避免切页卡顿） */
data class LedgerMonthGroup(val month: String, val rows: List<RowUi>)

data class LedgerUiState(
    val filter: LedgerFilter,
    val months: List<String> = DateUtil.recentMonths(12),
    val categories: List<Category> = emptyList(),
    val rows: List<RowUi> = emptyList(),
    val groups: List<LedgerMonthGroup> = emptyList(),
    /** true = 按金额降序；false = 按时间降序 */
    val sortByAmount: Boolean = false,
    val loading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
@HiltViewModel
class LedgerViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    private val _filter = MutableStateFlow(LedgerFilter())
    val filter: StateFlow<LedgerFilter> = _filter.asStateFlow()

    // 防抖：搜索框每个字符都触发全量查询+排序会导致输入卡顿，200ms 内的连续变更合并为一次
    private val filteredFlow = _filter
        .debounce(200)
        .distinctUntilChanged()
        .flatMapLatest { f ->
            if (f.query.isNotBlank()) {
                accountRepository.observeSearch(f.query, f.month, f.category, f.type)
            } else {
                accountRepository.observeFiltered(f.month, f.category, f.type)
            }
        }

    // 需要 category map 供行显示
    private val categoriesFlow = categoryRepository.observeAll()

    /** 全量月份列表：必须从全部交易派生 —— 若从筛选结果派生，选中某月后芯片就只剩该月 */
    private val allMonthsFlow = accountRepository.observeAll()
        .map { list -> list.map { it.date.take(7) }.distinct().sortedDescending() }

    /** 排序：false=按时间降序（默认），true=按金额降序 */
    private val amountSortFlow = MutableStateFlow(false)

    fun toggleSort() {
        amountSortFlow.value = !amountSortFlow.value
    }

    val uiState: StateFlow<LedgerUiState> =
        combine(filteredFlow, categoriesFlow, allMonthsFlow, _filter, amountSortFlow) { transactions, cats, allMonths, f, byAmount ->
                // 重计算（排序/映射/月份归纳）放在 Default 线程，避免大账本时主线程卡顿
                val catMap = cats.associateBy { it.name }
                val base = if (byAmount) {
                    transactions.sortedWith(
                        compareByDescending<Transaction> { it.amount }.thenByDescending { it.date }.thenByDescending { it.time }
                    )
                } else {
                    // 按时间排序要精确到分钟：date + time 组合排序，同一天内按时间
                    transactions.sortedWith(
                        compareByDescending<Transaction> { it.date }
                            .thenByDescending { it.time }
                            .thenByDescending { it.id }
                    )
                }
                val rows = base.map { RowUi(it, catMap[it.category]) }
                val groups = rows.groupBy { it.transaction.date.take(7) }
                    .toSortedMap(compareByDescending { it })
                    .map { (m, list) -> LedgerMonthGroup(m, list) }
                LedgerUiState(filter = f, months = allMonths, categories = cats, rows = rows, groups = groups, sortByAmount = byAmount, loading = false)
            }
            .flowOn(kotlinx.coroutines.Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LedgerUiState(filter = LedgerFilter()))

    fun setMonth(m: String?) {
        _filter.value = _filter.value.copy(month = m)
    }

    fun setCategory(c: String?) {
        _filter.value = _filter.value.copy(category = c)
    }

    fun setType(t: String?) {
        _filter.value = _filter.value.copy(type = t)
    }

    fun setQuery(q: String) {
        _filter.value = _filter.value.copy(query = q)
    }

    fun clearFilters() {
        _filter.value = LedgerFilter()
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            accountRepository.delete(id)
        }
    }
}
