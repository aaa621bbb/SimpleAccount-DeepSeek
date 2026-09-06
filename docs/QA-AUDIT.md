# 2.28 全量端到端审查

审查范围：叠在主干 **2.27.0 / versionCode 44**（`origin/main` = `e307080`）上。本批收口 **2.28.0 / versionCode 45**。

交付判据 = 全仓 `app/` 审核 + 风险闭环，不是局部自查。本环境 `ANDROID_HOME` 为空，不能编 APK / 跑真机。结论来自静态审查，每条判定都挂可追溯输入（文件 + 符号）。回滚点：本分支父提交。本会话只推 `arena/01a06d4a-simpleaccount-deepseek`，不直推主干、不 force-push、不叠远程 `06808dc`。

## 0. 本批要关掉的漏点（输入）

| 漏点 | 输入位置 | 原行为 | 2.28 处置 |
| --- | --- | --- | --- |
| 空工具终答假完成 | `AgentLoop.run`：`resp.toolCalls.isEmpty()` 直接 `return AgentResult(resp.content)` | 写意图模型不调工具也能把「已完成」交给用户 | 先强制再调一轮写工具；仍空则 `groundWriteReply(..., wrote=false)`，一律「账本没有改动」 |
| 查询窗 200 | `AgentTools.queryTransactions` `coerceIn(1, 200)` | 约 500 笔批量改写被截断，模型抄不全 ids | 默认 100、最大 **500**，`offset` 翻页；批量改直接 `reclassify_transactions(merchant/from_category)` |
| 无 id 改分类卡 30 | `reclassifyTransactions`：`ids.isEmpty() && targets.size > 30` 拒绝 | 商家下几十～几百笔无法一次改 | 上限 **800**；0 行回 `失败 rows_affected=0` |
| 本地 8 笔 / 240 字 | `LocalAccountant` `s.length > 240`；商家 `> 8` 拒绝 | 长指令进不了本地；批量被卡 | 写意图放宽到 4000 字；商家上限 800；金额子集 + 其余 |
| 写回执无 Rows Affected | `add/withdraw/edit/delete/update/reclassify` 只口语「已记账」 | 无法在完成前核行数 | 写路径回读 `getById`，统一 `rows_affected=N`；0 行报失败 |
| 识图「时间未知」 | `AiScreen` 无 time →「时间未知」；prompt「没有钟点可空」；`parseTimeText` 只看钟点前 ctx | 上午/下午/晚上/今天/昨天/前天大量空时间 | `DateResolver.parseTimeText` 全串 PM + 时段默认；`resolveTimeOrPeriod` 兜底 12:00；prompt 强制 HH:mm；预览不再写「时间未知」 |

## 1. 内核三条（额度 · 执行链 · 回执）

### 1.1 数据额度供给

- `AgentTools` `query_transactions`：`limit` 默认 100、`coerceIn(1, 500)`，`offset` 翻页。返回头带 `total/offset/returned`，并提示批量改不必抄流水号。
- `reclassify_transactions` 无 ids 时筛选命中最多 800 笔（覆盖「约 500 笔批量改写」）。
- `IntentGate` WRITE 加宽：`改分类/记到/算作/调成/批量改/把…改|归|算`，避免写意图被裁成闲聊、挂不上写工具。
- `pickTools(LEDGER_WRITE)` 固定挂：`add/withdraw/delete/edit/update_transaction_category/reclassify/query/list_merchants/set_merchant_category`。

### 1.2 工具执行链

- 写意图 `writeFast` → `THINKING_OFF`（不改用户设置档），`cap ≥ 4`。
- 空 `toolCalls`：若仍是 `LEDGER_WRITE` 且未强制过，注入「立刻调用写工具，禁止说已完成」再跑一轮。
- 第二轮仍空：`groundWriteReply` **不再**因正文没有「已完成」四字就放行——没写工具就只能「账本没有改动」或用户已写的「无法执行」。
- 写工具一旦执行，立刻用工具回执当终答，不再让模型润色成假成功。

### 1.3 写操作落库回执

| 工具 | 成功 | 失败 |
| --- | --- | --- |
| `add_transaction` | 插入后 `getById`，`rows_affected=1` | 读不到 → `失败 rows_affected=0` |
| `withdraw/delete` | 删后 `getById==null`，`rows_affected=1` | 仍在库 → 失败 |
| `edit/update_transaction_category` | 回读字段一致，`rows_affected=1` | 对不上 → 失败 |
| `reclassify_transactions` | 逐笔 update + 回读分类，`rows_affected=n` | n=0 或核验失败禁止报完成 |
| 本地 `parseAdd/Withdraw/Recategorize` | 同一套回读 | 同一套失败文案 |

声称完成前必须出现 `rows_affected` 且 `>0`；`=0` 对用户是失败，不是「已经改好」。

## 2. 识图相对时间

输入：`DateResolver.parseTimeText` / `resolveTimeOrPeriod` / `periodDefault`；`AiViewModel` 识图 prompt、`jsonToTx`；`AiScreen` 预览行。

- 全串识别下午/晚上/夜里（「8点下午」也会 +12）。
- 无钟点：上午 09:00、下午 15:00、晚上 20:00、早上 08:00、中午 12:00；今天/昨天/前天无钟点 → 12:00。
- `jsonToTx` 把 date/time/商家/商品拼起来解析，空则 12:00，不再 `null`。
- 入账 `commitScreenshot` 再走一遍 `resolveTimeOrPeriod`。
- 预览不再拼接「时间未知」。
- `max_tokens` 仍 ≤1024（`AiService.chatWithImage`，GLM 1210 降到 1024 再试一次）。识图最多主模型失败再回退一次，不反复烧 token。

## 3. 全仓 `app/` 审核（既有项不回退）

| 链路 | 结果 | 输入 |
| --- | --- | --- |
| 对象级改分类（流水号 / 刚才 / 商家+金额+其余） | 通过 | `LocalAccountant.parseRecategorize` + `AgentTools.reclassify` |
| 管家禁止假完成 | 通过 | 空工具 grounding + 回执核验 |
| reclassify 立刻写库 | 通过 | 对象级不走 `NEED_CONFIRM`；`classify_merchants` 仍确认 |
| 识图 JSON | 通过 | 数组/单对象/中文键/金额字符串 |
| 思考链折叠 | 通过 | `AiScreen.expandedReasoningId` 默认空；点一条只开一条；发图 `reasoning=null` |
| 流式视口 | 通过 | `stickToBottom`；上滑下探不拉回首行 |
| 识图 max_tokens | 通过 | `AiService` 上限 1024 |
| 写路径关思考不改用户档 | 通过 | `writeFast` 仅本回合 `THINKING_OFF`；设置页关/低/中/高仍在 |
| 差缺条 / 无「再记一笔」 | 通过 | 首页导入差缺/待归类，不克隆已导入 |
| 搜索 caret | 通过 | 账本筛选即时 filter + debounce 只过滤列表 |
| 商家联想 | 通过 | 前缀优先、其次包含；`MerchantMatcher` 不过度合并 |
| 「固定」口径 | 通过 | 体检模块无固定支出 |
| 账本多值筛选 | 通过 | 月/分类维内 OR、维间 AND |
| 日期/时间选择器 | 通过 | 日期 6 套非栅格默认；时间含 2.18 圆盘，设置分别开 |
| 动效 | 通过 | 合成器 `graphicsLayer` transform/opacity；`LocalReduceMotion` |
| 统计十视角 | 通过 | 互斥、非清一色条形（见 `docs/MOTION.md`） |
| 闲聊不扫库 | 通过 | `IntentGate.CHAT` → `pickTools` 空 |

## 4. 异常路径

| 场景 | 处理 |
| --- | --- |
| 模型口头「已完成」未调工具 | 强制写一轮；仍空 → 「账本没有改动」 |
| 写库后分类对不上 / 删除后仍在 | `失败 rows_affected=…`，不报成功 |
| 匹配 0 行 | `失败 rows_affected=0` |
| 商家 >800 且无 ids | 拒绝并要收窄，不整本账一刀切 |
| 识图 HTTP/超时 | 展示真实错误，不当成「图里没有账单」 |
| 相对时间 / 时段 | 解析成 HH:mm，禁止「时间未知」 |
| 新消息/发图 | `reasoning=null`，历史思考链各自折叠 |

## 5. 否决项核对

- 未用换配色充当重构。
- 未把日历栅格作默认。
- 「固定」仍下架。
- 写账必须落库或明示无法执行，禁止假完成。
- 思考档用户仍可自选；仅写回合关思考。
- 思考链不默认撑开视口。
- 工具预算未再收紧到 200/30。
- 识图 `max_tokens` 未超 1024。

## 6. 已知限制（非本批回归）

- 本环境无 Android SDK，不能编译/真机。安装后需核：① 商家下约 500 笔改分类 `rows_affected` 与账本一致；② 口头「已改好」但未调工具不得出现成功；③ 截图「昨天下午」「晚上」预览为具体时刻。
- 整商户 `classify_merchants` 仍需确认（破坏性）。

## 结论

静态审查通过。版本 **2.28.0 / 45**。内核三点（额度 500、执行链 grounding、回执 `rows_affected`）与识图相对时间已闭环。
