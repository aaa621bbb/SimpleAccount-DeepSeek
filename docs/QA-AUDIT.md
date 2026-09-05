# 2.23 全量端到端审查

审查范围：管家时效与工具闭环、流式+记忆、日期/时间各 6 套（含 2.18 圆盘）、全链路体验。已知问题闭环后发 **2.23.0（versionCode 40）**。

本环境 `ANDROID_HOME` 为空，不能编 APK / 跑真机帧率。结论来自静态审查。回滚点：本分支父提交（发布前 HEAD）。本会话只推 `arena/01a06d4a-simpleaccount-deepseek`，不直推主干。

## 1. 核心链路

| 链路 | 结果 | 说明 |
| --- | --- | --- |
| 记一笔 → 金额/分类/日期/时间/商家 → 底栏保存 → 回首页 | 通过 | 保存钮在 `Scaffold.bottomBar`；`dateStyle`/`timeStyle` 已 collect |
| 日期 6 套 | 通过 | 滚轮/胶片/叠卡/时间轴/算盘珠/地平；默认滚轮 |
| 时间 6 套，默认 2.18 圆盘 | 通过 | `time_dial` 标题「2.18 圆盘」；另有弧轨/翻页/双柱/双环/直尺 |
| 圆盘拾取 / 点按 / 拖中整分磁吸 / 松手回弹 | 通过 | 命中阈值 `0.28r`；`snapTo(m*6f)` |
| 首页拍账单 → `AI_SHOT` 自动拉相册 | 通过 | `autoPickImage` 只触发一次 |
| 体检弹层分块 + 点开流水 | 通过 | 含「固定」；订阅不进风险 |
| 「撤回 12」/「撤回一笔账单」本地秒删 | 通过 | `LocalAccountant.parseWithdraw`；`withdraw` 已出 DESTRUCTIVE |
| 写工具立刻回填 | 通过 | `AgentLoop` 写操作执行完直接 `AgentResult(writeReplies)` |
| 无感：通知 → 解析 → 去重 → `SOURCE_AUTO` | 通过 | 设置页三步教程；管家可 `set_auto_record` |
| 流式 CJK 合流 | 通过 | `MarkdownText.coalesceShards` + 段内 CJK 无空格拼接 |
| 记忆按日可查 | 通过 | 设置 → 管家记忆；`MemoryStore` 按日 Markdown |

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
| deepseek-chat 思考 | 默认 `THINKING_OFF`，不开 `enable_thinking`，避免空转数分钟 |

## 3. 状态一致

- 选择器风格：`SettingsRepository` `date_picker_style` / `time_picker_style`，默认 `date_wheel` / `time_dial`。
- 体检数字：`InsightsEngine.compute` 只读当前账本本月流水。
- 无感：`AutoRecordRuntime.health` 五灯；管家开开关走同一套 `setEnabled`。
- 记忆：`MemoryStore` 按日 Markdown，设置页可查。
- 撤回：本地短语与工具 `withdraw_transaction`（id 可空）都删最新一笔。

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
- 双环：静态 Canvas 重绘珠位置，无 layout 动画。
- 目标 60fps，剧烈 ≥45fps。真机需 Systrace。

## 7. 否决项核对

- 未用换配色充当本轮重构。
- 未把日历栅格作为默认日期方案。
- 未把订阅雷达放进风险。
- 未对管家撤回走长时间思考或「无法执行」。
- 日期+时间合计 12 套（≥10），2.18 圆盘已加回并作为时间默认。

## 结论

静态全量审查通过，已知问题已闭环。版本 **2.23.0 / 40** 作为本轮稳定版提交到当前工作分支。真机：表盘点按与拖动手势仲裁、OEM 杀后台、识图一次成功率仍建议实装验证。
