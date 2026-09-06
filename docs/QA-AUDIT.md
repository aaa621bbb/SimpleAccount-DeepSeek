# 2.29 全量端到端审查

审查范围：叠在已推 **2.28.0 / versionCode 45**（远程 arena `14f76a2`）上。本批收口 **2.29.0 / versionCode 46**。

交付判据 = 全仓 `app/` 审核 + 风险闭环。本环境 `ANDROID_HOME` 为空，不能编 APK / 跑真机。结论来自静态审查，每条判定都挂可追溯输入。回滚点：本分支父提交。本会话只推 `arena/01a06d4a-simpleaccount-deepseek`，不直推主干、不 force-push、不叠远程 `06808dc`。

## 0. 本批要关掉的漏点（输入）

| 漏点 | 输入位置 | 原行为 | 2.29 处置 |
| --- | --- | --- | --- |
| 商家归类只有预置 | `MerchantManageScreen` Sheet；`AgentTools.classify_merchants` 描述写死十类 | 自建分类选不到 | `CategoryPresets.union`；Sheet `LazyColumn` 分组可滚；工具描述改为「预置 ∪ 自建」 |
| 统计只能点箭头整序 | `StatsLayoutScreen` | 无拖拽、无落点预览 | 长按把手拖；Divider 落点；越界回滚；箭头留作无障碍 |
| 日志不可排障 | `LogScreen` 纯文本 | 无级别/时间分栏、无筛选 | 解析 `[DIWE] MM-dd HH:mm:ss.SSS`；Chip 筛选；关键字；导出 |
| 关于页空洞 | `AboutScreen` | 大量留白 | 版本 / versionCode / 包名 / 设备 / GPL-3 / 用法 / 反馈 |
| 记忆不能点进 | `MemoryScreen` 整日糊在一起 | 无法按条读 | 按日列表 → `##` 条目 → 正文 |
| GLM 已开思考仍报「需开启」 | `AiService.applyThinking` 同时发 `enable_thinking` + `thinking`；`writeFast` 关思考 | 刚需模型拒请求 | GLM 只发 `thinking.type`；刚需模型禁 off；设置页同步文案 |
| 换色充皮肤 | 仅 palette | 液态玻璃/景深未下沉 | `VisualStyle` 三档；`skinPanel`/`skinControl` 到卡/钮/输入/列表条 |
| 选择器设置无预览、分针误触、样式不够、版本号当名 | `PickerStyleScreen` / `AnalogClockFace` / `PickerStyles` | 6+6；`dist>0.28r`；「2.18 圆盘」 | 设置页可拖表盘+打开完整选择器；分针 ≥0.58r、时针 0.22–0.50、锁手；各 +5；更名机械表盘 |

## 1. 内核三条（2.28 不回退）

额度 500、写意图 grounding、回执 `rows_affected` 仍在。本批不改这些阈值。

写账 `writeFast`：非刚需模型仍本回合 `THINKING_OFF`；GLM 4.5/4.6/z1 走 `ThinkingPolicy.effectiveLevel` 至少 medium。

## 2. 全仓 `app/` 审核

| 链路 | 结果 | 输入 |
| --- | --- | --- |
| 商家候选 = 预置 ∪ 自建 | 通过 | `CategoryPresets.union`；`MerchantManageViewModel`；Sheet 分组；`classify_merchants` 描述 |
| 统计长按拖拽 | 通过 | `StatsLayoutScreen.detectDragGesturesAfterLongPress` |
| 日志分栏筛选导出 | 通过 | `LogScreen.parseLog` + FilterChip + `share` |
| 关于密度 | 通过 | `AboutScreen` versionName/versionCode/GPL |
| 记忆按日点进 | 通过 | `MemoryScreen` `openDate` / `parseEntries` |
| GLM 思考 | 通过 | `ThinkingPolicy` + `applyThinking` 不发 glm 的 `enable_thinking=false` |
| 三套皮肤 | 通过 | `VisualStyle`/`Skin`/`Theme`/`AppearanceScreen`/`SoftCard`/`TransactionRow`/`skinControl` |
| 选择器 11+11、预览、锁手、机械表盘 | 通过 | `PickerStyles`/`DateTimeExtraPickers`/`PickerStyleScreen`/`clockHandAt` |
| 对象级改分类 / 禁止假完成 / 识图时间 / 思考链折叠 / 流式视口 / max_tokens≤1024 / 差缺条 / caret / 商家联想 / 「固定」下架 / 多值筛选 / 闲聊不扫库 | 通过 | 2.28 未回退 |

## 3. 异常路径

| 场景 | 处理 |
| --- | --- |
| GLM 刚需 + 用户关思考 | 实际按 medium 发送，设置页说明不会再提示需开启 |
| 拖图表顺序出界 | `destRaw !in indices` 回滚 |
| 点表盘圆心 | 死区，不改针 |
| 皮肤切回默认 | Tokens/阴影/透明恢复 Default，无玻璃残留 |
| 自建分类重名预置 | union 库优先 |

## 4. 否决项核对

- 未用换配色充当重构（皮肤是玻璃/景深结构）。
- 未把日历栅格作默认。
- 「固定」仍下架。
- 写账必须落库或明示无法执行。
- 思考档用户仍可自选；刚需模型不再被强制关死。
- 工具预算未再收紧到 200/30。
- 识图 `max_tokens` 未超 1024。
- 选择器不用版本号当样式语义。

## 5. 已知限制

- 本环境无 Android SDK，不能编译/真机。安装后需核：① 自建分类出现在商家 Sheet；② 长按统计项拖到第三位落点正确；③ GLM 4.6 思考开着不再报「需开启」；④ 机械表盘拖外圈不带动时针；⑤ 玻璃皮肤下列表条半透。
- 整商户 `classify_merchants` 仍需确认（破坏性）。

## 结论

静态审查通过。版本 **2.29.0 / 46**。叠 2.28 内核。商家并集、拖拽整序、日志、关于、记忆、GLM 思考、三套皮肤、选择器扩容与预览已闭环。
