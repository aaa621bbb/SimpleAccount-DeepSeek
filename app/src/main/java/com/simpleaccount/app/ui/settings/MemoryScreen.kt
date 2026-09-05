package com.simpleaccount.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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

@Composable
fun MemoryScreen(
    navController: NavHostController,
    viewModel: MemoryViewModel = hiltViewModel(),
) {
    var days by remember { mutableStateOf(emptyList<Pair<String, String>>()) }
    var global by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        days = viewModel.store.listDaily()
        global = viewModel.store.globalFile().takeIf { it.exists() }?.readText().orEmpty()
    }
    Scaffold(topBar = { SettingsSubToolbar("管家记忆", onBack = { navController.popBackStack() }) }) { padding ->
        LazyColumn(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            item {
                Text("按天分开，不是一团黑箱。每天一个文件。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Text("全局规则", fontWeight = FontWeight.Bold)
                Text(global.ifBlank { "还没有全局规则。" }, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
                Spacer(Modifier.height(8.dp))
                Text("每日日志", fontWeight = FontWeight.Bold)
            }
            if (days.isEmpty()) {
                item { Text("还没有日记。跟管家说「记住…」就会写到今天。", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp)) }
            } else {
                items(days, key = { it.first }) { (date, body) ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                        Text(date, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        Text(body, fontSize = 13.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}
