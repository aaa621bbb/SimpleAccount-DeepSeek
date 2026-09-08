package com.simpleaccount.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.simpleaccount.app.data.entity.Transaction
import com.simpleaccount.app.data.insights.InsightsEngine
import com.simpleaccount.app.data.repository.AccountRepository
import com.simpleaccount.app.data.repository.SettingsRepository
import com.simpleaccount.app.ui.settings.SettingsSubToolbar
import com.simpleaccount.app.util.DateUtil
import com.simpleaccount.app.util.MoneyUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProjectionDetailViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    var breakdown by mutableStateOf<InsightsEngine.ProjectionBreakdown?>(null)
        private set

    fun load() {
        viewModelScope.launch {
            breakdown = runCatching {
                val month = DateUtil.thisMonth()
                val all = runCatching {
                    settingsRepository.filterByLedgerScope(accountRepository.getAll())
                }.getOrElse {
                    accountRepository.getAll().filter { it.source != Transaction.SOURCE_DRAFT }
                }
                val monthTx = all.filter { it.date.startsWith(month) }
                val day = java.time.LocalDate.now().dayOfMonth.coerceAtLeast(1)
                val days = java.time.YearMonth.now().lengthOfMonth()
                InsightsEngine.projectMonthEndBreakdown(
                    monthTx = monthTx,
                    dayOfMonth = day,
                    daysInMonth = days,
                    month = month,
                    budgetFen = runCatching { settingsRepository.monthlyBudget() }.getOrDefault(0L),
                    includeRefund = runCatching { settingsRepository.includeRefund() }.getOrDefault(true),
                    includeInvestIncome = runCatching { settingsRepository.includeInvestDividend() }.getOrDefault(true),
                    includeInvestExpense = runCatching { settingsRepository.includeInvestExpense() }.getOrDefault(true),
                )
            }.getOrElse { e ->
                InsightsEngine.ProjectionBreakdown(
                    month = DateUtil.thisMonth(),
                    dayOfMonth = java.time.LocalDate.now().dayOfMonth,
                    daysInMonth = java.time.YearMonth.now().lengthOfMonth(),
                    spentFen = 0, oneShotFen = 0, recurringSpentFen = 0, recurringProjectedFen = 0,
                    projectedFen = 0, budgetFen = 0,
                    includeRefund = true, includeInvestIncome = true, includeInvestExpense = true,
                    clusters = emptyList(),
                    formula = "测算暂不可用：${e.message ?: e.javaClass.simpleName}",
                )
            }
        }
    }
}

@Composable
fun ProjectionDetailScreen(
    navController: NavHostController,
    viewModel: ProjectionDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.load() }
    val b = viewModel.breakdown

    Scaffold(
        topBar = {
            SettingsSubToolbar("月底测算详情", onBack = { if (!navController.popBackStack()) navController.navigate(com.simpleaccount.app.ui.navigation.Routes.HOME) { launchSingleTop = true } })
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (b == null) {
                Text("计算中…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }

            // 总览
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "按这速度月底约",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    Text(
                        "¥" + MoneyUtil.fenToYuan(b.projectedFen),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "本月已花 ¥${MoneyUtil.fenToYuan(b.spentFen)} · 第 ${b.dayOfMonth}/${b.daysInMonth} 天",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (b.budgetFen > 0) {
                        val delta = b.projectedFen - b.budgetFen
                        Text(
                            if (delta > 0) "相对预算将超 ¥${MoneyUtil.fenToYuan(delta)}"
                            else "相对预算结余约 ¥${MoneyUtil.fenToYuan(-delta)}",
                            fontSize = 13.sp,
                            color = if (delta > 0) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("测算公式", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(b.formula, fontSize = 13.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "一次性 ¥${MoneyUtil.fenToYuan(b.oneShotFen)}（不摊销） · " +
                    "日频已发生 ¥${MoneyUtil.fenToYuan(b.recurringSpentFen)} → " +
                    "外推 ¥${MoneyUtil.fenToYuan(b.recurringProjectedFen)}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
            )

            Spacer(Modifier.height(16.dp))
            Text("口径与豁免", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            ScopeRow("退款计入统计", b.includeRefund)
            ScopeRow("投资收入计入", b.includeInvestIncome)
            ScopeRow("投资支出计入预算/测算", b.includeInvestExpense)
            TextButton(onClick = {
                navController.navigate(com.simpleaccount.app.ui.navigation.Routes.LEDGER_SCOPE)
            }) {
                Text("调整收支口径")
            }
            Text(
                "日频（地铁/餐饮等）按日均 × 整月；一次性（话费/火车票/房租等关键词，或本月 ≤2 天出现且非日频）只计已发生。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text(
                "聚类明细（${b.clusters.size}）",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            if (b.clusters.isEmpty()) {
                Text("本月暂无支出聚类。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                b.clusters.forEach { c ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(c.label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
                            Text(
                                "${c.category} · ${c.dayCount} 天 · ${if (c.oneShot) "一次性" else "日频外推"}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(c.reason, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            "¥" + MoneyUtil.fenToYuan(c.totalFen),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ScopeRow(label: String, on: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 13.sp)
        Text(
            if (on) "纳入" else "豁免",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
