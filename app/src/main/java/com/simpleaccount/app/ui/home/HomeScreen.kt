package com.simpleaccount.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.simpleaccount.app.ui.components.TransactionRow
import com.simpleaccount.app.ui.navigation.Routes
import com.simpleaccount.app.util.MoneyUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    navController: NavHostController,
) {
    val state by viewModel.uiState.collectAsState()
    val snack by viewModel.snack.collectAsState()
    var showBudgetDialog by remember { mutableStateOf(false) }
    var showInsight by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // 顶部工具条（不显示 App 名，仅导入入口）
            TopAppBar(
                title = { },
                navigationIcon = { Box(Modifier.width(48.dp)) },
                actions = {
                    IconButton(onClick = { navController.navigate(Routes.IMPORT) }) {
                        Icon(Icons.Filled.FileUpload, contentDescription = "导入账单")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )

            // 本月支出卡：固定在顶部，不随列表滚动消失
            SummaryCards(state, onSetBudget = { showBudgetDialog = true })

            if (state.todayDupes.isNotEmpty()) {
                com.simpleaccount.app.ui.components.SoftCard(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(
                            "可能重复记账",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            "今天 " + state.todayDupes.joinToString("、") + "。点进流水核对一下。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (state.insightReport.isNotBlank()) {
                com.simpleaccount.app.ui.components.SoftCard(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clickable { showInsight = true }
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            "本月体检",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            state.insightHeadline,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (state.insightSub.isNotBlank()) {
                            Text(
                                state.insightSub,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (state.quickRepeats.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(
                        "再记一笔",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, bottom = 6.dp)
                    )
                    Row(
                        Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.quickRepeats.forEach { q ->
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.clickable { viewModel.repeatQuick(q) }
                            ) {
                                Text(
                                    "${q.merchant} ¥${MoneyUtil.fenToYuan(q.amount)}",
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 最近记录标题 + 排序切换
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "最近记录",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { viewModel.toggleSort() }) {
                    Text(
                        if (state.sortByAmount) "按金额" else "按时间",
                        fontSize = 13.sp
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 88.dp)
            ) {
                if (state.recent.isEmpty()) {
                    item {
                        Text(
                            "暂无记录",
                            modifier = Modifier.padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    item {
                        com.simpleaccount.app.ui.components.SoftCard(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            state.recent.forEachIndexed { idx, row ->
                                Box { TransactionRow(row.transaction, row.category, onClick = { navController.navigate(Routes.edit(row.transaction.id)) }) }
                                if (idx != state.recent.lastIndex) {
                                    androidx.compose.material3.HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                                        modifier = Modifier.padding(start = 68.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (snack != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 88.dp, bottom = 20.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                shadowElevation = 6.dp
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        snack!!,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    if (snack!!.startsWith("已再记")) {
                        TextButton(onClick = { viewModel.undoRepeat() }) {
                            Text("撤销", color = MaterialTheme.colorScheme.inverseOnSurface)
                        }
                    } else {
                        TextButton(onClick = { viewModel.dismissSnack() }) {
                            Text("好", color = MaterialTheme.colorScheme.inverseOnSurface)
                        }
                    }
                }
            }
        }

        // FAB 记一笔（渐变主色）
        FloatingActionButton(
            onClick = { navController.navigate(Routes.ADD) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            shape = RoundedCornerShape(18.dp),
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "记一笔")
        }
    }

    if (showInsight) {
        AlertDialog(
            onDismissRequest = { showInsight = false },
            title = { Text("本月体检") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    com.simpleaccount.app.ui.components.MarkdownText(state.insightReport)
                }
            },
            confirmButton = {
                TextButton(onClick = { showInsight = false }) { Text("好的") }
            }
        )
    }

    // 每月预算设置对话框（点主卡上的"本月预算"格）
    if (showBudgetDialog) {
        var budgetText by remember(showBudgetDialog) {
            mutableStateOf(
                if (state.budgetFen > 0) com.simpleaccount.app.util.MoneyUtil.fenToYuan(state.budgetFen) else ""
            )
        }
        AlertDialog(
            onDismissRequest = { showBudgetDialog = false },
            title = { Text("每月预算") },
            text = {
                Column {
                    Text(
                        "设置每月消费预算，超支时首页会提醒。留空并保存可清除预算。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = budgetText,
                        onValueChange = { budgetText = it },
                        placeholder = { Text("例如 1500") },
                        suffix = { Text("元") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val cleaned = budgetText.trim().replace("¥", "").replace(",", "")
                    val yuan = cleaned.toDoubleOrNull()
                    viewModel.setMonthlyBudget(if (yuan == null || yuan <= 0) 0L else Math.round(yuan * 100))
                    showBudgetDialog = false
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showBudgetDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SummaryCards(state: HomeUiState, onSetBudget: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // 主卡：本月支出（渐变底，与新图标蓝紫主色呼应）；点击结余格设置每月预算
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF1B3344),
                            Color(0xFF2A4A5C),
                            Color(0xFF1F3A4D)
                        )
                    )
                )
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Column {
                Text(
                    "本月支出 · ${java.time.YearMonth.now().monthValue}月",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.85f)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "¥" + MoneyUtil.fenToYuan(state.expense),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .width(36.dp)
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(Color(0xFFC2A06A))
                )
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth()) {
                    // 结余 = 预算 − 消费（未设预算时 = 收入 − 支出）
                    val balanceText = if (state.budgetFen > 0)
                        "¥" + MoneyUtil.fenToYuan(state.budgetFen - state.expense)
                    else "¥" + MoneyUtil.fenToYuan(state.balance)
                    Box(
                        Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.14f))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Column {
                            Text(
                                "结余",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                balanceText,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (state.budgetFen > 0 && state.expense > state.budgetFen) Color(0xFFFFB4AB)
                                else Color.White,
                                maxLines = 1
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    // 预算格：点击设置
                    Box(
                        Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.14f))
                            .clickable(onClick = onSetBudget)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Column {
                            Text(
                                "本月预算",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                if (state.budgetFen > 0) "¥" + MoneyUtil.fenToYuan(state.budgetFen) else "点按设置",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1
                            )
                        }
                    }
                }
                // 预算进度条（已设预算时显示）
                if (state.budgetFen > 0) {
                    Spacer(Modifier.height(10.dp))
                    val usedPercent = (state.expense.toFloat() / state.budgetFen).coerceIn(0f, 1f)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White.copy(alpha = 0.25f))
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(usedPercent)
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    if (state.expense > state.budgetFen) Color(0xFFFFB4AB) else Color.White
                                )
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (state.expense > state.budgetFen) "已超预算 ¥" + MoneyUtil.fenToYuan(state.expense - state.budgetFen)
                        else "已用 ¥" + MoneyUtil.fenToYuan(state.expense) + " / ¥" + MoneyUtil.fenToYuan(state.budgetFen),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
                if (state.expense > 0 || state.todayFen > 0) {
                    Spacer(Modifier.height(8.dp))
                    val paceBits = mutableListOf<String>()
                    paceBits.add("今天 ¥" + MoneyUtil.fenToYuan(state.todayFen))
                    if (state.projectedFen > 0) {
                        paceBits.add("按这速度月底约 ¥" + MoneyUtil.fenToYuan(state.projectedFen))
                    }
                    if (state.todayCapFen > 0) {
                        val left = state.todayCapFen - state.todayFen
                        paceBits.add(
                            if (left >= 0) "今天还能花 ¥" + MoneyUtil.fenToYuan(left)
                            else "今天已超日均 ¥" + MoneyUtil.fenToYuan(-left)
                        )
                    }
                    Text(
                        paceBits.joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryCell(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.8f)
        )
        Spacer(Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
    }
}
