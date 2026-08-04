package com.simpleaccount.app.ui.ai

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simpleaccount.app.data.entity.AiMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiScreen(viewModel: AiViewModel = androidx.hilt.navigation.compose.hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 助手") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // 批量归类快捷按钮
            FilledTonalButton(
                onClick = { viewModel.classifyPendingMerchants() },
                enabled = state.enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Filled.SmartToy, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("帮我归类商家${if (state.pendingCount > 0) "（待归类 ${state.pendingCount}）" else ""}")
            }

            if (!state.enabled) {
                Box(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        "AI 功能未开启，请到「我的 → AI辅助设置」开启并配置 API Key",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp
                    )
                }
            }

            // 消息列表
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                items(state.messages, key = { it.id to it.timestamp }) { msg ->
                    MessageBubble(msg)
                }
                if (state.typing) {
                    item { TypingBubble() }
                }
            }

            if (state.error != null) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text(
                        state.error!!, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.input,
                    onValueChange = viewModel::onInputChange,
                    placeholder = { Text("输入问题…") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = { viewModel.sendMessage(state.input) },
                    enabled = state.enabled && state.input.isNotBlank() && !state.typing
                ) {
                    Text("发送")
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: AiMessage) {
    val isUser = msg.role == "user"
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = if (isUser) androidx.compose.foundation.layout.Arrangement.End
        else androidx.compose.foundation.layout.Arrangement.Start
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (isUser) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                msg.content,
                color = if (isUser) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun TypingBubble() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(8.dp))
        Text("正在输入…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    }
}
