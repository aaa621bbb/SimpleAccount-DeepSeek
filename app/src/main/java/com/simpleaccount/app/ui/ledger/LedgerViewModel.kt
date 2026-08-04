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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 账本筛选状态 */
data class LedgerFilter(
    val month: String? = null,       // null = 全部
    val category: String? = null,    // null = 全部
    val query: String = "",
)

data class LedgerUiState(
    val filter: LedgerFilter,
    val months: List<String> = DateUtil.recentMonths(12),
    val categories: List<Category> = emptyList(),
    val rows: List<RowUi> = emptyList(),
    val loading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LedgerViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    private val _filter = MutableStateFlow(LedgerFilter())
    val filter: StateFlow<LedgerFilter> = _filter.asStateFlow()

    private val hasQuery = _filter.map { it.query.isNotBlank() }

    private val filteredFlow = _filter.flatMapLatest { f ->
        if (f.query.isNotBlank()) {
            accountRepository.observeSearch(f.query)
        } else {
            accountRepository.observeFiltered(f.month, f.category)
        }
    }

    // 需要 category map 供行显示
    private val categoriesFlow = categoryRepository.observeAll()

    val uiState: StateFlow<LedgerUiState> =
        combine(filteredFlow, categoriesFlow, _filter) { transactions, cats, f ->
            val catMap = cats.associateBy { it.name }
            val rows = transactions
                .sortedWith(compareByDescending<Transaction> { it.date }.thenByDescending { it.id })
                .map { RowUi(it, catMap[it.category]) }
            LedgerUiState(filter = f, categories = cats, rows = rows, loading = false)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LedgerUiState(filter = LedgerFilter()))

    fun setMonth(m: String?) {
        _filter.value = _filter.value.copy(month = m)
    }

    fun setCategory(c: String?) {
        _filter.value = _filter.value.copy(category = c)
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
