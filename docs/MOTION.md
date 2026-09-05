# 动效意图与交互规范（2.21）

本文供评审：说明「为什么动、怎么动、何时不动」。实现全部走 Compose `graphicsLayer` 的 **transform / opacity**，动画在合成器线程，不改 layout 尺寸、不在动画中触发 measure。

系统「动画时长缩放 = 0」或「过渡动画缩放 = 0」时，`LocalReduceMotion = true`，所有弹簧/补间退化为瞬时 `snap`。

## 时长与缓动（统一开关）

| Token | 用途 | 规格 | 减弱动态 |
| --- | --- | --- | --- |
| `Motion.snapSpring` | 表盘指针吸附、日格弹回 | damping 0.52 / stiffness 520 | snap |
| `Motion.softSpring` | 入场上浮、输入框聚焦 | damping 0.82 / stiffness 240 | snap |
| `Motion.cursorSpring` | 品类高亮游标 | damping 0.88 / stiffness 380 | snap |
| `Motion.pressSpring` | 保存钮按下 | damping 0.72 / stiffness 700 | snap |
| `Motion.checkSpring` | 成功勾选舒展 | MediumBouncy / 420 | snap |
| `ENTER_MS = 280` | 首页上传入口淡入 | FastOutSlowIn | 0ms |
| `MONTH_MS = 340` | 月份飞入 | FastOutSlowIn | 0ms |
| `FADE_MS = 180` | 邻月格子/勾选淡化 | FastOutSlowIn | 0ms |
| `SUCCESS_MS = 420` | 保存成功停留 | — | ≥80ms 仍给状态一帧 |

禁止：动画 `height`/`width`、动画 `padding`、在 `onDraw` 里分配大对象、主线程 `Thread.sleep`。

## 记一笔

### 时间 · 经典表盘

- **意图**：像拧机械表，不是数字滚轮，也不是坐标瞬跳。
- **跟手**：分针在外圈、时针在内圈；拖动时指针 `rotationZ` 跟手指角度。
- **吸附回弹**：松手后角度弹簧到最近整分（最短弧），damping 带轻微过冲。
- **段落感**：每跨过 5 分钟给一次 `CLOCK_TICK` 触觉（减弱动态时关闭）。
- **越过 12 点**：分针顺/逆跨 12 点时小时 ±1。
- **a11y**：表盘 `contentDescription`，数字区读「当前时间 HH点MM分」。

### 日期 · 自研日历

- **月份飞入**：新月从目标方向整页滑入，旧月向反方向滑出 1/4 并淡出（视差感）。
- **日格**：选中 `scale 1 → 1.12` 弹簧放大回缩；**layout 格子尺寸不变**，只 scale。
- **离屏/邻月**：非本月格子 `alpha → 0.28`，月份切换时随 `AnimatedContent` 淡出。
- **今天**：浅底圆，不抢选中态。

### 品类 · 两行横向流

- 分类按列两两堆叠，`LazyRow` 横向流式滑动。
- **高度固定 132dp**，避免把页面纵滚的 measure 搅乱。
- **手势仲裁**：`NestedScrollConnection.onPreScroll` 在 `|dx| > |dy|` 时吃掉纵向分量，斜滑不会带动整页。
- **游标**：半透明圆角矩形用 `translationX/Y` 跟随选中格，弹簧插值。
- **视差**：列 `translationX` 随 `firstVisibleItemScrollOffset` 轻移（减弱动态时为 0）。
- **选中芯片**：`scale 1.06`，不改占位。

### 商家 / 商品

- 半高、并排双栏，同屏对比填写。
- 聚焦 `scale 1.03`（graphicsLayer），**不改高度**，IME `adjustResize` + Scaffold bottomBar 不跟键盘错位跳屏。
- IME action = Next，焦点在栏内移动。

### 保存

- **常驻底栏**，首屏零滚动可达。
- 按下 `scale 0.96` + 系统涟漪；成功 `VIRTUAL_KEY` 微震 + 勾选从 0.4 舒展到 1.0，文案淡出。
- 底栏高度固定 48dp，形变不顶内容。

## 首页

- 「上传账单」是 **图标 + 文字**，钉在 TopAppBar `actions`，不随流水列表滚走。
- 首次进入：`translationY 10 → 0` + `alpha 0 → 1`（280ms）。减弱动态时直接显示。

## 无感记账（非动效，运行时规范）

见 `docs/QA-AUDIT.md` 第 7 节。设置页展示五灯：开关 / 通知权 / 监听连接 / 保活 / 电池。

## 帧预算

- 目标 60fps；剧烈（拖表盘、月份飞入）不低于 45fps。
- 过场只动画 1–2 个 graphicsLayer 属性。
- 密度 / 横竖屏：全部 dp；IME：`windowSoftInputMode=adjustResize`，内容 `imePadding`，底栏 `navigationBarsPadding`。
