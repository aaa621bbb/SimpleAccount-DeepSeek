package com.simpleaccount.app.ui.addtransaction

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
    val dateStyle by vm.dateStyle.collectAsState()
    val timeStyle by vm.timeStyle.collectAsState()
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val reduce = LocalReduceMotion.current

    var showDateSheet by remember { mutableStateOf(false) }
    var showTimeSheet by remember { mutableStateOf(false) }
    var showKeypad by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var success by remember { mutableStateOf(false) }

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

            Text("分类", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            CategoryCarousel(
                categories = categories,
                selected = state.selectedCategory,
                onSelect = vm::onCategorySelect,
            )
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
