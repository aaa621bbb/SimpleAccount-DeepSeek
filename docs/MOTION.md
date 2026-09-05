# 动效意图与交互规范（2.23）

本文供评审：说明「为什么动、怎么动、何时不动」。实现全部走 Compose `graphicsLayer` 的 **transform / opacity**，动画在合成器线程，不改 layout 尺寸、不在动画中触发 measure。

系统「动画时长缩放 = 0」或「过渡动画缩放 = 0」时，`LocalReduceMotion = true`，所有弹簧/补间退化为瞬时 `snap`，触觉关闭。

## 时长与缓动（统一开关）

| Token | 用途 | 规格 | 减弱动态 |
| --- | --- | --- | --- |
| `Motion.snapSpring` | 表盘指针吸附、日格/胶片选中弹回 | damping 0.52 / stiffness 520 | snap |
| `Motion.softSpring` | 入场上浮、叠卡/地平展开 | damping 0.82 / stiffness 240 | snap |
| `Motion.cursorSpring` | 品类高亮游标 | damping 0.88 / stiffness 380 | snap |
| `Motion.pressSpring` | 保存钮按下 | damping 0.72 / stiffness 700 | snap |
| `Motion.checkSpring` | 成功勾选舒展 | MediumBouncy / 420 | snap |
| `ENTER_MS = 280` | 首页上传入口淡入 | FastOutSlowIn | 0ms |
| `MONTH_MS = 340` | 月份飞入 / 胶片换月 | FastOutSlowIn | 0ms |
| `FADE_MS = 180` | 邻月格子/勾选淡化 | FastOutSlowIn | 0ms |
| `SUCCESS_MS = 420` | 保存成功停留 | — | ≥80ms 仍给状态一帧 |

禁止：动画 `height`/`width`、动画 `padding`、在 `onDraw` 里分配大对象、主线程 `Thread.sleep`。换配色不算重构。

## 记一笔 · 时间（六套，设置分别开）

默认 **2.18 圆盘**（`time_dial`）。记一笔按设置分发，不在页内再选一次。

### 2.18 圆盘（`time_dial`）

- **意图**：像拧机械表。外圈分、内圈时。v2.18.0 形态原样加回。
- **命中**：半径 28% 以外都算分针，点到即命中；点内圈改小时。
- **跟手**：拖动时指针 `rotationZ` 跟手指角度；拖中磁吸整分（`snapTo(m*6f)`）。
- **吸附回弹**：松手后最短弧弹簧到整分，damping 带轻微过冲。
- **点按即设**：轻点外圈直接设分钟，不必先拖。
- **段落感**：每跨过 5 分钟 `CLOCK_TICK`（减弱动态时关）。
- **越过 12 点**：分针顺/逆跨 12 点时小时 ±1。

### 弧轨（`time_arc`）

- 单手大拇指拖一条半圆弧到目标刻度。没有指针，只有轨道和拇指点。

### 翻页数字（`time_flip`）

- 机场翻页钟。小时、分钟两列滚轮，惯性吸附。只滚 `translationY`。

### 双柱（`time_bars`）

- 两根立柱，左边小时右边分钟。高低即时刻。拖/点柱体改值。

### 双环轨道（`time_orbit`）

- 内外两环各一颗珠。内环小时，外环分钟。**不是指针**，珠子在环上走。

### 时间直尺（`time_ruler`）

- 24 小时横尺 + 分钟刻度。像在钢尺上找刻度。

## 记一笔 · 日期（六套，禁止日历栅格作主方案）

默认 **滚轮鼓**（`date_wheel`）。传统月网格日历不再作为默认。

### 滚轮鼓

- 年 / 月 / 日三列，惯性吸附。快、准、不占眼。列高固定，只滚内容。

### 胶片条（`date_film`）

- 日子像胶片横滑；换月整页水平飞入（`MONTH_MS`）。选中日 `scale 1.12`，layout 占位不变。

### 月份叠卡（`date_stack`）

- 近几个月叠成卡片，点一张展开日子。展开用 `scale` + 内容显隐。

### 纵向时间轴（`date_timeline`）

- 日子排成一条竖轴，当前那天最大，上下拨就走。选中用 `scale`，不改行高。

### 算盘珠（`date_beads`）

- 三串珠：年、月、日。拨一颗跳一格。圆点 `clip(CircleShape)`，选中放大。

### 远近地平（`date_horizon`）

- 选中的那天近在眼前，前后几天退到远处。`scale` + `alpha`，没有格子。

## 品类 / 商家商品 / 保存

同 2.21：两行横向流、高度固定；双栏聚焦 `scale 1.03` 不改高度；保存钮常驻底栏，按下 `scale 0.96`。

## 首页

- 「拍账单」相机图标 + 「上传账单」文字，钉在 TopAppBar，不随流水滚走。
- 主卡与体检卡纵向压缩，把 **一半以上首屏** 留给最近记录（`LazyColumn.weight(1f)`）。
- 上传入口首次：`translationY 10 → 0` + `alpha 0 → 1`。

## 统计

- 饼：宽环 + 段间留白 + 内圈高光。
- 折：平滑曲线 + 渐变填充；点选月份。
- 柱 / 星期 / 时段：新口径，不重复饼/折已经表达的「构成」和「走势」。

## 管家

- 「记一笔 / 撤回 / 无感」走本地会计秒回，不进思考深度。
- 写工具执行完立刻回填工具结果，不再空转一轮「正在思考」。
- 流式：CJK 碎行无空格拼接；一字一行的碎片会合流成句。

## 无感记账

非动效。设置页图文三步 + 五灯运行态。见 `docs/QA-AUDIT.md`。

## 帧预算

- 目标 60fps；剧烈（拖表盘、月份飞入、胶片横滑、珠子走环）不低于 45fps。
- 过场只动画 1–2 个 graphicsLayer 属性。
- 密度 / 横竖屏：全部 dp；IME：`windowSoftInputMode=adjustResize`，内容 `imePadding`，底栏 `navigationBarsPadding`。
- 本环境无 Android SDK，真机 GPU 分析需安装后 Systrace。
