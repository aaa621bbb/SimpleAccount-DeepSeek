package com.simpleaccount.app.ui.components

import androidx.compose.runtime.Composable

/**
 * 日期 / 时间选择器风格。形态、隐喻、手感各不相同；设置里分别切换。
 */
object PickerStyles {
    const val DATE_WHEEL = "date_wheel"
    const val DATE_FILM = "date_film"
    const val DATE_STACK = "date_stack"

    const val TIME_DIAL = "time_dial"
    const val TIME_ARC = "time_arc"
    const val TIME_FLIP = "time_flip"

    val DATE_ALL = listOf(DATE_WHEEL, DATE_FILM, DATE_STACK)
    val TIME_ALL = listOf(TIME_DIAL, TIME_ARC, TIME_FLIP)

    fun dateTitle(id: String) = when (id) {
        DATE_WHEEL -> "滚轮鼓"
        DATE_FILM -> "胶片条"
        DATE_STACK -> "月份叠卡"
        else -> id
    }

    fun dateHint(id: String) = when (id) {
        DATE_WHEEL -> "年 / 月 / 日三列滚轮，惯性吸附。快、准、不占眼。"
        DATE_FILM -> "日子像胶片横滑，月份飞入。适合「翻到那天」。"
        DATE_STACK -> "月份是叠起来的卡片，点开撒出日子。趣味优先。"
        else -> ""
    }

    fun timeTitle(id: String) = when (id) {
        TIME_DIAL -> "圆盘表"
        TIME_ARC -> "弧轨"
        TIME_FLIP -> "翻页数字"
        else -> id
    }

    fun timeHint(id: String) = when (id) {
        TIME_DIAL -> "机械表盘。外圈分、内圈时；点到即吸附，不会滑过。"
        TIME_ARC -> "一条弧轨拖到目标刻度，适合单手大拇指。"
        TIME_FLIP -> "机场翻页钟。上下拨小时和分钟，段落清晰。"
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
        else -> AnalogClockSheet(initial, onDismiss, onConfirm)
    }
}
