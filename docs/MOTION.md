# 动效意图与交互规范（2.24）

本文供评审：说明「为什么动、怎么动、何时不动」。实现全部走 Compose `graphicsLayer` 的 **transform / opacity**，动画在合成器线程，不改 layout 尺寸、不在动画中触发 measure。

系统「动画时长缩放 = 0」或「过渡动画缩放 = 0」时，`LocalReduceMotion = true`，所有弹簧/补间退化为瞬时 `snap`，触觉关闭。

设计令牌见 `ui/theme/Tokens.kt`：间距、圆角、投影层级、发丝描边全 App 引用 `LocalTokens`。主光源左上，投影只表达层级。换配色不算本轮交付。

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
| `PAGE_MS = 220` | 页进入：fade + 轻微水平位移 | FastOutSlowIn | 0ms |
| `PAGE_EXIT_MS = 160` | 页退出：fade | FastOutSlowIn | 0ms |

禁止：动画 `height`/`width`、动画 `padding`、在 `onDraw` 里分配大对象、主线程 `Thread.sleep`。换配色不算重构。

## 页面流转

`NavHost` 统一 `fadeIn + slideInHorizontally(1/14 屏)` 进入、`fadeOut` 退出。只改 transform/opacity。底栏 `elevNav` 来自 Tokens。空态走 `EmptyState`，卡片走 `SoftCard`（令牌圆角 + 轻投影 + 发丝）。

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

### 弧轨 / 翻页 / 双柱 / 双环 / 直尺

同 2.23：分别为半圆弧、机场翻页、立柱高低、内外珠、24h 横尺。只滚 `translationY` / `rotationZ` / 珠位置。

## 记一笔 · 日期（六套，禁止日历栅格作主方案）

默认 **滚轮鼓**（`date_wheel`）。传统月网格日历不再作为默认。另有胶片条、月份叠卡、纵向时间轴、算盘珠、远近地平。选中用 `scale`，不改行高。

## 品类 / 商家商品 / 保存

两行横向流、高度固定；双栏聚焦 `scale 1.03` 不改高度。商家框对已建档商家 **前缀优先、其次包含** 联想，不把相近店名合成一家。保存钮常驻底栏，按下 `scale 0.96`。

## 首页

- 「拍账单」相机图标 + 「上传账单」文字，钉在 TopAppBar。
- 主卡与体检卡纵向压缩，把一半以上首屏留给最近记录。
- 上传入口首次：`translationY 10 → 0` + `alpha 0 → 1`。
- 空列表走 `EmptyState`。

## 统计

旧 8 模块保留口径。新增 10 个互斥视角，禁止再堆同质条形：

| 模块 | 图 | 增量 |
| --- | --- | --- |
| 频次曲线 | 折线（笔数） | 不是金额走势 |
| 结构演进 | 堆叠面积 | 不是单月饼 |
| 环比水位 | 零轴正负柱 | 不是一个环比数字 |
| 商户集中度 | 洛伦兹 + CR1/CR3/HHI | 不是商家排行 |
| 类目弹性 | 散点（环比×占比） | 谁跟总盘走 |
| 帕累托 | 累计曲线 + 80% 线 | 钱集中在几家 |
| 星期×时段 | 热力格子 | 交叉，不是一维条 |
| 本月日火花 | sparkline | 不是日历格子 |
| 收支流向 | 双列来源/去向 | 不是收支对照柱 |
| 结构雷达 | 双多边形 | 本月 vs 上月形状 |

## 管家

- 思考深度 **强制关闭**（`enable_thinking=false`，GLM `thinking.disabled`）。设置页不再提供档位 Chip。
- 意图/工具管线：`maxRounds=2`，写工具立刻回填。首 token 目标 <10s（工具时延不计）。
- 「记一笔 / 撤回 / 无感」走本地会计秒回。
- 流式：CJK 碎行无空格拼接。

## 账本筛选

月份、分类均为 **多值集合**。维内 OR、维间 AND。空集 = 该维全部。禁止单值互斥。

## 本月体检

「固定」口径已下架。弹层只保留 总览 / 结构 / 节奏 / 风险 / 建议。

## 帧预算

- 目标 60fps；剧烈（拖表盘、月份飞入、胶片横滑、页转场）不低于 45fps。
- 过场只动画 1–2 个 graphicsLayer 属性。
- 密度 / 横竖屏：全部 dp；IME：`windowSoftInputMode=adjustResize`，内容 `imePadding`。
- 本环境无 Android SDK，真机 GPU 分析需安装后 Systrace。
