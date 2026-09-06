package com.simpleaccount.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.simpleaccount.app.data.repository.SettingsRepository
import com.simpleaccount.app.ui.components.AnalogClockFace
import com.simpleaccount.app.ui.components.DatePickerByStyle
import com.simpleaccount.app.ui.components.PickerStyles
import com.simpleaccount.app.ui.components.TimePickerByStyle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
class PickerStyleViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {
    val dateStyle = settings.datePickerFlow
    val timeStyle = settings.timePickerFlow
    fun setDate(id: String) = viewModelScope.launch { settings.setDatePickerStyle(id) }
    fun setTime(id: String) = viewModelScope.launch { settings.setTimePickerStyle(id) }
}

@Composable
fun PickerStyleScreen(
    navController: NavHostController,
    viewModel: PickerStyleViewModel = hiltViewModel(),
) {
    val date by viewModel.dateStyle.collectAsState()
    val time by viewModel.timeStyle.collectAsState()
    var previewDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var previewTime by remember { mutableStateOf("%02d:%02d".format(LocalTime.now().hour, LocalTime.now().minute)) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var hour by remember { mutableIntStateOf(LocalTime.now().hour) }
    var minute by remember { mutableIntStateOf(LocalTime.now().minute) }

    Scaffold(topBar = { SettingsSubToolbar("日期与时间选择器", onBack = { navController.popBackStack() }) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text("交互预览", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "下面就是当前选中的选择器。机械表盘可直接拖；其它样式点「打开」进完整手感。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .padding(12.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("日期 · ${PickerStyles.dateTitle(date)}  $previewDate", fontWeight = FontWeight.SemiBold)
                    OutlinedButton(onClick = { showDate = true }) { Text("打开日期选择器") }
                    Spacer(Modifier.height(12.dp))
                    Text("时间 · ${PickerStyles.timeTitle(time)}  $previewTime", fontWeight = FontWeight.SemiBold)
                    if (PickerStyles.normalizeTime(time) == PickerStyles.TIME_DIAL) {
                        AnalogClockFace(
                            hour = hour,
                            minute = minute,
                            onHourMinute = { h, m ->
                                hour = h
                                minute = m
                                previewTime = "%02d:%02d".format(h, m)
                            },
                        )
                    }
                    OutlinedButton(onClick = { showTime = true }) { Text("打开时间选择器") }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("日期", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("十一套完全不同的手感，记一笔时按这里选的来。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            PickerStyles.DATE_ALL.forEach { id ->
                StyleRow(PickerStyles.dateTitle(id), PickerStyles.dateHint(id), date == id) { viewModel.setDate(id) }
            }
            Spacer(Modifier.height(20.dp))
            Text("时间", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("机械表盘是默认。不用版本号当样式名。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            PickerStyles.TIME_ALL.forEach { id ->
                StyleRow(PickerStyles.timeTitle(id), PickerStyles.timeHint(id), time == id) { viewModel.setTime(id) }
            }
        }
    }

    if (showDate) {
        DatePickerByStyle(
            style = date,
            initialDate = previewDate,
            onDismiss = { showDate = false },
            onConfirm = { y, m, d ->
                previewDate = "%04d-%02d-%02d".format(y, m, d)
                showDate = false
            },
        )
    }
    if (showTime) {
        TimePickerByStyle(
            style = time,
            initial = previewTime,
            onDismiss = { showTime = false },
            onConfirm = { hhmm ->
                previewTime = hhmm
                val p = hhmm.split(":")
                hour = p.getOrNull(0)?.toIntOrNull() ?: hour
                minute = p.getOrNull(1)?.toIntOrNull() ?: minute
                showTime = false
            },
        )
    }
}

@Composable
private fun StyleRow(title: String, hint: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(hint, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)
        }
    }
}
