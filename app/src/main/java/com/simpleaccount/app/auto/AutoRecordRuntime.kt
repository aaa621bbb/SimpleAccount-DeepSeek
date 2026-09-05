package com.simpleaccount.app.auto

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import com.simpleaccount.app.data.repository.SettingsRepository
import com.simpleaccount.app.util.AppLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 无感记账运行时：把「开关 / 通知权 / 监听绑定 / 保活 / 电池」收成一份可观察状态。
 *
 * - 监听 [Settings.Secure.ENABLED_NOTIFICATION_LISTENERS] 变化
 * - 监听服务 onConnected / onDisconnected 上报绑定
 * - 保活服务心跳
 * - 进程被杀后由 Application / BootReceiver / 保活 START_STICKY 拉回
 */
@Singleton
class AutoRecordRuntime @Inject constructor(
    @ApplicationContext private val app: Context,
    private val settingsRepository: SettingsRepository,
) {
    private val prefs: SharedPreferences by lazy {
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    private val _health = MutableStateFlow(AutoRecordHealth())
    val health: StateFlow<AutoRecordHealth> = _health.asStateFlow()

    private val events = ArrayDeque<String>(MAX_EVENTS)
    private val main = Handler(Looper.getMainLooper())
    private var watcher: ContentObserver? = null
    private var attached = false

    @Volatile
    private var listenerBound = false

    @Volatile
    private var keepAliveRunning = false

    fun attach() {
        if (attached) {
            refresh()
            return
        }
        attached = true
        loadPersisted()
        startPermissionWatcher()
        refresh()
        if (_health.value.enabled) {
            ensurePipeline()
        }
        AppLog.i("无感记账: runtime 已挂载 ${_health.value.summary()}")
    }

    fun refresh() {
        val enabled = runCatching { settingsRepository.isAutoRecordEnabled() }.getOrDefault(false)
        val granted = isListenerGranted(app)
        val battery = isBatteryUnrestricted(app)
        publish(
            _health.value.copy(
                enabled = enabled,
                listenerGranted = granted,
                listenerBound = listenerBound,
                keepAliveRunning = keepAliveRunning,
                batteryUnrestricted = battery,
            ),
        )
    }

    fun setEnabled(enabled: Boolean) {
        refresh()
        if (enabled) ensurePipeline() else tearDown()
    }

    fun ensurePipeline() {
        refresh()
        val h = _health.value
        if (!h.enabled) return
        runCatching { AutoRecordKeepAliveService.start(app) }
            .onFailure { AppLog.w("无感记账: 启动保活失败 ${it.message}") }
        if (h.listenerGranted) {
            requestRebind()
        }
        refresh()
        logEvent("管线确保 " + _health.value.summary())
    }

    fun tearDown() {
        runCatching { AutoRecordKeepAliveService.stop(app) }
        listenerBound = false
        keepAliveRunning = false
        logEvent("管线已停止")
        refresh()
    }

    fun markListenerBound(bound: Boolean) {
        listenerBound = bound
        if (bound) {
            prefs.edit { putLong(KEY_LAST_BOUND, System.currentTimeMillis()) }
            logEvent("通知监听已连接")
        } else {
            logEvent("通知监听断开，请求重绑")
            requestRebind()
        }
        refresh()
    }

    fun markKeepAlive(running: Boolean) {
        keepAliveRunning = running
        if (running) {
            prefs.edit { putLong(KEY_LAST_KEEPALIVE, System.currentTimeMillis()) }
        }
        refresh()
    }

    fun markRecorded(desc: String) {
        prefs.edit { putLong(KEY_LAST_EVENT, System.currentTimeMillis()) }
        logEvent("已入账 $desc")
        refresh()
    }

    fun markSkipped(reason: String) {
        logEvent(reason)
        refresh()
    }

    fun markError(msg: String) {
        prefs.edit { putString(KEY_LAST_ERROR, msg.take(200)) }
        logEvent("错误 $msg")
        refresh()
    }

    fun requestRebind() {
        val cn = ComponentName(app, PaymentNotificationListenerService::class.java)
        if (Build.VERSION.SDK_INT >= 24) {
            runCatching { NotificationListenerService.requestRebind(cn) }
                .onFailure { AppLog.w("无感记账: requestRebind 失败 ${it.message}") }
        }
    }

    fun isListenerGranted(context: Context): Boolean {
        return try {
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        } catch (_: Exception) {
            false
        }
    }

    fun isBatteryUnrestricted(context: Context): Boolean {
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(context.packageName)
        } catch (_: Exception) {
            true
        }
    }

    private fun startPermissionWatcher() {
        if (watcher != null) return
        val observer = object : ContentObserver(main) {
            override fun onChange(selfChange: Boolean) {
                val granted = isListenerGranted(app)
                logEvent(if (granted) "通知权已授予" else "通知权被收回")
                refresh()
                if (granted && _health.value.enabled) requestRebind()
            }
        }
        watcher = observer
        runCatching {
            app.contentResolver.registerContentObserver(
                Settings.Secure.getUriFor("enabled_notification_listeners"),
                false,
                observer,
            )
        }
    }

    private fun loadPersisted() {
        val lastBound = prefs.getLong(KEY_LAST_BOUND, 0L)
        val lastEvent = prefs.getLong(KEY_LAST_EVENT, 0L)
        val lastError = prefs.getString(KEY_LAST_ERROR, "") ?: ""
        val stored = prefs.getString(KEY_EVENTS, "") ?: ""
        if (stored.isNotBlank()) {
            stored.split('\n').filter { it.isNotBlank() }.takeLast(MAX_EVENTS).forEach { events.addLast(it) }
        }
        _health.value = _health.value.copy(
            lastBoundAt = lastBound,
            lastEventAt = lastEvent,
            lastError = lastError,
            recentEvents = events.toList().asReversed(),
        )
    }

    private fun logEvent(msg: String) {
        val line = "${fmt.format(Date())} $msg"
        AppLog.i("无感记账: $msg")
        synchronized(events) {
            if (events.size >= MAX_EVENTS) events.removeFirst()
            events.addLast(line)
            prefs.edit { putString(KEY_EVENTS, events.joinToString("\n")) }
        }
    }

    private fun publish(next: AutoRecordHealth) {
        val lastBound = prefs.getLong(KEY_LAST_BOUND, 0L)
        val lastEvent = prefs.getLong(KEY_LAST_EVENT, 0L)
        val lastError = prefs.getString(KEY_LAST_ERROR, "") ?: ""
        val snapshot = synchronized(events) { events.toList().asReversed() }
        _health.value = next.copy(
            lastBoundAt = lastBound,
            lastEventAt = lastEvent,
            lastError = lastError,
            recentEvents = snapshot,
        )
    }

    companion object {
        private const val PREFS = "auto_record_runtime"
        private const val KEY_LAST_BOUND = "last_bound"
        private const val KEY_LAST_EVENT = "last_event"
        private const val KEY_LAST_KEEPALIVE = "last_keepalive"
        private const val KEY_LAST_ERROR = "last_error"
        private const val KEY_EVENTS = "events"
        private const val MAX_EVENTS = 24
        private val fmt = SimpleDateFormat("HH:mm:ss", Locale.CHINA)
    }
}
