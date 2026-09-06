package com.simpleaccount.app.ui.components

import androidx.compose.runtime.Composable

/**
 * 日期 / 时间选择器。形态、隐喻、手感各不相同；设置里分别切换。
 * 合计 22 套（日期 11 + 时间 11），每套形态互不相同，严禁换皮。
 * 时间经典表盘已更名为“同心表盘”，避免以版本号充当语义。
 */
object PickerStyles {
    // ---- 日期 11 ----
    const val DATE_WHEEL = "date_wheel"
    const val DATE_FILM = "date_film"
    const val DATE_STACK = "date_stack"
    const val DATE_TIMELINE = "date_timeline"
    const val DATE_BEADS = "date_beads"
    const val DATE_HORIZON = "date_horizon"
    // 新增 5
    const val DATE_CASCADE = "date_cascade"
    const val DATE_SPIRAL = "date_spiral"
    const val DATE_GRID = "date_grid"
    const val DATE_DIAL_YEAR = "date_dial_year"
    const val DATE_WAVE = "date_wave"

    // ---- 时间 11 ----
    const val TIME_DIAL = "time_dial" // 同心表盘
    const val TIME_ARC = "time_arc"
    const val TIME_FLIP = "time_flip"
    const val TIME_BARS = "time_bars"
    const val TIME_ORBIT = "time_orbit"
    const val TIME_RULER = "time_ruler"
    // 新增 5
    const val TIME_WAVE = "time_wave"
    const val TIME_DRUM = "time_drum"
    const val TIME_PIE = "time_pie"
    const val TIME_BLOCKS = "time_blocks"
    const val TIME_SPECTRUM = "time_spectrum"

    val DATE_ALL = listOf(
        DATE_WHEEL, DATE_FILM, DATE_STACK, DATE_TIMELINE, DATE_BEADS, DATE_HORIZON,
        DATE_CASCADE, DATE_SPIRAL, DATE_GRID, DATE_DIAL_YEAR, DATE_WAVE
    )
    val TIME_ALL = listOf(
        TIME_DIAL, TIME_ARC, TIME_FLIP, TIME_BARS, TIME_ORBIT, TIME_RULER,
        TIME_WAVE, TIME_DRUM, TIME_PIE, TIME_BLOCKS, TIME_SPECTRUM
    )

    fun dateTitle(id: String) = when (id) {
        DATE_WHEEL -> "滚轮鼓"
        DATE_FILM -> "胶片条"
        DATE_STACK -> "月份叠卡"
        DATE_TIMELINE -> "纵向时间轴"
        DATE_BEADS -> "算盘珠"
        DATE_HORIZON -> "远近地平"
        DATE_CASCADE -> "层叠瀑布"
        DATE_SPIRAL -> "螺旋年轮"
        DATE_GRID -> "九宫格"
        DATE_DIAL_YEAR -> "年轮转盘"
        DATE_WAVE -> "波浪起伏"
        else -> id
    }

    fun dateHint(id: String) = when (id) {
        DATE_WHEEL -> "年 / 月 / 日三列滚轮，惯性吸附。快、准、不占眼。"
        DATE_FILM -> "日子像胶片横滑，月份飞入。适合「翻到那天」。"
        DATE_STACK -> "月份是叠起来的卡片，点开撒出日子。趣味优先。"
        DATE_TIMELINE -> "日子排成一条竖轴，当前那天最大，上下拨就走。"
        DATE_BEADS -> "三串珠：年、月、日。拨一颗跳一格，像拨算盘。"
        DATE_HORIZON -> "选中的那天近在眼前，前后几天退到远处。没有格子。"
        DATE_CASCADE -> "年份如瀑布层叠倾泻，月份在落水处展开成池，选中如接住一片叶子。"
        DATE_SPIRAL -> "螺旋向外展开的年轮，中心是今天，向外是未来，逆时针回溯更紧凑。"
        DATE_GRID -> "3×4 月份九宫格，点一格弹出该月日子矩阵，像日历墙。"
        DATE_DIAL_YEAR -> "外圈年份刻度如罗盘，转一年拨一月，日随月动，像调年轮。"
        DATE_WAVE -> "日子在正弦波上起伏，波峰是选中日，左右拨动如抚琴弦。"
        else -> ""
    }

    fun timeTitle(id: String) = when (id) {
        TIME_DIAL -> "同心表盘"
        TIME_ARC -> "弧轨"
        TIME_FLIP -> "翻页数字"
        TIME_BARS -> "双柱"
        TIME_ORBIT -> "双环轨道"
        TIME_RULER -> "时间直尺"
        TIME_WAVE -> "波浪拨针"
        TIME_DRUM -> "鼓面滚筒"
        TIME_PIE -> "饼切时钟"
        TIME_BLOCKS -> "方块矩阵"
        TIME_SPECTRUM -> "色谱滑杆"
        else -> id
    }

    fun timeHint(id: String) = when (id) {
        TIME_DIAL -> "同心圆机械表盘。外圈分针、内圈时针分区明确，带磁吸与触区分隔，误触率低。"
        TIME_ARC -> "一条弧轨拖到目标刻度，适合单手大拇指。"
        TIME_FLIP -> "机场翻页钟。上下拨小时和分钟，段落清晰。"
        TIME_BARS -> "两根立柱，左边小时右边分钟，高低即时刻。"
        TIME_ORBIT -> "内外两环各一颗珠。内环小时，外环分钟。不是指针。"
        TIME_RULER -> "24 小时横尺，放大镜里看分钟。像在钢尺上找刻度。"
        TIME_WAVE -> "正弦波轨迹，波峰拖动分钟，波谷切小时，如拨琴弦。"
        TIME_DRUM -> "立体鼓面，左右滚时、上下滚分，像调老式收音机。"
        TIME_PIE -> "24 切饼，每切一小时，点内环切分 4 刻度取分钟，像切蛋糕。"
        TIME_BLOCKS -> "5×12 方块矩阵，行是小时区段、列是 5 分钟块，点亮即时刻。"
        TIME_SPECTRUM -> "色谱渐变滑杆，冷色早、暖色晚，磁吸整 15 分钟。"
        else -> ""
    }

    fun normalizeDate(id: String?) = if (id in DATE_ALL) id!! else DATE_WHEEL
    fun normalizeTime(id: String?) = if (id in TIME_ALL) id!! else TIME_DIAL
}

@Composable
fun DatePickerByStyle(
    style: String,
    initialDate: String,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int, Int) -> Unit,
) {
    when (PickerStyles.normalizeDate(style)) {
        PickerStyles.DATE_FILM -> DateFilmSheet(initialDate, onDismiss, onConfirm)
        PickerStyles.DATE_STACK -> DateStackSheet(initialDate, onDismiss, onConfirm)
        PickerStyles.DATE_TIMELINE -> DateTimelineSheet(initialDate, onDismiss, onConfirm)
        PickerStyles.DATE_BEADS -> DateBeadsSheet(initialDate, onDismiss, onConfirm)
        PickerStyles.DATE_HORIZON -> DateHorizonSheet(initialDate, onDismiss, onConfirm)
        PickerStyles.DATE_CASCADE -> DateCascadeSheet(initialDate, onDismiss, onConfirm)
        PickerStyles.DATE_SPIRAL -> DateSpiralSheet(initialDate, onDismiss, onConfirm)
        PickerStyles.DATE_GRID -> DateGridSheet(initialDate, onDismiss, onConfirm)
        PickerStyles.DATE_DIAL_YEAR -> DateDialYearSheet(initialDate, onDismiss, onConfirm)
        PickerStyles.DATE_WAVE -> DateWaveSheet(initialDate, onDismiss, onConfirm)
        else -> DateWheelSheet(initialDate, onDismiss, onConfirm)
    }
}

@Composable
fun TimePickerByStyle(
    style: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    when (PickerStyles.normalizeTime(style)) {
        PickerStyles.TIME_ARC -> TimeArcSheet(initial, onDismiss, onConfirm)
        PickerStyles.TIME_FLIP -> TimeFlipSheet(initial, onDismiss, onConfirm)
        PickerStyles.TIME_BARS -> TimeBarsSheet(initial, onDismiss, onConfirm)
        PickerStyles.TIME_ORBIT -> TimeOrbitSheet(initial, onDismiss, onConfirm)
        PickerStyles.TIME_RULER -> TimeRulerSheet(initial, onDismiss, onConfirm)
        PickerStyles.TIME_WAVE -> TimeWaveSheet(initial, onDismiss, onConfirm)
        PickerStyles.TIME_DRUM -> TimeDrumSheet(initial, onDismiss, onConfirm)
        PickerStyles.TIME_PIE -> TimePieSheet(initial, onDismiss, onConfirm)
        PickerStyles.TIME_BLOCKS -> TimeBlocksSheet(initial, onDismiss, onConfirm)
        PickerStyles.TIME_SPECTRUM -> TimeSpectrumSheet(initial, onDismiss, onConfirm)
        else -> AnalogClockSheet(initial, onDismiss, onConfirm)
    }
}
