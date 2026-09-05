package com.simpleaccount.app.ui.addtransaction

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.simpleaccount.app.data.entity.Category
import com.simpleaccount.app.data.entity.Transaction
import com.simpleaccount.app.ui.components.CategoryIconCircle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    navController: NavHostController,
    editId: Long?,
) {
    val vm: AddTransactionViewModel = hiltViewModel()
    val state by vm.state.collectAsState()
    val categories by vm.categoriesByType.collectAsState()
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()

    var showCategorySheet by remember { mutableStateOf(false) }
    var showDateSheet by remember { mutableStateOf(false) }
    var showTimeSheet by remember { mutableStateOf(false) }
    var showMerchantSheet by remember { mutableStateOf(false) }
    var showProductSheet by remember { mutableStateOf(false) }
    var showKeypad by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editId != null) "编辑记录" else "记一笔") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                // 键盘弹出时页面可上下滚动，保存键不会被顶出屏幕
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .pointerInput(showKeypad) {
                    detectTapGestures { showKeypad = false }
                }
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

            // 金额：可点击行 + 内置数字键盘（不抢焦点，无光标）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .clickable { showKeypad = !showKeypad }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("金额", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text(
                    if (state.amountText.isEmpty()) "0.00" else state.amountText,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            if (showKeypad) {
                NumberKeypad(
                    value = state.amountText,
                    onKey = vm::onAmountChange,
                    style = NumberKeypadStyle.AMOUNT
                )
            }
            Spacer(Modifier.height(12.dp))

            // 分类
            CategorySelector(selected = state.selectedCategory, onClick = { showCategorySheet = true })
            Spacer(Modifier.height(12.dp))

            // 日期：点选 年/月/日
            PickerRow(
                label = "日期",
                value = state.date,
                onClick = { showDateSheet = true }
            )
            Spacer(Modifier.height(12.dp))

            PickerRow(
                label = "时间",
                value = state.time.ifBlank { "现在" },
                isPlaceholder = state.time.isBlank(),
                onClick = { showTimeSheet = true }
            )
            Spacer(Modifier.height(12.dp))

            // 商家：点选（搜索 + 候选 + 其他）
            PickerRow(
                label = "商家（可选）",
                value = state.merchant.ifEmpty { "请选择/输入" },
                isPlaceholder = state.merchant.isEmpty(),
                onClick = { showMerchantSheet = true }
            )
            Spacer(Modifier.height(12.dp))

            // 商品：点选
            PickerRow(
                label = "商品（可选）",
                value = state.product.ifEmpty { "请选择/输入" },
                isPlaceholder = state.product.isEmpty(),
                onClick = { showProductSheet = true }
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

    if (showCategorySheet) {
        ModalBottomSheet(onDismissRequest = { showCategorySheet = false }, sheetState = sheetState) {
            LazyColumn(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                item {
                    Text("选择分类", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                items(categories) { cat ->
                    CategorySheetRow(cat, state.selectedCategory?.name == cat.name) {
                        vm.onCategorySelect(cat); showCategorySheet = false
                    }
                }
            }
        }
    }

    if (showDateSheet) {
        DatePickerSheet(
            initialDate = state.date,
            onDismiss = { showDateSheet = false },
            onConfirm = { y, m, d ->
                vm.onDateSet(y, m, d); showDateSheet = false
            }
        )
    }

    if (showTimeSheet) {
        TimePickerSheet(
            initial = state.time,
            onDismiss = { showTimeSheet = false },
            onConfirm = { hhmm ->
                vm.onTimeChange(hhmm)
                showTimeSheet = false
            }
        )
    }

    if (showMerchantSheet) {
        TextPickerSheet(
            title = "选择/输入商家",
            placeholder = "搜索商家…",
            current = state.merchant,
            onDismiss = { showMerchantSheet = false },
            onSelect = { vm.onMerchantChange(it); showMerchantSheet = false },
            loadCandidates = { vm.knownMerchants() }
        )
    }

    if (showProductSheet) {
        TextPickerSheet(
            title = "选择/输入商品",
            placeholder = "搜索商品…",
            current = state.product,
            onDismiss = { showProductSheet = false },
            onSelect = { vm.onProductChange(it); showProductSheet = false },
            loadCandidates = { vm.knownProducts() }
        )
    }
}

/** 通用"点选行" */
@Composable
private fun PickerRow(
    label: String,
    value: String,
    isPlaceholder: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isPlaceholder) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
    }
}

/** 内置键盘模式 */
enum class NumberKeypadStyle { AMOUNT }

/** 内置数字键盘：仅数字 + 小数点 + 退格 */
@Composable
private fun NumberKeypad(value: String, onKey: (String) -> Unit, style: NumberKeypadStyle) {
    fun append(digit: Char) {
        when (style) {
            NumberKeypadStyle.AMOUNT -> {
                val cur = value.replace(",", "")
                if (digit == '.') {
                    if (cur.contains('.')) return
                    if (cur.isEmpty()) { onKey("0."); return }
                } else {
                    // 最多两位小数
                    if (cur.contains('.')) {
                        val dec = cur.substringAfter('.')
                        if (dec.length >= 2) return
                    }
                    // 整数部分限长
                    if (!cur.contains('.') && cur.length >= 9) return
                }
                onKey(cur + digit)
            }
        }
    }
    val keys = listOf('1', '2', '3', '4', '5', '6', '7', '8', '9', '.', '0')
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .padding(8.dp)
    ) {
        // 每行显示, 布局网格
        val rows = listOf(
            listOf('1','2','3','4'),
            listOf('5','6','7','8'),
            listOf('9','.','0', '#'),
        )
        rows.forEach { rowKeys ->
            Row(Modifier.fillMaxWidth()) {
                rowKeys.forEach { k ->
                    val weight = 1f
                    Box(
                        Modifier
                            .weight(weight)
                            .padding(4.dp)
                            .height(52.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .clickable {
                                when (k) {
                                    '#' -> { // 退格
                                        if (value.isNotEmpty()) onKey(value.dropLast(1))
                                    }
                                    else -> append(k)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (k == '#') {
                            Icon(Icons.Filled.Backspace, contentDescription = "删除")
                        } else {
                            Text("$k", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
        }
    }
}

/** 日期选择：Material3 日历对话框（替换旧版三段滚轮，操作更直观） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerSheet(
    initialDate: String,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int, Int) -> Unit,
) {
    val initialMillis = remember(initialDate) {
        val parts = initialDate.split("-").mapNotNull { it.toIntOrNull() }
        java.time.LocalDate.of(
            parts.getOrElse(0) { 2026 },
            parts.getOrElse(1) { 1 },
            parts.getOrElse(2) { 1 }
        ).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
    }
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val ms = pickerState.selectedDateMillis ?: initialMillis
                val d = java.time.Instant.ofEpochMilli(ms)
                    .atZone(java.time.ZoneOffset.UTC).toLocalDate()
                onConfirm(d.year, d.monthValue, d.dayOfMonth)
            }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    ) {
        DatePicker(
            state = pickerState,
            title = null,
            headline = null,
            showModeToggle = false
        )
    }
}

/** 时间选择：Material3 时钟弹层（24 小时制）；未选过时间时从当前时刻开始 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerSheet(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val now = java.time.LocalTime.now()
    val parts = initial.split(":").mapNotNull { it.toIntOrNull() }
    val tpState = rememberTimePickerState(
        initialHour = parts.getOrElse(0) { now.hour }.coerceIn(0, 23),
        initialMinute = parts.getOrElse(1) { now.minute }.coerceIn(0, 59),
        is24Hour = true
    )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "选择时间",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            TimePicker(state = tpState)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onConfirm("%02d:%02d".format(tpState.hour, tpState.minute)) }) {
                    Text("确定")
                }
            }
        }
    }
}

/** 文本选择器（商家/商品）：搜索 + 候选 + "其他" */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TextPickerSheet(
    title: String,
    placeholder: String,
    current: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    loadCandidates: suspend () -> List<String>,
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf(current) }
    var candidates by remember { mutableStateOf(mutableListOf<String>()) }

    LaunchedEffect(Unit) {
        candidates = loadCandidates().toMutableList()
    }

    val filtered = candidates.filter { it.contains(query.trim(), ignoreCase = true) || query.isBlank() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp).fillMaxHeight(0.8f)) {
            Text(title, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(placeholder) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            )

            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                // "其他" 选项：始终可搜可选
                item {
                    CandidateRow("其他", query.isNotBlank() && "其他".contains(query, true)) {
                        onSelect("其他")
                    }
                }
                // 精确匹配当前输入 -> 允许直接使用输入内容
                if (query.isNotBlank()) {
                    item {
                        CandidateRow("使用「$query」", false) { onSelect(query.trim()) }
                    }
                }
                items(filtered, key = { it }) { cand ->
                    CandidateRow(cand, cand == query.trim()) { onSelect(cand) }
                }
            }
        }
    }
}

@Composable
private fun CandidateRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.weight(1f))
        if (selected) Icon(Icons.Filled.Check, contentDescription = "已选择", tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun CategorySelector(selected: Category?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
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
private fun CategorySheetRow(cat: Category, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryIconCircle(cat, size = 36)
        Spacer(Modifier.size(12.dp))
        Text(cat.name, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.weight(1f))
        if (selected) Icon(Icons.Filled.Check, contentDescription = "已选择", tint = MaterialTheme.colorScheme.primary)
    }
}
