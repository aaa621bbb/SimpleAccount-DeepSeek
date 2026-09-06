package com.simpleaccount.app.ui.logs

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simpleaccount.app.util.AppLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** 单条结构化日志 */
data class ParsedLog(
    val raw: String,
    val level: Char, // D/I/W/E
    val time: String, // MM-dd HH:mm:ss.SSS
    val message: String,
)

fun parseLine(line: String): ParsedLog {
    // 格式 "[X] MM-dd HH:mm:ss.SSS message"
    return try {
        val lvl = line.getOrNull(1) ?: 'I'
        val ts = if (line.length >= 22) line.substring(3, 21).trim() else ""
        val msg = if (line.length > 22) line.substring(22).trim() else line
        ParsedLog(line, lvl, ts, msg)
    } catch (_: Exception) {
        ParsedLog(line, 'I', "", line)
    }
}

data class LogUiState(
    val lines: List<String> = emptyList(),
    val parsed: List<ParsedLog> = emptyList(),
    val files: List<File> = emptyList(),
    val fileCount: Int = 0,
    val totalSizeText: String = "0B",
    val selectedFile: File? = null,
    val fileContent: String = "",
    val filteredContent: List<ParsedLog> = emptyList(),
    val error: String? = null,
    val levelFilter: Char? = null, // null=全部
    val query: String = "",
    val tab: Int = 0, // 0=实时 1=文件
)

@HiltViewModel
class LogViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(LogUiState())
    val state = _state.asStateFlow()

    init {
        refresh()
    }

    /** 刷新内存缓冲 + 文件统计 */
    fun refresh() {
        viewModelScope.launch {
            val files = AppLog.logFiles()
            val lines = AppLog.recentLogs()
            val parsed = lines.map { parseLine(it) }
            val totalSize = AppLog.formatSize(files.sumOf { it.length() })
            _state.value = LogUiState(
                lines = lines,
                parsed = parsed,
                files = files,
                fileCount = files.size,
                totalSizeText = totalSize,
                levelFilter = _state.value.levelFilter,
                query = _state.value.query,
                tab = _state.value.tab,
                filteredContent = filterParsed(parsed, _state.value.levelFilter, _state.value.query)
            )
        }
    }

    fun setLevelFilter(level: Char?) {
        val cur = _state.value
        _state.value = cur.copy(
            levelFilter = level,
            filteredContent = filterParsed(if (cur.selectedFile == null) cur.parsed else cur.filteredContent, level, cur.query).let {
                // 若在文件视图，重新解析文件内容
                if (cur.selectedFile != null) filterParsed(cur.fileContent.lineSequence().map { parseLine(it) }.toList(), level, cur.query)
                else filterParsed(cur.parsed, level, cur.query)
            }
        )
        // 若在文件视图，重新过滤文件内容
        if (cur.selectedFile != null) {
            val parsedFile = cur.fileContent.lineSequence().map { parseLine(it) }.toList()
            _state.value = _state.value.copy(filteredContent = filterParsed(parsedFile, level, cur.query))
        } else {
            _state.value = _state.value.copy(filteredContent = filterParsed(cur.parsed, level, cur.query))
        }
    }

    fun setQuery(q: String) {
        val cur = _state.value
        _state.value = cur.copy(query = q)
        if (cur.selectedFile != null) {
            val parsedFile = cur.fileContent.lineSequence().map { parseLine(it) }.toList()
            _state.value = _state.value.copy(filteredContent = filterParsed(parsedFile, cur.levelFilter, q))
        } else {
            _state.value = _state.value.copy(filteredContent = filterParsed(cur.parsed, cur.levelFilter, q))
        }
    }

    fun setTab(tab: Int) {
        _state.value = _state.value.copy(tab = tab)
    }

    private fun filterParsed(list: List<ParsedLog>, level: Char?, query: String): List<ParsedLog> {
        var out = list
        if (level != null) out = out.filter { it.level == level }
        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            out = out.filter { it.raw.lowercase().contains(q) || it.message.lowercase().contains(q) }
        }
        return out
    }

    /** 选择某个日志文件查看内容 */
    fun openFile(file: File) {
        viewModelScope.launch {
            val content = withContext(Dispatchers.IO) { AppLog.readFile(file, 3000) }
            val parsed = content.lineSequence().map { parseLine(it) }.toList()
            _state.value = _state.value.copy(
                selectedFile = file,
                fileContent = content,
                filteredContent = filterParsed(parsed, _state.value.levelFilter, _state.value.query)
            )
        }
    }

    fun closeFile() {
        val cur = _state.value
        _state.value = cur.copy(
            selectedFile = null,
            fileContent = "",
            filteredContent = filterParsed(cur.parsed, cur.levelFilter, cur.query)
        )
    }

    /** 合并所有日志文件为一个文本，供分享 */
    fun share(context: Context) {
        viewModelScope.launch {
            val files = AppLog.logFiles()
            if (files.isEmpty()) return@launch
            val target = withContext(Dispatchers.IO) {
                val merged = File(context.cacheDir, "merged-log.txt")
                val sb = StringBuilder()
                files.forEach { sb.append("===== ${it.name} =====\n").append(AppLog.readFile(it, 3000)).append("\n") }
                merged.writeText(sb.toString())
                merged
            }
            val uri = FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                target
            )
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "SimpleAccount 日志")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "分享日志"))
        }
    }

    /** 一键导出当前筛选后的日志（实时缓冲或文件） */
    fun exportFiltered(context: Context) {
        viewModelScope.launch {
            val list = _state.value.filteredContent
            if (list.isEmpty()) return@launch
            val target = withContext(Dispatchers.IO) {
                val file = File(context.cacheDir, "filtered-log-${System.currentTimeMillis()}.txt")
                file.writeText(list.joinToString("\n") { it.raw })
                file
            }
            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", target)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "SimpleAccount 筛选日志（${list.size} 条）")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "导出筛选日志"))
        }
    }

    /** 清空内存缓冲（便于排障前后对比） */
    fun clearBuffer() {
        AppLog.clearRing()
        refresh()
    }
}
