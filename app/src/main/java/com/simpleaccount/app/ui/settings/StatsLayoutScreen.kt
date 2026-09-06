package com.simpleaccount.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.simpleaccount.app.data.repository.SettingsRepository
import com.simpleaccount.app.ui.stats.StatsLayoutUi
import com.simpleaccount.app.ui.stats.StatsModules
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StatsLayoutViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {

    val layout = combine(
        settings.statsOrderFlow,
        settings.statsHiddenFlow,
        settings.pieLegendCountFlow,
    ) { order, hidden, count ->
        StatsLayoutUi(
            order = StatsModules.parseOrder(order),
            hidden = StatsModules.parseHidden(hidden),
            pieLegendCount = count,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsLayoutUi())

    fun move(id: String, delta: Int) {
        val cur = layout.value.order.toMutableList()
        val i = cur.indexOf(id)
        val j = i + delta
        if (i < 0 || j !in cur.indices) return
        val tmp = cur[i]
        cur[i] = cur[j]
        cur[j] = tmp
        viewModelScope.launch { settings.setStatsOrder(cur.joinToString(",")) }
    }

    fun toggle(id: String, visible: Boolean) {
        val hidden = layout.value.hidden.toMutableSet()
        if (visible) hidden.remove(id) else hidden.add(id)
        viewModelScope.launch { settings.setStatsHidden(hidden.joinToString(",")) }
    }

    fun setPieCount(n: Int) {
        viewModelScope.launch { settings.setPieLegendCount(n) }
    }
}

@Composable
fun StatsLayoutScreen(
    navController: NavHostController,
    viewModel: StatsLayoutViewModel = hiltViewModel(),
) {
    val layout by viewModel.layout.collectAsState()

    Scaffold(
        topBar = { SettingsSubToolbar("统计页图表", onBack = { navController.popBackStack() }) }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text("显示与顺序", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "关掉的卡片不出现在统计页。用箭头调整上下顺序。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            layout.order.forEachIndexed { index, id ->
                val visible = id !in layout.hidden
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(checked = visible, onCheckedChange = { viewModel.toggle(id, it) })
                    Spacer(Modifier.width(8.dp))
                    Text(StatsModules.title(id), modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.move(id, -1) }, enabled = index > 0) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "上移")
                    }
                    IconButton(onClick = { viewModel.move(id, 1) }, enabled = index < layout.order.lastIndex) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "下移")
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("饼图百分比条数", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "默认不显示百分比，点饼图下的「查看占比」才展开。设成 3 就只占 3 条的位置；再点「展开全部」在当前页看完，不会从底部弹出。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                listOf(0, 3, 5, 8).forEach { n ->
                    FilterChip(
                        selected = layout.pieLegendCount == n,
                        onClick = { viewModel.setPieCount(n) },
                        label = { Text(if (n == 0) "不预览" else "${n} 条", fontSize = 13.sp) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
