package com.simpleaccount.app.ui.components

import androidx.compose.runtime.Composable

/**
 * 日期 / 时间选择器。形态、隐喻、手感各不相同；设置里分别切换。
 * 合计 12 套（日期 6 + 时间 6），默认时间是 2.18 圆盘。
 */
object PickerStyles {
    const val DATE_WHEEL = "date_wheel"
    const val DATE_FILM = "date_film"
    const val DATE_STACK = "date_stack"
    const val DATE_TIMELINE = "date_timeline"
    const val DATE_BEADS = "date_beads"
    const val DATE_HORIZON = "date_horizon"

    const val TIME_DIAL = "time_dial"
    const val TIME_ARC = "time_arc"
    const val TIME_FLIP = "time_flip"
    const val TIME_BARS = "time_bars"
    const val TIME_ORBIT = "time_orbit"
    const val TIME_RULER = "time_ruler"

    val DATE_ALL = listOf(DATE_WHEEL, DATE_FILM, DATE_STACK, DATE_TIMELINE, DATE_BEADS, DATE_HORIZON)
    val TIME_ALL = listOf(TIME_DIAL, TIME_ARC, TIME_FLIP, TIME_BARS, TIME_ORBIT, TIME_RULER)

    fun dateTitle(id: String) = when (id) {
        DATE_WHEEL -> "滚轮鼓"
        DATE_FILM -> "胶片条"
        DATE_STACK -> "月份叠卡"
        DATE_TIMELINE -> "纵向时间轴"
        DATE_BEADS -> "算盘珠"
        DATE_HORIZON -> "远近地平"
        else -> id
    }

    fun dateHint(id: String) = when (id) {
        DATE_WHEEL -> "年 / 月 / 日三列滚轮，惯性吸附。快、准、不占眼。"
        DATE_FILM -> "日子像胶片横滑，月份飞入。适合「翻到那天」。"
        DATE_STACK -> "月份是叠起来的卡片，点开撒出日子。趣味优先。"
        DATE_TIMELINE -> "日子排成一条竖轴，当前那天最大，上下拨就走。"
        DATE_BEADS -> "三串珠：年、月、日。拨一颗跳一格，像拨算盘。"
        DATE_HORIZON -> "选中的那天近在眼前，前后几天退到远处。没有格子。"
        else -> ""
    }

    fun timeTitle(id: String) = when (id) {
        TIME_DIAL -> "2.18 圆盘"
        TIME_ARC -> "弧轨"
        TIME_FLIP -> "翻页数字"
        TIME_BARS -> "双柱"
        TIME_ORBIT -> "双环轨道"
        TIME_RULER -> "时间直尺"
        else -> id
    }

    fun timeHint(id: String) = when (id) {
        TIME_DIAL -> "v2.18.0 机械表盘。外圈分、内圈时；点到即吸附，拖中整分磁吸。"
        TIME_ARC -> "一条弧轨拖到目标刻度，适合单手大拇指。"
        TIME_FLIP -> "机场翻页钟。上下拨小时和分钟，段落清晰。"
        TIME_BARS -> "两根立柱，左边小时右边分钟，高低即时刻。"
        TIME_ORBIT -> "内外两环各一颗珠。内环小时，外环分钟。不是指针。"
        TIME_RULER -> "24 小时横尺，放大镜里看分钟。像在钢尺上找刻度。"
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
        else -> AnalogClockSheet(initial, onDismiss, onConfirm)
    }
}
