package com.simpleaccount.app.ui.settings

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.simpleaccount.app.data.repository.SettingsRepository
import com.simpleaccount.app.ui.components.DatePickerByStyle
import com.simpleaccount.app.ui.components.PickerStyles
import com.simpleaccount.app.ui.components.TimePickerByStyle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.cos
import kotlin.math.sin

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
    var datePreview by remember { mutableStateOf<String?>(null) }
    var timePreview by remember { mutableStateOf<String?>(null) }
    val todayStr = remember { java.time.LocalDate.now().toString() }
    val nowHm = remember { java.time.LocalTime.now().let { "%02d:%02d".format(it.hour, it.minute) } }

    Scaffold(topBar = { SettingsSubToolbar("日期与时间选择器", onBack = { navController.popBackStack() }) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text("日期", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("11 种互不相同的手感，所见即所得。左侧单选即时生效，右侧点「预览」看真实组件形态（非示意图标）。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            PickerStyles.DATE_ALL.forEach { id ->
                StyleRow(
                    title = PickerStyles.dateTitle(id),
                    hint = PickerStyles.dateHint(id),
                    selected = date == id,
                    preview = { MiniDatePreview(id, selected = date == id) },
                    onClick = { viewModel.setDate(id) },
                    onPreview = { datePreview = id }
                )
            }
            Spacer(Modifier.height(20.dp))
            Text("时间", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("同心表盘为默认（已更名，不再以版本号命名）。11 种形态各有隐喻，点预览看真实交互。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            PickerStyles.TIME_ALL.forEach { id ->
                StyleRow(
                    title = PickerStyles.timeTitle(id),
                    hint = PickerStyles.timeHint(id),
                    selected = time == id,
                    preview = { MiniTimePreview(id, selected = time == id) },
                    onClick = { viewModel.setTime(id) },
                    onPreview = { timePreview = id }
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "提示：同心表盘已优化触区分隔（外环分/内盘时/中间缓冲 0.35–0.55 不触发），大幅降低误触；点击上方任意「预览」即可在真实弹窗中试拨，确认形态与渲染一致后再设为默认。",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)).padding(8.dp).fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "已选：日期「${PickerStyles.dateTitle(date)}」· 时间「${PickerStyles.timeTitle(time)}」—— 去「记一笔」点日期/时间即可看到所选样式的真实渲染。",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)).padding(8.dp).fillMaxWidth()
            )
        }
    }

    // ---- 所见即所得：真实组件预览（非图标） ----
    datePreview?.let { id ->
        DatePickerByStyle(
            style = id,
            initialDate = todayStr,
            onDismiss = { datePreview = null },
            onConfirm = { _, _, _ -> datePreview = null }
        )
    }
    timePreview?.let { id ->
        TimePickerByStyle(
            style = id,
            initial = nowHm,
            onDismiss = { timePreview = null },
            onConfirm = { timePreview = null }
        )
    }
}

@Composable
private fun StyleRow(
    title: String,
    hint: String,
    selected: Boolean,
    preview: @Composable ()->Unit,
    onClick: () -> Unit,
    onPreview: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            )
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(
            Modifier
                .weight(1f)
                .padding(end = 8.dp)
                .clickable(onClick = onClick)
        ) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(hint, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 15.sp)
        }
        // 右侧：迷你形态 + 真实预览入口
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)).padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                preview()
            }
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)).clickable(onClick = onPreview).padding(horizontal = 10.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("预览", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun MiniDatePreview(id: String, selected: Boolean) {
    val accent = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    when (id) {
        PickerStyles.DATE_WHEEL -> Row(verticalAlignment = Alignment.CenterVertically) {
            repeat(3) { Box(Modifier.width(10.dp).height(22.dp).clip(RoundedCornerShape(3.dp)).background(accent.copy(alpha = 0.25f)).padding(1.dp)) }
        }
        PickerStyles.DATE_FILM -> Row {
            repeat(4) { Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(if (it==1) accent else accent.copy(alpha = 0.3f)).padding(1.dp)) }
        }
        PickerStyles.DATE_STACK -> Box(Modifier.size(30.dp)) {
            repeat(3) { i -> Box(Modifier.size(22.dp).clip(RoundedCornerShape(4.dp)).background(accent.copy(alpha = 0.3f + i*0.2f)).align(Alignment.Center)) }
        }
        PickerStyles.DATE_TIMELINE -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            repeat(3) { i -> Box(Modifier.width(if (i==1) 18.dp else 12.dp).height(if (i==1) 10.dp else 6.dp).clip(CircleShape).background(if (i==1) accent else accent.copy(alpha = 0.4f))) { Spacer(Modifier.height(2.dp)) } ; Spacer(Modifier.height(2.dp)) }
        }
        PickerStyles.DATE_BEADS -> Row {
            repeat(3) { Box(Modifier.size(10.dp).clip(CircleShape).background(accent).padding(1.dp)) }
        }
        PickerStyles.DATE_HORIZON -> Row(verticalAlignment = Alignment.Bottom) {
            listOf(0.5f,0.8f,1f,0.8f,0.5f).forEach { s -> Box(Modifier.width(8.dp).height((10*s).dp).clip(RoundedCornerShape(2.dp)).background(accent.copy(alpha = s))) }
        }
        PickerStyles.DATE_CASCADE -> Column {
            repeat(3) { i -> Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(4.dp)).background(accent.copy(alpha = 0.5f - i*0.1f)).padding(start = (i*6).dp)) }
        }
        PickerStyles.DATE_SPIRAL -> Canvas(Modifier.size(30.dp)) {
            val c=center; for (i in 0 until 6) { val a=i*60f; val r=4f+i*2f
                drawCircle(color = if (i==2) accent else accent.copy(alpha = 0.4f), radius = if (i==2) 4.dp.toPx() else 2.dp.toPx(), center = Offset(c.x+ cos(Math.toRadians(a.toDouble())).toFloat()*r, c.y+ sin(Math.toRadians(a.toDouble())).toFloat()*r))
            }
        }
        PickerStyles.DATE_GRID -> Column {
            repeat(2) { Box(Modifier.size(12.dp).clip(RoundedCornerShape(2.dp)).background(accent.copy(alpha = 0.7f))) ; Spacer(Modifier.height(2.dp)) }
        }
        PickerStyles.DATE_DIAL_YEAR -> Canvas(Modifier.size(30.dp)) {
            drawCircle(color = accent.copy(alpha = 0.2f), radius = 14.dp.toPx(), style = Stroke(width = 2.dp.toPx()))
            drawCircle(color = accent, radius = 3.dp.toPx())
        }
        PickerStyles.DATE_WAVE -> Canvas(Modifier.size(36.dp)) {
            val path = androidx.compose.ui.graphics.Path()
            path.moveTo(0f, size.height/2f)
            for (x in 0..size.width.toInt() step 4) { val y=size.height/2f+ sin(x/size.width*2*Math.PI).toFloat()*6f; path.lineTo(x.toFloat(), y) }
            drawPath(path, color = accent, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        }
        else -> Box(Modifier.size(12.dp).clip(CircleShape).background(accent))
    }
}

@Composable
private fun MiniTimePreview(id: String, selected: Boolean) {
    val accent = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    when (id) {
        PickerStyles.TIME_DIAL -> Canvas(Modifier.size(30.dp)) {
            drawCircle(color = accent.copy(alpha = 0.15f), radius = 14.dp.toPx())
            drawCircle(color = accent, radius = 2.dp.toPx())
            val a=Math.toRadians(30.0); drawLine(color = accent, start = center, end = Offset(center.x+ (cos(a)*10.dp.toPx()).toFloat(), center.y+ (sin(a)*10.dp.toPx()).toFloat()), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }
        PickerStyles.TIME_ARC -> Canvas(Modifier.size(30.dp)) {
            drawArc(color = accent.copy(alpha = 0.3f), startAngle = 180f, sweepAngle = 180f, useCenter = false, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            drawArc(color = accent, startAngle = 180f, sweepAngle = 100f, useCenter = false, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        }
        PickerStyles.TIME_FLIP -> Row {
            repeat(2) { Box(Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)).background(accent.copy(alpha = 0.6f)).padding(1.dp)) }
        }
        PickerStyles.TIME_BARS -> Row(verticalAlignment = Alignment.Bottom) {
            Box(Modifier.width(8.dp).height(16.dp).clip(RoundedCornerShape(2.dp)).background(accent))
            Spacer(Modifier.width(2.dp))
            Box(Modifier.width(8.dp).height(22.dp).clip(RoundedCornerShape(2.dp)).background(accent.copy(alpha = 0.7f)))
        }
        PickerStyles.TIME_ORBIT -> Canvas(Modifier.size(30.dp)) {
            drawCircle(color = accent.copy(alpha = 0.3f), radius = 12.dp.toPx(), style = Stroke(2.dp.toPx()))
            drawCircle(color = accent.copy(alpha = 0.3f), radius = 7.dp.toPx(), style = Stroke(2.dp.toPx()))
            drawCircle(color = accent, radius = 3.dp.toPx(), center = Offset(center.x+8.dp.toPx(), center.y))
        }
        PickerStyles.TIME_RULER -> Box(Modifier.width(28.dp).height(6.dp).clip(RoundedCornerShape(2.dp)).background(accent.copy(alpha = 0.3f))) { Box(Modifier.size(6.dp).clip(CircleShape).background(accent).align(Alignment.CenterStart)) }
        PickerStyles.TIME_WAVE -> Canvas(Modifier.size(30.dp)) {
            val path = androidx.compose.ui.graphics.Path()
            path.moveTo(0f, size.height/2f)
            for (x in 0..size.width.toInt() step 3) { val y=size.height/2f+ sin(x/size.width*4*Math.PI).toFloat()*5f; path.lineTo(x.toFloat(), y) }
            drawPath(path, color = accent, style = Stroke(width = 2.dp.toPx()))
        }
        PickerStyles.TIME_DRUM -> Row {
            Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(accent.copy(alpha = 0.5f)))
            Spacer(Modifier.width(2.dp))
            Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(accent))
        }
        PickerStyles.TIME_PIE -> Canvas(Modifier.size(26.dp)) {
            drawArc(color = accent, startAngle = 0f, sweepAngle = 90f, useCenter = true)
            drawArc(color = accent.copy(alpha = 0.5f), startAngle = 90f, sweepAngle = 90f, useCenter = true)
            drawArc(color = accent.copy(alpha = 0.3f), startAngle = 180f, sweepAngle = 180f, useCenter = true)
        }
        PickerStyles.TIME_BLOCKS -> Column {
            repeat(2) { row -> Row { repeat(3) { col -> Box(Modifier.size(6.dp).clip(RoundedCornerShape(1.dp)).background(if (row==0&&col==1) accent else accent.copy(alpha = 0.3f))) { Spacer(Modifier.size(2.dp)) } ; Spacer(Modifier.width(2.dp)) } } ; Spacer(Modifier.height(2.dp)) }
        }
        PickerStyles.TIME_SPECTRUM -> Box(Modifier.width(28.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color(0xFF5DADE2), Color(0xFFF7DC6F), Color(0xFFE74C3C)))))
        else -> Box(Modifier.size(12.dp).clip(CircleShape).background(accent))
    }
}
