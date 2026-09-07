package com.simpleaccount.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LooksOne
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.simpleaccount.app.data.repository.SettingsRepository

/** 服务商图标映射（每个厂商一个专属图标 + 颜色） */
private fun providerIcon(name: String): ImageVector = when (name) {
    "Psychology" -> Icons.Filled.Psychology
    "Hub" -> Icons.Filled.Hub
    "Cloud" -> Icons.Filled.Cloud
    "AutoAwesome" -> Icons.Filled.AutoAwesome
    "FlashOn" -> Icons.Filled.FlashOn
    "RocketLaunch" -> Icons.Filled.RocketLaunch
    "ElectricBolt" -> Icons.Filled.ElectricBolt
    "Star" -> Icons.Filled.Star
    "OneK" -> Icons.Filled.LooksOne
    "LooksOne" -> Icons.Filled.LooksOne
    "Waves" -> Icons.Filled.Waves
    "Explore" -> Icons.Filled.Explore
    "LocalFireDepartment" -> Icons.Filled.LocalFireDepartment
    "AccountTree" -> Icons.Filled.AccountTree
    "Science" -> Icons.Filled.Science
    "Workspaces" -> Icons.Filled.Workspaces
    "Public" -> Icons.Filled.Public
    else -> Icons.Filled.SmartToy
}

@Composable
fun AiSettingsScreen(
    navController: NavHostController,
    viewModel: AiSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = { SettingsSubToolbar("AI 服务商设置", onBack = { navController.popBackStack() }) }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // --------- 启用开关 ---------
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("启用 AI 服务商", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = state.enabled,
                    onCheckedChange = viewModel::onEnabledChange
                )
            }
            Text(
                "开启后可让 AI 助手查账、自动归类、回答记账问题。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            // --------- 智能体模型后端 ---------
            Text("智能体大脑", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "云端大模型理解最强；端侧小模型下载后全离线可用（0.6B–1.5B，免费、数据不出设备）。本地管家始终可用。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            listOf(
                SettingsRepository.BACKEND_CLOUD to "云端大模型（推荐）",
                SettingsRepository.BACKEND_ONDEVICE to "端侧小模型（离线免费）",
            ).forEach { (value, label) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { viewModel.setModelBackend(value) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = state.modelBackend == value,
                        onClick = { viewModel.setModelBackend(value) }
                    )
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (state.modelBackend == SettingsRepository.BACKEND_ONDEVICE) {
                TextButton(onClick = { navController.navigate(com.simpleaccount.app.ui.navigation.Routes.ONDEVICE_MODELS) }) {
                    Text("管理端侧模型（下载 / 基准 / 引导）")
                }
            }
            Spacer(Modifier.height(16.dp))

            // --------- 智能体执行授权 ---------
            Text("智能体执行授权", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "记账、改账、删除等写操作：默认每次确认；也可授权后自动执行。金额与时刻未说清时仍会先追问。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            listOf(
                SettingsRepository.EXEC_AUTH_CONFIRM to "每次执行前需确认（默认）",
                SettingsRepository.EXEC_AUTH_AUTO to "授权后自动执行",
            ).forEach { (value, label) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { viewModel.setAgentExecAuth(value) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = state.agentExecAuth == value,
                        onClick = { viewModel.setAgentExecAuth(value) }
                    )
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
            }
            Spacer(Modifier.height(24.dp))

            // --------- 选择服务商 ---------
            Text("选择服务商", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "点一下自动填接口。小米 MiMo 和硅基流动共用同一网关，但选中互不影响。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            // 服务商卡片网格（2列）
            val cols = 2
            val chunked = AI_PROVIDERS.chunked(cols)
            chunked.forEach { rowProviders ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowProviders.forEach { p ->
                        ProviderCard(
                            provider = p,
                            selected = state.providerId == p.id,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.selectProvider(p) }
                        )
                    }
                    // 补齐空位
                    repeat(cols - rowProviders.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // --------- 配置区 ---------
            Text("接口与密钥", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.baseUrl,
                onValueChange = viewModel::onBaseUrlChange,
                label = { Text("API Base URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.apiKey,
                onValueChange = viewModel::onKeyChange,
                label = { Text("API Key") },
                singleLine = true,
                visualTransformation = if (state.showKey)
                    androidx.compose.ui.text.input.VisualTransformation.None
                else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = viewModel::toggleKeyVisibility) {
                        Icon(
                            if (state.showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (state.showKey) "隐藏" else "显示"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // --------- 选择模型 ---------
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("选择模型", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                if (state.loadingModels) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(16.dp), strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("正在获取可用模型…", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    TextButton(onClick = { viewModel.refreshModels() }) {
                        Text(
                            if (state.models.isEmpty()) "拉取可用模型" else "刷新模型列表",
                            fontSize = 12.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (state.models.isEmpty()) "填好接口和 Key 后点「拉取可用模型」，自动获取该接口真实可用的模型。"
                else "已获取 ${state.models.size} 个该接口可用的模型，点选即可。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            val provider = AI_PROVIDERS.firstOrNull { it.id == state.providerId } ?: AI_PROVIDERS[0]

            // 模型 Chips：优先展示接口真实可用的模型；拉取失败回退厂商预设
            val modelChoices = state.models.ifEmpty { provider.models }
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                modelChoices.forEach { m ->
                    FilterChip(
                        selected = state.model == m,
                        onClick = { viewModel.onModelChange(m) },
                        label = { Text(m) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.model,
                onValueChange = viewModel::onModelChange,
                label = { Text("模型名（可自定义）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))
            Text("思考深度", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "关闭则不输出推理链、更快。部分推理模型开思考是刚需；延迟靠意图门控和工具裁剪治理，不会强制关思考。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    SettingsRepository.THINKING_OFF to "关闭",
                    SettingsRepository.THINKING_LOW to "低",
                    SettingsRepository.THINKING_MEDIUM to "中",
                    SettingsRepository.THINKING_HIGH to "高",
                ).forEach { (id, label) ->
                    FilterChip(
                        selected = state.thinkingLevel == id,
                        onClick = { viewModel.setThinkingLevel(id) },
                        label = { Text(label) }
                    )
                }
            }
            // GLM 思考态误报修复：仅当模型真正需要思考且当前为关闭时才提示；GLM 标准版不误报
            val showWarning = shouldShowThinkingWarning(state.model, state.thinkingLevel)
            if (showWarning) {
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("该模型建议开启思考", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                            Text("检测到 ${state.model} 需要思考才能稳定调用工具，请将思考切到 低/中/高。", fontSize = 11.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            } else {
                // 已开启或无需思考的正向反馈，消除“已开启仍提示”的误导
                Spacer(Modifier.height(8.dp))
                val msg = when {
                    state.thinkingLevel != SettingsRepository.THINKING_OFF && modelRequiresThinking(state.model) ->
                        "✓ 已开启思考（${state.thinkingLevel}），${state.model} 将携带思考参数"
                    state.providerId == "glm" && state.thinkingLevel == SettingsRepository.THINKING_OFF ->
                        "GLM 标准模型无需强制思考，当前关闭为正常（非误报）"
                    state.thinkingLevel != SettingsRepository.THINKING_OFF ->
                        "已开启思考，模型将输出推理过程"
                    else -> null
                }
                if (msg != null) {
                    Text(msg, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp)).padding(8.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            // --------- 截图记账（识图）：主模型优先 + 独立配置兜底 ---------
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "优先用主模型识图",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "主模型是多模态（如 glm-4v/qwen-vl/gemini）时直接用它；识别失败自动改用下方独立识图配置",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                Switch(checked = state.useMainForVision, onCheckedChange = { viewModel.setUseMainForVision(it) })
            }
            HorizontalDivider(Modifier.padding(start = 16.dp))
            Text(
                "独立识图配置（主模型是纯文本如 DeepSeek 时用）：推荐智谱，接口 open.bigmodel.cn/api/paas/v4，模型 glm-4v-flash（免费），bigmodel.cn 注册即得 Key",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.visionBaseUrl,
                onValueChange = viewModel::onVisionBaseUrlChange,
                label = { Text("识图接口地址") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.visionApiKey,
                onValueChange = viewModel::onVisionKeyChange,
                label = { Text("识图 API Key") },
                singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.visionModel,
                onValueChange = viewModel::onVisionModelChange,
                label = { Text("识图模型名（如 glm-4v-flash）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(24.dp))

            // --------- 保存 ---------
            Button(
                onClick = { viewModel.save(); navController.popBackStack() },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("保存配置", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 服务商卡片：品牌色圆形图标 + 厂商名，选中时高亮描边 */
@Composable
private fun ProviderCard(
    provider: AiProvider,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val brandColor = Color(provider.color)
    Column(
        modifier = modifier
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) brandColor else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp)
            )
            .background(
                if (selected) brandColor.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(brandColor.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                providerIcon(provider.iconName),
                contentDescription = provider.name,
                tint = brandColor,
                modifier = Modifier.size(26.dp)
            )
            if (selected) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(16.dp)
                        .background(brandColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "已选",
                        tint = Color.White,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            provider.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) brandColor else MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
        if (provider.subtitle.isNotBlank()) {
            Text(
                provider.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
