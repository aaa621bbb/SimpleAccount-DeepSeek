package com.simpleaccount.app.ui.ai

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.simpleaccount.app.data.entity.AiMessage
import com.simpleaccount.app.ui.components.MarkdownText
import com.simpleaccount.app.ui.navigation.Routes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 长按消息后的操作目标 */
private data class MessageActions(val msg: AiMessage, val isLastAssistant: Boolean)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AiScreen(
    viewModel: AiViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
    navController: NavHostController? = null,
    autoPickImage: Boolean = false,
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    var showSessions by remember { mutableStateOf(false) }
    var messageActions by remember { mutableStateOf<MessageActions?>(null) }
    var confirmDeleteConversation by remember { mutableStateOf<String?>(null) }

    // 截图记账：从相册选 1-5 张（支持长图），发给视觉模型提取账单
    val imagePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia(5)
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.sendScreenshotMessage(uris)
    }

    // 识别结果预览弹层状态
    val screenshotSheetState = androidx.compose.material3.rememberModalBottomSheetState()

    LaunchedEffect(Unit) { viewModel.refreshEnabled() }

    var autoPicked by remember { mutableStateOf(false) }
    LaunchedEffect(autoPickImage) {
        if (autoPickImage && !autoPicked) {
            autoPicked = true
            runCatching {
                imagePicker.launch(
                    androidx.activity.result.PickVisualMediaRequest(
                        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                    )
                )
            }
        }
    }

    // 贴底跟随：用户上滑下探时不再强制滚回首行
    var stickToBottom by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            val atEnd = last != null &&
                last.index >= info.totalItemsCount - 1 &&
                (last.offset + last.size) <= info.viewportEndOffset + 120
            listState.isScrollInProgress to atEnd
        }.collect { (scrolling, atEnd) ->
            if (scrolling && !atEnd) stickToBottom = false
            else if (!scrolling && atEnd) stickToBottom = true
        }
    }
    LaunchedEffect(state.messages.size) {
        if (state.messages.lastOrNull()?.role == AiMessage.ROLE_USER) stickToBottom = true
    }
    LaunchedEffect(stickToBottom, state.messages.size, state.typing, state.streamingText?.length?.div(80)) {
        if (!stickToBottom) return@LaunchedEffect
        val last = listState.layoutInfo.totalItemsCount - 1
        if (last >= 0) listState.scrollToItem(last, scrollOffset = Int.MAX_VALUE / 8)
    }

    Scaffold { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
        ) {
            // 紧凑标题行：会话列表入口 + 当前会话名 + 归类 + 设置
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showSessions = true }) {
                    Icon(Icons.Filled.Menu, contentDescription = "会话列表")
                }
                Text(
                    state.currentTitle.ifEmpty { "AI 管家" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                if (state.pendingCount > 0) {
                    Text(
                        "${state.pendingCount} 待归类",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
                IconButton(onClick = { viewModel.classifyPendingMerchants() }) {
                    Icon(Icons.Filled.SmartToy, contentDescription = "帮我归类商家")
                }
                IconButton(onClick = { navController?.navigate(Routes.AI_SETTINGS) }) {
                    Icon(Icons.Filled.Settings, contentDescription = "AI 设置")
                }
            }

            if (!state.enabled) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "AI 功能未开启，请到设置中开启并配置 API Key",
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 13.sp
                        )
                        TextButton(onClick = { navController?.navigate(Routes.AI_SETTINGS) }) {
                            Text("去设置")
                        }
                    }
                }
            }

            // 消息列表
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
            ) {
                val visible = state.messages
                items(visible, key = { it.id }) { msg ->
                    val isLastAssistant =
                        msg.role == AiMessage.ROLE_ASSISTANT && msg.id == visible.lastOrNull {
                            it.role == AiMessage.ROLE_ASSISTANT
                        }?.id
                    MessageBubble(
                        msg = msg,
                        isLastAssistant = isLastAssistant,
                        onLongPress = { messageActions = MessageActions(msg, isLastAssistant) }
                    )
                }
                if (state.typing) {
                    item {
                        if (state.traces.isNotEmpty()) {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                                state.traces.takeLast(8).forEach { t ->
                                    Text(
                                        "· $t",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                        val stream = state.streamingText
                        if (!stream.isNullOrEmpty()) StreamingBubble(stream, state.reasoning)
                        else TypingBubble(state.phase, state.reasoning)
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }

            state.pendingConfirm?.let { pending ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(
                            pending.summary,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = { viewModel.dismissPending() }) { Text("取消") }
                            TextButton(onClick = { viewModel.confirmPending() }) { Text("确认执行") }
                        }
                    }
                }
            }

            // 错误横幅 + 重试
            if (state.error != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            state.error!!,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(onClick = { viewModel.regenerate() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("重试", fontSize = 13.sp)
                        }
                    }
                }
            }

            // 输入栏（紧凑）
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ) {
                Row(
                    Modifier.padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            runCatching {
                                imagePicker.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            }
                        },
                        enabled = !state.typing,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Filled.AddPhotoAlternate,
                            contentDescription = "截图记账",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    OutlinedTextField(
                        value = state.input,
                        onValueChange = viewModel::onInputChange,
                        placeholder = { Text("问点什么…", fontSize = 13.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 40.dp, max = 96.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        maxLines = 3
                    )
                    Spacer(Modifier.width(6.dp))
                    FilledIconButton(
                        onClick = {
                            if (state.typing) viewModel.stopAgent()
                            else viewModel.sendMessage(state.input)
                        },
                        enabled = state.typing || (state.input.isNotBlank()),
                        modifier = Modifier.size(38.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (state.typing) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        if (state.typing) {
                            Icon(Icons.Filled.Stop, contentDescription = "停止", modifier = Modifier.size(17.dp))
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送", modifier = Modifier.size(17.dp))
                        }
                    }
                }
            }
        }
    }

    // ---------------- 会话列表抽屉 ----------------
    if (showSessions) {
        ModalBottomSheet(onDismissRequest = { showSessions = false }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("会话列表", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = {
                        viewModel.newConversation()
                        showSessions = false
                    }) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("新建会话")
                    }
                }
                HorizontalDivider()
                LazyColumn(modifier = Modifier.height(360.dp)) {
                    items(state.conversations, key = { it.id }) { conv ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    conv.title,
                                    fontWeight = if (conv.id == state.currentConversationId) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1
                                )
                            },
                            supportingContent = {
                                Text(formatTime(conv.updatedAt), fontSize = 11.sp)
                            },
                            leadingContent = {
                                Icon(
                                    Icons.Filled.SmartToy,
                                    contentDescription = null,
                                    tint = if (conv.id == state.currentConversationId)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            trailingContent = {
                                IconButton(onClick = { confirmDeleteConversation = conv.id }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "删除会话",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            modifier = Modifier.clickable {
                                viewModel.switchTo(conv.id)
                                showSessions = false
                            }
                        )
                    }
                }
            }
        }
    }

    // 删除会话确认
    confirmDeleteConversation?.let { convId ->
        AlertDialog(
            onDismissRequest = { confirmDeleteConversation = null },
            title = { Text("删除会话") },
            text = { Text("删除后会话及其全部消息无法恢复，确定删除？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteConversation(convId)
                    confirmDeleteConversation = null
                    showSessions = false
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteConversation = null }) { Text("取消") }
            }
        )
    }

    // 截图识别结果预览：勾选后确认入账
    state.screenshotPending?.let { pending ->
        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissScreenshot() },
            sheetState = screenshotSheetState
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "识别到 ${pending.items.size} 笔，勾选后入账",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { viewModel.toggleAllScreenshot(true) }) { Text("全选") }
                }
                LazyColumn(
                    modifier = Modifier.height(340.dp)
                ) {
                    items(pending.items.size) { i ->
                        val it0 = pending.items[i]
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { viewModel.toggleScreenshotItem(i) },
                                    onLongClick = {}
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.Checkbox(
                                checked = it0.selected && !it0.duplicate,
                                onCheckedChange = { viewModel.toggleScreenshotItem(i) },
                                enabled = !it0.duplicate
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    it0.merchant.ifBlank { "未识别商家" } +
                                        (it0.product?.let { " · $it" } ?: ""),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1
                                )
                                Text(
                                    (it0.date ?: "日期未知（确认后将记入今天）") +
                                        (it0.time?.let { tm -> " $tm" } ?: " 时间未知") +
                                        if (it0.duplicate) " · 账本已有（跳过）" else "",
                                    fontSize = 11.sp,
                                    color = if (it0.duplicate) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                (if (it0.type == "income") "+" else "-") +
                                    "¥" + java.lang.String.format("%.2f", it0.amount),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = if (it0.type == "income") androidx.compose.ui.graphics.Color(0xFF2ECC71)
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        onClick = { viewModel.dismissScreenshot() },
                        modifier = Modifier.weight(1f)
                    ) { Text("放弃") }
                    FilledIconButton(
                        onClick = { viewModel.commitScreenshot() },
                        enabled = pending.selectableCount > 0,
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("确认入账（${pending.selectableCount} 笔）", fontSize = 13.sp)
                    }
                }
            }
        }
    }

    // 长按消息操作：复制 / 删除 / 撤回 / 重新生成
    messageActions?.let { actions ->        ModalBottomSheet(
            onDismissRequest = { messageActions = null },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(Modifier.fillMaxWidth().padding(bottom = 28.dp)) {
                Text(
                    if (actions.msg.role == AiMessage.ROLE_USER) "消息操作" else "回复操作",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
                HorizontalDivider()
                SheetAction(Icons.Filled.ContentCopy, "复制内容") {
                    clipboard.setText(AnnotatedString(actions.msg.content))
                    android.widget.Toast.makeText(context, "已复制", android.widget.Toast.LENGTH_SHORT).show()
                    messageActions = null
                }
                if (actions.isLastAssistant) {
                    SheetAction(Icons.Filled.Refresh, "重新生成这条回复") {
                        viewModel.regenerate()
                        messageActions = null
                    }
                }
                SheetAction(Icons.Filled.Undo, "撤回（保留占位）") {
                    viewModel.withdrawMessage(actions.msg.id)
                    messageActions = null
                }
                SheetAction(Icons.Filled.Delete, "删除") {
                    viewModel.deleteMessage(actions.msg.id)
                    messageActions = null
                }
            }
        }
    }
}

@Composable
private fun SheetAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    msg: AiMessage,
    isLastAssistant: Boolean,
    onLongPress: () -> Unit,
) {
    // 撤回的消息显示占位
    if (msg.status == AiMessage.STATUS_WITHDRAWN) {
        Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
            Text("消息已撤回", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        }
        return
    }
    val isUser = msg.role == AiMessage.ROLE_USER
    val isError = msg.status == AiMessage.STATUS_ERROR
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .combinedClickable(onClick = {}, onLongClick = onLongPress),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isUser) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.SmartToy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
        }
        Box(
            Modifier
                .widthIn(max = 340.dp)
                .clip(
                    if (isUser) RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
                    else RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
                )
                .background(
                    when {
                        isUser -> MaterialTheme.colorScheme.primary
                        isError -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    }
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (isUser) {
                Text(
                    msg.content,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 14.sp,
                    lineHeight = 21.sp
                )
            } else {
                MarkdownText(
                    text = msg.content,
                    baseColor = if (isError) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }
        if (isUser) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun TypingBubble(phase: String?, reasoning: String? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.SmartToy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Row(
            Modifier
                .clip(RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(Modifier.size(13.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(7.dp))
            Column {
                Text(
                    phase ?: "正在办理…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
                if (!reasoning.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        com.simpleaccount.app.ui.components.coalesceReasoning(reasoning),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun StreamingBubble(text: String, reasoning: String? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.SmartToy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .widthIn(max = 340.dp)
                .clip(RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column {
                if (!reasoning.isNullOrBlank()) {
                    Text(
                        com.simpleaccount.app.ui.components.coalesceReasoning(reasoning),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    Spacer(Modifier.height(6.dp))
                }
                MarkdownText(text = text, baseColor = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

private fun formatTime(ts: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(ts))
