package com.simpleaccount.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.simpleaccount.app.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LedgerScopeUi(
    val includeRefund: Boolean = true,
    val includeInvestDividend: Boolean = true,
    val includeInvestExpense: Boolean = true,
)

@HiltViewModel
class LedgerScopeViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(LedgerScopeUi())
    val state = _state.asStateFlow()

    fun load() {
        _state.value = LedgerScopeUi(
            includeRefund = settings.includeRefund(),
            includeInvestDividend = settings.includeInvestDividend(),
            includeInvestExpense = settings.includeInvestExpense(),
        )
    }

    fun setRefund(v: Boolean) {
        _state.value = _state.value.copy(includeRefund = v)
        viewModelScope.launch { settings.setIncludeRefund(v) }
    }

    fun setInvestDiv(v: Boolean) {
        _state.value = _state.value.copy(includeInvestDividend = v)
        viewModelScope.launch { settings.setIncludeInvestDividend(v) }
    }

    fun setInvestExp(v: Boolean) {
        _state.value = _state.value.copy(includeInvestExpense = v)
        viewModelScope.launch { settings.setIncludeInvestExpense(v) }
    }
}

/**
 * 收支口径：退款 / 投资分红 / 投资支出 是否计入统计、首页、预算与月底测算。
 * 默认均计入；关闭后对应流水从统计过滤（账单列表仍可见）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScopeScreen(
    navController: NavHostController,
    viewModel: LedgerScopeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(
        topBar = { SettingsSubToolbar("收支口径", onBack = { navController.popBackStack() }) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Text(
                "这些开关只影响首页 / 统计 / 体检 / 预算测算的合计口径，" +
                    "不会删除账单。草稿（待确认的无感记账）始终不计入。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )

            ScopeSwitch(
                title = "退款计入流水",
                caption = "关闭后，分类/商家/备注含「退款」的记录不计入收入与支出统计。",
                checked = state.includeRefund,
                onChange = viewModel::setRefund,
            )
            HorizontalDivider(Modifier.padding(start = 16.dp))
            ScopeSwitch(
                title = "投资分红计入收入",
                caption = "关闭后，一级分类为「投资」的收入（分红、赎回收益等）不计入收入统计。",
                checked = state.includeInvestDividend,
                onChange = viewModel::setInvestDiv,
            )
            HorizontalDivider(Modifier.padding(start = 16.dp))
            ScopeSwitch(
                title = "投资支出计入支出",
                caption = "关闭后，一级分类为「投资」的支出（买入、定投等）不计入支出与预算。",
                checked = state.includeInvestExpense,
                onChange = viewModel::setInvestExp,
            )

            Spacer(Modifier.height(16.dp))
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("月底预算测算", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "「按这速度月底约」不再把所有支出一刀切日均摊销：\n" +
                        "· 地铁 / 公交 / 餐饮外卖等日频支出：按已发生日均 × 整月天数外推；\n" +
                        "· 话费、单次火车票等一次性/低频：只计已发生金额一次，不按剩余天数摊销。\n" +
                        "豁免范围从严收窄，地铁等常态仍按日折算。",
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ScopeSwitch(
    title: String,
    caption: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(caption, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 17.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
