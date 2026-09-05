package com.simpleaccount.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.simpleaccount.app.data.entity.Ledger
import com.simpleaccount.app.data.repository.LedgerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LedgerManageViewModel @Inject constructor(
    private val ledgerRepository: LedgerRepository,
) : ViewModel() {
    val ledgers = ledgerRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2000), emptyList())
    val currentId = ledgerRepository.currentIdFlow

    fun create(name: String) = viewModelScope.launch { ledgerRepository.create(name) }
    fun rename(id: Long, name: String) = viewModelScope.launch { ledgerRepository.rename(id, name) }
    fun delete(id: Long) = viewModelScope.launch { ledgerRepository.delete(id) }
    fun switchTo(id: Long) = ledgerRepository.switchTo(id)
}

@Composable
fun LedgerManageScreen(
    navController: NavHostController,
    vm: LedgerManageViewModel = hiltViewModel(),
) {
    val ledgers by vm.ledgers.collectAsState()
    val current by vm.currentId.collectAsState()
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Ledger?>(null) }
    var deleting by remember { mutableStateOf<Ledger?>(null) }
    var name by remember { mutableStateOf("") }

    Scaffold(
        topBar = { SettingsSubToolbar("账本管理", onBack = { navController.popBackStack() }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { name = ""; creating = true }) {
                Icon(Icons.Filled.Add, contentDescription = "新建账本")
            }
        }
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                Text(
                    "每个账本的流水完全独立。AI 管家只看当前账本，不会把别的账本算进来。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
            items(ledgers, key = { it.id }) { l ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { vm.switchTo(l.id) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            l.name + if (l.isDefault) "（主）" else "",
                            fontWeight = if (l.id == current) FontWeight.Bold else FontWeight.Normal,
                            color = if (l.id == current) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                        if (l.id == current) Text("当前使用", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { renaming = l; name = l.name }) {
                        Icon(Icons.Filled.Edit, contentDescription = "改名")
                    }
                    if (!l.isDefault) {
                        IconButton(onClick = { deleting = l }) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    if (creating || renaming != null) {
        AlertDialog(
            onDismissRequest = { creating = false; renaming = null },
            title = { Text(if (creating) "新建账本" else "改名") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it.take(16) }, label = { Text("账本名称") }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    if (creating) vm.create(name) else renaming?.let { vm.rename(it.id, name) }
                    creating = false; renaming = null
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { creating = false; renaming = null }) { Text("取消") } }
        )
    }
    deleting?.let { l ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除账本") },
            text = { Text("将删除「${l.name}」及其全部流水，不可恢复。") },
            confirmButton = {
                TextButton(onClick = { vm.delete(l.id); deleting = null }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } }
        )
    }
}
