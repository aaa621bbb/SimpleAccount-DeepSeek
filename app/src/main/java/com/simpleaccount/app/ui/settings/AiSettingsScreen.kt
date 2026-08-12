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
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController

/** 服务商图标映射（OpenMinis 风格：每个厂商一个专属图标 + 颜色） */
private fun providerIcon(name: String): ImageVector = when (name) {
    "Psychology" -> Icons.Filled.Psychology
    "Hub" -> Icons.Filled.Hub
    "Cloud" -> Icons.Filled.Cloud
    "AutoAwesome" -> Icons.Filled.AutoAwesome
    "FlashOn" -> Icons.Filled.FlashOn
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
            Spacer(Modifier.height(24.dp))

            // --------- 选择服务商 ---------
            Text("选择服务商", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "选厂商点一下，自动填好接口地址。",
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
                        val isSelected = state.baseUrl.contains(p.baseUrl.replace("https://", "").replace("/v1", ""))
                        ProviderCard(
                            provider = p,
                            selected = isSelected,
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
            Text("选择模型", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "从下方推荐点选，或直接输入自定义模型名。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            val provider = AI_PROVIDERS.firstOrNull {
                state.baseUrl.contains(it.baseUrl.replace("https://", "").replace("/v1", ""))
            } ?: AI_PROVIDERS[0]

            // 模型 Chips
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                provider.models.forEach { m ->
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
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 18.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(brandColor.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                providerIcon(provider.iconName),
                contentDescription = provider.name,
                tint = brandColor,
                modifier = Modifier.size(30.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            provider.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) brandColor else MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}
