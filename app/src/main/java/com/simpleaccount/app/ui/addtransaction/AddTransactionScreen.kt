package com.simpleaccount.app.ui.addtransaction

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.simpleaccount.app.data.entity.Transaction
import com.simpleaccount.app.ui.components.CategoryCarousel
import com.simpleaccount.app.ui.components.DatePickerByStyle
import com.simpleaccount.app.ui.components.TimePickerByStyle
import com.simpleaccount.app.ui.components.DualFocusFields
import com.simpleaccount.app.ui.components.PressSaveBar
import com.simpleaccount.app.ui.motion.LocalReduceMotion
import com.simpleaccount.app.ui.motion.Motion
import kotlinx.coroutines.delay
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
    val subOptions by vm.subOptions.collectAsState()
    val dateStyle by vm.dateStyle.collectAsState(initial = "date_wheel")
    val timeStyle by vm.timeStyle.collectAsState(initial = "time_dial")
    val merchantHints by vm.merchantHints.collectAsState()
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val reduce = LocalReduceMotion.current

var showDateSheet by remember { mutableStateOf(false) }
    var showTimeSheet by remember { mutableStateOf(false) }
    var showKeypad by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var success by remember { mutableStateOf(false) }
    var showCatEditor by remember { mutableStateOf(false) }
    var showAddSub by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editId != null) "编辑记录" else "记一笔") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
            PressSaveBar(
                enabled = !state.loading && !saving,
                success = success,
                onClick = {
                    if (saving || success) return@PressSaveBar
                    saving = true
                    scope.launch {
                        val ok = vm.save()
                        saving = false
                        if (ok) {
                            success = true
                            delay(Motion.dur(reduce, Motion.SUCCESS_MS).toLong().coerceAtLeast(80L))
                            navController.popBackStack()
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .pointerInput(showKeypad) {
                    detectTapGestures {
                        showKeypad = false
                        focus.clearFocus()
                    }
                },
        ) {
            com.simpleaccount.app.ui.components.SegmentedThree(
                options = listOf("支出", "收入"),
                selected = if (state.type == Transaction.TYPE_EXPENSE) 0 else 1,
                onSelect = {
                    vm.onTypeChange(if (it == 0) Transaction.TYPE_EXPENSE else Transaction.TYPE_INCOME)
                },
            )
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        RoundedCornerShape(12.dp),
                    )
                    .clickable { showKeypad = !showKeypad }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("金额", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text(
                    if (state.amountText.isEmpty()) "0.00" else state.amountText,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (showKeypad) {
                NumberKeypad(
                    value = state.amountText,
                    onKey = vm::onAmountChange,
                )
            }
            Spacer(Modifier.height(12.dp))

Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("分类", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = { showCatEditor = true }) {
                    Text("管理", fontSize = 12.sp)
                }
            }
            CategoryCarousel(
                categories = categories,
                selected = state.selectedCategory,
                onSelect = vm::onCategorySelect,
            )
            // 二级分类（可选，不选=不细分；再点一次取消）+ 就地新增
            if (state.selectedCategory != null) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("细分（可选）", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    TextButton(onClick = { showAddSub = true }) {
                        Text("+ 二级", fontSize = 12.sp)
                    }
                }
                if (subOptions.isNotEmpty()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        subOptions.forEach { sub ->
                            val selected = state.subCategory == sub.name
                            FilterChip(
                                selected = selected,
                                onClick = { vm.onSubCategorySelect(sub.name) },
                                leadingIcon = {
                                    Icon(
                                        com.simpleaccount.app.util.IconMapper.map(
                                            sub.iconName.ifBlank { "more_horiz" }
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                                label = { Text(sub.name) },
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth()) {
                PickerChip(
                    label = "日期",
                    value = state.date,
                    modifier = Modifier.weight(1f),
                    onClick = { showDateSheet = true },
                )
                Spacer(Modifier.width(10.dp))
                PickerChip(
                    label = "时间",
                    value = state.time.ifBlank { "现在" },
                    isPlaceholder = state.time.isBlank(),
                    modifier = Modifier.weight(1f),
                    onClick = { showTimeSheet = true },
                )
            }
            Spacer(Modifier.height(12.dp))

            DualFocusFields(
                merchant = state.merchant,
                product = state.product,
                onMerchant = vm::onMerchantChange,
                onProduct = vm::onProductChange,
                merchantHints = merchantHints,
                onPickMerchant = vm::onMerchantChange,
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.note,
                onValueChange = vm::onNoteChange,
                label = { Text("备注（可选）") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )

            if (state.error != null) {
                Spacer(Modifier.height(8.dp))
                Text(state.error!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
            if (state.loading) {
                Spacer(Modifier.height(8.dp))
                CircularProgressIndicator(Modifier.size(20.dp))
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showDateSheet) {
        DatePickerByStyle(
            style = dateStyle,
            initialDate = state.date,
            onDismiss = { showDateSheet = false },
            onConfirm = { y, m, d ->
                vm.onDateSet(y, m, d)
                showDateSheet = false
            },
        )
    }

if (showTimeSheet) {
        TimePickerByStyle(
            style = timeStyle,
            initial = state.time,
            onDismiss = { showTimeSheet = false },
            onConfirm = { hhmm ->
                vm.onTimeChange(hhmm)
                showTimeSheet = false
            },
        )
    }

    // 就地分类管理：新增/改名/删除一级；二级增删改
    if (showCatEditor) {
        CategoryInPlaceSheet(
            categories = categories,
            selected = state.selectedCategory,
            subOptions = subOptions,
            selectedSub = state.subCategory,
            onSelect = { c -> vm.onCategorySelect(c); },
            onSelectSub = { vm.onSubCategorySelect(it) },
            onAdd = { name -> scope.launch { if (vm.addCategoryInPlace(name)) { /* stay */ } } },
            onRename = { name -> scope.launch { vm.renameCategoryInPlace(name) } },
            onDelete = { scope.launch { vm.deleteCategoryInPlace() } },
            onAddSub = { name -> scope.launch { vm.addSubInPlace(name) } },
            onRenameSub = { old, neu -> scope.launch { vm.renameSubInPlace(old, neu) } },
            onDeleteSub = { name -> scope.launch { vm.deleteSubInPlace(name) } },
            onDismiss = { showCatEditor = false },
        )
    }
    if (showAddSub) {
        var draft by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddSub = false },
            title = { Text("新增二级分类") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        if (vm.addSubInPlace(draft)) showAddSub = false
                    }
                }) { Text("添加") }
            },
            dismissButton = {
                TextButton(onClick = { showAddSub = false }) { Text("取消") }
            },
        )
    }
}

/**
 * 记一笔页就地分类编辑底栏：一级增删改 + 当前一级下二级增删改。
 * 不跳转到设置页分类管理。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryInPlaceSheet(
    categories: List<com.simpleaccount.app.data.entity.Category>,
    selected: com.simpleaccount.app.data.entity.Category?,
    subOptions: List<com.simpleaccount.app.data.entity.SubCategory>,
    selectedSub: String,
    onSelect: (com.simpleaccount.app.data.entity.Category) -> Unit,
    onSelectSub: (String) -> Unit,
    onAdd: (String) -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onAddSub: (String) -> Unit,
    onRenameSub: (String, String) -> Unit,
    onDeleteSub: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var mode by remember { mutableStateOf("list") } // list | add | rename | addSub | renameSub
    var draft by remember { mutableStateOf("") }
    var renameTargetSub by remember { mutableStateOf("") }
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp)
        ) {
            Text("分类管理", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "在记一笔里直接增删改一级/二级，不用跳到设置。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            when (mode) {
                "add", "rename", "addSub", "renameSub" -> {
                    val title = when (mode) {
                        "add" -> "新一级分类"
                        "rename" -> "重命名「${selected?.name.orEmpty()}」"
                        "addSub" -> "新二级（${selected?.name.orEmpty()}）"
                        else -> "重命名二级「$renameTargetSub」"
                    }
                    Text(title, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("名称") },
                    )
                    Spacer(Modifier.height(8.dp))
                    Row {
                        TextButton(onClick = {
                            when (mode) {
                                "add" -> onAdd(draft)
                                "rename" -> onRename(draft)
                                "addSub" -> onAddSub(draft)
                                "renameSub" -> onRenameSub(renameTargetSub, draft)
                            }
                            draft = ""
                            mode = "list"
                        }) { Text("保存") }
                        TextButton(onClick = { mode = "list"; draft = "" }) { Text("取消") }
                    }
                }
                else -> {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("一级", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { mode = "add"; draft = "" }) { Text("+ 新增") }
                        if (selected != null && !selected.isPreset) {
                            TextButton(onClick = { mode = "rename"; draft = selected.name }) { Text("改名") }
                            TextButton(onClick = onDelete) { Text("删除", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        categories.forEach { c ->
                            FilterChip(
                                selected = selected?.id == c.id,
                                onClick = { onSelect(c) },
                                label = { Text(c.name) },
                                modifier = Modifier.padding(end = 6.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (selected != null) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("二级 · ${selected.name}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            TextButton(onClick = { mode = "addSub"; draft = "" }) { Text("+ 新增") }
                        }
                        if (subOptions.isEmpty()) {
                            Text("暂无二级，可点上方新增。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            subOptions.forEach { sub ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    FilterChip(
                                        selected = selectedSub == sub.name,
                                        onClick = { onSelectSub(sub.name) },
                                        leadingIcon = {
                                            Icon(
                                                com.simpleaccount.app.util.IconMapper.map(
                                                    sub.iconName.ifBlank { "more_horiz" }
                                                ),
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        },
                                        label = { Text(sub.name) },
                                    )
                                    Spacer(Modifier.weight(1f))
                                    TextButton(onClick = {
                                        renameTargetSub = sub.name
                                        draft = sub.name
                                        mode = "renameSub"
                                    }) { Text("改名", fontSize = 12.sp) }
                                    TextButton(onClick = { onDeleteSub(sub.name) }) {
                                        Text("删", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("完成") }
                }
            }
        }
    }
}

@Composable
private fun PickerChip(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPlaceholder: Boolean = false,
) {
    Row(
        modifier
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isPlaceholder) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        }
        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
    }
}

@Composable
private fun NumberKeypad(value: String, onKey: (String) -> Unit) {
    fun append(digit: Char) {
        val cur = value.replace(",", "")
        if (digit == '.') {
            if (cur.contains('.')) return
            if (cur.isEmpty()) {
                onKey("0.")
                return
            }
        } else {
            if (cur.contains('.')) {
                val dec = cur.substringAfter('.')
                if (dec.length >= 2) return
            }
            if (!cur.contains('.') && cur.length >= 9) return
        }
        onKey(cur + digit)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .padding(8.dp),
    ) {
        val rows = listOf(
            listOf('1', '2', '3', '4'),
            listOf('5', '6', '7', '8'),
            listOf('9', '.', '0', '#'),
        )
        rows.forEach { rowKeys ->
            Row(Modifier.fillMaxWidth()) {
                rowKeys.forEach { k ->
                    Box(
                        Modifier
                            .weight(1f)
                            .padding(4.dp)
                            .height(48.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                RoundedCornerShape(8.dp),
                            )
                            .clickable {
                                when (k) {
                                    '#' -> if (value.isNotEmpty()) onKey(value.dropLast(1))
                                    else -> append(k)
                                }
                            },
                        contentAlignment = Alignment.Center,
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
