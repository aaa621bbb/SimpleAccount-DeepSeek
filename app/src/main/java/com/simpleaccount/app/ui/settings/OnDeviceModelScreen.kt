package com.simpleaccount.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.simpleaccount.app.data.ondevice.DeviceTier
import com.simpleaccount.app.data.ondevice.ModelLocalState
import java.io.File

/**
 * 端侧小模型落地页：设备画像、分级推荐、下载管理、基准验证、使用引导。
 */
@Composable
fun OnDeviceModelScreen(
    navController: NavHostController,
    viewModel: OnDeviceModelViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var importMsg by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { viewModel.refresh() }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // 复制到缓存再导入（content:// 不能直接当 File）
        runCatching {
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "local-model.gguf"
            val cache = File(context.cacheDir, "od_import_${System.currentTimeMillis()}_$name")
            context.contentResolver.openInputStream(uri)?.use { input ->
                cache.outputStream().use { output -> input.copyTo(output) }
            }
            viewModel.importLocal(cache.absolutePath, displayName = name.substringBeforeLast('.')) { ok, msg ->
                importMsg = if (ok) "已导入并启用：$msg" else msg
                cache.delete()
            }
        }.onFailure {
            importMsg = "导入失败：${it.message}"
        }
    }

    Scaffold(
        topBar = { SettingsSubToolbar("管理端侧模型", onBack = { navController.popBackStack() }) }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // ---- 首次引导 ----
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("免费 AI · 全离线", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "下载 0.6B–1.5B 量化模型后，飞行模式也能查账、记账。无账号、无订阅，数据不出设备。" +
                            "引擎自动选 GPU 加速（Vulkan/OpenCL），不支持则回退 CPU。下载走直连，不经业务 API，飞行模式外均可拉。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 18.sp,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    importLauncher.launch(arrayOf("*/*", "application/octet-stream"))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("从本机导入模型包（GGUF/bin）")
            }
            importMsg?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(16.dp))

            // ---- 设备画像 ----
            Text("设备画像", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.PhoneAndroid, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        val tierLabel = when (state.profile?.tier) {
                            DeviceTier.ENTRY -> "入门档 · 推荐 0.6B–0.8B"
                            DeviceTier.MID -> "中端档 · 推荐 0.8B–1.5B"
                            DeviceTier.HIGH -> "高端档 · 可跑 1.5B Q5"
                            null -> "探测中…"
                        }
                        Text(tierLabel, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            state.profile?.summary ?: "正在探测内存与 SoC…",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp,
                        )
                        Text(
                            "可用磁盘 ${state.freeDiskLabel}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))

            // ---- 模型列表 ----
            Text("可下载模型", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "未下载即可预览「装得下、跑多快」。绿标=当前设备推荐。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))

            com.simpleaccount.app.data.ondevice.OnDeviceModelCatalog.TIER_GROUPS.forEach { (groupId, groupLabel) ->
                val groupItems = state.models.filter { it.spec.tierGroup == groupId }
                if (groupItems.isEmpty()) return@forEach
                Text(
                    groupLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
                )
                groupItems.forEach { item ->
                    ModelCard(
                        item = item,
                        activeId = state.activeId,
                        recommended = item.spec.id in state.recommendedIds,
                        fits = item.spec.id in state.fitsIds,
                        onDownload = { viewModel.download(item.spec.id) },
                        onCancel = { viewModel.cancel(item.spec.id) },
                        onDelete = { viewModel.delete(item.spec.id) },
                        onActivate = { viewModel.activate(item.spec.id) },
                        fmt = viewModel::fmtSize,
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // ---- 基准验证 ----
            Text("性能基准", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "在本机跑一轮真实吞吐，确认 tok/s 与首字延迟后再决定是否启用。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { viewModel.runBenchmark() },
                    enabled = !state.benchmarking && state.activeId != null,
                ) {
                    if (state.benchmarking) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("测试中…")
                    } else {
                        Icon(Icons.Filled.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("跑基准")
                    }
                }
                Spacer(Modifier.width(12.dp))
                if (state.activeId == null) {
                    Text("请先下载并启用一个模型", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            state.benchmark?.let { b ->
                Spacer(Modifier.height(10.dp))
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (b.ok) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        if (b.ok) {
                            Text("吞吐 ${"%.1f".format(b.tokPerSec)} tok/s · 首字 ${b.firstTokenMs} ms", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(b.note, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(b.deviceSummary, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text(b.note, color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 13.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            // ---- 使用说明 ----
            Text("使用说明", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            val steps = listOf(
                "1. 按设备档位选「推荐」模型点下载（支持断点续传）；或点「从本机导入」注册已有 GGUF。",
                "2. 下载/导入完成后点「启用」，再到 AI 设置把「记账引擎」切到纯端侧或端侧×规则。",
                "3. 卡片展示名称、简介、参数量、体积、量化与预估吞吐。",
                "4. 可跑基准看本机 tok/s；满意后再日常使用。",
                "5. 飞行模式/无网下照常查账记账；数据全程留在本机。下载本身需网络，不经业务断网开关。",
                "6. 磁盘不够或不想用了，点删除即可释放空间。",
            )
            steps.forEach {
                Text(it, fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ModelCard(
    item: ModelLocalState,
    activeId: String?,
    recommended: Boolean,
    fits: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onActivate: () -> Unit,
    fmt: (Long) -> String,
) {
    val selected = activeId == item.spec.id && item.status == ModelLocalState.Status.READY
    val borderColor = when {
        selected -> MaterialTheme.colorScheme.primary
        recommended -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    Column(
        Modifier
            .fillMaxWidth()
            .border(if (selected || recommended) 2.dp else 1.dp, borderColor, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(enabled = item.status == ModelLocalState.Status.READY) { onActivate() }
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Memory,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.spec.displayName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    if (recommended) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "推荐",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                    if (selected) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Filled.CheckCircle, contentDescription = "已启用", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    }
                }
                Text(
                    "${item.spec.paramsLabel} · ${item.spec.quant} · ${fmt(item.spec.sizeBytes)}" +
                        " · 预估 ${"%.0f".format(item.spec.estTokPerSec)} tok/s · 首字 ~${item.spec.estFirstTokenMs}ms",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(item.spec.description, fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!fits && item.status == ModelLocalState.Status.NOT_DOWNLOADED) {
            Text("当前设备磁盘/内存可能吃力，仍可尝试下载。", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
        }
        if (item.status == ModelLocalState.Status.DOWNLOADING || item.status == ModelLocalState.Status.VERIFYING) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = item.progress, modifier = Modifier.fillMaxWidth())
            Text(
                if (item.status == ModelLocalState.Status.VERIFYING) "正在校验完整性…"
                else "下载中 ${fmt(item.downloadedBytes)} / ${fmt(item.totalBytes)}（${(item.progress * 100).toInt()}%）",
                fontSize = 11.sp,
            )
            item.error?.let { Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (item.status == ModelLocalState.Status.FAILED) {
            Text(item.error ?: "失败", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
        }
        if (item.status == ModelLocalState.Status.NOT_DOWNLOADED && item.downloadedBytes > 0) {
            Text("已缓存 ${fmt(item.downloadedBytes)}，可断点续传", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            when (item.status) {
                ModelLocalState.Status.NOT_DOWNLOADED, ModelLocalState.Status.FAILED -> {
                    Button(onClick = onDownload) {
                        Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (item.downloadedBytes > 0) "续传" else "下载")
                    }
                }
                ModelLocalState.Status.DOWNLOADING, ModelLocalState.Status.VERIFYING -> {
                    OutlinedButton(onClick = onCancel) {
                        Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("取消")
                    }
                }
                ModelLocalState.Status.READY -> {
                    if (!selected) {
                        Button(onClick = onActivate) { Text("启用") }
                    } else {
                        Text("当前启用", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                    }
                }
            }
        }
    }
}
