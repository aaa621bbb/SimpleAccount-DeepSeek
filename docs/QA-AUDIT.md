# 2.22 全量端到端审查

审查范围：记一笔选择器（日期三套 / 时间三套）、表盘拾取、本月体检可追溯、管家撤回快闭环、首页半屏最近记录、无感教程、统计新口径。已知问题闭环后发 **2.22.0（versionCode 39）**。

本环境 `ANDROID_HOME` 为空，不能编 APK / 跑真机帧率。结论来自静态审查。回滚点：本分支父提交（发布前 HEAD）。

## 1. 核心链路

| 链路 | 结果 | 说明 |
| --- | --- | --- |
| 记一笔 → 金额/分类/日期/时间/商家 → 底栏保存 → 回首页 | 通过 | 保存钮在 `Scaffold.bottomBar` |
| 日期默认滚轮，胶片/叠卡可切换 | 通过 | `DatePickerByStyle`；日历栅格不再默认 |
| 时间默认圆盘，弧轨/翻页可切换 | 通过 | `TimePickerByStyle` |
| 圆盘拾取 / 点按 / 拖中整分磁吸 / 松手回弹 | 通过 | 命中阈值 `0.28r`；`snapTo(m*6f)` |
| 首页拍账单 → `AI_SHOT` 自动拉相册 | 通过 | `autoPickImage` 只触发一次 |
| 首页上传账单 → 导入页 | 通过 | TopAppBar 常驻 |
| 体检弹层分块 + 点开流水 | 通过 | 含「固定」；订阅不进风险 |
| 「撤回 12」/「撤回一笔」本地秒删 | 通过 | `LocalAccountant.parseWithdraw`，写操作 `THINKING_OFF` |
| 无感：通知 → 解析 → 去重 → `SOURCE_AUTO` | 通过 | 设置页三步教程 |
| 统计 柱/星期/时段 | 通过 | 与饼/折口径不重复 |

## 2. 异常路径

| 场景 | 处理 |
| --- | --- |
| 金额空 / 未选分类 / 日期非法 | ViewModel 设 `error`，不关闭页 |
| 撤回不存在的流水号 | 本地回「不在账本里」 |
| 账本空时撤回一笔 | 「没有可撤回的」 |
| 识图未配 Key | 明确去设置，不空转 |
| 识图 | 一次请求；主模型失败才回退独立识图，禁止第三次 |
| 通知未授权 | 运行态未授权，不入账 |
| 异常日 | 仅当天 > 日均×2.2 且 >80 元才进风险 |

## 3. 状态一致

- 选择器风格：`SettingsRepository` `date_picker_style` / `time_picker_style`，默认 `date_wheel` / `time_dial`。
- 体检数字：`InsightsEngine.compute` 只读当前账本本月流水。
- 无感：`AutoRecordRuntime.health` 五灯。
- 记忆：`MemoryStore` 按日 Markdown，设置页可查。

## 4. 泄漏

- 同 2.21：监听 `SupervisorJob`、保活 `Handler`、ContentObserver 均随生命周期取消。
- 选择器 Sheet 随 dismiss 释放；表盘 `Animatable` 在 Composable 内 remember。

## 5. 无障碍

- 表盘、胶片日、拍账单、上传账单有描述。
- 减弱动态：时长/过渡为 0 时全部 snap，无触觉。
- 风险文案写规则，不把信息只放在颜色里。

## 6. 帧率 / 内存

- 动效只改 transform/opacity。
- 表盘：静态 Canvas + 两根 `rotationZ`。
- 胶片：横滑 Row，月份 `AnimatedContent` 只保留进出两页。
- 目标 60fps，剧烈 ≥45fps。真机需 Systrace。

## 7. 否决项核对

- 未用换配色充当本轮重构。
- 未把日历栅格作为默认日期方案。
- 未把订阅雷达放进风险。
- 未对管家撤回走长时间思考。

## 结论

静态全量审查通过，已知问题已闭环。版本 **2.22.0 / 39** 作为本轮稳定版提交到当前工作分支。真机：表盘点按与拖动手势仲裁、OEM 杀后台、识图一次成功率仍建议实装验证。
