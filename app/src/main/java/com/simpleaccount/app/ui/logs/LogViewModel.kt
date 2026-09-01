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

data class LogUiState(
    val lines: List<String> = emptyList(),
    val files: List<File> = emptyList(),
    val fileCount: Int = 0,
    val totalSizeText: String = "0B",
    val selectedFile: File? = null,
    val fileContent: String = "",
    val error: String? = null,
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
            val totalSize = AppLog.formatSize(files.sumOf { it.length() })
            _state.value = LogUiState(lines = lines, files = files, fileCount = files.size, totalSizeText = totalSize)
        }
    }

    /** 选择某个日志文件查看内容 */
    fun openFile(file: File) {
        viewModelScope.launch {
            val content = withContext(Dispatchers.IO) { AppLog.readFile(file, 2000) }
            _state.value = _state.value.copy(selectedFile = file, fileContent = content)
        }
    }

    fun closeFile() {
        _state.value = _state.value.copy(selectedFile = null, fileContent = "")
    }

    /** 合并所有日志文件为一个文本，供分享 */
    fun share(context: Context) {
        viewModelScope.launch {
            val files = AppLog.logFiles()
            if (files.isEmpty()) return@launch
            val target = withContext(Dispatchers.IO) {
                val merged = File(context.cacheDir, "merged-log.txt")
                val sb = StringBuilder()
                files.forEach { sb.append("===== ${it.name} =====\n").append(AppLog.readFile(it, 2000)).append("\n") }
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
}
