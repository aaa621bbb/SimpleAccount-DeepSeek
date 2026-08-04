# SimpleAccount（记账）

一个面向个人的原生 Android 记账 App。支持手动记账、微信/支付宝账单导入、自动/辅助分类、账本、分类管理、统计报表、AI 辅助记账。

## 编译

**推荐方式：GitHub Actions 云编译（已配置）**

把本项目推送到 GitHub 仓库（`main` 分支），或手动触发 `Actions → Build APK → Run workflow`，
自动出包（JDK17 + `./gradlew assembleDebug`），在 workflow 的 Artifacts 里下载 `SimpleAccount-debug` APK。

**本地编译**

需要 JDK 17 + Android SDK（platform 34 / build-tools 34）。
```bash
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

## 技术栈
- Kotlin 1.9.22 · Jetpack Compose（BOM 2024.02）· Material3
- Room 2.6.1 · Hilt 2.50 · AGP 8.2.2 · Gradle 8.4
- POI 5.2.5（xlsx 解析）· OkHttp 4.12（AI 调用）
- EncryptedSharedPreferences（AI API Key 加密存储）
- minSdk 26 / target 34 / compileSdk 34

## 已实现功能
- **记一笔**：金额/收支切换/分类(BottomSheet 单击)/日期/商家/商品/备注；编辑复用
- **账单导入**：微信/支付宝 xlsx/csv/zip；表头语义识别；编码检测(UTF-8/GBK)；金额清洗；自动去重；自动分类（商家记忆→关键词规则→其它）；导入进度与结果页
- **账本**：按日倒序+按月分组；搜索；月份+分类筛选(AND)+清除；编辑/删除(二次确认)
- **分类管理**：支出/收入 Tab；新增自定义分类(选图标/色)；预置不可删；删除非空自动转"其它"
- **统计**：环形饼图(分类占比，月份筛选)+近12月趋势折线(不受筛选影响)；金额保留两位小数；Canvas 自绘
- **AI 辅助**：对话窗口；开关/BaseUrl/Key(掩码)/模型名设置；API Key 用 EncryptedSharedPreferences 加密存储；"帮我归类商家"批量归类 pending(≤20/批)+历史追改；失败明确提示
- **商家归类管理**：状态筛选(全部/待归类/已归类/手动)+搜索；点击选分类；历史追改只改 import 记录
- **数据管理**：导出 JSON(经 SAF)；清空所有数据(清全部表+加密Key+重建预置)
- **首页**：支出/收入/结余三卡 + 最近记录(5条)；FAB=记一笔、工具栏=导入；不显示 App 名
- **纯手动用户**首页正常显示当月数据
- 深色模式跟随系统；应用名"记账"；全中文；底部导航 5 项

## 已知 / 未完成
- 应用图标为**占位** adaptive icon（记账图形）；最终"鲸鱼娘"图标待替换为 `/res/mipmap-anydpi-v26/` + drawable。
- 不做数据还原（仅导出）。
- 图表为 Compose Canvas 自绘，未接第三方图表库。

## 项目结构
```
app/src/main/java/com/simpleaccount/app/
  MainActivity.kt / SimpleAccountApp.kt
  data/           实体 + DAO + RoomDB + 解析器 + 导入处理 + 仓库 + 服务
  di/             Hilt 模块
  ui/             theme/navigation/components + 各页面(ViewModel+Compose)
  util/           日期/金额/预置分类/关键词规则/图标映射
```
