package com.simpleaccount.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.navigation.NavHostController
import com.simpleaccount.app.data.repository.SettingsRepository
import com.simpleaccount.app.ui.theme.ColorPalettes
import com.simpleaccount.app.ui.theme.UiSkin
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon

@Composable
fun AppearanceScreen(
    navController: NavHostController,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val paletteId by viewModel.paletteId.collectAsState()
    val uiSkin by viewModel.uiSkin.collectAsState()

    Scaffold(
        topBar = { SettingsSubToolbar("外观", onBack = { navController.popBackStack() }) }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text("深浅色", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            listOf(
                SettingsRepository.THEME_SYSTEM,
                SettingsRepository.THEME_LIGHT,
                SettingsRepository.THEME_DARK,
            ).forEach { mode ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { viewModel.setThemeMode(mode) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = themeMode == mode, onClick = { viewModel.setThemeMode(mode) })
                    Text(themeModeLabel(mode), style = MaterialTheme.typography.bodyLarge)
                }
            }

            Spacer(Modifier.height(18.dp))
            Text("顶级视觉方案", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "三套并行全局皮肤（切换无残留、深浅色全成立）。默认保留；液态玻璃下沉至每个按钮/输入框/卡片；3D 景深以高低错落与光影塑形真实层级。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            UiSkin.values().forEach { skin ->
                val selected = uiSkin == skin.id
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (skin == UiSkin.DEPTH) 6.dp else 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .border(
                            width = if (selected) 2.dp else 0.5.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable { viewModel.setSkin(skin.id) }
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(
                                when (skin) {
                                    UiSkin.GLASS -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    UiSkin.DEPTH -> MaterialTheme.colorScheme.primary.copy(alpha = 0.95f)
                                    else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                }
                            ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                when (skin) {
                                    UiSkin.GLASS -> Icons.Filled.AutoAwesome
                                    UiSkin.DEPTH -> Icons.Filled.ViewInAr
                                    else -> Icons.Filled.Palette
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(skin.label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(skin.desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (skin == UiSkin.GLASS) Text("按钮/输入框/卡片/列表皆为玻璃态", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                            if (skin == UiSkin.DEPTH) Text("按钮/面板呈现真实光影压差", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                        }
                        if (selected) Text("已选", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
            // 玻璃与立体的小预览：模拟按钮/输入框/卡片
            Text("预览", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Card(
                shape = RoundedCornerShape(if (UiSkin.from(uiSkin) == UiSkin.GLASS) 22.dp else if (UiSkin.from(uiSkin) == UiSkin.DEPTH) 12.dp else 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when (UiSkin.from(uiSkin)) {
                        UiSkin.GLASS -> MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
                        else -> MaterialTheme.colorScheme.surface
                    }
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = if (uiSkin == UiSkin.DEPTH.id || uiSkin == "depth") 8.dp else 0.dp),
                modifier = Modifier.fillMaxWidth().border(
                    0.5.dp,
                    when (UiSkin.from(uiSkin)) {
                        UiSkin.GLASS -> MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                        UiSkin.DEPTH -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                    },
                    RoundedCornerShape(if (UiSkin.from(uiSkin) == UiSkin.GLASS) 22.dp else if (UiSkin.from(uiSkin) == UiSkin.DEPTH) 12.dp else 16.dp)
                )
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary).padding(horizontal = 14.dp, vertical = 6.dp)
                        ) { Text("按钮", color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp) }
                        Box(
                            Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (UiSkin.from(uiSkin) == UiSkin.GLASS) 0.5f else 0.9f))
                                .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) { Text("输入框", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(10.dp)
                    ) {
                        Text("列表条目 · 卡片", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Text("高级配色", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "选一套立刻生效。每套都有独立的浅色/深色。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            ColorPalettes.all.chunked(2).forEach { row ->
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    row.forEach { pal ->
                        val selected = pal.id == paletteId
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .border(
                                    width = if (selected) 2.dp else 0.5.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable { viewModel.setPalette(pal.id) }
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(pal.swatch)
                                )
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(pal.expense)
                                )
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(pal.income)
                                )
                                Spacer(Modifier.weight(1f))
                                if (selected) {
                                    Text("已选", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(pal.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                pal.tagline,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
