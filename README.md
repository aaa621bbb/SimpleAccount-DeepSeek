# SimpleAccount（记账）

一个面向个人的原生 Android 记账 App。支持手动记账、微信/支付宝账单导入、自动/辅助分类、账本、分类管理、统计报表、AI 辅助记账。


> **许可证声明（请先读）**
> 本仓库采用 **GNU GPL v3.0**（见 LICENSE），并附加**作者保留商业授权的例外条款**（见 LICENSE-ADDITIONAL-TERMS.md）：
> - 非商业用途（学习 / 研究 / 个人使用）：可自由使用、修改、分发。
> - **商业用途须事先获得作者书面授权**：未经授权，不得将本软件或衍生作品用于任何商业用途。
> - 如需商业授权，请通过 GitHub Issues 联系作者。

仓库已包含 Gradle Wrapper、依赖镜像和签名回退，**克隆后即可编译**，不必自己整理 Gradle / keystore。

## 克隆后直接编译

需要：**JDK 17** + **Android SDK（platform 34 / build-tools 34）**。Gradle 不用装，用仓库里的 `./gradlew`。

`local.properties` 不要提交（本机 SDK 路径）。可复制 `local.properties.example` 改 `sdk.dir`，或让 Android Studio 自动生成。没有 `app/debug.keystore` 时会回退到系统默认 debug 签名，**不影响编译和安装**。

### 方式 A：Android Studio（推荐）

1. 安装 [Android Studio](https://developer.android.com/studio) Hedgehog 或更新（自带 JDK 17）。
2. SDK Manager 勾选 **Android 14 (API 34)** 和 **Build-Tools 34.x**。
3. `File → Open` 选本仓库根目录（含 `settings.gradle` 的那一层）。
4. 等 Gradle Sync 结束，点 Run，或 `Build → Build APK(s)`。
5. 产物：`app/build/outputs/apk/debug/app-debug.apk`。

国内网络已在 `settings.gradle` 配了阿里云镜像，一般能直接拉依赖。

### 方式 B：命令行

```bash
git clone <本仓库> && cd SimpleAccount-DeepSeek
chmod +x gradlew

# 指定本机 SDK（二选一）
export ANDROID_HOME=$HOME/Android/Sdk          # 按实际路径改
# 或：cp local.properties.example local.properties 然后改 sdk.dir

./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

`JAVA_HOME` 指到 JDK 17。macOS 示例：`/Applications/Android Studio.app/Contents/jbr/Contents/Home`。

### 方式 C：GitHub Actions 云编译

推送任意分支，或 `Actions → Build APK → Run workflow`。JDK 17 + `./gradlew assembleDebug`，在 Artifacts 下载 `SimpleAccount-debug`。

## 技术栈
- Kotlin 1.9.22 · Jetpack Compose（BOM 2024.02）· Material3
- Room 2.6.1 · Hilt 2.50 · AGP 8.2.2 · Gradle 8.4
- POI 5.2.5（xlsx 解析）· OkHttp 4.12（AI 调用）
- EncryptedSharedPreferences（AI API Key 加密存储）
- minSdk 26 / target 34 / compileSdk 34

## 已实现功能
- **记一笔**：金额/收支切换；分类两行横向流（游标跟随）；自研日历；表盘时钟（指针吸附回弹）；商家/商品并排双栏；保存钮常驻底栏（按下形变+成功勾选）；编辑复用
- **账单导入**：微信/支付宝 xlsx/csv/zip；表头语义识别；编码检测(UTF-8/GBK)；金额清洗；自动去重；自动分类（商家记忆→关键词规则→其它）；导入进度与结果页
- **账本**：按日倒序+按月分组；搜索；月份+分类筛选(AND)+清除；编辑/删除(二次确认)
- **分类管理**：支出/收入 Tab；新增自定义分类(选图标/色)；**已有分类可二次改图标/颜色**（预置可改外观不可改名）；删除非空自动转"其它"
- **统计**：环形饼图 + 趋势 + 日历；设置里可自定义图表顺序/显隐；饼图百分比默认隐藏，点击页内展开（条数可配），不从底部弹出
- **AI 辅助**：对话窗口；开关/BaseUrl/Key(掩码)/模型名设置；API Key 用 EncryptedSharedPreferences 加密存储；"帮我归类商家"批量归类+历史追改；失败明确提示
- **本地会计（快路径）**：只答「查实数 / 记一笔」。分析、建议、怎么办、体检（AI 开启时）一律交给模型，避免模板截胡
- **本月体检**：评分等级 + 总览/结构/节奏/风险/建议，可点开看对应流水；不再灌水商家条
- **外观**：浅/深/跟随系统 + 8 套高级配色（松绿/墨兰/紫雾/赤陶/石墨/月白/绛红/海盐），即时生效
- **首页**：支出卡 + 日均燃尽（今天还能花多少）+ 可能重复记账提醒 + 再记一笔芯片
- **商家归类管理**：状态筛选(全部/待归类/已归类/手动)+搜索；点击选分类；历史追改只改 import 记录
- **数据管理**：导出 JSON(经 SAF)；清空所有数据(清全部表+加密Key+重建预置)
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
  ui/             theme/navigation/components/motion + 各页面(ViewModel+Compose)
  auto/           无感记账（监听/保活/运行态）
  util/           日期/金额/预置分类/关键词规则/图标映射
docs/             MOTION.md 动效规范 · QA-AUDIT.md 审查
```
