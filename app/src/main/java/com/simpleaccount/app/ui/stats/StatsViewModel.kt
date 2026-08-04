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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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

    private val monthFlow = MutableStateFlow(DateUtil.thisMonth())
    private val typeFlow = MutableStateFlow(Transaction.TYPE_EXPENSE)
    private val categoriesState = MutableStateFlow<List<Category>>(emptyList())
    private val slicesState = MutableStateFlow<List<PieSlice>>(emptyList())
    private val totalState = MutableStateFlow(0L)
    private val trendState = MutableStateFlow<List<TrendPoint>>(emptyList())

    init {
        viewModelScope.launch {
            categoriesState.value = categoryRepository.getAll()
            loadTrend()
            loadPie()
        }
    }

    private suspend fun loadTrend() {
        val months = DateUtil.recentMonths(12)
        val start = DateUtil.monthStart(months.minOrNull() ?: DateUtil.thisMonth())
        val end = DateUtil.monthEnd(months.maxOrNull() ?: DateUtil.thisMonth())
        val rows: List<RangeRow> = accountRepository.rangeTotals(start, end)
        trendState.value = buildTrend(rows, months)
    }

    private suspend fun loadPie() {
        val month = monthFlow.value
        val type = typeFlow.value
        val cats = categoriesState.value
        val colorMap = cats.associate { it.name to it.colorHex }

        val raw = if (month == "all") {
            accountRepository.categoryTotalsAll(type)
        } else {
            accountRepository.categoryTotals(month, type)
        }
        val slices = raw
            .map { PieSlice(it.category, colorMap[it.category] ?: "#BDC3C7", it.total ?: 0L) }
            .sortedByDescending { it.value }
        slicesState.value = slices
        totalState.value = raw.sumOf { it.total ?: 0L }
    }

    private data class PieHolder(val month: String, val type: String, val slices: List<PieSlice>, val total: Long)
    private data class TrendHolder(val trend: List<TrendPoint>, val cats: List<Category>)

    val uiState: StateFlow<StatsUiState> =
        combine(
            combine(monthFlow, typeFlow, slicesState, totalState) { m, t, s, tot ->
                PieHolder(m, t, s, tot)
            },
            combine(trendState, categoriesState) { tr, c ->
                TrendHolder(tr, c)
            }
        ) { pie, tr ->
            StatsUiState(
                month = pie.month, months = DateUtil.recentMonths(12),
                total = pie.total, slices = pie.slices, type = pie.type,
                trend = tr.trend,
                categoryColorMap = tr.cats.associate { it.name to it.colorHex }
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatsUiState())

    fun setMonth(m: String) {
        monthFlow.value = m
        viewModelScope.launch { loadPie() }
    }

    fun setType(t: String) {
        typeFlow.value = t
        viewModelScope.launch { loadPie() }
    }

    private fun buildTrend(rows: List<RangeRow>, months: List<String>): List<TrendPoint> {
        val byMonthType = mutableMapOf<Pair<String, String>, Long>()
        rows.forEach { r ->
            val m = r.date.take(7)
            val type = r.type
            if (type != null) byMonthType[m to type] = (byMonthType[m to type] ?: 0L) + (r.total ?: 0L)
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
