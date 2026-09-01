package com.simpleaccount.app.ui.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simpleaccount.app.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AutoRecordUiState(
    val enabled: Boolean = false,
    /** 通知使用权是否已授予 */
    val listenerGranted: Boolean = false,
)

@HiltViewModel
class AutoRecordViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val simulator: com.simpleaccount.app.auto.AutoRecordSimulator,
) : ViewModel() {

    private val _state = MutableStateFlow(AutoRecordUiState())
    val state = _state.asStateFlow()

    /** 模拟测试结果（每条：样本描述 + 结论） */
    private val _testOutput = MutableStateFlow<List<String>>(emptyList())
    val testOutput = _testOutput.asStateFlow()

    fun load(context: Context) {
        viewModelScope.launch {
            _state.value = AutoRecordUiState(
                enabled = settingsRepository.isAutoRecordEnabled(),
                listenerGranted = isListenerGranted(context),
            )
        }
    }

    /** 跑内置通知样本全链路（解析→去重→入库），结果写日志并在页面展示 */
    fun runSimulation() {
        viewModelScope.launch {
            _testOutput.value = listOf("测试中…")
            val results = simulator.runAll()
            _testOutput.value = results.map { r ->
                buildString {
                    append("#${r.index + 1} ${r.case.title}｜${r.case.text.take(24)}")
                    append("\n→ ")
                    append(if (r.recorded) "✅ 已入账（${r.parsedDesc}）" else if (r.error != null) "❌ 出错：${r.error}" else "⏭ 未入账（${r.parsedDesc}）")
                    r.error?.let { append("\n⚠ $it") }
                }
            }
        }
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        _state.value = _state.value.copy(enabled = enabled)
        viewModelScope.launch { settingsRepository.setAutoRecordEnabled(enabled) }
    }

    fun isListenerGranted(context: Context): Boolean {
        return try {
            androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(context.packageName)
        } catch (_: Exception) {
            false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoRecordScreen(
    navController: androidx.navigation.NavHostController,
    viewModel: AutoRecordViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.load(context) }

    Scaffold(
        topBar = {
            SettingsSubToolbar("无感记账", onBack = { navController.popBackStack() })
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("自动记账", fontWeight = FontWeight.SemiBold)
                    Text(
                        "微信/支付宝支付后自动记入账本，无需手动操作",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = state.enabled,
                    onCheckedChange = { viewModel.setEnabled(context, it) }
                )
            }
            HorizontalDivider(Modifier.padding(start = 16.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("通知使用权", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (state.listenerGranted) "已授权，可以自动抓取支付通知"
                        else "尚未授权，点击右侧按钮去系统设置开启",
                        fontSize = 12.sp,
                        color = if (state.listenerGranted) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                }) { Text(if (state.listenerGranted) "管理" else "去授权") }
            }
            HorizontalDivider(Modifier.padding(start = 16.dp))

            // 模拟测试：跑内置通知样本，验证解析/去重/入库全链路
            val testOutput by viewModel.testOutput.collectAsState()
            TextButton(onClick = { viewModel.runSimulation() }) {
                Text("▶ 运行模拟测试（9 条真实通知样本）")
            }
            if (testOutput.isNotEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    testOutput.forEach { line ->
                        Text(
                            line,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 3.dp)
                        )
                    }
                }
            }
            HorizontalDivider(Modifier.padding(start = 16.dp))

            Column(Modifier.padding(16.dp)) {
                Text("说明", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "· 监听微信/支付宝的支付通知，自动解析金额并按你的分类规则入账；\n" +
                        "· 通知里一般只有金额，商家名可能不完整，导入账单时会自动覆盖补全（以账单为准）；\n" +
                        "· 60 秒内相同金额的重复通知只记一笔；\n" +
                        "· 不读取任何短信；解析失败的通知直接忽略。\n" +
                        "· 建议同时把本 App 加入系统后台白名单，避免服务被清理。",
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
