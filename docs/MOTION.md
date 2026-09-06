# 动效意图与交互规范（2.29）

本文供评审：说明「为什么动、怎么动、何时不动」。实现全部走 Compose `graphicsLayer` 的 **transform / opacity**，动画在合成器线程，不改 layout 尺寸、不在动画中触发 measure。

系统「动画时长缩放 = 0」或「过渡动画缩放 = 0」时，`LocalReduceMotion = true`，所有弹簧/补间退化为瞬时 `snap`，触觉关闭。

设计令牌见 `ui/theme/Tokens.kt`：间距、圆角、投影层级、发丝描边全 App 引用 `LocalTokens`。主光源左上，投影只表达层级。换配色不算本轮交付。三套皮肤（`VisualStyle`）改的是面板透明度、高光描边、塑形阴影，不改业务 layout。

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

`NavHost` 统一 `fadeIn + slideInHorizontally(1/14 屏)` 进入、`fadeOut` 退出。只改 transform/opacity。底栏 `elevNav` 来自 Tokens。空态走 `EmptyState`，卡片走 `SoftCard`（`skinPanel`：玻璃半透+高光，景深双层阴影）。列表条、输入、按钮走 `skinControl`。

## 记一笔 · 时间（十一套，设置分别开）

默认 **机械表盘**（`time_dial`）。记一笔按设置分发，不在页内再选一次。设置页可当场拖表盘。

### 机械表盘（`time_dial`）

- **意图**：像拧机械表。外圈分、内圈时。不用版本号当名字。
- **命中**：分针圈 ≥ ~0.58r；时针 0.22–0.50r。圆心与 0.50–0.58 缝为死区。
- **锁手**：`onDragStart` 命中哪根针，整段拖动不换针。
- **跟手**：拖动时指针 `rotationZ` 跟手指角度；拖中磁吸整分。
- **吸附回弹**：松手后最短弧弹簧到整分。
- **越过 12 点**：分针顺/逆跨 12 点时小时 ±1。

### 弧轨 / 翻页 / 双柱 / 双环 / 直尺 / 沙漏 / 双鼓 / 日晷 / 节拍器 / 积木

形态与隐喻互异：半圆弧、机场翻页、立柱高低、内外珠、24h 横尺、沙子高度、滚筒、晷影、摆锤、四块数字。只滚 `translationY` / `rotationZ` / 珠位置 / 填充高度。

## 记一笔 · 日期（十一套，禁止日历栅格作主方案）

默认 **滚轮鼓**。另有胶片条、月份叠卡、纵向时间轴、算盘珠、远近地平、扇骨、螺线、三柱碑、罗盘、账页。选中用 `scale`，不改行高。

## 品类 / 商家商品 / 保存

两行横向流、高度固定；双栏聚焦 `scale 1.03` 不改高度。商家框对已建档商家 **前缀优先、其次包含** 联想。保存钮常驻底栏，按下 `scale 0.96`，并走 `skinControl`。

## 皮肤

| 档 | 卡片 | 按钮/输入/列表 |
| --- | --- | --- |
| 现行默认 | 令牌圆角 + 轻投影 + 发丝 | 平面 |
| 液态玻璃 | 半透明填充 + 模糊 + 内高光描边 | 发丝高光边 |
| 景深立体 | 双层偏移阴影 + 明暗描边 | `shadowElevation` + 微抬 |

切肤立刻换 `LocalVisualStyle` + Tokens，不残留上一档投影。深浅色各自成立。

## 统计排序

长按把手：`detectDragGesturesAfterLongPress`。拖中行 `translationY` + 落点 Divider 预览。松手落点 `reorder`；`destRaw` 越界则回滚。箭头「上/下」保留。

## 首页

- 「拍账单」相机图标 + 「上传账单」文字，钉在 TopAppBar。
- 主卡与体检卡纵向压缩。空列表走 `EmptyState`。
- **不再**横向「再记一笔」芯片。

## 统计视角

十个互斥视角，禁止再堆同质条形。见 2.28 表。

## 管家

- 思考深度用户自选（关/低/中/高）。GLM 只发 `thinking.type`；刚需模型 `off` 提升为 `medium`，不发 `enable_thinking=false`。
- 写账回合：非刚需模型 `THINKING_OFF`；刚需模型保持用户档（至少 medium）。
- 写工具立刻回填并带 `rows_affected`。查询窗最大 500；无 id 改分类上限 800。
- 识图 `max_tokens` 上限 1024。相对时间解析成 HH:mm。
- 流式：贴底跟随；用户上滑后不再拉回首行。

## 账本筛选

月份、分类均为 **多值集合**。维内 OR、维间 AND。搜索框绑即时 `filter`。

## 本月体检

「固定」口径已下架。弹层只保留 总览 / 结构 / 节奏 / 风险 / 建议。

## 帧预算

- 目标 60fps；剧烈（拖表盘、拖图表顺序、月份飞入、胶片横滑、页转场）不低于 45fps。
- 过场只动画 1–2 个 graphicsLayer 属性。
- 密度 / 横竖屏：全部 dp；IME：`windowSoftInputMode=adjustResize`，内容 `imePadding`。
- 本环境无 Android SDK，真机 GPU 分析需安装后 Systrace。
