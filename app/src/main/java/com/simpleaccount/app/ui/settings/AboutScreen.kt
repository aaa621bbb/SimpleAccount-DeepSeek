package com.simpleaccount.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController

@Composable
fun AboutScreen(navController: NavHostController) {
    val ctx = LocalContext.current
    val pkg = remember {
        try {
            val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            Triple(info.versionName ?: "-", info.longVersionCode.toString(), ctx.packageName)
        } catch (_: Exception) {
            Triple("-", "-", ctx.packageName)
        }
    }
    val versionName = pkg.first
    val versionCode = pkg.second
    val packageName = pkg.third
    val buildTime = remember {
        try {
            val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            val t = info.lastUpdateTime
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(t))
        } catch (_: Exception) { "-" }
    }

    Scaffold(
        topBar = { SettingsSubToolbar("关于", onBack = { navController.popBackStack() }) }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 顶部品牌卡
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(32.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("记账", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("SimpleAccount", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "一个简单好用的个人记账应用 — 手动记账、微信/支付宝账单导入、统计报表与 AI 辅助分类，全部离线数据在本地。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp
                    )
                }
            }

            // 版本与构建信息
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("版本与构建", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    InfoRow("版本名", versionName)
                    InfoRow("版本号", versionCode)
                    InfoRow("包名", packageName)
                    InfoRow("构建时间", buildTime)
                    InfoRow("渠道", "Google Play / 直装")
                    InfoRow("最低系统", "Android 8.0 (API 26)")
                    InfoRow("目标系统", "Android 14 (API 34)")
                    Text(
                        "提示：点击可复制版本信息，提问题时请带上版本名与版本号。",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(8.dp)
                            .fillMaxWidth()
                            .clickable {
                                val label = "SimpleAccount $versionName ($versionCode)"
                                val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                cm.setPrimaryClip(android.content.ClipData.newPlainText("version", label))
                            }
                    )
                }
            }

            // 开源许可与致谢
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("开源许可 / 致谢", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Text("本应用基于以下开源项目构建，感谢社区：", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Bullet("Kotlin / Jetpack Compose / Material 3 — Apache 2.0")
                    Bullet("Room / Hilt / Navigation — Apache 2.0")
                    Bullet("OkHttp — Apache 2.0")
                    Bullet("Apache POI (xlsx 解析) — Apache 2.0")
                    Bullet("EncryptedSharedPreferences (Jetpack Security) — Apache 2.0")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("许可证：GNU GPL v3.0，详见仓库 LICENSE。附加商业授权条款见 LICENSE-ADDITIONAL-TERMS.md。", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedLink("查看 LICENSE") {
                            openUrl(ctx, "https://github.com/aaa621bbb/SimpleAccount-DeepSeek/blob/main/LICENSE")
                        }
                        OutlinedLink("致谢名单") {
                            openUrl(ctx, "https://github.com/aaa621bbb/SimpleAccount-DeepSeek#%E8%87%B4%E8%B0%A2")
                        }
                    }
                }
            }

            // 使用规范入口
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Gavel, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("使用规范", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    SpecRow(Icons.Filled.Security, "隐私说明", "数据仅存本地私有目录，不上传服务器；AI 调用仅在你主动开启并配置 Key 后才会联网。") {
                        openUrl(ctx, "https://github.com/aaa621bbb/SimpleAccount-DeepSeek/blob/main/README.md")
                    }
                    SpecRow(Icons.Filled.Gavel, "用户协议", "非商业用途可自由学习与个人使用；商业用途需经作者书面授权。") {
                        openUrl(ctx, "https://github.com/aaa621bbb/SimpleAccount-DeepSeek/blob/main/LICENSE-ADDITIONAL-TERMS.md")
                    }
                    SpecRow(Icons.Filled.Link, "仓库与文档", "GitHub 仓库、交接文档、导入说明与常见问题。") {
                        openUrl(ctx, "https://github.com/aaa621bbb/SimpleAccount-DeepSeek")
                    }
                }
            }

            // 反馈渠道
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Feedback, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("反馈与联系", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Text("遇到问题或有建议？欢迎通过以下渠道联系，带上“关于”里的版本信息更易排障。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FeedbackRow("GitHub Issues", "提 Bug / 需求，开公开 Issue 便于追踪", "去提 Issue") {
                        openUrl(ctx, "https://github.com/aaa621bbb/SimpleAccount-DeepSeek/issues")
                    }
                    FeedbackRow("邮件反馈", "私密问题可发邮件，标题注明 SimpleAccount", "发邮件") {
                        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:aaa621bbb@users.noreply.github.com?subject=SimpleAccount%20反馈%20v$versionName"))
                        runCatching { ctx.startActivity(intent) }
                    }
                    FeedbackRow("日志一键分享", "设置 → 日志与排障 → 分享，可把运行日志发来排障", "去日志") {
                        navController.navigate(com.simpleaccount.app.ui.navigation.Routes.LOGS)
                    }
                }
            }

            // 底部占位与版权
            Text(
                "© 2026 SimpleAccount · 用心记好每一笔",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Bullet(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text("• ", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)
    }
}

@Composable
private fun OutlinedLink(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SpecRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, desc: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 14.sp)
        }
        Icon(Icons.Filled.Link, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun FeedbackRow(title: String, desc: String, action: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(action, fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Medium)
        }
    }
}

private fun openUrl(ctx: android.content.Context, url: String) {
    runCatching {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
