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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 趋势点 */
data class TrendPoint(val month: String, val expense: Long, val income: Long)

/** 饼图扇区：默认一级；展开后可挂二级 children。 */
data class PieSlice(
    val categoryName: String,
    val colorHex: String,
    val value: Long,
    /** 一级分类名（用于取色与点选） */
    val parentCategory: String = categoryName.substringBefore('/'),
    /** 二级扇区（仅一级聚合行有值） */
    val children: List<PieSlice> = emptyList(),
    /** 是否为二级行 */
    val isSub: Boolean = categoryName.contains('/'),
)

/** 日历某天的收支合计 */
data class DayTotals(val expense: Long = 0L, val income: Long = 0L)

/** 日历某天的一条流水（供点击查看明细） */
data class CalDayTx(
    val type: String,
    val amount: Long,
    val category: String,
    val merchant: String,
    val product: String,
    val date: String = "",
    val subCategory: String = "",
)

data class StatsUiState(
    val month: String = DateUtil.thisMonth(),
    val months: List<String> = DateUtil.recentMonths(12),
    val total: Long = 0L,
    val slices: List<PieSlice> = emptyList(),
    val type: String = Transaction.TYPE_EXPENSE,
    val trend: List<TrendPoint> = emptyList(),
    val categoryColorMap: Map<String, String> = emptyMap(),
    // 日历视图（默认当前月）
    val calYear: Int = java.time.YearMonth.now().year,
    val calMonthNum: Int = java.time.YearMonth.now().monthValue,
    val calDayTotals: Map<Int, DayTotals> = emptyMap(),
    val calDayTx: Map<Int, List<CalDayTx>> = emptyMap(),
    /** 当前月份+收支口径下，每个分类的流水明细（供点击分类弹出账单） */
    val catTx: Map<String, List<CalDayTx>> = emptyMap(),
    val topMerchants: List<Pair<String, Long>> = emptyList(),
    val weekdayAvgFen: Long = 0L,
    val weekendAvgFen: Long = 0L,
    val dailyAvgFen: Long = 0L,
    val lastMonthTotal: Long = 0L,
    val weekdayTotals: List<Pair<String, Long>> = emptyList(),
    val hourBuckets: List<Pair<String, Long>> = emptyList(),
    val freqPoints: List<FreqPoint> = emptyList(),
    val shareMonths: List<ShareMonth> = emptyList(),
    val momDeltas: List<MomDelta> = emptyList(),
    val conc: ConcUi = ConcUi(),
    val elastic: List<ElasticPoint> = emptyList(),
    val pareto: List<ParetoPoint> = emptyList(),
    val heat: List<HeatCell> = emptyList(),
    val spark: List<SparkPoint> = emptyList(),
    val flowIncome: List<FlowPart> = emptyList(),
    val flowExpense: List<FlowPart> = emptyList(),
    val radar: RadarUi = RadarUi(),
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val settingsRepository: com.simpleaccount.app.data.repository.SettingsRepository,
) : ViewModel() {

    val layout: StateFlow<StatsLayoutUi> = kotlinx.coroutines.flow.combine(
        settingsRepository.statsOrderFlow,
        settingsRepository.statsHiddenFlow,
        settingsRepository.pieLegendCountFlow,
    ) { order, hidden, count ->
        StatsLayoutUi(
            order = StatsModules.parseOrder(order),
            hidden = StatsModules.parseHidden(hidden),
            pieLegendCount = count,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsLayoutUi())

    private val monthFlow = kotlinx.coroutines.flow.MutableStateFlow(DateUtil.thisMonth())
    private val typeFlow = kotlinx.coroutines.flow.MutableStateFlow(Transaction.TYPE_EXPENSE)
    private val categoriesFlow = categoryRepository.observeAll()

    // 交易数据变更 Flow：任何增删改/清空都会重算统计
    private val transactionsFlow = accountRepository.observeAll()

    // 日历视图与月份选择共用同一来源：切日历月份 = 切统计月份（总额大卡联动）

    /** 日历上切换月份（等同切换统计月份；"全部"时以当前月为基准步进） */
    fun calPrevMonth() = shiftMonth(-1)

    fun calNextMonth() = shiftMonth(1)

    private fun shiftMonth(delta: Int) {
        val base = monthFlow.value.takeIf { it != "all" } ?: DateUtil.thisMonth()
        monthFlow.value = runCatching { java.time.YearMonth.parse(base).plusMonths(delta.toLong()).toString() }
            .getOrDefault(DateUtil.thisMonth())
    }

val uiState: StateFlow<StatsUiState> = combine(
        monthFlow,
        typeFlow,
        combine(transactionsFlow, settingsRepository.ledgerScopeVersionFlow) { txs, _ -> txs },
        categoriesFlow
    ) { month, type, txs, cats ->
        computeStats(month, type, txs, cats)
    }
        // 大账本（1800+ 条）的分组/求和/排序都放 Default 线程，切月份不再卡主线程
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatsUiState())

private fun computeStats(
        month: String,
        type: String,
        txsIn: List<Transaction>,
        cats: List<com.simpleaccount.app.data.entity.Category>,
    ): StatsUiState {
        // 收支口径：按用户开关过滤退款/投资
        val txs = settingsRepository.filterByLedgerScope(txsIn)
        // 月份列表从真实数据生成（有账的月份，倒序），而不是固定的最近12个月
        val months = txs.map { it.date.take(7) }.distinct().sortedDescending()
            .ifEmpty { DateUtil.recentMonths(12) }

        // 饼图：一级为主；每项挂二级 children 供图例展开下钻
        val colorMap = cats.associate { it.name to it.colorHex }
        val filtered = when {
            month == "all" -> txs.filter { it.type == type }
            else -> txs.filter { it.type == type && it.date.startsWith(month) }
        }
        fun dimKey(t: Transaction): String =
            if (t.subCategory.isNotBlank()) "${t.category}/${t.subCategory}" else t.category
        // 一级聚合
        val byParent = filtered.groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
            .toList()
            .sortedByDescending { it.second }
        val slices = byParent.map { (parent, parentTotal) ->
            val subs = filtered.filter { it.category == parent && it.subCategory.isNotBlank() }
                .groupBy { it.subCategory }
                .mapValues { it.value.sumOf { t -> t.amount } }
                .toList()
                .sortedByDescending { it.second }
                .map { (sub, amt) ->
                    PieSlice(
                        categoryName = "$parent/$sub",
                        colorHex = colorMap[parent] ?: "#BDC3C7",
                        value = amt,
                        parentCategory = parent,
                        isSub = true,
                    )
                }
            // 无二级的金额并入「未细分」
            val unsub = filtered.filter { it.category == parent && it.subCategory.isBlank() }
                .sumOf { it.amount }
            val children = if (unsub > 0 && subs.isNotEmpty()) {
                subs + PieSlice(
                    categoryName = parent,
                    colorHex = colorMap[parent] ?: "#BDC3C7",
                    value = unsub,
                    parentCategory = parent,
                    isSub = false,
                )
            } else subs
            PieSlice(
                categoryName = parent,
                colorHex = colorMap[parent] ?: "#BDC3C7",
                value = parentTotal,
                parentCategory = parent,
                children = children,
            )
        }
        val total = filtered.sumOf { it.amount }

        // 趋势：固定按最近 12 个连续月份（无数据的月为 0），与上方选择器的数据月份列表无关
        val trend = buildTrend(txs, DateUtil.recentMonths(12))

        // 日历视图：显示的月份与统计月份联动（"全部"时日历显示当前月）
        val calMonth = runCatching {
            if (month == "all") java.time.YearMonth.now() else java.time.YearMonth.parse(month)
        }.getOrDefault(java.time.YearMonth.now())
        val calPrefix = "%04d-%02d".format(calMonth.year, calMonth.monthValue)
        val calDayTotals = mutableMapOf<Int, DayTotals>()
        val calDayTx = mutableMapOf<Int, MutableList<CalDayTx>>()
        val extra = StatsExtra.compute(month, type, txs, filtered, trend, calPrefix)

        txs.filter { it.date.startsWith(calPrefix) }.forEach { t ->
            val day = t.date.substring(8, 10).toIntOrNull() ?: return@forEach
            val cur = calDayTotals.getOrPut(day) { DayTotals() }
            calDayTotals[day] = if (t.type == Transaction.TYPE_EXPENSE)
                cur.copy(expense = cur.expense + t.amount)
            else cur.copy(income = cur.income + t.amount)
calDayTx.getOrPut(day) { mutableListOf() }.add(
                CalDayTx(t.type, t.amount, t.category, t.merchant, t.product, subCategory = t.subCategory)
            )
        }

        return StatsUiState(
            month = month,
            months = months,
            total = total,
            slices = slices,
            type = type,
            trend = trend,
            categoryColorMap = colorMap,
            calYear = calMonth.year,
            calMonthNum = calMonth.monthValue,
            calDayTotals = calDayTotals,
            calDayTx = calDayTx,
            // 明细：一级键 + 二级键（一级/二级）均可点开
            catTx = buildMap {
                filtered.groupBy { it.category }.forEach { (parent, list) ->
                    put(parent, list.sortedByDescending { it.date }.map {
                        CalDayTx(it.type, it.amount, it.category, it.merchant, it.product, it.date, it.subCategory)
                    })
                }
                filtered.filter { it.subCategory.isNotBlank() }
                    .groupBy { dimKey(it) }
                    .forEach { (key, list) ->
                        put(key, list.sortedByDescending { it.date }.map {
                            CalDayTx(it.type, it.amount, it.category, it.merchant, it.product, it.date, it.subCategory)
                        })
                    }
            },
            topMerchants = filtered.filter { it.merchant.isNotBlank() }
                .groupBy { it.merchant }
                .mapValues { it.value.sumOf { t -> t.amount } }
                .toList()
                .sortedByDescending { it.second }
                .take(8),
            weekdayAvgFen = run {
                val days = filtered.groupBy { it.date }.filterKeys {
                    runCatching { java.time.LocalDate.parse(it).dayOfWeek.value }.getOrDefault(1) < 6
                }
                if (days.isEmpty()) 0L else days.values.sumOf { list -> list.sumOf { it.amount } } / days.size
            },
            weekendAvgFen = run {
                val days = filtered.groupBy { it.date }.filterKeys {
                    runCatching { java.time.LocalDate.parse(it).dayOfWeek.value }.getOrDefault(1) >= 6
                }
                if (days.isEmpty()) 0L else days.values.sumOf { list -> list.sumOf { it.amount } } / days.size
            },
            dailyAvgFen = run {
                val days = filtered.groupBy { it.date }
                if (days.isEmpty()) 0L else total / days.size
            },
            lastMonthTotal = run {
                val last = runCatching {
                    if (month == "all") null else java.time.YearMonth.parse(month).minusMonths(1).toString()
                }.getOrNull()
                if (last == null) 0L
                else txs.filter { it.type == type && it.date.startsWith(last) }.sumOf { it.amount }
            },
            weekdayTotals = run {
                val names = listOf("一", "二", "三", "四", "五", "六", "日")
                val acc = LongArray(7)
                filtered.forEach { t ->
                    val i = runCatching { java.time.LocalDate.parse(t.date).dayOfWeek.value }.getOrDefault(1) - 1
                    if (i in 0..6) acc[i] += t.amount
                }
                names.mapIndexed { i, n -> n to acc[i] }
            },
            hourBuckets = run {
                val labels = listOf("晨 6–11", "午 11–14", "下午 14–18", "晚 18–22", "夜 22–6")
                val acc = LongArray(5)
                filtered.forEach { t ->
                    val h = t.time.substringBefore(':').toIntOrNull() ?: return@forEach
                    val i = when {
                        h in 6..10 -> 0
                        h in 11..13 -> 1
                        h in 14..17 -> 2
                        h in 18..21 -> 3
                        else -> 4
                    }
                    acc[i] += t.amount
                }
                labels.mapIndexed { i, n -> n to acc[i] }
            },
            freqPoints = extra.freqPoints,
            shareMonths = extra.shareMonths,
            momDeltas = extra.momDeltas,
            conc = extra.conc,
            elastic = extra.elastic,
            pareto = extra.pareto,
            heat = extra.heat,
            spark = extra.spark,
            flowIncome = extra.flowIncome,
            flowExpense = extra.flowExpense,
            radar = extra.radar,
        )
    }

fun setMonth(m: String) {
        monthFlow.value = m
    }

    fun setType(t: String) {
        typeFlow.value = t
    }

    /** 统计页「高级分析」是否默认展开（读设置）。 */
    fun advancedOpenInitially(): Boolean = settingsRepository.statsAdvancedOpen()

    fun persistAdvancedOpen(open: Boolean) {
        viewModelScope.launch {
            settingsRepository.setStatsAdvancedOpen(open)
        }
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