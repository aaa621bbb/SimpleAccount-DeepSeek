package com.simpleaccount.app.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simpleaccount.app.auto.AutoRecordHealth
import com.simpleaccount.app.auto.AutoRecordRuntime
import com.simpleaccount.app.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class AutoRecordUiState(
    val enabled: Boolean = false,
    val listenerGranted: Boolean = false,
    /** 与 AI 管家共用：confirm / auto */
    val execAuth: String = SettingsRepository.EXEC_AUTH_CONFIRM,
    val drafts: List<com.simpleaccount.app.data.entity.Transaction> = emptyList(),
)

@HiltViewModel
class AutoRecordViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val simulator: com.simpleaccount.app.auto.AutoRecordSimulator,
    private val autoRecordManager: com.simpleaccount.app.auto.AutoRecordManager,
    val runtime: AutoRecordRuntime,
) : ViewModel() {

    private val _state = MutableStateFlow(AutoRecordUiState())
    val state = _state.asStateFlow()

    private val _testOutput = MutableStateFlow<List<String>>(emptyList())
    val testOutput = _testOutput.asStateFlow()

    fun load(context: Context) {
        runtime.refresh()
        viewModelScope.launch {
            _state.value = AutoRecordUiState(
                enabled = settingsRepository.isAutoRecordEnabled(),
                listenerGranted = runtime.isListenerGranted(context),
                execAuth = settingsRepository.agentExecAuth(),
                drafts = autoRecordManager.listDrafts(),
            )
        }
    }

    fun confirmDraft(id: Long) {
        viewModelScope.launch {
            autoRecordManager.confirmDraft(id)
            _state.value = _state.value.copy(drafts = autoRecordManager.listDrafts())
        }
    }

    fun discardDraft(id: Long) {
        viewModelScope.launch {
            autoRecordManager.discardDraft(id)
            _state.value = _state.value.copy(drafts = autoRecordManager.listDrafts())
        }
    }

    fun runSimulation() {
        viewModelScope.launch {
            _testOutput.value = listOf("测试中…")
            val results = simulator.runAll()
            _testOutput.value = results.map { r ->
                buildString {
                    append("#${r.index + 1} ${r.case.title}｜${r.case.text.take(24)}")
                    append("\n→ ")
                    append(
                        if (r.recorded) "✅ 已入账（${r.parsedDesc}）"
                        else if (r.error != null) "❌ 出错：${r.error}"
                        else "⏭ 未入账（${r.parsedDesc}）",
                    )
                    r.error?.let { append("\n⚠ $it") }
                }
            }
        }
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        _state.value = _state.value.copy(enabled = enabled)
        viewModelScope.launch {
            settingsRepository.setAutoRecordEnabled(enabled)
            runtime.setEnabled(enabled)
            if (enabled && !runtime.isListenerGranted(context)) {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
            }
            load(context)
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
    val health by viewModel.runtime.health.collectAsState()

    LaunchedEffect(Unit) { viewModel.load(context) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) viewModel.load(context)
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    Scaffold(
        topBar = {
            SettingsSubToolbar("无感记账", onBack = { navController.popBackStack() })
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            HealthCard(health)

            TutorialCard(onShot = { navController.navigate(com.simpleaccount.app.ui.navigation.Routes.AI_SHOT) })

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("自动记账", fontWeight = FontWeight.SemiBold)
                    Text(
                        "微信/支付宝支付后自动记入账本，无需手动操作",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = state.enabled,
                    onCheckedChange = { viewModel.setEnabled(context, it) },
                )
            }
            HorizontalDivider(Modifier.padding(start = 16.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("通知使用权", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (state.listenerGranted) "已授权，可以自动抓取支付通知"
                        else "尚未授权，点击右侧按钮去系统设置开启",
                        fontSize = 12.sp,
                        color = if (state.listenerGranted) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.error,
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

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("忽略电池优化", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (health.batteryUnrestricted) "已忽略，后台不易被杀"
                        else "小米/华为等机会把后台服务杀掉，支付通知就记不上。建议允许本应用不优化。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = {
                    runCatching {
                        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                        if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
                            context.startActivity(
                                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                },
                            )
                        } else {
                            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        }
                    }
                }) { Text(if (health.batteryUnrestricted) "已忽略" else "去设置") }
            }
HorizontalDivider(Modifier.padding(start = 16.dp))

            // 待确认草稿
            if (state.drafts.isNotEmpty()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("待确认草稿（${state.drafts.size}）", fontWeight = FontWeight.SemiBold)
                    Text(
                        "低置信解析，或执行授权为「每次确认」时，会先落草稿不进统计。确认后才计入流水。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    state.drafts.forEach { d ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${d.merchant.ifBlank { d.category }} · ¥${"%.2f".format(d.amount / 100.0)}",
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    "${d.date} ${d.time} · ${d.category}" +
                                        if (d.subCategory.isNotBlank()) "/${d.subCategory}" else "",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { viewModel.confirmDraft(d.id) }) { Text("确认") }
                            TextButton(onClick = { viewModel.discardDraft(d.id) }) {
                                Text("丢弃", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(start = 16.dp))
            }

            // 执行授权提示
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("入账授权", fontWeight = FontWeight.SemiBold)
                Text(
                    when (state.execAuth) {
                        SettingsRepository.EXEC_AUTH_AUTO ->
                            "当前：自动执行。高置信通知直接入账；低置信仍进草稿。"
                        else ->
                            "当前：每次确认。所有无感记账先落草稿，需在上方确认后才计入统计。"
                    } + "（与 AI 管家「执行授权」共用，可在 AI 辅助设置里改。）",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp,
                )
            }
            HorizontalDivider(Modifier.padding(start = 16.dp))

            val testOutput by viewModel.testOutput.collectAsState()
            TextButton(onClick = { viewModel.runSimulation() }) {
                Text("▶ 运行模拟测试（9 条真实通知样本）")
            }
            if (testOutput.isNotEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    testOutput.forEach { line ->
                        Text(
                            line,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 3.dp),
                        )
                    }
                }
            }
            HorizontalDivider(Modifier.padding(start = 16.dp))

            if (health.recentEvents.isNotEmpty()) {
                Column(Modifier.padding(16.dp)) {
                    Text("最近事件", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    health.recentEvents.take(8).forEach { line ->
                        Text(
                            line,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
                HorizontalDivider(Modifier.padding(start = 16.dp))
            }

Column(Modifier.padding(16.dp)) {
                Text("说明与隐私", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "· 监听范围：仅微信 / 支付宝 / 云闪付 / 系统钱包等支付类包名；聊天噪声会被过滤；\n" +
                        "· 必须同时打开「自动记账」总开关，并授予通知使用权，否则通知来了也不会记；\n" +
                        "· 本地-only：通知正文只在本机解析入账，不上传云端、不读短信、不读通讯录；\n" +
                        "· 去重：60 秒窗口 + 当天同金额同商家（支付通知与后续到账通知合并）；\n" +
                        "· 低置信或「每次确认」→ 草稿待确认，不进统计；确认后才计入流水；\n" +
                        "· 授权引导只请求通知监听权限，范围限于支付通知；\n" +
                        "· 进程被杀后由开机/安装广播与前台保活拉起；小米/华为请关电池优化。",
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TutorialCard(onShot: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(14.dp),
    ) {
        Text("三步就能自动记", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("1. 打开下方「自动记账」开关", fontSize = 13.sp)
        Text("2. 点「去授权」，把本应用的通知使用权打开", fontSize = 13.sp)
        Text("3. 忽略电池优化（小米/华为必做，否则付完钱记不上）", fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            "然后用微信或支付宝付一笔。首页最近记录里会出现，来源是自动。通知里往往只有金额，商家名可能不完整——导入账单时会覆盖补全。",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onShot) { Text("拍账单识图（一键选图）") }
    }
}

@Composable
private fun HealthCard(health: AutoRecordHealth) {
    val ok = health.pipelineReady
    val bg = if (ok) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .padding(14.dp),
    ) {
        Text(
            if (ok) "运行中 · 支付通知会自动入账" else "尚未就绪 · 按下方清单补齐",
            fontWeight = FontWeight.Bold,
            color = if (ok) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onErrorContainer,
        )
        Spacer(Modifier.height(8.dp))
        HealthLine("总开关", health.enabled)
        HealthLine("通知使用权", health.listenerGranted)
        HealthLine("监听已连接", health.listenerBound)
        HealthLine("保活进程", health.keepAliveRunning)
        HealthLine("电池不受限", health.batteryUnrestricted)
        if (health.lastBoundAt > 0) {
            Text(
                "上次连接 " + timeFmt.format(Date(health.lastBoundAt)),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (health.lastEventAt > 0) {
            Text(
                "上次入账 " + timeFmt.format(Date(health.lastEventAt)),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (health.lastError.isNotBlank()) {
            Text(
                "最近错误：${health.lastError}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun HealthLine(label: String, ok: Boolean) {
    Text(
        (if (ok) "✓ " else "○ ") + label,
        fontSize = 13.sp,
        color = if (ok) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.error,
    )
}

private val timeFmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA)
