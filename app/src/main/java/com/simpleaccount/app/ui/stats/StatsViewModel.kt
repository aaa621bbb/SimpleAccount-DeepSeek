package com.simpleaccount.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simpleaccount.app.data.dao.RangeRow
import com.simpleaccount.app.data.entity.Category
import com.simpleaccount.app.data.entity.Transaction
import com.simpleaccount.app.data.repository.AccountRepository
import com.simpleaccount.app.data.repository.CategoryRepository
import com.simpleaccount.app.util.DateUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** 趋势点 */
data class TrendPoint(val month: String, val expense: Long, val income: Long)

/** 饼图扇区 */
data class PieSlice(val categoryName: String, val colorHex: String, val value: Long)

data class StatsUiState(
    val month: String = DateUtil.thisMonth(),
    val months: List<String> = DateUtil.recentMonths(12),
    val total: Long = 0L,
    val slices: List<PieSlice> = emptyList(),
    val type: String = Transaction.TYPE_EXPENSE,
    val trend: List<TrendPoint> = emptyList(),
    val categoryColorMap: Map<String, String> = emptyMap(),
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    private val monthFlow = kotlinx.coroutines.flow.MutableStateFlow(DateUtil.thisMonth())
    private val typeFlow = kotlinx.coroutines.flow.MutableStateFlow(Transaction.TYPE_EXPENSE)
    private val categoriesFlow = categoryRepository.observeAll()

    // 交易数据变更 Flow：任何增删改/清空都会重算统计
    private val transactionsFlow = accountRepository.observeAll()

    private data class PieInput(
        val month: String,
        val type: String,
        val txs: List<Transaction>,
        val cats: List<Category>,
    )

    private data class AllInput(
        val pie: PieInput,
        val cats: List<Category>,
    )

    val uiState: StateFlow<StatsUiState> = combine(
        combine(
            combine(
                monthFlow,
                typeFlow,
                transactionsFlow,
                categoriesFlow
            ) { month, type, txs, cats -> PieInput(month, type, txs, cats) },
            categoriesFlow
        ) { pie, cats ->
            AllInput(pie, cats)
        },
        categoriesFlow
    ) { all, cats ->
        val pie = all.pie
        val month = pie.month
        val type = pie.type
        val txs = pie.txs
        val months = DateUtil.recentMonths(12)

        // 饼图 & 总数
        val colorMap = cats.associate { it.name to it.colorHex }
        val filtered = when {
            month == "all" -> txs.filter { it.type == type }
            else -> txs.filter { it.type == type && it.date.startsWith(month) }
        }
        val byCat = filtered.groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
            .toList()
            .sortedByDescending { it.second }
        val slices = byCat.map { (cat, total) ->
            PieSlice(cat, colorMap[cat] ?: "#BDC3C7", total)
        }
        val total = filtered.sumOf { it.amount }

        // 趋势
        val trend = buildTrend(txs, months)

        StatsUiState(
            month = month,
            months = months,
            total = total,
            slices = slices,
            type = type,
            trend = trend,
            categoryColorMap = colorMap
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatsUiState())

    fun setMonth(m: String) {
        monthFlow.value = m
    }

    fun setType(t: String) {
        typeFlow.value = t
    }

    private fun buildTrend(txs: List<Transaction>, months: List<String>): List<TrendPoint> {
        val byMonthType = mutableMapOf<Pair<String, String>, Long>()
        txs.forEach { t ->
            if (t.amount <= 0) return@forEach
            val m = t.date.take(7)
            byMonthType[m to t.type] = (byMonthType[m to t.type] ?: 0L) + t.amount
        }
        return months.reversed().map { m ->
            TrendPoint(
                month = m,
                expense = byMonthType[m to Transaction.TYPE_EXPENSE] ?: 0L,
                income = byMonthType[m to Transaction.TYPE_INCOME] ?: 0L
            )
        }
    }
}