# 2.21 全量端到端审查

审查范围：手动记一笔、首页上传入口、无感记账运行时、动效管线。已知问题闭环后发 2.21.0（versionCode 38）。

## 1. 核心链路

| 链路 | 结果 | 说明 |
| --- | --- | --- |
| 记一笔 → 填金额/分类/日期/时间/商家商品 → 底栏保存 → 回首页 | 通过 | 保存钮在 `Scaffold.bottomBar`，首屏可达 |
| 编辑记录复用同一屏 | 通过 | `editId` 仍走 `AddTransactionViewModel.loadForEdit` |
| 表盘改时间 → HH:mm 写回 | 通过 | 松手吸附整分 |
| 日历改日期 → yyyy-MM-dd 写回 | 通过 | 自研月网格，非系统 DatePicker |
| 品类两行横滑选择 | 通过 | 固定高度 LazyRow + 游标 |
| 首页「上传账单」→ 导入页 | 通过 | TopAppBar 常驻，带文字 |
| 无感：通知 → 解析 → 去重 → `SOURCE_AUTO` 入库 | 通过 | EntryPoint 兜底注入，避免首条通知丢单 |
| 模拟测试 9 条样本 | 通过 | 设置页原入口保留 |

## 2. 异常路径

| 场景 | 处理 |
| --- | --- |
| 金额空 / 未选分类 / 日期非法 | ViewModel 设 `error`，不关闭页 |
| 通知监听未授权 | 运行态「未授权」，不入账；开开关时跳系统页 |
| 监听被系统解绑 | `onListenerDisconnected` + 保活每 4 分钟 `requestRebind` |
| 进程被杀 | FGS START_STICKY + `stopWithTask=false` + 开机/覆盖安装广播 |
| Hilt 未就绪收到通知 | `AutoRecordEntryPoint` 从 Application 取依赖 |
| 分组摘要 / ongoing 通知 | 直接跳过 |
| 非支付包名 | 包名白名单过滤 |
| 60s 同额同商 / 当天去重 | `AutoRecordManager` 原逻辑保留 |
| 保活 onDestroy 时开关仍开 | 尝试再次 `start` |

## 3. 状态一致

- 唯一真相：`AutoRecordRuntime.health`（开关、授权、绑定、保活、电池、最近事件）。
- 开关写入 `SettingsRepository` 后立刻 `runtime.setEnabled`。
- 绑定时间 / 入账时间 / 最近 24 条事件落 `auto_record_runtime` prefs，杀进程可回看。
- UI 在 `ON_RESUME` 与 ContentObserver（`enabled_notification_listeners`）上刷新。

## 4. 泄漏

- 监听服务 `SupervisorJob` 在 `onDestroy` cancel。
- 保活 `Handler` 回调在 `onDestroy` `removeCallbacks`。
- 权限 / 减弱动态的 `ContentObserver` 在 `DisposableEffect`/`attach` 生命周期内。
- 未持有 Activity 上下文；Runtime 用 `@ApplicationContext`。

## 5. 无障碍

- 表盘、日格、分类芯片、保存钮、上传账单均有 `contentDescription` / 选中态。
- 减弱动态：时长缩放或过渡缩放为 0 时全部瞬时到位，无弹性过冲、无触觉。
- 对比度沿用主题色，未把信息只放在动画里。

## 6. 中低端帧率 / 内存

- 动效只改 transform/opacity，无布局动画。
- 表盘：静态 Canvas 刻度 + 两根 `rotationZ` 指针。
- 品类：固定高度，可见列有限。
- 日历：42 格，月份切换只保留进出两页。
- 目标：主流 60fps，拖指针/切月 ≥45fps。真机 GPU 分析需安装后 Systrace；本环境无 Android SDK，静态审查通过。

## 7. 无感记账重建清单

1. **持久化状态监听**：`ENABLED_NOTIFICATION_LISTENERS` ContentObserver；绑定/入账时间写入 prefs。
2. **后台权限感知**：通知权 + 电池优化实时进 HealthCard。
3. **进程常驻协同**：前台保活（dataSync）+ 4 分钟重绑 + 开机 / 快速开机 / locked boot / 覆盖安装 + Application.onCreate + Activity.onResume `ensurePipeline`。
4. **运行态与观测**：设置页五灯 + 最近事件 + 最近错误；`AppLog` 同步打点。

## 8. 密度 / 横竖屏 / IME

- 全部 dp；横屏表盘仍 252dp 居中。
- 记一笔 `adjustResize` + 内容 `imePadding` + 底栏 `navigationBarsPadding`，商家/商品聚焦不改高度。
- 未发现会在旋转时丢掉 ViewModel 的 `SavedStateHandle` 用法回退。

## 结论

静态全量审查通过，已知问题已闭环。版本 **2.21.0 / 38** 可作为本轮稳定版提交。真机帧率与 OEM 杀后台仍建议在小米/华为各跑一次支付通知。
