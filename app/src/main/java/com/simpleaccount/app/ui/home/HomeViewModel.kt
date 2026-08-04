package com.simpleaccount.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simpleaccount.app.data.entity.Category
import com.simpleaccount.app.data.entity.Transaction
import com.simpleaccount.app.data.repository.AccountRepository
import com.simpleaccount.app.data.repository.CategoryRepository
import com.simpleaccount.app.ui.components.RowUi
import com.simpleaccount.app.util.DateUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HomeUiState(
    val month: String = DateUtil.thisMonth(),
    val expense: Long = 0L,
    val income: Long = 0L,
    val balance: Long = 0L,
    val recent: List<RowUi> = emptyList(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    private val categoriesFlow = categoryRepository.observeAll()

    /** 当月流水 + 分类，合并为 UI 状态 */
    val uiState: StateFlow<HomeUiState> =
        combine(
            accountRepository.observeMonth(DateUtil.monthPrefix(DateUtil.thisMonth())),
            categoriesFlow
        ) { transactions, categories ->
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
            HomeUiState(
                month = DateUtil.thisMonth(),
                expense = expense,
                income = income,
                balance = income - expense,
                recent = transactions
                    .sortedByDescending { it.date }
                    .take(5)
                    .map { RowUi(it, catMap[it.category]) }
            )
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())
}
