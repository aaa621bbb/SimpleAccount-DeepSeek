package com.simpleaccount.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.simpleaccount.app.ui.navigation.Routes

@Composable
fun AboutScreen(navController: NavHostController) {
    val ctx = LocalContext.current
    val info = remember {
        runCatching {
            val p = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            val code = if (android.os.Build.VERSION.SDK_INT >= 28) p.longVersionCode else p.versionCode.toLong()
            Triple(p.versionName ?: "-", code, p.packageName)
        }.getOrElse { Triple("-", 0L, ctx.packageName) }
    }
    Scaffold(
        topBar = { SettingsSubToolbar("关于", onBack = { navController.popBackStack() }) }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text("记账", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("个人账本 · 导入 · 统计 · 管家", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            InfoBlock("版本", info.first)
            InfoBlock("versionCode", info.second.toString())
            InfoBlock("包名", info.third)
            InfoBlock("Android", "API ${Build.VERSION.SDK_INT} / ${Build.VERSION.RELEASE}")
            InfoBlock("设备", "${Build.MANUFACTURER} ${Build.MODEL}")
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text("使用规范", fontWeight = FontWeight.Bold)
            Text(
                "金额以账本为准。管家写账必须真正落库；识图结果需确认后入账。账单截图只在本机发给你配置的模型，不经过本 App 的服务器。",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp),
            )
            Text(
                "打开「日期与时间选择器」",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { navController.navigate(Routes.PICKER_STYLE) }.padding(vertical = 6.dp)
            )
            Text(
                "打开「日志与排障」导出日志",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { navController.navigate(Routes.LOGS) }.padding(vertical = 6.dp)
            )
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text("开源许可", fontWeight = FontWeight.Bold)
            Text(
                "本应用以 GNU GPL v3 发布。源代码随仓库提供。第三方：AndroidX / Compose / Material 3 / OkHttp / Hilt / Room。字体与图标按各自许可。",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp),
            )
            Text("致谢", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            Text(
                "账单解析规则来自常见微信/支付宝导出格式。智谱 / DeepSeek 等模型由用户自备 Key。",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp),
            )
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text("反馈", fontWeight = FontWeight.Bold)
            Text(
                "出问题请先到「日志与排障」导出日志。源码与议题：",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp),
            )
            Text(
                "github.com/aaa621bbb/SimpleAccount-DeepSeek",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                modifier = Modifier
                    .clickable {
                        runCatching {
                            ctx.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/aaa621bbb/SimpleAccount-DeepSeek"))
                            )
                        }
                    }
                    .padding(vertical = 4.dp)
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun InfoBlock(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}
