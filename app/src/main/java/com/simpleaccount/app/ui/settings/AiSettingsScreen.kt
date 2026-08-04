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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController

@Composable
fun AiSettingsScreen(
    navController: NavHostController,
    viewModel: AiSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = { SettingsSubToolbar("AI 辅助设置", onBack = { navController.popBackStack() }) }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // 总开关
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("启用 AI 辅助", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = state.enabled,
                    onCheckedChange = viewModel::onEnabledChange
                )
            }
            Text(
                "开启后可借 AI 归纳商家、回答记账问题。默认关闭。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))

            // 厂商预设
            var selectedProvider by remember {
                mutableStateOf(if (state.baseUrl.contains("deepseek")) 0
                    else if (state.baseUrl.contains("openai")) 1
                    else -1)
            }
            Text("厂商预设", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                AI_PROVIDERS.forEachIndexed { idx, p ->
                    FilterChip(
                        selected = selectedProvider == idx,
                        onClick = {
                            selectedProvider = idx
                            viewModel.selectProvider(p)
                        },
                        label = { Text(p.name) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
            Spacer(Modifier.height(20.dp))

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
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.model,
                onValueChange = viewModel::onModelChange,
                label = { Text("模型名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            // 模型快捷选择：点击填入，不必手打
            val providers = if (selectedProvider in AI_PROVIDERS.indices) AI_PROVIDERS[selectedProvider].models
                else AI_PROVIDERS[0].models
            if (state.model.isNotBlank() && state.model !in providers) {
                Text(
                    "当前为自定义模型，可直接点击下方推荐填入",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 4.dp)
            ) {
                providers.forEach { m ->
                    TextButton(onClick = { viewModel.onModelChange(m) }) {
                        Text(m)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { viewModel.save(); navController.popBackStack() },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("保存")
            }
        }
    }
}
