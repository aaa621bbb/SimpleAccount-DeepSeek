package com.simpleaccount.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import com.simpleaccount.app.data.repository.SettingsRepository
import com.simpleaccount.app.ui.navigation.SimpleAccountNavHost
import com.simpleaccount.app.ui.theme.SimpleAccountTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var autoRecordRuntime: com.simpleaccount.app.auto.AutoRecordRuntime

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 全面屏：内容延伸到状态栏/导航栏后面，状态栏图标颜色自动跟随深浅色模式，
        // 修复深色模式下状态栏仍是白色小条的问题
        enableEdgeToEdge()
        setContent {
            // 外观模式：跟随系统 / 浅色 / 深色（设置里可切换，即时生效）
            val themeMode by settingsRepository.themeModeFlow.collectAsState()
            val paletteId by settingsRepository.colorPaletteFlow.collectAsState()
            val darkTheme = when (themeMode) {
                SettingsRepository.THEME_LIGHT -> false
                SettingsRepository.THEME_DARK -> true
                else -> isSystemInDarkTheme()
            }
            SimpleAccountTheme(darkTheme = darkTheme, paletteId = paletteId) {
                SimpleAccountNavHost()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::autoRecordRuntime.isInitialized) {
            autoRecordRuntime.refresh()
            if (autoRecordRuntime.health.value.enabled) {
                autoRecordRuntime.ensurePipeline()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    SimpleAccountTheme {
        SimpleAccountNavHost()
    }
}
