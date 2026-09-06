package com.simpleaccount.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavHostController
import com.simpleaccount.app.data.memory.MemoryStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MemoryViewModel @Inject constructor(
    val store: MemoryStore,
) : ViewModel()

private data class MemoryEntry(val heading: String, val body: String)

private fun parseEntries(body: String): List<MemoryEntry> {
    val blocks = body.split(Regex("(?m)^## ")).drop(1)
    if (blocks.isEmpty()) {
        val trimmed = body.trim()
        return if (trimmed.isBlank() || trimmed.startsWith("# ")) emptyList()
        else listOf(MemoryEntry("全文", trimmed))
    }
    return blocks.map { b ->
        val nl = b.indexOf('\n')
        val title = if (nl < 0) b.trim() else b.substring(0, nl).trim()
        val text = if (nl < 0) "" else b.substring(nl + 1).trim()
        MemoryEntry(title, text)
    }
}

@Composable
fun MemoryScreen(
    navController: NavHostController,
    viewModel: MemoryViewModel = hiltViewModel(),
) {
    var days by remember { mutableStateOf(emptyList<Pair<String, String>>()) }
    var global by remember { mutableStateOf("") }
    var openDate by remember { mutableStateOf<String?>(null) }
    var openGlobal by remember { mutableStateOf(false) }
    var openEntry by remember { mutableStateOf<MemoryEntry?>(null) }
    LaunchedEffect(Unit) {
        days = viewModel.store.listDaily()
        global = viewModel.store.globalFile().takeIf { it.exists() }?.readText().orEmpty()
    }
    val openDay = days.firstOrNull { it.first == openDate }
    Scaffold(topBar = { SettingsSubToolbar("管家记忆", onBack = { navController.popBackStack() }) }) { padding ->
        when {
            openEntry != null -> {
                Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
                    TextButton(onClick = { openEntry = null }) { Text("返回条目列表") }
                    Text(openEntry!!.heading, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(openEntry!!.body.ifBlank { "（空）" }, fontSize = 14.sp, lineHeight = 22.sp)
                }
            }
            openGlobal -> {
                val rules = global.lines().filter { it.startsWith("- ") }.map { it.removePrefix("- ").trim() }
                LazyColumn(Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
                    item {
                        TextButton(onClick = { openGlobal = false }) { Text("返回") }
                        Text("全局规则", fontWeight = FontWeight.Bold)
                        Text("点一条阅读全文。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (rules.isEmpty()) {
                        item { Text("还没有全局规则。", modifier = Modifier.padding(vertical = 12.dp)) }
                    } else {
                        items(rules) { r ->
                            Text(
                                r,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { openEntry = MemoryEntry("全局规则", r) }
                                    .padding(vertical = 10.dp),
                                fontSize = 14.sp,
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
            openDay != null -> {
                val entries = parseEntries(openDay.second)
                LazyColumn(Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
                    item {
                        TextButton(onClick = { openDate = null }) { Text("返回日历") }
                        Text(openDay.first, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("当天 ${entries.size} 条，点开阅读。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (entries.isEmpty()) {
                        item { Text(openDay.second.ifBlank { "这一天是空的。" }, modifier = Modifier.padding(vertical = 12.dp)) }
                    } else {
                        items(entries) { e ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { openEntry = e }
                                    .padding(vertical = 10.dp)
                            ) {
                                Text(e.heading, fontWeight = FontWeight.SemiBold)
                                Text(
                                    e.body.replace("\n", " ").take(80),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
            else -> {
                LazyColumn(
                    Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                ) {
                    item {
                        Text("按天分开。点一天进入当日条目。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        Text("全局规则", fontWeight = FontWeight.Bold)
                    }
                    item {
                        val preview = global.lines().filter { it.startsWith("- ") }.size
                        Text(
                            if (preview == 0) "还没有全局规则。点这里查看。" else "共 $preview 条，点开查看",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { openGlobal = true }
                                .padding(vertical = 10.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("每日日志", fontWeight = FontWeight.Bold)
                    }
                    if (days.isEmpty()) {
                        item {
                            Text(
                                "还没有日记。跟管家说「记住…」就会写到今天。",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                    } else {
                        items(days, key = { it.first }) { (date, body) ->
                            val n = parseEntries(body).size
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { openDate = date }
                                    .padding(vertical = 10.dp)
                            ) {
                                Text(date, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                Text(
                                    if (n > 0) "$n 条记忆" else "点开阅读",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
