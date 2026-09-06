package com.simpleaccount.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.simpleaccount.app.ui.components.PickerStyles
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
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
    Scaffold(topBar = { SettingsSubToolbar("日期与时间选择器", onBack = { navController.popBackStack() }) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text("日期", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("三种完全不同的手感，记一笔时按这里选的来。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            PickerStyles.DATE_ALL.forEach { id ->
                StyleRow(PickerStyles.dateTitle(id), PickerStyles.dateHint(id), date == id) { viewModel.setDate(id) }
            }
            Spacer(Modifier.height(20.dp))
            Text("时间", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("圆盘是默认。弧轨适合单手，翻页像机场钟。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            PickerStyles.TIME_ALL.forEach { id ->
                StyleRow(PickerStyles.timeTitle(id), PickerStyles.timeHint(id), time == id) { viewModel.setTime(id) }
            }
        }
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
