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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 多值布尔筛选：同一维度内 OR，维度之间 AND。
 * 例：月份={6月,8月} ∧ 分类={餐饮,交通} ∧ 类型=支出。
 */
data class LedgerFilter(
    val months: Set<String> = emptySet(),
    val categories: Set<String> = emptySet(),
    val query: String = "",
    val type: String? = null,
) {
    val month: String? get() = months.singleOrNull()
    val category: String? get() = categories.singleOrNull()
    val active: Boolean get() = months.isNotEmpty() || categories.isNotEmpty() || query.isNotBlank() || type != null

    fun monthsLabel(): String = when {
        months.isEmpty() -> "全部月份"
        months.size == 1 -> months.first()
        else -> "${months.size} 个月"
    }

    fun categoriesLabel(): String = when {
        categories.isEmpty() -> "全部分类"
        categories.size == 1 -> categories.first()
        else -> "${categories.size} 个分类"
    }
}

data class LedgerMonthGroup(
    val month: String,
    val rows: List<RowUi>,
    val expenseFen: Long = 0,
    val incomeFen: Long = 0,
)

data class LedgerUiState(
    val filter: LedgerFilter,
    val months: List<String> = DateUtil.recentMonths(12),
    val categories: List<Category> = emptyList(),
    val rows: List<RowUi> = emptyList(),
    val groups: List<LedgerMonthGroup> = emptyList(),
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

    private val categoriesFlow = categoryRepository.observeAll()

    private val allMonthsFlow = accountRepository.observeAll()
        .map { list -> list.map { it.date.take(7) }.distinct().sortedDescending() }

    private val amountSortFlow = MutableStateFlow(false)

    fun toggleSort() {
        amountSortFlow.value = !amountSortFlow.value
    }

    val uiState: StateFlow<LedgerUiState> =
        combine(
            accountRepository.observeAll(),
            categoriesFlow,
            allMonthsFlow,
            _filter.debounce(160).distinctUntilChanged(),
            amountSortFlow,
        ) { all, cats, allMonths, f, byAmount ->
            val catMap = cats.associateBy { it.name }
            val q = f.query.trim()
            val matched = all.filter { t ->
                (f.type == null || t.type == f.type) &&
                    (f.months.isEmpty() || t.date.take(7) in f.months) &&
                    (f.categories.isEmpty() || t.category in f.categories) &&
                    (q.isEmpty() ||
                        t.merchant.contains(q, true) ||
                        t.product.contains(q, true) ||
                        t.note.contains(q, true) ||
                        t.category.contains(q, true))
            }
            val base = if (byAmount) {
                matched.sortedWith(
                    compareByDescending<Transaction> { it.amount }.thenByDescending { it.date }.thenByDescending { it.time }
                )
            } else {
                matched.sortedWith(
                    compareByDescending<Transaction> { it.date }
                        .thenByDescending { it.time }
                        .thenByDescending { it.id }
                )
            }
            val rows = base.map { RowUi(it, catMap[it.category]) }
            val groups = rows.groupBy { it.transaction.date.take(7) }
                .toSortedMap(compareByDescending { it })
                .map { (m, list) -> LedgerMonthGroup(m, list) }
            LedgerUiState(
                filter = f, months = allMonths, categories = cats,
                rows = rows, groups = groups, sortByAmount = byAmount, loading = false,
            )
        }
            .flowOn(kotlinx.coroutines.Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LedgerUiState(filter = LedgerFilter()))

    fun toggleMonth(m: String) {
        val cur = _filter.value.months.toMutableSet()
        if (!cur.add(m)) cur.remove(m)
        _filter.value = _filter.value.copy(months = cur)
    }

    fun clearMonths() {
        _filter.value = _filter.value.copy(months = emptySet())
    }

    fun setMonths(ms: Set<String>) {
        _filter.value = _filter.value.copy(months = ms)
    }

    fun toggleCategory(c: String) {
        val cur = _filter.value.categories.toMutableSet()
        if (!cur.add(c)) cur.remove(c)
        _filter.value = _filter.value.copy(categories = cur)
    }

    fun setCategories(cs: Set<String>) {
        _filter.value = _filter.value.copy(categories = cs)
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
