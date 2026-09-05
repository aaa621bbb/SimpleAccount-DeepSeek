package com.simpleaccount.app.auto

/**
 * 无感记账运行态快照。UI 与日志都只读这份，避免各处自己猜权限/绑定。
 */
data class AutoRecordHealth(
    val enabled: Boolean = false,
    val listenerGranted: Boolean = false,
    val listenerBound: Boolean = false,
    val keepAliveRunning: Boolean = false,
    val batteryUnrestricted: Boolean = false,
    val lastBoundAt: Long = 0L,
    val lastEventAt: Long = 0L,
    val lastError: String = "",
    val recentEvents: List<String> = emptyList(),
) {
    val pipelineReady: Boolean
        get() = enabled && listenerGranted && listenerBound && keepAliveRunning

    fun summary(): String = buildString {
        append(if (enabled) "开关开" else "开关关")
        append(" · ")
        append(if (listenerGranted) "已授权" else "未授权")
        append(" · ")
        append(if (listenerBound) "监听已连接" else "监听未连接")
        append(" · ")
        append(if (keepAliveRunning) "保活中" else "保活停")
        if (!batteryUnrestricted) append(" · 电池受限")
    }
}
