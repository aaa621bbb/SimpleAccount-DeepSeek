package com.simpleaccount.app.ui.components

import androidx.compose.runtime.Composable

/**
 * 日期 / 时间选择器。形态、隐喻、手感各不相同；设置里分别切换。
 * 日期 11 + 时间 11。默认时间是机械表盘（原圆盘，不用版本号当名字）。
 */
object PickerStyles {
    const val DATE_WHEEL = "date_wheel"
    const val DATE_FILM = "date_film"
    const val DATE_STACK = "date_stack"
    const val DATE_TIMELINE = "date_timeline"
    const val DATE_BEADS = "date_beads"
    const val DATE_HORIZON = "date_horizon"
    const val DATE_FAN = "date_fan"
    const val DATE_SPIRAL = "date_spiral"
    const val DATE_PILLARS = "date_pillars"
    const val DATE_COMPASS = "date_compass"
    const val DATE_LEDGER = "date_ledger"

    const val TIME_DIAL = "time_dial"
    const val TIME_ARC = "time_arc"
    const val TIME_FLIP = "time_flip"
    const val TIME_BARS = "time_bars"
    const val TIME_ORBIT = "time_orbit"
    const val TIME_RULER = "time_ruler"
    const val TIME_SAND = "time_sand"
    const val TIME_DRUM = "time_drum"
    const val TIME_SUNDIAL = "time_sundial"
    const val TIME_METRONOME = "time_metronome"
    const val TIME_BLOCKS = "time_blocks"

    val DATE_ALL = listOf(
        DATE_WHEEL, DATE_FILM, DATE_STACK, DATE_TIMELINE, DATE_BEADS, DATE_HORIZON,
        DATE_FAN, DATE_SPIRAL, DATE_PILLARS, DATE_COMPASS, DATE_LEDGER,
    )
    val TIME_ALL = listOf(
        TIME_DIAL, TIME_ARC, TIME_FLIP, TIME_BARS, TIME_ORBIT, TIME_RULER,
        TIME_SAND, TIME_DRUM, TIME_SUNDIAL, TIME_METRONOME, TIME_BLOCKS,
    )

    fun dateTitle(id: String) = when (id) {
        DATE_WHEEL -> "滚轮鼓"
        DATE_FILM -> "胶片条"
        DATE_STACK -> "月份叠卡"
        DATE_TIMELINE -> "纵向时间轴"
        DATE_BEADS -> "算盘珠"
        DATE_HORIZON -> "远近地平"
        DATE_FAN -> "扇骨"
        DATE_SPIRAL -> "螺线"
        DATE_PILLARS -> "三柱碑"
        DATE_COMPASS -> "罗盘"
        DATE_LEDGER -> "账页"
        else -> id
    }

    fun dateHint(id: String) = when (id) {
        DATE_WHEEL -> "年 / 月 / 日三列滚轮，惯性吸附。快、准、不占眼。"
        DATE_FILM -> "日子像胶片横滑，月份飞入。适合「翻到那天」。"
        DATE_STACK -> "月份是叠起来的卡片，点开撒出日子。趣味优先。"
        DATE_TIMELINE -> "日子排成一条竖轴，当前那天最大，上下拨就走。"
        DATE_BEADS -> "三串珠：年、月、日。拨一颗跳一格，像拨算盘。"
        DATE_HORIZON -> "选中的那天近在眼前，前后几天退到远处。没有格子。"
        DATE_FAN -> "十二根扇骨是月份，日子挂在展开的那根上。"
        DATE_SPIRAL -> "日子从中心旋出，点哪天就旋到哪天。"
        DATE_PILLARS -> "三根石柱高低即年、月、日。像纪念碑。"
        DATE_COMPASS -> "外圈月份、内圈日子。不是日历栅格。"
        DATE_LEDGER -> "一页一天，左右翻账本。"
        else -> ""
    }

    fun timeTitle(id: String) = when (id) {
        TIME_DIAL -> "机械表盘"
        TIME_ARC -> "弧轨"
        TIME_FLIP -> "翻页数字"
        TIME_BARS -> "双柱"
        TIME_ORBIT -> "双环轨道"
        TIME_RULER -> "时间直尺"
        TIME_SAND -> "沙漏"
        TIME_DRUM -> "双鼓"
        TIME_SUNDIAL -> "日晷"
        TIME_METRONOME -> "节拍器"
        TIME_BLOCKS -> "四块积木"
        else -> id
    }

    fun timeHint(id: String) = when (id) {
        TIME_DIAL -> "机械表盘。外圈分（≥0.58r）、内圈时（0.22–0.50r）；拖中锁手，不串针。"
        TIME_ARC -> "一条弧轨拖到目标刻度，适合单手大拇指。"
        TIME_FLIP -> "机场翻页钟。上下拨小时和分钟，段落清晰。"
        TIME_BARS -> "两根立柱，左边小时右边分钟，高低即时刻。"
        TIME_ORBIT -> "内外两环各一颗珠。内环小时，外环分钟。不是指针。"
        TIME_RULER -> "24 小时横尺，放大镜里看分钟。像在钢尺上找刻度。"
        TIME_SAND -> "沙子高度是分钟，点两侧漏斗换小时。"
        TIME_DRUM -> "两个滚筒，像老虎机转出时和分。"
        TIME_SUNDIAL -> "影子扫过晷面改分钟，圆心附近改小时。"
        TIME_METRONOME -> "摆锤左右是分钟，底座是小时。"
        TIME_BLOCKS -> "四块数字积木各自加减，像车站翻牌但按块点。"
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
        PickerStyles.DATE_FAN -> DateFanSheet(initialDate, onDismiss, onConfirm)
        PickerStyles.DATE_SPIRAL -> DateSpiralSheet(initialDate, onDismiss, onConfirm)
        PickerStyles.DATE_PILLARS -> DatePillarsSheet(initialDate, onDismiss, onConfirm)
        PickerStyles.DATE_COMPASS -> DateCompassSheet(initialDate, onDismiss, onConfirm)
        PickerStyles.DATE_LEDGER -> DateLedgerSheet(initialDate, onDismiss, onConfirm)
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
        PickerStyles.TIME_SAND -> TimeSandSheet(initial, onDismiss, onConfirm)
        PickerStyles.TIME_DRUM -> TimeDrumSheet(initial, onDismiss, onConfirm)
        PickerStyles.TIME_SUNDIAL -> TimeSundialSheet(initial, onDismiss, onConfirm)
        PickerStyles.TIME_METRONOME -> TimeMetronomeSheet(initial, onDismiss, onConfirm)
        PickerStyles.TIME_BLOCKS -> TimeBlocksSheet(initial, onDismiss, onConfirm)
        else -> AnalogClockSheet(initial, onDismiss, onConfirm)
    }
}
