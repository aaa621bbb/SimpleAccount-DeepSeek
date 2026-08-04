package com.simpleaccount.app.ui.addtransaction

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.simpleaccount.app.data.entity.Category
import com.simpleaccount.app.data.entity.Transaction
import com.simpleaccount.app.ui.components.CategoryIconCircle
import com.simpleaccount.app.ui.navigation.Routes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    navController: NavHostController,
    editId: Long?,
) {
    val vm: AddTransactionViewModel = hiltViewModel()
    // 需要给 vm 传 editId —— 通过 SavedStateHandle 已由 NavHost 参数提供
    val state by vm.state.collectAsState()
    val categories by vm.categoriesByType.collectAsState()
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()
    var showCategorySheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editId != null) "编辑记录" else "记一笔") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // 收支切换
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.type == Transaction.TYPE_EXPENSE,
                    onClick = { vm.onTypeChange(Transaction.TYPE_EXPENSE) },
                    label = { Text("支出") }
                )
                FilterChip(
                    selected = state.type == Transaction.TYPE_INCOME,
                    onClick = { vm.onTypeChange(Transaction.TYPE_INCOME) },
                    label = { Text("收入") }
                )
            }
            Spacer(Modifier.height(16.dp))

            // 金额
            OutlinedTextField(
                value = state.amountText,
                onValueChange = vm::onAmountChange,
                label = { Text("金额（元）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            // 分类：整块可点，单击打开 BottomSheet
            CategorySelector(
                selected = state.selectedCategory,
                onClick = { showCategorySheet = true }
            )
            Spacer(Modifier.height(12.dp))

            // 日期
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = state.date,
                    onValueChange = vm::onDateChange,
                    label = { Text("日期（yyyy-MM-dd）") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.merchant,
                onValueChange = vm::onMerchantChange,
                label = { Text("商家（可选）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.product,
                onValueChange = vm::onProductChange,
                label = { Text("商品（可选）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.note,
                onValueChange = vm::onNoteChange,
                label = { Text("备注（可选）") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            if (state.error != null) {
                Spacer(Modifier.height(8.dp))
                Text(state.error!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    scope.launch {
                        val ok = vm.save()
                        if (ok) navController.popBackStack()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                if (state.loading) CircularProgressIndicator(Modifier.size(20.dp))
                else Text("保存")
            }
        }
    }

    // 分类选择 BottomSheet
    if (showCategorySheet) {
        ModalBottomSheet(
            onDismissRequest = { showCategorySheet = false },
            sheetState = sheetState
        ) {
            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                item {
                    Text(
                        "选择分类",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(categories) { cat ->
                    CategorySheetRow(
                        cat = cat,
                        selected = state.selectedCategory?.name == cat.name,
                        onClick = {
                            vm.onCategorySelect(cat)
                            showCategorySheet = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CategorySelector(selected: Category?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selected != null) {
            CategoryIconCircle(selected, size = 36)
            Spacer(Modifier.size(10.dp))
            Text(selected.name, style = MaterialTheme.typography.bodyLarge)
        } else {
            Text("请选择分类", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.weight(1f))
        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
    }
}

@Composable
private fun CategorySheetRow(
    cat: Category,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryIconCircle(cat, size = 36)
        Spacer(Modifier.size(12.dp))
        Text(cat.name, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.weight(1f))
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "已选择",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
