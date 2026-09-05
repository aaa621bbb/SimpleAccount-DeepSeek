package com.simpleaccount.app.util

import android.content.Context
import android.util.Log
import android.os.Process
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * 轻量日志工具：同时输出到 Logcat 和 App 私有目录日志文件。
 * 目的：不需要 USB 调试/adb，就能在手机上抓取日志（供排障/开发）。
 *
 * 日志文件位置：Context.filesDir/logs/app-yyyy-MM-dd.log（按天分文件）
 * 仅保留最近 KEEP_DAYS 天的日志，自动清理更早文件。
 */
object AppLog {

    private const val TAG = "SimpleAccount"
    private const val DROPBOX_DIR = "logs"
    private const val KEEP_DAYS = 7
    private const val MAX_BUFFER_LINES = 500
    private const val MAX_FILE_KB = 512

    private var appContext: Context? = null

    /** 内存环形缓冲，供"最近日志"页面无需读文件快速展示 */
    private val ring = ConcurrentLinkedQueue<String>()

    fun init(context: Context) {
        appContext = context.applicationContext
        ensureDir()
        pruneOldLogs()
        Log.d(TAG, "[AppLog] initialized")
    }

    // ---------------- 主接口 ----------------

    fun d(msg: String) = log('D', msg)
    fun i(msg: String) = log('I', msg)
    fun w(msg: String) = log('W', msg)
    fun e(msg: String, t: Throwable? = null) = log('E', msg + if (t != null) "\n" + stackTrace(t) else "")

    private fun log(level: Char, msg: String) {
        when (level) {
            'D' -> Log.d(TAG, msg)
            'I' -> Log.i(TAG, msg)
            'W' -> Log.w(TAG, msg)
            'E' -> Log.e(TAG, msg)
        }
        val line = "[${level}] ${ts()} $msg"
        ring.offer(line)
        // 防内存无界增长
        while (ring.size > MAX_BUFFER_LINES) ring.poll()
        writeLine(line)
    }

    // ---------------- 崩溃捕获 ----------------

    fun installCrashHandler() {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                log('E', "CRASH on thread=${thread.name}\n" + stackTrace(throwable))
            } catch (_: Throwable) {
            }
            // 继续交给系统/原有 handler 处理（避免吞掉崩溃导致无响应）
            prev?.uncaughtException(thread, throwable) ?: Process.killProcess(Process.myPid())
        }
    }

    // ---------------- private ----------------

    private fun ts(): String =
        SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())

    private fun stackTrace(t: Throwable): String {
        val sw = java.io.StringWriter()
        t.printStackTrace(java.io.PrintWriter(sw))
        return sw.toString()
    }

    private fun ensureDir() {
        appContext?.let {
            val dir = File(it.filesDir, DROPBOX_DIR)
            if (!dir.exists()) dir.mkdirs()
        }
    }

    private fun logFile(): File? {
        appContext?.let {
            val name = "app-" + SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) + ".log"
            return File(File(it.filesDir, DROPBOX_DIR), name)
        }
        return null
    }

    @Synchronized
    private fun writeLine(line: String) {
        try {
            val f = logFile() ?: return
            val append = f.exists() && f.length() < MAX_FILE_KB * 1024
            if (!append && f.exists()) {
                // 单文件超限：改名加序号，重新开新文件
                f.renameTo(File(f.parentFile, f.nameWithoutExtension + "-prev.log"))
            }
            f.appendText(line + "\n")
        } catch (_: Throwable) {
        }
    }

    private fun pruneOldLogs() {
        try {
            val ctx = appContext ?: return
            val dir = File(ctx.filesDir, DROPBOX_DIR)
            if (!dir.exists()) return
            val cutoff = System.currentTimeMillis() - KEEP_DAYS * 24 * 60 * 60 * 1000L
            dir.listFiles()?.forEach { file ->
                if (file.isFile && file.lastModified() < cutoff) {
                    file.delete()
                }
            }
        } catch (_: Throwable) {
        }
    }

    // ---------------- 对外查询 ----------------

    /** 最近日志（内存缓冲，最多 MAX_BUFFER_LINES 行） */
    fun recentLogs(): List<String> = ring.toList().takeLast(MAX_BUFFER_LINES)

    /** 低内存时清空环形缓冲（onTrimMemory 钩子调用） */
    @Synchronized
    fun clearRing() {
        ring.clear()
    }

    /** 获取日志目录下的全部日志文件，按修改时间倒序 */
    fun logFiles(): List<File> {
        val ctx = appContext ?: return emptyList()
        val dir = File(ctx.filesDir, DROPBOX_DIR)
        return dir.listFiles()?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    /** 读取指定日志文件内容（限制行数避免 OOM） */
    fun readFile(file: File, maxLines: Int = 2000): String {
        return try {
            file.readLines().takeLast(maxLines).joinToString("\n")
        } catch (_: Throwable) {
            ""
        }
    }

    /**
     * 字节数 → 人类可读大小。修复"日志老是 0KB"的显示问题：
     * 之前用整数除法 length/1024，小于 1KB 的文件（新一天的日志通常只有几百字节）全部显示成 0KB。
     */
    fun formatSize(bytes: Long): String {
        return when {
            bytes < 0 -> "0B"
            bytes < 1024 -> "${bytes}B"
            bytes < 1024 * 1024 -> {
                val kb = bytes / 1024.0
                if (kb >= 100) "${kb.toInt()}KB" else "%.1fKB".format(kb)
            }
            else -> "%.1fMB".format(bytes / 1024.0 / 1024.0)
        }
    }
}
